package com.riprod.patchly.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.directive.ElementDirective;
import com.riprod.patchly.core.directive.ObjectDirective;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.core.directive.RootDirective;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MergeEngine implements MergeContext {
    private final MergeTable table;
    private final PatchContext patchContext;
    private final ImportResolver importResolver;
    private final String fromTarget;
    private final List<String> path = new ArrayList<>();

    public MergeEngine(@Nonnull MergeTable table, @Nonnull PatchContext patchContext) {
        this(table, patchContext, null, "");
    }

    public MergeEngine(@Nonnull MergeTable table, @Nonnull PatchContext patchContext,
            @Nullable ImportResolver importResolver, @Nonnull String fromTarget) {
        this.table = table;
        this.patchContext = patchContext;
        this.importResolver = importResolver;
        this.fromTarget = fromTarget;
    }

    @Override
    public void mergeObject(@Nonnull JsonObject target, @Nonnull JsonObject patch) {
        for (ObjectDirective d : table.directives().objectDirectives()) {
            JsonElement marker = patch.get(d.markerKey());
            if (marker != null) d.apply(target, marker, this);
        }
        List<Resolved> entries = new ArrayList<>(patch.size());
        for (String key : patch.keySet()) {
            if (key.startsWith(MetaKeys.PREFIX)) continue;
            entries.add(new Resolved(key, table.operatorFor(key)));
        }
        entries.sort(Comparator.comparingInt(e -> e.operator.phase()));
        for (Resolved e : entries) {
            String baseKey = table.baseKey(e.key, e.operator);
            e.operator.apply(target, baseKey, patch.get(e.key), this);
        }
    }

    @Override
    public void mergeObject(@Nonnull JsonObject target, @Nonnull JsonObject patch, @Nonnull String key) {
        path.add(key);
        try {
            mergeObject(target, patch);
        } finally {
            path.remove(path.size() - 1);
        }
    }

    @Nonnull
    @Override
    public List<String> currentPath() {
        return List.copyOf(path);
    }

    @Nullable
    @Override
    public JsonObject resolveImport(@Nonnull String ref) {
        return importResolver == null ? null : importResolver.resolve(fromTarget, ref);
    }

    private record Resolved(String key, MergeOperator operator) {}

    @Override
    public void mergeAtIndex(@Nonnull JsonArray base, int index, @Nonnull JsonElement element) {
        if (index < base.size()) {
            JsonElement baseEl = base.get(index);
            if (baseEl.isJsonObject() && element.isJsonObject()) {
                mergeObject(baseEl.getAsJsonObject(), element.getAsJsonObject());
            } else {
                base.set(index, element.deepCopy());
            }
        } else {
            base.add(element.deepCopy());
        }
    }

    @Override
    public void runArrayMerge(@Nonnull JsonObject target, @Nonnull String baseKey,
                              @Nonnull JsonArray patchArray, @Nonnull MergeOperator operator) {
        JsonElement existing = target.get(baseKey);
        JsonArray base = (existing != null && existing.isJsonArray())
                ? existing.getAsJsonArray()
                : new JsonArray();
        if (existing == null || !existing.isJsonArray()) {
            target.add(baseKey, base);
        }

        for (int i = 0; i < patchArray.size(); i++) {
            JsonElement patchEl = patchArray.get(i);
            if (patchEl.isJsonObject() && isGatedOut(patchEl.getAsJsonObject())) continue;

            LocatorPlan plan = resolveLocator(patchEl, base);
            if (plan != null) {
                MetaKeys.strip(plan.cleanPayload());
                if (plan.matched()) {
                    operator.onLocatorHit(base, plan.targetIndices(), plan.cleanPayload(), this);
                } else {
                    operator.onLocatorMiss(base, i, plan.cleanPayload(), this);
                }
            } else if (patchEl.isJsonObject()) {
                JsonObject stripped = patchEl.getAsJsonObject().deepCopy();
                MetaKeys.strip(stripped);
                operator.onPlainElement(base, i, stripped, this);
            } else {
                operator.onPlainElement(base, i, patchEl, this);
            }
        }
    }

    @Override
    public boolean isGatedOut(@Nonnull JsonObject patchObject) {
        for (RootDirective rd : table.directives().rootDirectives()) {
            JsonElement value = patchObject.get(rd.markerKey());
            if (value != null && !rd.keep(value, patchContext)) return true;
        }
        return false;
    }

    @Nullable
    @Override
    public LocatorPlan resolveLocator(@Nonnull JsonElement element, @Nonnull JsonArray base) {
        if (!element.isJsonObject()) return null;
        JsonObject obj = element.getAsJsonObject();
        ElementDirective found = null;
        for (String key : obj.keySet()) {
            if (key.isEmpty() || key.charAt(0) != '$') continue;
            ElementDirective d = table.elementDirective(key);
            if (d == null) continue;
            if (found != null) {
                throw new MergeException(
                        "element carries multiple locator markers: " + found.markerKey() + " and " + key);
            }
            found = d;
        }
        if (found == null) return null;
        return found.locate(obj, base);
    }

    @Override
    public boolean hasLocatorMarker(@Nonnull JsonElement element) {
        if (!element.isJsonObject()) return false;
        for (String key : element.getAsJsonObject().keySet()) {
            if (!key.isEmpty() && key.charAt(0) == '$' && table.elementDirective(key) != null) return true;
        }
        return false;
    }
}

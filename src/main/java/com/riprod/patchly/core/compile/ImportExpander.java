package com.riprod.patchly.core.compile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.JsonDeepMerge;
import com.riprod.patchly.core.MergeOperator;
import com.riprod.patchly.core.MergeTable;
import com.riprod.patchly.core.MetaKeys;
import com.riprod.patchly.core.directive.ImportDirective;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.core.directive.RootDirective;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ImportExpander {
    private final MergeTable table;
    private final PatchContext ctx;
    private final BaseResolver baseResolver;
    private final AssetIndex index;
    private final Map<String, List<JsonObject>> putsByTarget;
    private final List<CompileResult.UnresolvedImport> unresolved;
    private final Collection<ImportDirective> importDirectives;
    private final Map<String, JsonObject> snapshots = new HashMap<>();

    public ImportExpander(@Nonnull MergeTable table, @Nonnull PatchContext ctx,
            @Nonnull BaseResolver baseResolver, @Nullable AssetIndex index,
            @Nonnull Map<String, List<JsonObject>> putsByTarget,
            @Nonnull List<CompileResult.UnresolvedImport> unresolved) {
        this.table = table;
        this.ctx = ctx;
        this.baseResolver = baseResolver;
        this.index = index;
        this.putsByTarget = putsByTarget;
        this.unresolved = unresolved;
        this.importDirectives = table.directives().importDirectives();
    }

    // the import-only layer for `patch`: imported content with no literal keys of the patch itself.
    // a deeper $Import overrides a shallower one because child layers merge over this node's own.
    @Nonnull
    public JsonObject importLayer(@Nonnull JsonObject patch, @Nonnull String fromTarget) {
        return expand(patch, fromTarget, new ArrayDeque<>());
    }

    @Nonnull
    private JsonObject expand(@Nonnull JsonObject node, @Nonnull String fromTarget, @Nonnull Deque<String> stack) {
        JsonObject layer = new JsonObject();
        if (isGatedOut(node)) return layer;

        for (ImportDirective d : importDirectives) {
            JsonElement marker = node.get(d.markerKey());
            if (marker == null) continue;
            for (String ref : d.refs(marker)) {
                String target = index == null ? null : index.resolveRef(fromTarget, ref);
                if (target == null) {
                    unresolved.add(new CompileResult.UnresolvedImport(fromTarget, ref));
                    continue;
                }
                JsonObject snap = resolveTarget(target, stack);
                if (snap.size() == 0) continue;
                layer = JsonDeepMerge.merge(layer, snap, table, ctx);
            }
        }

        for (Map.Entry<String, JsonElement> e : node.entrySet()) {
            String key = e.getKey();
            if (key.startsWith(MetaKeys.PREFIX)) continue;
            JsonElement value = e.getValue();
            if (!value.isJsonObject()) continue;
            JsonObject childLayer = expand(value.getAsJsonObject(), fromTarget, stack);
            if (childLayer.size() == 0) continue;
            MergeOperator op = table.operatorFor(key);
            String baseKey = table.baseKey(key, op);
            JsonElement existing = layer.get(baseKey);
            JsonObject base = existing != null && existing.isJsonObject() ? existing.getAsJsonObject() : new JsonObject();
            layer.add(baseKey, JsonDeepMerge.merge(base, childLayer, table, ctx));
        }
        return layer;
    }

    // base + .put output of `target`, with the put's own imports resolved; memoized per target.
    // a target already on the stack is a cycle and resolves to empty so expansion always terminates.
    @Nonnull
    private JsonObject resolveTarget(@Nonnull String target, @Nonnull Deque<String> stack) {
        JsonObject cached = snapshots.get(target);
        if (cached != null) return cached;
        if (stack.contains(target)) return new JsonObject();
        stack.push(target);
        JsonObject snap;
        try {
            snap = snapshot(target, stack);
        } finally {
            stack.pop();
        }
        snapshots.put(target, snap);
        return snap;
    }

    @Nonnull
    private JsonObject snapshot(@Nonnull String target, @Nonnull Deque<String> stack) {
        JsonObject base = baseResolver.resolveBase(target);
        JsonObject acc = base != null ? base.deepCopy() : new JsonObject();
        for (JsonObject put : putsByTarget.getOrDefault(target, List.of())) {
            if (isGatedOut(put)) continue;
            JsonObject impLayer = expand(put, target, stack);
            JsonObject literal = put.deepCopy();
            MetaKeys.strip(literal);
            JsonObject withImports = impLayer.size() == 0 ? acc : JsonDeepMerge.merge(acc, impLayer, table, ctx);
            acc = JsonDeepMerge.merge(withImports, literal, table, ctx);
        }
        return acc;
    }

    private boolean isGatedOut(@Nonnull JsonObject obj) {
        for (RootDirective rd : table.directives().rootDirectives()) {
            JsonElement v = obj.get(rd.markerKey());
            if (v != null && !rd.keep(v, ctx)) return true;
        }
        return false;
    }
}

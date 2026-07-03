package com.riprod.patchly.core.ops.builtin.interfaces;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.LocatorPlan;
import com.riprod.patchly.core.MergeContext;
import com.riprod.patchly.core.MergeOperator;
import com.riprod.patchly.core.MetaKeys;

import javax.annotation.Nonnull;

public abstract class ArrayAddOperator implements MergeOperator {
    protected abstract boolean front();

    protected abstract boolean dedupe();

    @Override
    public int phase() {
        return 100;
    }

    @Override
    public void apply(@Nonnull JsonObject target, @Nonnull String baseKey,
                      @Nonnull JsonElement patchValue, @Nonnull MergeContext ctx) {
        if (!patchValue.isJsonArray()) return;
        JsonArray patchArray = patchValue.getAsJsonArray();

        JsonElement existing = target.get(baseKey);
        JsonArray base = (existing != null && existing.isJsonArray())
                ? existing.getAsJsonArray()
                : new JsonArray();

        int boundary = base.size();
        JsonArray additions = new JsonArray();
        for (int i = 0; i < patchArray.size(); i++) {
            JsonElement patchEl = patchArray.get(i);
            if (patchEl.isJsonObject() && ctx.isGatedOut(patchEl.getAsJsonObject())) continue;

            JsonElement candidate;
            LocatorPlan plan = ctx.resolveLocator(patchEl, base);
            if (plan != null) {
                MetaKeys.strip(plan.cleanPayload());
                if (plan.matched()) {
                    onLocatorHit(base, plan.targetIndices(), plan.cleanPayload(), ctx);
                    continue;
                }
                candidate = plan.cleanPayload();
            } else if (patchEl.isJsonObject()) {
                JsonObject stripped = patchEl.getAsJsonObject().deepCopy();
                MetaKeys.strip(stripped);
                JsonObject merged = new JsonObject();
                ctx.mergeObject(merged, stripped);
                candidate = merged;
            } else {
                candidate = patchEl.deepCopy();
            }

            if (dedupe() && containsEqual(base, boundary, candidate)) continue;
            additions.add(candidate);
        }

        if (front()) {
            JsonArray result = new JsonArray();
            result.addAll(additions);
            result.addAll(base);
            target.add(baseKey, result);
        } else {
            base.addAll(additions);
            target.add(baseKey, base);
        }
    }

    @Override
    public void onLocatorMiss(@Nonnull JsonArray base, int index,
                              @Nonnull JsonObject cleanPayload, @Nonnull MergeContext ctx) {
    }

    @Override
    public void onPlainElement(@Nonnull JsonArray base, int index,
                               @Nonnull JsonElement element, @Nonnull MergeContext ctx) {
    }

    protected static boolean containsEqual(@Nonnull JsonArray base, int boundary,
                                           @Nonnull JsonElement candidate) {
        for (int i = 0; i < boundary; i++) {
            if (base.get(i).equals(candidate)) return true;
        }
        return false;
    }
}

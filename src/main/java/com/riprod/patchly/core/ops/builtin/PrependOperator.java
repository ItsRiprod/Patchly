package com.riprod.patchly.core.ops.builtin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.LocatorPlan;
import com.riprod.patchly.core.MergeContext;
import com.riprod.patchly.core.MergeOperator;
import com.riprod.patchly.core.MetaKeys;

import javax.annotation.Nonnull;

public final class PrependOperator implements MergeOperator {
    @Nonnull
    @Override
    public String suffix() {
        return "-";
    }

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

        // gson arrays have no insert(i, e), and the per-element callbacks can only append; front
        // insertion that preserves patch order requires rebuilding, so apply owns the loop here
        JsonArray prepend = new JsonArray();
        for (int i = 0; i < patchArray.size(); i++) {
            JsonElement patchEl = patchArray.get(i);
            if (patchEl.isJsonObject() && ctx.isGatedOut(patchEl.getAsJsonObject())) continue;

            LocatorPlan plan = ctx.resolveLocator(patchEl, base);
            if (plan != null) {
                MetaKeys.strip(plan.cleanPayload());
                if (plan.matched()) {
                    onLocatorHit(base, plan.targetIndices(), plan.cleanPayload(), ctx);
                } else {
                    prepend.add(plan.cleanPayload());
                }
            } else if (patchEl.isJsonObject()) {
                JsonObject stripped = patchEl.getAsJsonObject().deepCopy();
                MetaKeys.strip(stripped);
                prepend.add(stripped);
            } else {
                prepend.add(patchEl.deepCopy());
            }
        }

        JsonArray result = new JsonArray();
        result.addAll(prepend);
        result.addAll(base);
        target.add(baseKey, result);
    }

    @Override
    public void onLocatorMiss(@Nonnull JsonArray base, int index,
                              @Nonnull JsonObject cleanPayload, @Nonnull MergeContext ctx) {
        // apply rebuilds the array directly; the array callbacks are never routed for prepend
    }

    @Override
    public void onPlainElement(@Nonnull JsonArray base, int index,
                               @Nonnull JsonElement element, @Nonnull MergeContext ctx) {
    }
}

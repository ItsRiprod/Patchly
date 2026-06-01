package com.riprod.patchly.engine.ops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.engine.MergeContext;
import com.riprod.patchly.engine.MergeOperator;

import javax.annotation.Nonnull;

public final class AppendOperator implements MergeOperator {
    @Nonnull
    @Override
    public String suffix() {
        return "+";
    }

    @Override
    public int phase() {
        return 100;
    }

    @Override
    public void apply(@Nonnull JsonObject target, @Nonnull String baseKey,
                      @Nonnull JsonElement patchValue, @Nonnull MergeContext ctx) {
        if (!patchValue.isJsonArray()) return;
        ctx.runArrayMerge(target, baseKey, patchValue.getAsJsonArray(), this);
    }

    @Override
    public void onLocatorMiss(@Nonnull JsonArray base, int index,
                              @Nonnull JsonObject cleanPayload, @Nonnull MergeContext ctx) {
        base.add(cleanPayload);
    }

    @Override
    public void onPlainElement(@Nonnull JsonArray base, int index,
                               @Nonnull JsonElement element, @Nonnull MergeContext ctx) {
        base.add(element.deepCopy());
    }
}

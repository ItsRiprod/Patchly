package com.riprod.patchly.builtin.ops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.MergeEngine;
import com.riprod.patchly.core.ops.MergeOperator;

import javax.annotation.Nonnull;

public final class FillOperator implements MergeOperator {
    private final ReplaceOperator resolve = new ReplaceOperator();

    @Nonnull
    @Override
    public String suffix() {
        return "?";
    }

    @Override
    public int phase() {
        return 0;
    }

    @Override
    public void apply(@Nonnull JsonObject target, @Nonnull String baseKey,
                      @Nonnull JsonElement patchValue, @Nonnull MergeEngine ctx) {
        if (target.has(baseKey)) return;
        if (patchValue.isJsonNull()) return;
        resolve.apply(target, baseKey, patchValue, ctx);
    }

    @Override
    public void onLocatorMiss(@Nonnull JsonArray base, int index,
                              @Nonnull JsonObject cleanPayload, @Nonnull MergeEngine ctx) {
    }

    @Override
    public void onPlainElement(@Nonnull JsonArray base, int index,
                               @Nonnull JsonElement element, @Nonnull MergeEngine ctx) {
    }
}

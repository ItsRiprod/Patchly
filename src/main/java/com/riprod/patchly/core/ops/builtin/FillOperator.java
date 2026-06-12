package com.riprod.patchly.core.ops.builtin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.MergeContext;
import com.riprod.patchly.core.MergeOperator;

import javax.annotation.Nonnull;

public final class FillOperator implements MergeOperator {
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
                      @Nonnull JsonElement patchValue, @Nonnull MergeContext ctx) {
        // base wins when the key already exists, regardless of value type
        if (target.has(baseKey)) return;
        if (patchValue.isJsonNull()) return;
        target.add(baseKey, patchValue.deepCopy());
    }

    @Override
    public void onLocatorMiss(@Nonnull JsonArray base, int index,
                              @Nonnull JsonObject cleanPayload, @Nonnull MergeContext ctx) {
        // fill writes the whole value or nothing; it never merges array elements in place
    }

    @Override
    public void onPlainElement(@Nonnull JsonArray base, int index,
                               @Nonnull JsonElement element, @Nonnull MergeContext ctx) {
    }
}

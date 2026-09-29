package com.riprod.patchly.core.ops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.MergeEngine;

import javax.annotation.Nonnull;
import java.util.List;

public interface MergeOperator {
    @Nonnull
    String suffix();

    int phase();

    void apply(@Nonnull JsonObject target, @Nonnull String baseKey,
               @Nonnull JsonElement patchValue, @Nonnull MergeEngine ctx);

    default void onLocatorHit(@Nonnull JsonArray base, @Nonnull List<Integer> indices,
                              @Nonnull JsonObject cleanPayload, @Nonnull MergeEngine ctx) {
        for (int idx : indices) {
            JsonElement b = base.get(idx);
            if (b.isJsonObject()) ctx.mergeObject(b.getAsJsonObject(), cleanPayload);
        }
    }

    void onLocatorMiss(@Nonnull JsonArray base, int index,
                       @Nonnull JsonObject cleanPayload, @Nonnull MergeEngine ctx);

    void onPlainElement(@Nonnull JsonArray base, int index,
                        @Nonnull JsonElement element, @Nonnull MergeEngine ctx);
}

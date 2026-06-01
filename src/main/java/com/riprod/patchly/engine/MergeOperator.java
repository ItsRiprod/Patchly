package com.riprod.patchly.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import java.util.List;

public interface MergeOperator {
    @Nonnull
    String suffix();

    int phase();

    void apply(@Nonnull JsonObject target, @Nonnull String baseKey,
               @Nonnull JsonElement patchValue, @Nonnull MergeContext ctx);

    default void onLocatorHit(@Nonnull JsonArray base, @Nonnull List<Integer> indices,
                              @Nonnull JsonObject cleanPayload, @Nonnull MergeContext ctx) {
        for (int idx : indices) {
            JsonElement b = base.get(idx);
            if (b.isJsonObject()) ctx.mergeObject(b.getAsJsonObject(), cleanPayload);
        }
    }

    void onLocatorMiss(@Nonnull JsonArray base, int index,
                       @Nonnull JsonObject cleanPayload, @Nonnull MergeContext ctx);

    void onPlainElement(@Nonnull JsonArray base, int index,
                        @Nonnull JsonElement element, @Nonnull MergeContext ctx);
}

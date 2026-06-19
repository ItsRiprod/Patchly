package com.riprod.patchly.core.ops.builtin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.MergeContext;
import com.riprod.patchly.core.MergeOperator;
import com.riprod.patchly.core.MetaKeys;

import javax.annotation.Nonnull;

public final class ReplaceOperator implements MergeOperator {
    @Nonnull
    @Override
    public String suffix() {
        return "";
    }

    @Override
    public int phase() {
        return 0;
    }

    @Override
    public void apply(@Nonnull JsonObject target, @Nonnull String baseKey,
                      @Nonnull JsonElement patchValue, @Nonnull MergeContext ctx) {
        if (patchValue.isJsonNull()) {
            target.remove(baseKey);
            return;
        }
        if (patchValue.isJsonObject()) {
            JsonObject patchObj = patchValue.getAsJsonObject();
            if (hasReservedKey(patchObj) && ctx.isGatedOut(patchObj)) return;
            JsonElement existing = target.get(baseKey);
            JsonObject child = (existing != null && existing.isJsonObject())
                    ? existing.getAsJsonObject()
                    : new JsonObject();
            if (existing == null || !existing.isJsonObject()) {
                target.add(baseKey, child);
            }
            ctx.mergeObject(child, patchObj, baseKey);
        } else if (patchValue.isJsonArray()) {
            JsonArray patchArray = patchValue.getAsJsonArray();
            if (!hasAnyMarker(patchArray, ctx)) target.add(baseKey, new JsonArray());
            ctx.runArrayMerge(target, baseKey, patchArray, this);
        } else {
            target.add(baseKey, patchValue.deepCopy());
        }
    }

    @Override
    public void onLocatorMiss(@Nonnull JsonArray base, int index,
                              @Nonnull JsonObject cleanPayload, @Nonnull MergeContext ctx) {
        // a bare array switches to in-place merge only to address its $Match elements; an unmatched
        // element has no slot to land in, so it is dropped
    }

    @Override
    public void onPlainElement(@Nonnull JsonArray base, int index,
                               @Nonnull JsonElement element, @Nonnull MergeContext ctx) {
        base.add(element.deepCopy());
    }

    private static boolean hasAnyMarker(@Nonnull JsonArray arr, @Nonnull MergeContext ctx) {
        for (JsonElement el : arr) {
            if (ctx.hasLocatorMarker(el)) return true;
        }
        return false;
    }

    private static boolean hasReservedKey(@Nonnull JsonObject obj) {
        for (String key : obj.keySet()) {
            if (key.startsWith(MetaKeys.PREFIX)) return true;
        }
        return false;
    }
}

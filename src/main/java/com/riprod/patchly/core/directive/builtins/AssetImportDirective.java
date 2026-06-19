package com.riprod.patchly.core.directive.builtins;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.MergeContext;
import com.riprod.patchly.core.directive.ObjectDirective;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public final class AssetImportDirective implements ObjectDirective {
    private static final String MARKER = "$Import";

    @Nonnull
    @Override
    public String markerKey() {
        return MARKER;
    }

    @Override
    public void apply(@Nonnull JsonObject target, @Nonnull JsonElement markerValue, @Nonnull MergeContext ctx) {
        List<String> path = ctx.currentPath();
        if (markerValue.isJsonArray()) {
            for (JsonElement e : markerValue.getAsJsonArray()) applyRef(target, e, path, ctx);
        } else {
            applyRef(target, markerValue, path, ctx);
        }
    }

    private void applyRef(@Nonnull JsonObject target, @Nonnull JsonElement ref,
            @Nonnull List<String> path, @Nonnull MergeContext ctx) {
        if (!ref.isJsonPrimitive() || !ref.getAsJsonPrimitive().isString()) return;
        JsonObject asset = ctx.resolveImport(ref.getAsString());
        if (asset == null) return;
        JsonObject scoped = scopeTo(asset, path);
        if (scoped != null) ctx.mergeObject(target, scoped);
    }

    @Nullable
    private static JsonObject scopeTo(@Nonnull JsonObject asset, @Nonnull List<String> path) {
        JsonObject cur = asset;
        for (String key : path) {
            JsonElement next = cur.get(key);
            if (next == null || !next.isJsonObject()) return null;
            cur = next.getAsJsonObject();
        }
        return cur;
    }
}

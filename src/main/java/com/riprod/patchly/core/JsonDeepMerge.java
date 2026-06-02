package com.riprod.patchly.core;

import com.google.gson.JsonObject;
import com.riprod.patchly.core.directive.DirectiveRegistry;
import com.riprod.patchly.core.directive.PatchContext;

import javax.annotation.Nonnull;

public final class JsonDeepMerge {
    private JsonDeepMerge() {}

    @Nonnull
    public static JsonObject merge(@Nonnull JsonObject base, @Nonnull JsonObject patch) {
        return merge(base, patch, activeTable(), PatchContext.ALWAYS);
    }

    @Nonnull
    public static JsonObject merge(@Nonnull JsonObject base, @Nonnull JsonObject patch, @Nonnull MergeTable table) {
        return merge(base, patch, table, PatchContext.ALWAYS);
    }

    @Nonnull
    public static JsonObject merge(@Nonnull JsonObject base, @Nonnull JsonObject patch,
                                   @Nonnull MergeTable table, @Nonnull PatchContext ctx) {
        JsonObject result = base.deepCopy();
        new MergeEngine(table, ctx).mergeObject(result, patch);
        return result;
    }

    @Nonnull
    public static MergeTable activeTable() {
        return new MergeTable(OperatorRegistry.table(), DirectiveRegistry.table());
    }

    public static void stripMergeKey(@Nonnull JsonObject obj) {
        obj.remove("merge");
    }
}

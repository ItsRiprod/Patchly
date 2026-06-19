package com.riprod.patchly.core;

import com.google.gson.JsonObject;
import com.riprod.patchly.core.directive.DirectiveRegistry;
import com.riprod.patchly.core.directive.PatchContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
        return merge(base, patch, table, ctx, null, "");
    }

    @Nonnull
    public static JsonObject merge(@Nonnull JsonObject base, @Nonnull JsonObject patch,
                                   @Nonnull MergeTable table, @Nonnull PatchContext ctx,
                                   @Nullable ImportResolver importResolver, @Nonnull String fromTarget) {
        JsonObject result = base.deepCopy();
        new MergeEngine(table, ctx, importResolver, fromTarget).mergeObject(result, patch);
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

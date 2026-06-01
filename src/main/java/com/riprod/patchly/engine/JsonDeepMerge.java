package com.riprod.patchly.engine;

import com.google.gson.JsonObject;
import com.riprod.patchly.engine.directive.DirectiveRegistry;

import javax.annotation.Nonnull;

public final class JsonDeepMerge {
    private JsonDeepMerge() {}

    @Nonnull
    public static JsonObject merge(@Nonnull JsonObject base, @Nonnull JsonObject patch) {
        return merge(base, patch, activeTable());
    }

    @Nonnull
    public static JsonObject merge(@Nonnull JsonObject base, @Nonnull JsonObject patch, @Nonnull MergeTable table) {
        JsonObject result = base.deepCopy();
        new MergeEngine(table).mergeObject(result, patch);
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

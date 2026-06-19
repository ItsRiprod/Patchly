package com.riprod.patchly.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface MergeContext {
    void mergeObject(@Nonnull JsonObject target, @Nonnull JsonObject patch);

    void mergeObject(@Nonnull JsonObject target, @Nonnull JsonObject patch, @Nonnull String key);

    @Nonnull
    List<String> currentPath();

    void mergeAtIndex(@Nonnull JsonArray base, int index, @Nonnull JsonElement element);

    void runArrayMerge(@Nonnull JsonObject target, @Nonnull String baseKey,
                       @Nonnull JsonArray patchArray, @Nonnull MergeOperator operator);

    @Nullable
    LocatorPlan resolveLocator(@Nonnull JsonElement element, @Nonnull JsonArray base);

    boolean hasLocatorMarker(@Nonnull JsonElement element);

    boolean isGatedOut(@Nonnull JsonObject patchObject);

    @Nullable
    JsonObject resolveImport(@Nonnull String ref);
}

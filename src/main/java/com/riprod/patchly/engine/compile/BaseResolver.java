package com.riprod.patchly.engine.compile;

import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@FunctionalInterface
public interface BaseResolver {
    @Nullable
    JsonObject resolveBase(@Nonnull String targetRelative);
}

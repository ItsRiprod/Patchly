package com.riprod.patchly.core.compile;

import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@FunctionalInterface
public interface BaseResolver {
    @Nullable
    JsonObject resolveBase(@Nonnull String targetRelative);
}

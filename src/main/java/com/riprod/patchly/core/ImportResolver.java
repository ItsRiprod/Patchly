package com.riprod.patchly.core;

import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@FunctionalInterface
public interface ImportResolver {
    @Nullable
    JsonObject resolve(@Nonnull String fromTarget, @Nonnull String ref);
}

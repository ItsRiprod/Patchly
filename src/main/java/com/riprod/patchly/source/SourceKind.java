package com.riprod.patchly.source;

import com.google.gson.JsonObject;

import javax.annotation.Nonnull;

public interface SourceKind {
    @Nonnull
    String extension();

    @Nonnull
    BasePolicy basePolicy();

    @Nonnull
    default JsonObject seedWhenAbsent() {
        return new JsonObject();
    }
}

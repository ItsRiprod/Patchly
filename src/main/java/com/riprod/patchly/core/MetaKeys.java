package com.riprod.patchly.core;

import com.google.gson.JsonObject;

import javax.annotation.Nonnull;

public final class MetaKeys {
    public static final String PREFIX = "$";

    private MetaKeys() {}

    public static void strip(@Nonnull JsonObject obj) {
        obj.entrySet().removeIf(e -> e.getKey().startsWith(PREFIX));
    }
}

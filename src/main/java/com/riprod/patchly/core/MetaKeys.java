package com.riprod.patchly.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;

public final class MetaKeys {
    public static final String PREFIX = "$";

    private MetaKeys() {}

    public static void strip(@Nonnull JsonObject obj) {
        obj.entrySet().removeIf(e -> e.getKey().startsWith(PREFIX));
    }

    public static void stripMarkersDeep(@Nonnull JsonElement node, @Nonnull Collection<String> markers) {
        if (node.isJsonObject()) {
            JsonObject obj = node.getAsJsonObject();
            obj.entrySet().removeIf(e -> markers.contains(e.getKey()));
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) stripMarkersDeep(e.getValue(), markers);
        } else if (node.isJsonArray()) {
            for (JsonElement e : node.getAsJsonArray()) stripMarkersDeep(e, markers);
        }
    }
}

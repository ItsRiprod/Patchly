package com.riprod.patchly;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class JsonDeepMerge {
    private static final String APPEND_SUFFIX = "+";
    private static final String POSITIONAL_SUFFIX = "~";
    private static final String MATCH_KEY = "$Match";

    private enum ArrayMode { REPLACE, APPEND, POSITIONAL }

    private JsonDeepMerge() {}

    @Nonnull
    public static JsonObject merge(@Nonnull JsonObject base, @Nonnull JsonObject patch) {
        JsonObject result = base.deepCopy();
        applyObjectPatch(result, patch);
        return result;
    }

    private static void applyObjectPatch(@Nonnull JsonObject target, @Nonnull JsonObject patch) {
        List<String> positionalKeys = new ArrayList<>();
        List<String> appendKeys = new ArrayList<>();

        for (String key : patch.keySet()) {
            if (key.length() > 1 && key.endsWith(APPEND_SUFFIX)) {
                appendKeys.add(key);
                continue;
            }
            if (key.length() > 1 && key.endsWith(POSITIONAL_SUFFIX)) {
                positionalKeys.add(key);
                continue;
            }
            applyReplaceOrRecurse(target, key, patch.get(key));
        }

        for (String key : positionalKeys) {
            String baseKey = key.substring(0, key.length() - POSITIONAL_SUFFIX.length());
            applyArrayMerge(target, baseKey, patch.get(key), ArrayMode.POSITIONAL);
        }
        for (String key : appendKeys) {
            String baseKey = key.substring(0, key.length() - APPEND_SUFFIX.length());
            applyArrayMerge(target, baseKey, patch.get(key), ArrayMode.APPEND);
        }
    }

    private static void applyReplaceOrRecurse(@Nonnull JsonObject target, @Nonnull String key, JsonElement patchValue) {
        if (patchValue == null || patchValue.isJsonNull()) {
            target.remove(key);
            return;
        }
        if (patchValue.isJsonObject()) {
            JsonElement existing = target.get(key);
            JsonObject child = (existing != null && existing.isJsonObject())
                    ? existing.getAsJsonObject()
                    : new JsonObject();
            if (existing == null || !existing.isJsonObject()) {
                target.add(key, child);
            }
            applyObjectPatch(child, patchValue.getAsJsonObject());
        } else if (patchValue.isJsonArray() && arrayHasMatchElement(patchValue.getAsJsonArray())) {
            applyArrayMerge(target, key, patchValue, ArrayMode.REPLACE);
        } else {
            target.add(key, patchValue.deepCopy());
        }
    }

    private static void applyArrayMerge(@Nonnull JsonObject target, @Nonnull String key, JsonElement patchValue, @Nonnull ArrayMode mode) {
        if (patchValue == null || !patchValue.isJsonArray()) return;
        JsonArray patchArray = patchValue.getAsJsonArray();

        JsonElement existing = target.get(key);
        JsonArray base = (existing != null && existing.isJsonArray())
                ? existing.getAsJsonArray()
                : new JsonArray();
        if (existing == null || !existing.isJsonArray()) {
            target.add(key, base);
        }

        for (int i = 0; i < patchArray.size(); i++) {
            JsonElement patchEl = patchArray.get(i);
            String matchField = matchField(patchEl);

            if (matchField != null) {
                JsonObject clean = patchEl.getAsJsonObject().deepCopy();
                clean.remove(MATCH_KEY);
                JsonElement wanted = clean.get(matchField);
                boolean matched = false;
                if (wanted != null && !wanted.isJsonNull()) {
                    for (JsonElement baseEl : base) {
                        if (!baseEl.isJsonObject()) continue;
                        JsonElement have = baseEl.getAsJsonObject().get(matchField);
                        if (have != null && have.equals(wanted)) {
                            applyObjectPatch(baseEl.getAsJsonObject(), clean);
                            matched = true;
                        }
                    }
                }
                if (!matched) {
                    applyMatchMiss(base, i, clean, mode);
                }
            } else {
                applyPlain(base, i, patchEl, mode);
            }
        }
    }

    private static void applyMatchMiss(@Nonnull JsonArray base, int index, @Nonnull JsonObject clean, @Nonnull ArrayMode mode) {
        switch (mode) {
            case POSITIONAL -> mergeAtIndex(base, index, clean);
            case APPEND -> base.add(clean);
            case REPLACE -> { }
        }
    }

    private static void applyPlain(@Nonnull JsonArray base, int index, @Nonnull JsonElement element, @Nonnull ArrayMode mode) {
        switch (mode) {
            case POSITIONAL -> mergeAtIndex(base, index, element);
            case APPEND, REPLACE -> base.add(element.deepCopy());
        }
    }

    private static void mergeAtIndex(@Nonnull JsonArray base, int index, @Nonnull JsonElement element) {
        if (index < base.size()) {
            JsonElement baseEl = base.get(index);
            if (baseEl.isJsonObject() && element.isJsonObject()) {
                applyObjectPatch(baseEl.getAsJsonObject(), element.getAsJsonObject());
            } else {
                base.set(index, element.deepCopy());
            }
        } else {
            base.add(element.deepCopy());
        }
    }

    private static boolean arrayHasMatchElement(@Nonnull JsonArray arr) {
        for (JsonElement el : arr) {
            if (matchField(el) != null) return true;
        }
        return false;
    }

    private static String matchField(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonElement m = el.getAsJsonObject().get(MATCH_KEY);
        if (m != null && m.isJsonPrimitive() && m.getAsJsonPrimitive().isString()) {
            return m.getAsString();
        }
        return null;
    }

    public static void stripMergeKey(@Nonnull JsonObject obj) {
        obj.remove("merge");
    }
}

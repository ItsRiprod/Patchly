package com.riprod.patchly.core.directive.builtins;

import com.google.gson.JsonElement;
import com.riprod.patchly.core.directive.ImportDirective;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class AssetImportDirective implements ImportDirective {
    private static final String MARKER = "$Import";

    @Nonnull
    @Override
    public String markerKey() {
        return MARKER;
    }

    @Nonnull
    @Override
    public List<String> refs(@Nonnull JsonElement markerValue) {
        List<String> refs = new ArrayList<>();
        if (markerValue.isJsonArray()) {
            // later entries win, so they layer on top of earlier ones during expansion
            for (JsonElement e : markerValue.getAsJsonArray()) {
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) refs.add(e.getAsString());
            }
        } else if (markerValue.isJsonPrimitive() && markerValue.getAsJsonPrimitive().isString()) {
            refs.add(markerValue.getAsString());
        }
        return refs;
    }
}

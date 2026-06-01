package com.riprod.patchly.engine.directive.builtins;

import com.google.gson.JsonElement;
import com.riprod.patchly.engine.directive.RootDirective;

import javax.annotation.Nonnull;

public final class PriorityDirective implements RootDirective {
    private static final String MARKER = "$Priority";

    @Nonnull
    @Override
    public String markerKey() {
        return MARKER;
    }

    @Override
    public int order(@Nonnull JsonElement markerValue) {
        if (markerValue.isJsonPrimitive() && markerValue.getAsJsonPrimitive().isNumber()) {
            return markerValue.getAsInt();
        }
        return 0;
    }
}

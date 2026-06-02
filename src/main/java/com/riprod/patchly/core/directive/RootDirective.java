package com.riprod.patchly.core.directive;

import com.google.gson.JsonElement;

import javax.annotation.Nonnull;

public interface RootDirective extends Directive {
    default boolean keep(@Nonnull JsonElement markerValue, @Nonnull PatchContext ctx) {
        return true;
    }

    default int order(@Nonnull JsonElement markerValue) {
        return 0;
    }
}

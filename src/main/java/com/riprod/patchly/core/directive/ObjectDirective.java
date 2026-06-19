package com.riprod.patchly.core.directive;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.MergeContext;

import javax.annotation.Nonnull;

public interface ObjectDirective extends Directive {
    void apply(@Nonnull JsonObject target, @Nonnull JsonElement markerValue, @Nonnull MergeContext ctx);
}

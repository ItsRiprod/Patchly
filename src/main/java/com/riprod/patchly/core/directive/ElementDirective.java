package com.riprod.patchly.core.directive;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.LocatorPlan;

import javax.annotation.Nonnull;

public interface ElementDirective extends Directive {
    @Nonnull
    LocatorPlan locate(@Nonnull JsonObject element, @Nonnull JsonArray base);
}

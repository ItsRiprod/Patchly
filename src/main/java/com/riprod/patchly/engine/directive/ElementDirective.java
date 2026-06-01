package com.riprod.patchly.engine.directive;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.riprod.patchly.engine.LocatorPlan;

import javax.annotation.Nonnull;

public interface ElementDirective extends Directive {
    @Nonnull
    LocatorPlan locate(@Nonnull JsonObject element, @Nonnull JsonArray base);
}

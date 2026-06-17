package com.riprod.patchly.core.directive;

import com.google.gson.JsonElement;

import javax.annotation.Nonnull;
import java.util.List;

public interface ImportDirective extends Directive {
    @Nonnull
    List<String> refs(@Nonnull JsonElement markerValue);
}

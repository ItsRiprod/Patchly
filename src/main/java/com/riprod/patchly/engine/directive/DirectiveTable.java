package com.riprod.patchly.engine.directive;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DirectiveTable {
    private final Map<String, ElementDirective> elementMarkers = new HashMap<>();
    private final List<RootDirective> rootDirectives = new ArrayList<>();

    public DirectiveTable(@Nonnull Collection<Directive> all) {
        for (Directive d : all) {
            if (d instanceof ElementDirective e) elementMarkers.put(e.markerKey(), e);
            if (d instanceof RootDirective r) rootDirectives.add(r);
        }
    }

    @Nullable
    public ElementDirective elementDirective(@Nonnull String marker) {
        return elementMarkers.get(marker);
    }

    @Nonnull
    public List<RootDirective> rootDirectives() {
        return Collections.unmodifiableList(rootDirectives);
    }
}

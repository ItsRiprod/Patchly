package com.riprod.patchly.core.directive;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DirectiveTable {
    private final Map<String, ElementDirective> elementMarkers = new HashMap<>();
    private final List<RootDirective> rootDirectives = new ArrayList<>();
    private final List<ObjectDirective> objectDirectives = new ArrayList<>();
    private final Set<String> objectMarkers = new HashSet<>();

    public DirectiveTable(@Nonnull Collection<Directive> all) {
        for (Directive d : all) {
            if (d instanceof ElementDirective e) elementMarkers.put(e.markerKey(), e);
            if (d instanceof RootDirective r) rootDirectives.add(r);
            if (d instanceof ObjectDirective o) {
                objectDirectives.add(o);
                objectMarkers.add(o.markerKey());
            }
        }
    }

    @Nonnull
    public Set<String> objectMarkerKeys() {
        return Collections.unmodifiableSet(objectMarkers);
    }

    @Nullable
    public ElementDirective elementDirective(@Nonnull String marker) {
        return elementMarkers.get(marker);
    }

    @Nonnull
    public List<RootDirective> rootDirectives() {
        return Collections.unmodifiableList(rootDirectives);
    }

    @Nonnull
    public List<ObjectDirective> objectDirectives() {
        return Collections.unmodifiableList(objectDirectives);
    }

    @Nonnull
    public Set<String> markerKeys() {
        Set<String> keys = new HashSet<>(elementMarkers.keySet());
        for (RootDirective r : rootDirectives) keys.add(r.markerKey());
        for (ObjectDirective o : objectDirectives) keys.add(o.markerKey());
        return keys;
    }
}

package com.riprod.patchly.source;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

public final class SourceKindTable {
    private final List<SourceKind> kinds;

    public SourceKindTable(@Nonnull Collection<SourceKind> kinds) {
        this.kinds = List.copyOf(kinds);
    }

    @Nullable
    public SourceKind kindFor(@Nonnull String fileName) {
        for (SourceKind k : kinds) {
            if (fileName.endsWith(k.extension())) return k;
        }
        return null;
    }

    public boolean claims(@Nonnull String fileName) {
        return kindFor(fileName) != null;
    }
}

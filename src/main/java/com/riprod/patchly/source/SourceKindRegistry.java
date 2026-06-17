package com.riprod.patchly.source;

import com.riprod.patchly.registry.KeyValidator;
import com.riprod.patchly.registry.Registry;
import com.riprod.patchly.source.kinds.PatchKind;
import com.riprod.patchly.source.kinds.PutKind;

import javax.annotation.Nonnull;

public final class SourceKindRegistry {
    private static final Registry<SourceKind, SourceKindTable> REGISTRY =
            new Registry<>(SourceKind::extension, SourceKindTable::new, KeyValidator.requirePrefix("."));

    static {
        REGISTRY.register(new PatchKind());
        REGISTRY.register(new PutKind());
    }

    private SourceKindRegistry() {}

    public static void register(@Nonnull SourceKind kind) {
        REGISTRY.register(kind);
    }

    @Nonnull
    public static SourceKindTable table() {
        return REGISTRY.snapshot();
    }

    @Nonnull
    public static SourceKindTable isolatedTable(@Nonnull SourceKind... kinds) {
        return REGISTRY.isolatedSnapshot(kinds);
    }
}

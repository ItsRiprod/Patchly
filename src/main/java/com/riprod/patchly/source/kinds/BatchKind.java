package com.riprod.patchly.source.kinds;

import com.riprod.patchly.source.BasePolicy;
import com.riprod.patchly.source.SourceKind;

import javax.annotation.Nonnull;

public final class BatchKind implements SourceKind {
    @Nonnull
    @Override
    public String extension() {
        return ".batch";
    }

    @Nonnull
    @Override
    public BasePolicy basePolicy() {
        return BasePolicy.SCOPE;
    }
}

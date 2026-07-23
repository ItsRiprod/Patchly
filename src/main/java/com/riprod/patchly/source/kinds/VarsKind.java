package com.riprod.patchly.source.kinds;

import com.riprod.patchly.source.BasePolicy;
import com.riprod.patchly.source.SourceKind;

import javax.annotation.Nonnull;

public final class VarsKind implements SourceKind {
    @Nonnull
    @Override
    public String extension() {
        return ".vars";
    }

    @Nonnull
    @Override
    public BasePolicy basePolicy() {
        return BasePolicy.ENVIRONMENT;
    }
}

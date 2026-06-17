package com.riprod.patchly.core.compile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@FunctionalInterface
public interface AssetIndex {
    @Nullable
    String resolveRef(@Nonnull String fromTarget, @Nonnull String ref);
}

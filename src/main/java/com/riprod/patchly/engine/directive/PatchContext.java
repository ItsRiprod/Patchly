package com.riprod.patchly.engine.directive;

import javax.annotation.Nonnull;

public interface PatchContext {
    boolean packPresent(@Nonnull String packName);

    boolean versionSatisfies(@Nonnull String packName, @Nonnull String range);
}

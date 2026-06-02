package com.riprod.patchly.core.directive;

import javax.annotation.Nonnull;

public interface PatchContext {
    PatchContext ALWAYS = new PatchContext() {
        @Override
        public boolean packPresent(@Nonnull String packName) {
            return true;
        }

        @Override
        public boolean versionSatisfies(@Nonnull String packName, @Nonnull String range) {
            return true;
        }
    };

    boolean packPresent(@Nonnull String packName);

    boolean versionSatisfies(@Nonnull String packName, @Nonnull String range);
}

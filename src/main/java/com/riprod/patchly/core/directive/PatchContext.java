package com.riprod.patchly.core.directive;

import com.riprod.patchly.core.vars.ExpressionEvaluator.VarLookup;
import com.riprod.patchly.core.vars.ExpressionException;

import javax.annotation.Nonnull;

public interface PatchContext {
    VarLookup NO_VARS = ref -> {
        throw new ExpressionException("no variables in this context (for '$" + ref + "')");
    };

    PatchContext ALWAYS = new PatchContext() {
        @Override
        public boolean packPresent(@Nonnull String packName) {
            return true;
        }

        @Override
        public boolean versionSatisfies(@Nonnull String packName, @Nonnull String range) {
            return true;
        }

        @Nonnull
        @Override
        public VarLookup vars() {
            return ref -> 1.0;
        }
    };

    boolean packPresent(@Nonnull String packName);

    boolean versionSatisfies(@Nonnull String packName, @Nonnull String range);

    @Nonnull
    default VarLookup vars() {
        return NO_VARS;
    }

    @Nonnull
    static PatchContext withVars(@Nonnull PatchContext base, @Nonnull VarLookup vars) {
        return new PatchContext() {
            @Override
            public boolean packPresent(@Nonnull String packName) {
                return base.packPresent(packName);
            }

            @Override
            public boolean versionSatisfies(@Nonnull String packName, @Nonnull String range) {
                return base.versionSatisfies(packName, range);
            }

            @Nonnull
            @Override
            public VarLookup vars() {
                return vars;
            }
        };
    }
}

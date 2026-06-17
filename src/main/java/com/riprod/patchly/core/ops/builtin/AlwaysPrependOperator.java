package com.riprod.patchly.core.ops.builtin;

import javax.annotation.Nonnull;

import com.riprod.patchly.core.ops.builtin.interfaces.ArrayAddOperator;

public final class AlwaysPrependOperator extends ArrayAddOperator {
    @Nonnull
    @Override
    public String suffix() {
        return "--";
    }

    @Override
    protected boolean front() {
        return true;
    }

    @Override
    protected boolean dedupe() {
        return false;
    }
}

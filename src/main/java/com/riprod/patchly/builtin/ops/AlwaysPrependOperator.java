package com.riprod.patchly.builtin.ops;

import javax.annotation.Nonnull;

import com.riprod.patchly.builtin.ops.interfaces.ArrayAddOperator;

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

package com.riprod.patchly.core.ops.builtin;

import javax.annotation.Nonnull;

import com.riprod.patchly.core.ops.builtin.interfaces.ArrayAddOperator;

public final class AlwaysAppendOperator extends ArrayAddOperator {
    @Nonnull
    @Override
    public String suffix() {
        return "++";
    }

    @Override
    protected boolean front() {
        return false;
    }

    @Override
    protected boolean dedupe() {
        return false;
    }
}

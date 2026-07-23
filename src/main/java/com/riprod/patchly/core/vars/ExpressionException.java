package com.riprod.patchly.core.vars;

import javax.annotation.Nonnull;

public final class ExpressionException extends Exception {
    public ExpressionException(@Nonnull String message) {
        super(message);
    }
}

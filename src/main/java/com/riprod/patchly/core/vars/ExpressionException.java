package com.riprod.patchly.core.vars;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ExpressionException extends Exception {
    private final String missingScope;

    public ExpressionException(@Nonnull String message) {
        this(message, null);
    }

    public ExpressionException(@Nonnull String message, @Nullable String missingScope) {
        super(message);
        this.missingScope = missingScope;
    }

    @Nullable
    public String missingScope() {
        return missingScope;
    }
}

package com.riprod.patchly.core;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.riprod.patchly.core.directive.DirectiveTable;
import com.riprod.patchly.core.directive.ElementDirective;

public record MergeTable(@Nonnull OperatorTable operators, @Nonnull DirectiveTable directives) {
    @Nonnull
    public MergeOperator operatorFor(@Nonnull String key) {
        return operators.forKey(key);
    }

    @Nonnull
    public String baseKey(@Nonnull String key, @Nonnull MergeOperator operator) {
        return operators.baseKey(key, operator);
    }

    @Nullable
    public ElementDirective elementDirective(@Nonnull String marker) {
        return directives.elementDirective(marker);
    }
}

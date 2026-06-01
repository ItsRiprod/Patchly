package com.riprod.patchly.engine;

import com.riprod.patchly.engine.ops.AppendOperator;
import com.riprod.patchly.engine.ops.PositionalOperator;
import com.riprod.patchly.engine.ops.ReplaceOperator;
import com.riprod.patchly.registry.KeyValidator;
import com.riprod.patchly.registry.Registry;

import javax.annotation.Nonnull;

public final class OperatorRegistry {
    private static final Registry<MergeOperator, OperatorTable> REGISTRY =
            new Registry<>(MergeOperator::suffix, OperatorTable::new, KeyValidator.NONE);

    static {
        REGISTRY.register(new ReplaceOperator());
        REGISTRY.register(new PositionalOperator());
        REGISTRY.register(new AppendOperator());
    }

    private OperatorRegistry() {}

    public static void register(@Nonnull MergeOperator operator) {
        REGISTRY.register(operator);
    }

    @Nonnull
    public static OperatorTable table() {
        return REGISTRY.snapshot();
    }

    @Nonnull
    public static OperatorTable isolatedTable(@Nonnull MergeOperator... operators) {
        return REGISTRY.isolatedSnapshot(operators);
    }
}

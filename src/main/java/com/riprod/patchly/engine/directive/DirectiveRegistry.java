package com.riprod.patchly.engine.directive;

import com.riprod.patchly.engine.directive.builtins.MatchDirective;
import com.riprod.patchly.engine.directive.builtins.PriorityDirective;
import com.riprod.patchly.engine.directive.builtins.RequiresDirective;
import com.riprod.patchly.registry.KeyValidator;
import com.riprod.patchly.registry.Registry;

import javax.annotation.Nonnull;

public final class DirectiveRegistry {
    private static final Registry<Directive, DirectiveTable> REGISTRY =
            new Registry<>(Directive::markerKey, DirectiveTable::new, KeyValidator.requirePrefix("$"));

    static {
        REGISTRY.register(new MatchDirective());
        REGISTRY.register(new RequiresDirective());
        REGISTRY.register(new PriorityDirective());
    }

    private DirectiveRegistry() {}

    public static void register(@Nonnull Directive directive) {
        REGISTRY.register(directive);
    }

    @Nonnull
    public static DirectiveTable table() {
        return REGISTRY.snapshot();
    }

    @Nonnull
    public static DirectiveTable isolatedTable(@Nonnull Directive... directives) {
        return REGISTRY.isolatedSnapshot(directives);
    }
}

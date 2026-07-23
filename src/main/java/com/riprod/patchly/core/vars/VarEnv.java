package com.riprod.patchly.core.vars;

import com.riprod.patchly.core.vars.ExpressionEvaluator.VarLookup;

import javax.annotation.Nonnull;
import java.util.Map;

public final class VarEnv {
    public static final String GLOBAL_SCOPE = "Globals";

    private final Map<String, Double> globals;
    private final Map<String, Map<String, Double>> scopes;

    public VarEnv(@Nonnull Map<String, Double> globals, @Nonnull Map<String, Map<String, Double>> scopes) {
        this.globals = Map.copyOf(globals);
        this.scopes = Map.copyOf(scopes);
    }

    @Nonnull
    public VarLookup lookup() {
        return this::resolve;
    }

    private double resolve(@Nonnull String ref) throws ExpressionException {
        int dot = ref.indexOf('.');
        if (dot < 0) {
            Double value = globals.get(ref);
            if (value == null) throw new ExpressionException("unknown global variable '$" + ref + "'");
            return value;
        }
        String scope = ref.substring(0, dot);
        String name = ref.substring(dot + 1);
        Map<String, Double> members = scopes.get(scope);
        if (members == null) throw new ExpressionException("unknown scope '" + scope + "' (in '$" + ref + "')");
        Double value = members.get(name);
        if (value == null) throw new ExpressionException("unknown variable '$" + ref + "'");
        return value;
    }
}

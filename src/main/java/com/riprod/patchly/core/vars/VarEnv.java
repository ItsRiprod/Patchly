package com.riprod.patchly.core.vars;

import com.riprod.patchly.core.vars.ExpressionEvaluator.VarLookup;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
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

    @Nonnull
    public Map<String, Map<String, Double>> asMap() {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        out.put(GLOBAL_SCOPE, globals);
        for (Map.Entry<String, Map<String, Double>> scope : scopes.entrySet()) {
            out.put(scope.getKey(), scope.getValue());
        }
        return Map.copyOf(out);
    }

    private double resolve(@Nonnull String ref) throws ExpressionException {
        int dot = ref.indexOf('.');
        if (dot < 0) return global(ref, ref);
        String scope = ref.substring(0, dot);
        String name = ref.substring(dot + 1);
        if (scope.equals(GLOBAL_SCOPE)) return global(name, ref);
        Map<String, Double> members = scopes.get(scope);
        if (members == null) {
            throw new ExpressionException("unknown scope '" + scope + "' (in '$" + ref + "')", scope);
        }
        Double value = members.get(name);
        if (value == null) throw new ExpressionException("unknown variable '$" + ref + "'");
        return value;
    }

    private double global(@Nonnull String name, @Nonnull String ref) throws ExpressionException {
        Double value = globals.get(name);
        if (value == null) throw new ExpressionException("unknown global variable '$" + ref + "'");
        return value;
    }
}

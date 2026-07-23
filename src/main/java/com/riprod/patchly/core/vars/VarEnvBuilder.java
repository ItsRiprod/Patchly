package com.riprod.patchly.core.vars;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.compile.CompileResult.UnresolvedExpression;
import com.riprod.patchly.core.compile.PatchSource;
import com.riprod.patchly.core.vars.ExpressionEvaluator.VarLookup;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VarEnvBuilder {
    private static final String VARS_EXTENSION = ".vars";
    private static final String META_PREFIX = "$";

    private VarEnvBuilder() {}

    @Nonnull
    public static VarEnv build(@Nonnull List<PatchSource> envSources,
            @Nonnull List<UnresolvedExpression> errors) {
        Map<String, LinkedHashMap<String, JsonElement>> raw = accumulate(envSources);

        Map<String, Double> globals = new HashMap<>();
        LinkedHashMap<String, JsonElement> globalRaw = raw.get(VarEnv.GLOBAL_SCOPE);
        if (globalRaw != null) {
            for (Map.Entry<String, JsonElement> entry : globalRaw.entrySet()) {
                Double number = asNumber(entry.getValue());
                if (number == null) {
                    errors.add(new UnresolvedExpression(VarEnv.GLOBAL_SCOPE + VARS_EXTENSION + ":" + entry.getKey(),
                            entry.getValue().toString(), "global variable must be a plain number"));
                    continue;
                }
                globals.put(entry.getKey(), number);
            }
        }

        VarLookup globalsOnly = new VarEnv(globals, Map.of()).lookup();
        Map<String, Map<String, Double>> scopes = new HashMap<>();
        for (Map.Entry<String, LinkedHashMap<String, JsonElement>> scope : raw.entrySet()) {
            if (scope.getKey().equals(VarEnv.GLOBAL_SCOPE)) continue;
            Map<String, Double> resolved = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : scope.getValue().entrySet()) {
                Double value = resolveScoped(entry.getValue(), globalsOnly,
                        scope.getKey() + VARS_EXTENSION + ":" + entry.getKey(), errors);
                if (value != null) resolved.put(entry.getKey(), value);
            }
            scopes.put(scope.getKey(), resolved);
        }

        return new VarEnv(globals, scopes);
    }

    @Nonnull
    private static Map<String, LinkedHashMap<String, JsonElement>> accumulate(@Nonnull List<PatchSource> envSources) {
        Map<String, LinkedHashMap<String, JsonElement>> raw = new LinkedHashMap<>();
        for (PatchSource source : envSources) {
            String stem = stemOf(source.id());
            LinkedHashMap<String, JsonElement> scope = raw.computeIfAbsent(stem, k -> new LinkedHashMap<>());
            for (Map.Entry<String, JsonElement> entry : source.patchJson().entrySet()) {
                if (entry.getKey().startsWith(META_PREFIX)) continue;
                scope.put(entry.getKey(), entry.getValue());
            }
        }
        return raw;
    }

    private static Double resolveScoped(@Nonnull JsonElement value, @Nonnull VarLookup globalsOnly,
            @Nonnull String where, @Nonnull List<UnresolvedExpression> errors) {
        Double number = asNumber(value);
        if (number != null) return number;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String expression = value.getAsString();
            try {
                return ExpressionEvaluator.eval(expression, globalsOnly);
            } catch (ExpressionException e) {
                errors.add(new UnresolvedExpression(where, expression, e.getMessage()));
                return null;
            }
        }
        errors.add(new UnresolvedExpression(where, value.toString(),
                "variable must be a number or an expression string"));
        return null;
    }

    private static Double asNumber(@Nonnull JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsDouble();
        }
        return null;
    }

    @Nonnull
    private static String stemOf(@Nonnull Path id) {
        String name = id.getFileName().toString();
        return name.endsWith(VARS_EXTENSION) ? name.substring(0, name.length() - VARS_EXTENSION.length()) : name;
    }
}

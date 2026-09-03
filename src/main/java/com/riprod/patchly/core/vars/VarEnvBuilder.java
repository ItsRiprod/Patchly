package com.riprod.patchly.core.vars;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.compile.CompileResult.UnresolvedExpression;
import com.riprod.patchly.core.compile.PatchSource;
import com.riprod.patchly.core.vars.ExpressionEvaluator.VarLookup;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.ArrayList;
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
        List<PatchSource> globalSources = new ArrayList<>();
        List<PatchSource> scopedSources = new ArrayList<>();
        for (PatchSource source : envSources) {
            if (isGlobals(source)) globalSources.add(source);
            else scopedSources.add(source);
        }
        Map<String, Double> globals = buildGlobals(globalSources, errors);
        return new VarEnv(globals, buildScopes(globals, scopedSources, errors));
    }

    public static boolean isGlobals(@Nonnull PatchSource source) {
        return stemOf(source.id()).equals(VarEnv.GLOBAL_SCOPE);
    }

    @Nonnull
    public static Map<String, Double> buildGlobals(@Nonnull List<PatchSource> globalSources,
            @Nonnull List<UnresolvedExpression> errors) {
        Map<String, Double> globals = new HashMap<>();
        LinkedHashMap<String, JsonElement> globalRaw = accumulate(globalSources).get(VarEnv.GLOBAL_SCOPE);
        if (globalRaw == null) return globals;
        for (Map.Entry<String, JsonElement> entry : globalRaw.entrySet()) {
            Double number = asNumber(entry.getValue());
            if (number == null) {
                String where = VarEnv.GLOBAL_SCOPE + VARS_EXTENSION + ":" + entry.getKey();
                errors.add(new UnresolvedExpression(where, entry.getValue().toString(),
                        "global variable must be a plain number or boolean", where, null));
                continue;
            }
            globals.put(entry.getKey(), number);
        }
        return globals;
    }

    @Nonnull
    public static Map<String, Map<String, Double>> buildScopes(@Nonnull Map<String, Double> globals,
            @Nonnull List<PatchSource> scopedSources,
            @Nonnull List<UnresolvedExpression> errors) {
        VarLookup globalsOnly = new VarEnv(globals, Map.of()).lookup();
        Map<String, Map<String, Double>> scopes = new HashMap<>();
        for (Map.Entry<String, LinkedHashMap<String, JsonElement>> scope : accumulate(scopedSources).entrySet()) {
            String stem = scope.getKey();
            if (stem.equals(VarEnv.GLOBAL_SCOPE)) continue;
            Map<String, Double> resolved = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : scope.getValue().entrySet()) {
                Double value = resolveScoped(entry.getValue(), globalsOnly,
                        stem + VARS_EXTENSION + ":" + entry.getKey(), errors);
                if (value != null) resolved.put(entry.getKey(), value);
            }
            scopes.put(stem, resolved);
        }
        return scopes;
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
                errors.add(new UnresolvedExpression(where, expression, e.getMessage(), where, e.missingScope()));
                return null;
            }
        }
        errors.add(new UnresolvedExpression(where, value.toString(),
                "variable must be a number, a boolean, or an expression string", where, null));
        return null;
    }

    private static Double asNumber(@Nonnull JsonElement value) {
        if (!value.isJsonPrimitive()) return null;
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber()) return primitive.getAsDouble();
        if (primitive.isBoolean()) return primitive.getAsBoolean() ? 1.0 : 0.0;
        return null;
    }

    @Nonnull
    private static String stemOf(@Nonnull Path id) {
        String name = id.getFileName().toString();
        return name.endsWith(VARS_EXTENSION) ? name.substring(0, name.length() - VARS_EXTENSION.length()) : name;
    }
}

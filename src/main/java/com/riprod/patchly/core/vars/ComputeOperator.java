package com.riprod.patchly.core.vars;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.riprod.patchly.core.MergeContext;
import com.riprod.patchly.core.MergeOperator;
import com.riprod.patchly.core.compile.CompileResult.UnresolvedExpression;
import com.riprod.patchly.core.vars.ExpressionEvaluator.VarLookup;

import javax.annotation.Nonnull;
import java.util.List;

public final class ComputeOperator implements MergeOperator {
    public static final String SUFFIX = "#";
    private static final double MAX_SAFE_INTEGER = 9007199254740992.0;

    private final VarLookup lookup;
    private final List<UnresolvedExpression> errors;

    public ComputeOperator(@Nonnull VarEnv env, @Nonnull List<UnresolvedExpression> errors) {
        this.lookup = env.lookup();
        this.errors = errors;
    }

    @Nonnull
    @Override
    public String suffix() {
        return SUFFIX;
    }

    @Override
    public int phase() {
        return 0;
    }

    @Override
    public void apply(@Nonnull JsonObject target, @Nonnull String baseKey,
                      @Nonnull JsonElement patchValue, @Nonnull MergeContext ctx) {
        if (patchValue.isJsonPrimitive() && patchValue.getAsJsonPrimitive().isNumber()) {
            target.add(baseKey, numberPrimitive(patchValue.getAsDouble()));
            return;
        }
        if (patchValue.isJsonPrimitive() && patchValue.getAsJsonPrimitive().isString()) {
            String expression = patchValue.getAsString();
            try {
                target.add(baseKey, numberPrimitive(ExpressionEvaluator.eval(expression, lookup)));
            } catch (ExpressionException e) {
                record(ctx, baseKey, expression, e.getMessage());
                target.add(baseKey + SUFFIX, patchValue.deepCopy());
            }
            return;
        }
        record(ctx, baseKey, patchValue.toString(), "expression key must hold a number or an expression string");
        target.add(baseKey + SUFFIX, patchValue.deepCopy());
    }

    @Override
    public void onLocatorMiss(@Nonnull JsonArray base, int index,
                              @Nonnull JsonObject cleanPayload, @Nonnull MergeContext ctx) {
    }

    @Override
    public void onPlainElement(@Nonnull JsonArray base, int index,
                               @Nonnull JsonElement element, @Nonnull MergeContext ctx) {
    }

    private void record(@Nonnull MergeContext ctx, @Nonnull String baseKey,
            @Nonnull String expression, @Nonnull String reason) {
        String path = String.join("/", ctx.currentPath());
        String where = path.isEmpty() ? baseKey + SUFFIX : path + "/" + baseKey + SUFFIX;
        errors.add(new UnresolvedExpression(where, expression, reason));
    }

    @Nonnull
    private static JsonPrimitive numberPrimitive(double value) {
        if (value == Math.rint(value) && Math.abs(value) < MAX_SAFE_INTEGER) {
            return new JsonPrimitive((long) value);
        }
        return new JsonPrimitive(value);
    }
}

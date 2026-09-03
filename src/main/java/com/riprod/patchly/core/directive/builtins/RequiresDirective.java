package com.riprod.patchly.core.directive.builtins;

import com.google.gson.JsonElement;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.core.directive.RootDirective;
import com.riprod.patchly.core.vars.ExpressionEvaluator;
import com.riprod.patchly.core.vars.ExpressionException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class RequiresDirective implements RootDirective {
    private static final String MARKER = "$Requires";

    public record Diagnostic(@Nonnull String literal, @Nonnull String reason, @Nullable String missingScope) {
    }

    @Nonnull
    @Override
    public String markerKey() {
        return MARKER;
    }

    @Override
    public boolean keep(@Nonnull JsonElement markerValue, @Nonnull PatchContext ctx) {
        for (String clause : clauses(markerValue)) {
            if (!clauseSatisfied(clause, ctx)) return false;
        }
        return true;
    }

    @Nonnull
    public static List<Diagnostic> diagnose(@Nonnull JsonElement markerValue, @Nonnull PatchContext ctx) {
        List<Diagnostic> out = new ArrayList<>();
        for (String clause : clauses(markerValue)) {
            for (String literal : clause.split(",")) {
                String s = literal.trim();
                if (s.isEmpty()) continue;
                String entry = stripNegation(s);
                if (isPackLiteral(entry)) continue;
                try {
                    ExpressionEvaluator.eval(entry, ctx.vars());
                } catch (ExpressionException e) {
                    out.add(new Diagnostic(s, e.getMessage(), e.missingScope()));
                }
            }
        }
        return out;
    }

    @Nonnull
    private static List<String> clauses(@Nonnull JsonElement markerValue) {
        List<String> clauses = new ArrayList<>();
        if (markerValue.isJsonArray()) {
            for (JsonElement e : markerValue.getAsJsonArray()) {
                if (e.isJsonPrimitive()) clauses.add(e.getAsString());
            }
        } else if (markerValue.isJsonPrimitive()) {
            clauses.add(markerValue.getAsString());
        }
        return clauses;
    }

    private static boolean clauseSatisfied(@Nonnull String clause, @Nonnull PatchContext ctx) {
        boolean sawLiteral = false;
        for (String literal : clause.split(",")) {
            String s = literal.trim();
            if (s.isEmpty()) continue;
            sawLiteral = true;
            if (literalSatisfied(s, ctx)) return true;
        }
        return !sawLiteral;
    }

    private static boolean literalSatisfied(@Nonnull String literal, @Nonnull PatchContext ctx) {
        boolean negated = literal.charAt(0) == '-';
        String entry = stripNegation(literal);
        boolean satisfied = isPackLiteral(entry) ? packSatisfied(entry, ctx) : expressionSatisfied(entry, ctx);
        return negated != satisfied;
    }

    private static boolean packSatisfied(@Nonnull String entry, @Nonnull PatchContext ctx) {
        int secondColon = entry.indexOf(':', entry.indexOf(':') + 1);
        String name = secondColon >= 0 ? entry.substring(0, secondColon) : entry;
        String range = secondColon >= 0 ? entry.substring(secondColon + 1) : null;
        return ctx.packPresent(name) && (range == null || ctx.versionSatisfies(name, range));
    }

    private static boolean expressionSatisfied(@Nonnull String entry, @Nonnull PatchContext ctx) {
        try {
            return ExpressionEvaluator.eval(entry, ctx.vars()) > 0;
        } catch (ExpressionException e) {
            return false;
        }
    }

    private static boolean isPackLiteral(@Nonnull String entry) {
        return entry.indexOf(':') >= 0;
    }

    @Nonnull
    private static String stripNegation(@Nonnull String literal) {
        return literal.charAt(0) == '-' ? literal.substring(1).trim() : literal;
    }
}

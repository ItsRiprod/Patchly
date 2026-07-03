package com.riprod.patchly.core.directive.builtins;

import com.google.gson.JsonElement;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.core.directive.RootDirective;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class RequiresDirective implements RootDirective {
    private static final String MARKER = "$Requires";

    @Nonnull
    @Override
    public String markerKey() {
        return MARKER;
    }

    @Override
    public boolean keep(@Nonnull JsonElement markerValue, @Nonnull PatchContext ctx) {
        List<String> clauses = new ArrayList<>();
        if (markerValue.isJsonArray()) {
            for (JsonElement e : markerValue.getAsJsonArray()) {
                if (e.isJsonPrimitive()) clauses.add(e.getAsString());
            }
        } else if (markerValue.isJsonPrimitive()) {
            clauses.add(markerValue.getAsString());
        }

        for (String clause : clauses) {
            if (!clauseSatisfied(clause, ctx)) return false;
        }
        return true;
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
        String entry = negated ? literal.substring(1).trim() : literal;

        int secondColon = entry.indexOf(':', entry.indexOf(':') + 1);
        String name = secondColon >= 0 ? entry.substring(0, secondColon) : entry;
        String range = secondColon >= 0 ? entry.substring(secondColon + 1) : null;

        boolean present = ctx.packPresent(name) && (range == null || ctx.versionSatisfies(name, range));
        return negated != present;
    }
}

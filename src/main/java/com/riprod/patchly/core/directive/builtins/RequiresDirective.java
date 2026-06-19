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

    // the array of clauses is an AND; within a clause, comma-separated literals are an OR
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

    // a leading - negates the literal (the pack must be absent); otherwise the pack must be present
    private static boolean literalSatisfied(@Nonnull String literal, @Nonnull PatchContext ctx) {
        boolean negated = literal.charAt(0) == '-';
        String entry = negated ? literal.substring(1).trim() : literal;

        // pack identifiers are exactly Group:Name (one colon); a second colon starts the optional
        // semver range, which itself never contains a colon
        int secondColon = entry.indexOf(':', entry.indexOf(':') + 1);
        String name = secondColon >= 0 ? entry.substring(0, secondColon) : entry;
        String range = secondColon >= 0 ? entry.substring(secondColon + 1) : null;

        boolean present = ctx.packPresent(name) && (range == null || ctx.versionSatisfies(name, range));
        return negated != present;
    }
}

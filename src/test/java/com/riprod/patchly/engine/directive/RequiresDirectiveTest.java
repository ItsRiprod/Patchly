package com.riprod.patchly.engine.directive;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.core.directive.builtins.RequiresDirective;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiresDirectiveTest {
    private static final RequiresDirective DIRECTIVE = new RequiresDirective();

    // A and C are installed; B and D are not
    private static final PatchContext CTX = new PatchContext() {
        private final Set<String> present = Set.of("Author:A", "Author:C");

        @Override
        public boolean packPresent(@Nonnull String packName) {
            return present.contains(packName);
        }

        @Override
        public boolean versionSatisfies(@Nonnull String packName, @Nonnull String range) {
            return present.contains(packName);
        }
    };

    private static boolean keep(String json) {
        JsonElement value = JsonParser.parseString(json);
        return DIRECTIVE.keep(value, CTX);
    }

    @Test
    void bareRequiredPresent() {
        assertTrue(keep("\"Author:A\""));
        assertTrue(keep("[\"Author:A\"]"));
    }

    @Test
    void bareRequiredAbsentFails() {
        assertFalse(keep("\"Author:B\""));
        assertFalse(keep("[\"Author:A\", \"Author:B\"]"));
    }

    @Test
    void negatedAbsentPasses() {
        assertTrue(keep("[\"-Author:B\"]"));
        assertTrue(keep("[\"Author:A\", \"-Author:B\"]"));
    }

    @Test
    void negatedPresentFails() {
        assertFalse(keep("[\"-Author:A\"]"));
    }

    @Test
    void orClauseAnyPresent() {
        assertTrue(keep("[\"Author:A,Author:B\"]"));
        assertTrue(keep("[\"Author:B,Author:C\"]"));
    }

    @Test
    void orClauseNonePresentFails() {
        assertFalse(keep("[\"Author:B,Author:D\"]"));
    }

    @Test
    void mixedAndOrNot() {
        // A present && B absent && (C or D present)
        assertTrue(keep("[\"Author:A\", \"-Author:B\", \"Author:C,Author:D\"]"));
        // breaks because A is required but negated here
        assertFalse(keep("[\"-Author:A\", \"Author:C,Author:D\"]"));
    }

    @Test
    void whitespaceAndNegationInOrClause() {
        // (A absent  ||  C present) -> C present satisfies
        assertTrue(keep("[\" -Author:B , Author:C \"]"));
        // (A present || ...) negated A is absent? A is present so -A fails, but C present in next literal
        assertTrue(keep("[\"-Author:A, Author:C\"]"));
    }

    @Test
    void semverRangeRespectedUnderOrAndNegation() {
        assertTrue(keep("[\"Author:A:>=1.0.0\"]"));
        // negated literal: A present at range -> present true -> negated fails; but B absent literal saves the OR
        assertTrue(keep("[\"-Author:A:>=1.0.0,-Author:B\"]"));
    }
}

package com.riprod.patchly.engine.directive;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.core.directive.builtins.RequiresDirective;
import com.riprod.patchly.core.vars.VarEnv;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiresDirectiveTest {
    private static final RequiresDirective DIRECTIVE = new RequiresDirective();

    // A and C are installed; B and D are not
    private static final PatchContext PACKS = new PatchContext() {
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

    private static final Map<String, Double> GLOBALS = Map.of("On", 1.0, "Off", 0.0, "Neg", -15.0, "Zero", 0.0);
    private static final PatchContext CTX = PatchContext.withVars(PACKS,
            new VarEnv(GLOBALS, Map.of("Scope", Map.of("X", 3.0))).lookup());

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

    @Test
    void flagLiteralTruthyAboveZero() {
        assertTrue(keep("\"$On\""));
        assertFalse(keep("\"$Off\""));
        assertFalse(keep("[\"$Neg\"]"));
        assertTrue(keep("[\"$Globals.On\"]"));
    }

    @Test
    void leadingMinusIsBooleanNot() {
        assertTrue(keep("[\"-$Off\"]"));
        assertTrue(keep("[\"-$Neg\"]"));
        assertTrue(keep("[\"-$Zero\"]"));
        assertFalse(keep("[\"-$On\"]"));
    }

    @Test
    void arithmeticExpressionsEvaluate() {
        assertTrue(keep("[\"abs($Neg * 2)\"]"));
        assertTrue(keep("[\"$Scope.X - 2\"]"));
        assertFalse(keep("[\"$Scope.X - 3\"]"));
        assertTrue(keep("[\"abs(-$Neg)\"]"));
    }

    @Test
    void mixedPackAndFlagClauses() {
        assertTrue(keep("[\"Author:B,$On\"]"));
        assertTrue(keep("[\"Author:A,$Off\"]"));
        assertFalse(keep("[\"Author:B,$Off\"]"));
        assertTrue(keep("[\"$On\", \"Author:A\", \"-$Off\"]"));
        assertFalse(keep("[\"$On\", \"Author:B\"]"));
    }

    @Test
    void unknownVariableIsFalseAndDiagnosed() {
        assertFalse(keep("[\"$Missing\"]"));
        assertTrue(keep("[\"-$Missing\"]"));
        List<RequiresDirective.Diagnostic> d = RequiresDirective.diagnose(JsonParser.parseString(
                "[\"Author:A\", \"$Missing\", \"$Gone.X\"]"), CTX);
        assertEquals(2, d.size());
        assertEquals("$Missing", d.get(0).literal());
        assertNull(d.get(0).missingScope());
        assertEquals("Gone", d.get(1).missingScope());
    }

    @Test
    void contextWithoutVarsFailsFlagsButNotPacks() {
        assertFalse(DIRECTIVE.keep(JsonParser.parseString("\"$On\""), PACKS));
        assertTrue(DIRECTIVE.keep(JsonParser.parseString("\"Author:A\""), PACKS));
        assertTrue(DIRECTIVE.keep(JsonParser.parseString("\"$Anything\""), PatchContext.ALWAYS));
    }
}

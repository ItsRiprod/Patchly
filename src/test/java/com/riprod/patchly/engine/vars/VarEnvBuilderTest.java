package com.riprod.patchly.engine.vars;

import com.google.gson.JsonParser;
import com.riprod.patchly.core.compile.CompileResult.UnresolvedExpression;
import com.riprod.patchly.core.compile.PatchSource;
import com.riprod.patchly.core.vars.ExpressionException;
import com.riprod.patchly.core.vars.VarEnv;
import com.riprod.patchly.core.vars.VarEnvBuilder;
import com.riprod.patchly.source.kinds.VarsKind;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VarEnvBuilderTest {
    private static final VarsKind VARS = new VarsKind();

    private static PatchSource vars(String fileName, String json) {
        return new PatchSource(Path.of(fileName), 0, fileName, VARS, JsonParser.parseString(json).getAsJsonObject());
    }

    private static double lookup(VarEnv env, String ref) throws ExpressionException {
        return env.lookup().lookup(ref);
    }

    @Test
    void globalsResolveBare() throws ExpressionException {
        List<UnresolvedExpression> errors = new ArrayList<>();
        VarEnv env = VarEnvBuilder.build(List.of(vars("Globals.vars", "{ \"Mana\": 100 }")), errors);
        assertTrue(errors.isEmpty());
        assertEquals(100.0, lookup(env, "Mana"));
    }

    @Test
    void namedScopeDerivesFromGlobals() throws ExpressionException {
        List<UnresolvedExpression> errors = new ArrayList<>();
        VarEnv env = VarEnvBuilder.build(List.of(
                vars("Globals.vars", "{ \"Head\": 15 }"),
                vars("Adamantite.vars", "{ \"Head\": \"$Head * 2\" }")), errors);
        assertTrue(errors.isEmpty());
        assertEquals(30.0, lookup(env, "Adamantite.Head"));
    }

    @Test
    void namedScopeCannotReferenceOtherScope() {
        List<UnresolvedExpression> errors = new ArrayList<>();
        VarEnv env = VarEnvBuilder.build(List.of(
                vars("Globals.vars", "{ \"X\": 1 }"),
                vars("Other.vars", "{ \"Y\": 2 }"),
                vars("Adamantite.vars", "{ \"Bad\": \"$Other.Y\" }")), errors);
        assertEquals(1, errors.size());
        assertThrows(ExpressionException.class, () -> env.lookup().lookup("Adamantite.Bad"));
    }

    @Test
    void sameStemMergesByOrderLastWins() throws ExpressionException {
        List<UnresolvedExpression> errors = new ArrayList<>();
        VarEnv env = VarEnvBuilder.build(List.of(
                vars("Globals.vars", "{ \"Mult\": 1 }"),
                vars("Globals.vars", "{ \"Mult\": 2 }")), errors);
        assertEquals(2.0, lookup(env, "Mult"));
    }

    @Test
    void globalsAreCaseExact() {
        List<UnresolvedExpression> errors = new ArrayList<>();
        VarEnv env = VarEnvBuilder.build(List.of(vars("globals.vars", "{ \"X\": 5 }")), errors);
        assertThrows(ExpressionException.class, () -> env.lookup().lookup("X"));
        assertDoesNotThrow(() -> env.lookup().lookup("globals.X"));
    }

    @Test
    void booleansBecomeOneAndZero() throws ExpressionException {
        List<UnresolvedExpression> errors = new ArrayList<>();
        VarEnv env = VarEnvBuilder.build(List.of(
                vars("Globals.vars", "{ \"On\": true, \"Off\": false }"),
                vars("Scope.vars", "{ \"Flag\": true, \"Derived\": \"$On * 5\" }")), errors);
        assertTrue(errors.isEmpty());
        assertEquals(1.0, lookup(env, "On"));
        assertEquals(0.0, lookup(env, "Off"));
        assertEquals(1.0, lookup(env, "Scope.Flag"));
        assertEquals(5.0, lookup(env, "Scope.Derived"));
    }

    @Test
    void qualifiedGlobalsResolveSameAsBare() throws ExpressionException {
        List<UnresolvedExpression> errors = new ArrayList<>();
        VarEnv env = VarEnvBuilder.build(List.of(
                vars("Globals.vars", "{ \"Mana\": 7 }"),
                vars("Scope.vars", "{ \"Twice\": \"$Globals.Mana * 2\" }")), errors);
        assertTrue(errors.isEmpty());
        assertEquals(7.0, lookup(env, "Globals.Mana"));
        assertEquals(14.0, lookup(env, "Scope.Twice"));
    }

    @Test
    void asMapNestsGlobalsUnderReservedKey() {
        List<UnresolvedExpression> errors = new ArrayList<>();
        VarEnv env = VarEnvBuilder.build(List.of(
                vars("Globals.vars", "{ \"On\": true }"),
                vars("Adamantite.vars", "{ \"Head\": 3 }")), errors);
        var map = env.asMap();
        assertEquals(1.0, map.get("Globals").get("On"));
        assertEquals(3.0, map.get("Adamantite").get("Head"));
        assertThrows(UnsupportedOperationException.class, () -> map.put("X", null));
    }

    @Test
    void missingScopeIsCarriedOnTheException() {
        List<UnresolvedExpression> errors = new ArrayList<>();
        VarEnv env = VarEnvBuilder.build(List.of(vars("Globals.vars", "{ \"X\": 1 }")), errors);
        ExpressionException e = assertThrows(ExpressionException.class, () -> env.lookup().lookup("Gone.X"));
        assertEquals("Gone", e.missingScope());
    }

    @Test
    void nonNumericGlobalRecordsError() {
        List<UnresolvedExpression> errors = new ArrayList<>();
        VarEnv env = VarEnvBuilder.build(List.of(vars("Globals.vars", "{ \"Bad\": \"text\" }")), errors);
        assertEquals(1, errors.size());
        assertThrows(ExpressionException.class, () -> env.lookup().lookup("Bad"));
    }
}

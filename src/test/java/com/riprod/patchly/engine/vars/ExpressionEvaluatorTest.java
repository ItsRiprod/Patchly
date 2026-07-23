package com.riprod.patchly.engine.vars;

import com.riprod.patchly.core.vars.ExpressionEvaluator;
import com.riprod.patchly.core.vars.ExpressionEvaluator.VarLookup;
import com.riprod.patchly.core.vars.ExpressionException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpressionEvaluatorTest {
    private static VarLookup vars(Map<String, Double> values) {
        return ref -> {
            Double v = values.get(ref);
            if (v == null) throw new ExpressionException("unknown '" + ref + "'");
            return v;
        };
    }

    private static double eval(String expr) throws ExpressionException {
        return ExpressionEvaluator.eval(expr, vars(Map.of()));
    }

    private static double eval(String expr, Map<String, Double> values) throws ExpressionException {
        return ExpressionEvaluator.eval(expr, vars(values));
    }

    @Test
    void precedence() throws ExpressionException {
        assertEquals(14.0, eval("2 + 3 * 4"));
    }

    @Test
    void parentheses() throws ExpressionException {
        assertEquals(20.0, eval("(2 + 3) * 4"));
    }

    @Test
    void unaryMinus() throws ExpressionException {
        assertEquals(-6.0, eval("-2 * 3"));
    }

    @Test
    void division() throws ExpressionException {
        assertEquals(2.5, eval("5 / 2"));
    }

    @Test
    void variablesAndArithmetic() throws ExpressionException {
        assertEquals(31.0, eval("$Mana * $Head + 1", Map.of("Mana", 100.0, "Head", 0.3)));
    }

    @Test
    void qualifiedReference() throws ExpressionException {
        assertEquals(130.0, eval("$Adamantite.Mana * 2", Map.of("Adamantite.Mana", 65.0)));
    }

    @Test
    void functions() throws ExpressionException {
        assertEquals(8.0, eval("round(7.8)"));
        assertEquals(7.0, eval("floor(7.8)"));
        assertEquals(8.0, eval("ceil(7.2)"));
        assertEquals(5.0, eval("abs(-5)"));
        assertEquals(7.0, eval("int(7.9)"));
        assertEquals(2.0, eval("min(9, 2, 5)"));
        assertEquals(9.0, eval("max(9, 2, 5)"));
        assertEquals(5.0, eval("clamp(10, 1, 5)"));
    }

    @Test
    void divideByZeroThrows() {
        assertThrows(ExpressionException.class, () -> eval("5 / 0"));
    }

    @Test
    void unknownVariableThrows() {
        assertThrows(ExpressionException.class, () -> eval("$Nope + 1"));
    }

    @Test
    void unknownFunctionThrows() {
        assertThrows(ExpressionException.class, () -> eval("bogus(1)"));
    }

    @Test
    void barewordWithoutDollarThrows() {
        assertThrows(ExpressionException.class, () -> eval("Mana * 2"));
    }

    @Test
    void trailingInputThrows() {
        assertThrows(ExpressionException.class, () -> eval("1 2"));
    }

    @Test
    void wrongArityThrows() {
        assertThrows(ExpressionException.class, () -> eval("round(1, 2)"));
    }
}

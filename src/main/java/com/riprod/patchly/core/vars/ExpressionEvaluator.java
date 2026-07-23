package com.riprod.patchly.core.vars;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class ExpressionEvaluator {
    @FunctionalInterface
    public interface VarLookup {
        double lookup(@Nonnull String ref) throws ExpressionException;
    }

    private final String src;
    private final VarLookup lookup;
    private int pos;

    private ExpressionEvaluator(@Nonnull String src, @Nonnull VarLookup lookup) {
        this.src = src;
        this.lookup = lookup;
    }

    public static double eval(@Nonnull String expr, @Nonnull VarLookup lookup) throws ExpressionException {
        ExpressionEvaluator e = new ExpressionEvaluator(expr, lookup);
        e.skipWs();
        if (e.pos >= e.src.length()) throw new ExpressionException("empty expression");
        double value = e.parseExpr();
        e.skipWs();
        if (e.pos < e.src.length()) {
            throw new ExpressionException("unexpected input '" + e.src.substring(e.pos) + "' in \"" + expr + "\"");
        }
        if (!Double.isFinite(value)) throw new ExpressionException("non-finite result in \"" + expr + "\"");
        return value;
    }

    private double parseExpr() throws ExpressionException {
        double value = parseTerm();
        while (true) {
            skipWs();
            char c = peek();
            if (c == '+') {
                pos++;
                value += parseTerm();
            } else if (c == '-') {
                pos++;
                value -= parseTerm();
            } else {
                return value;
            }
        }
    }

    private double parseTerm() throws ExpressionException {
        double value = parseFactor();
        while (true) {
            skipWs();
            char c = peek();
            if (c == '*') {
                pos++;
                value *= parseFactor();
            } else if (c == '/') {
                pos++;
                double divisor = parseFactor();
                if (divisor == 0.0) throw new ExpressionException("division by zero in \"" + src + "\"");
                value /= divisor;
            } else {
                return value;
            }
        }
    }

    private double parseFactor() throws ExpressionException {
        skipWs();
        char c = peek();
        if (c == '-') {
            pos++;
            return -parseFactor();
        }
        if (c == '+') {
            pos++;
            return parseFactor();
        }
        if (c == '(') {
            pos++;
            double value = parseExpr();
            expect(')');
            return value;
        }
        if (c == '$') {
            return parseVarRef();
        }
        if (isDigit(c) || c == '.') {
            return parseNumber();
        }
        if (isIdentStart(c)) {
            return parseFunctionCall();
        }
        throw new ExpressionException("unexpected character '" + c + "' at position " + pos + " in \"" + src + "\"");
    }

    private double parseVarRef() throws ExpressionException {
        pos++;
        StringBuilder ref = new StringBuilder(readIdent("variable name"));
        if (peek() == '.') {
            pos++;
            ref.append('.').append(readIdent("variable name"));
        }
        return lookup.lookup(ref.toString());
    }

    private double parseFunctionCall() throws ExpressionException {
        String name = readIdent("function name");
        skipWs();
        expect('(');
        List<Double> args = new ArrayList<>();
        skipWs();
        if (peek() != ')') {
            args.add(parseExpr());
            skipWs();
            while (peek() == ',') {
                pos++;
                args.add(parseExpr());
                skipWs();
            }
        }
        expect(')');
        return applyFunction(name, args);
    }

    private double parseNumber() throws ExpressionException {
        int start = pos;
        while (isDigit(peek())) pos++;
        if (peek() == '.') {
            pos++;
            while (isDigit(peek())) pos++;
        }
        String text = src.substring(start, pos);
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new ExpressionException("malformed number '" + text + "' in \"" + src + "\"");
        }
    }

    private static double applyFunction(@Nonnull String name, @Nonnull List<Double> args) throws ExpressionException {
        switch (name) {
            case "round":
                return Math.rint(arg(name, args, 1).get(0));
            case "floor":
                return Math.floor(arg(name, args, 1).get(0));
            case "ceil":
                return Math.ceil(arg(name, args, 1).get(0));
            case "abs":
                return Math.abs(arg(name, args, 1).get(0));
            case "int":
                return (double) (long) arg(name, args, 1).get(0).doubleValue();
            case "min": {
                requireAtLeast(name, args, 1);
                double m = args.get(0);
                for (double v : args) m = Math.min(m, v);
                return m;
            }
            case "max": {
                requireAtLeast(name, args, 1);
                double m = args.get(0);
                for (double v : args) m = Math.max(m, v);
                return m;
            }
            case "clamp": {
                arg(name, args, 3);
                return Math.min(Math.max(args.get(0), args.get(1)), args.get(2));
            }
            default:
                throw new ExpressionException("unknown function '" + name + "'");
        }
    }

    @Nonnull
    private static List<Double> arg(@Nonnull String name, @Nonnull List<Double> args, int arity) throws ExpressionException {
        if (args.size() != arity) {
            throw new ExpressionException(name + "() expects " + arity + " argument(s), got " + args.size());
        }
        return args;
    }

    private static void requireAtLeast(@Nonnull String name, @Nonnull List<Double> args, int min) throws ExpressionException {
        if (args.size() < min) {
            throw new ExpressionException(name + "() expects at least " + min + " argument(s), got " + args.size());
        }
    }

    @Nonnull
    private String readIdent(@Nonnull String what) throws ExpressionException {
        int start = pos;
        while (isIdentPart(peek())) pos++;
        if (pos == start) throw new ExpressionException("expected " + what + " at position " + pos + " in \"" + src + "\"");
        return src.substring(start, pos);
    }

    private void expect(char c) throws ExpressionException {
        skipWs();
        if (peek() != c) throw new ExpressionException("expected '" + c + "' at position " + pos + " in \"" + src + "\"");
        pos++;
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
        return c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }
}

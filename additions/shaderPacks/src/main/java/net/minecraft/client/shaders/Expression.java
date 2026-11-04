package net.minecraft.client.shaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * The little expression language of OptiFine shader packs, used both for
 * {@code #if} lines and for the custom uniforms in shaders.properties
 * ({@code uniform.float.x = smooth(if(a > 0, 1, 0), 2, 2)}): numbers, names
 * (with {@code .x}/{@code .y}/{@code .z} parts), C operators and a handful of
 * functions. Everything is a double; true is 1 and false 0. Names are looked up
 * each time the expression is evaluated, so one parse serves every frame.
 */
public final class Expression {
    /** A parsed expression. */
    public interface Node { double eval(ToDoubleFunction<String> names); }

    private final String text;
    private int at;

    private Expression(String text) { this.text = text; }

    public static Node parse(String text) {
        Expression parser = new Expression(text);
        Node node = parser.ternary();
        parser.space();
        if (parser.at < text.length()) throw new IllegalArgumentException("Unexpected '" + text.substring(parser.at) + "' in " + text);
        return node;
    }

    private Node ternary() {
        Node condition = binary(0);
        if (!eat("?")) return condition;
        Node yes = ternary();
        expect(":");
        Node no = ternary();
        return n -> condition.eval(n) != 0 ? yes.eval(n) : no.eval(n);
    }

    private static final String[][] LEVELS = {
        {"||"}, {"&&"}, {"|"}, {"^"}, {"&"}, {"==", "!="}, {"<=", ">=", "<", ">"}, {"<<", ">>"}, {"+", "-"}, {"*", "/", "%"}};

    private Node binary(int level) {
        if (level == LEVELS.length) return unary();
        Node left = binary(level + 1);
        while (true) {
            String op = null;
            space();
            for (String candidate : LEVELS[level]) {
                // "|" must not take the first half of "||", nor "<" of "<<" or "<=".
                if (text.startsWith(candidate, at) && !longer(candidate)) { op = candidate; break; }
            }
            if (op == null) return left;
            at += op.length();
            Node a = left, b = binary(level + 1);
            left = switch (op) {
                case "||" -> n -> a.eval(n) != 0 || b.eval(n) != 0 ? 1 : 0;
                case "&&" -> n -> a.eval(n) != 0 && b.eval(n) != 0 ? 1 : 0;
                case "|" -> n -> (long) a.eval(n) | (long) b.eval(n);
                case "^" -> n -> (long) a.eval(n) ^ (long) b.eval(n);
                case "&" -> n -> (long) a.eval(n) & (long) b.eval(n);
                case "==" -> n -> a.eval(n) == b.eval(n) ? 1 : 0;
                case "!=" -> n -> a.eval(n) != b.eval(n) ? 1 : 0;
                case "<=" -> n -> a.eval(n) <= b.eval(n) ? 1 : 0;
                case ">=" -> n -> a.eval(n) >= b.eval(n) ? 1 : 0;
                case "<" -> n -> a.eval(n) < b.eval(n) ? 1 : 0;
                case ">" -> n -> a.eval(n) > b.eval(n) ? 1 : 0;
                case "<<" -> n -> (long) a.eval(n) << (long) b.eval(n);
                case ">>" -> n -> (long) a.eval(n) >> (long) b.eval(n);
                case "+" -> n -> a.eval(n) + b.eval(n);
                case "-" -> n -> a.eval(n) - b.eval(n);
                case "*" -> n -> a.eval(n) * b.eval(n);
                case "/" -> n -> a.eval(n) / b.eval(n);
                default -> n -> a.eval(n) % b.eval(n);
            };
        }
    }

    private boolean longer(String op) {
        int end = at + op.length();
        if (end >= text.length()) return false;
        char next = text.charAt(end);
        return switch (op) {
            case "|" -> next == '|';
            case "&" -> next == '&';
            case "<" -> next == '<' || next == '=';
            case ">" -> next == '>' || next == '=';
            default -> false;
        };
    }

    private Node unary() {
        space();
        if (eat("!")) { Node a = unary(); return n -> a.eval(n) == 0 ? 1 : 0; }
        if (eat("-")) { Node a = unary(); return n -> -a.eval(n); }
        if (eat("+")) return unary();
        if (eat("~")) { Node a = unary(); return n -> ~(long) a.eval(n); }
        return primary();
    }

    private Node primary() {
        space();
        if (eat("(")) {
            Node inner = ternary();
            expect(")");
            return inner;
        }
        int start = at;
        if (at < text.length() && (Character.isDigit(text.charAt(at)) || text.charAt(at) == '.')) {
            while (at < text.length() && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '.'
                || (text.charAt(at) == '-' || text.charAt(at) == '+') && Character.toLowerCase(text.charAt(at - 1)) == 'e'
                && !text.startsWith("0x", start))) at++;
            double value = number(text.substring(start, at));
            return n -> value;
        }
        while (at < text.length() && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '_' || text.charAt(at) == '.')) at++;
        if (start == at) throw new IllegalArgumentException("Expected a value at '" + text.substring(at) + "' in " + text);
        String name = text.substring(start, at);
        space();
        if (!eat("(")) {
            if (name.equals("true")) return n -> 1;
            if (name.equals("false")) return n -> 0;
            if (name.equals("pi")) return n -> Math.PI;
            return n -> n.applyAsDouble(name);
        }
        List<Node> args = new ArrayList<>();
        space();
        if (!eat(")")) {
            do args.add(ternary()); while (eat(","));
            expect(")");
        }
        return function(name, args.toArray(new Node[0]));
    }

    private static double number(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        if (t.startsWith("0x")) return Long.parseLong(t.substring(2).replaceAll("[ul]+$", ""), 16);
        t = t.replaceAll("(?<=[0-9.])(f|lf|u|l)$", "");
        return Double.parseDouble(t);
    }

    private static Node function(String name, Node[] a) {
        return switch (name) {
            case "if" -> n -> {
                // if(c1, v1, c2, v2, ..., else)
                for (int i = 0; i + 1 < a.length; i += 2) if (a[i].eval(n) != 0) return a[i + 1].eval(n);
                return a.length % 2 == 1 ? a[a.length - 1].eval(n) : 0;
            };
            case "in" -> n -> {
                double value = a[0].eval(n);
                for (int i = 1; i < a.length; i++) if (a[i].eval(n) == value) return 1;
                return 0;
            };
            case "between" -> n -> { double v = a[0].eval(n); return v >= a[1].eval(n) && v <= a[2].eval(n) ? 1 : 0; };
            case "equals" -> n -> Math.abs(a[0].eval(n) - a[1].eval(n)) <= (a.length > 2 ? a[2].eval(n) : 0) ? 1 : 0;
            case "smooth" -> smooth(a);
            case "abs" -> n -> Math.abs(a[0].eval(n));
            case "min" -> n -> { double m = a[0].eval(n); for (int i = 1; i < a.length; i++) m = Math.min(m, a[i].eval(n)); return m; };
            case "max" -> n -> { double m = a[0].eval(n); for (int i = 1; i < a.length; i++) m = Math.max(m, a[i].eval(n)); return m; };
            case "clamp" -> n -> Math.max(a[1].eval(n), Math.min(a[2].eval(n), a[0].eval(n)));
            case "sqrt" -> n -> Math.sqrt(a[0].eval(n));
            case "pow" -> n -> Math.pow(a[0].eval(n), a[1].eval(n));
            case "exp" -> n -> Math.exp(a[0].eval(n));
            case "log" -> n -> Math.log(a[0].eval(n));
            case "sin" -> n -> Math.sin(a[0].eval(n));
            case "cos" -> n -> Math.cos(a[0].eval(n));
            case "tan" -> n -> Math.tan(a[0].eval(n));
            case "asin" -> n -> Math.asin(a[0].eval(n));
            case "acos" -> n -> Math.acos(a[0].eval(n));
            case "atan" -> n -> a.length > 1 ? Math.atan2(a[0].eval(n), a[1].eval(n)) : Math.atan(a[0].eval(n));
            case "atan2" -> n -> Math.atan2(a[0].eval(n), a[1].eval(n));
            case "torad" -> n -> Math.toRadians(a[0].eval(n));
            case "todeg" -> n -> Math.toDegrees(a[0].eval(n));
            case "floor" -> n -> Math.floor(a[0].eval(n));
            case "ceil" -> n -> Math.ceil(a[0].eval(n));
            case "round" -> n -> Math.round(a[0].eval(n));
            case "frac" -> n -> { double v = a[0].eval(n); return v - Math.floor(v); };
            case "signum" -> n -> Math.signum(a[0].eval(n));
            case "fmod" -> n -> { double x = a[0].eval(n), y = a[1].eval(n); return x - y * Math.floor(x / y); };
            case "hypot" -> n -> Math.hypot(a[0].eval(n), a[1].eval(n));
            case "random" -> n -> Math.random();
            case "defined" -> n -> 0;
            default -> n -> 0; // shortcut: functions this table lacks (vec2(), randomInt()...) read as 0; add them when a pack needs one.
        };
    }

    /**
     * smooth([id,] value, [up seconds, [down seconds]]): follows {@code value}
     * exponentially, covering 99% of a step in the given time. Each call site
     * keeps its own state, so the id is not needed.
     */
    private static Node smooth(Node[] a) {
        int skip = a.length >= 4 ? 1 : 0; // the optional leading id
        Node value = a[skip];
        Node up = a.length > skip + 1 ? a[skip + 1] : n -> 1;
        Node down = a.length > skip + 2 ? a[skip + 2] : up;
        double[] state = {Double.NaN, 0};
        return n -> {
            double target = value.eval(n);
            long now = System.nanoTime();
            if (Double.isNaN(state[0])) { state[0] = target; state[1] = now; return target; }
            double seconds = (now - state[1]) / 1e9;
            state[1] = now;
            double time = target > state[0] ? up.eval(n) : down.eval(n);
            double k = time <= 0 ? 1 : 1 - Math.pow(0.01, seconds / time);
            state[0] += (target - state[0]) * k;
            return state[0];
        };
    }

    private void space() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }

    private boolean eat(String token) {
        space();
        if (!text.startsWith(token, at)) return false;
        at += token.length();
        return true;
    }

    private void expect(String token) {
        if (!eat(token)) throw new IllegalArgumentException("Expected '" + token + "' at '" + text.substring(at) + "' in " + text);
    }
}

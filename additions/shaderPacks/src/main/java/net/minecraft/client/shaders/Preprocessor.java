package net.minecraft.client.shaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enough of the C preprocessor to read a shader pack the way the driver will:
 * {@code #define}/{@code #undef} of plain macros and the {@code #if} family.
 * The game uses it to find what the active code declares (buffer formats,
 * DRAWBUFFERS, shadow map size...) and to read shaders.properties and
 * block.properties, which packs write with {@code #if} lines too. The GLSL
 * itself still goes to the driver unprocessed.
 */
final class Preprocessor {
    private static final Pattern DIRECTIVE = Pattern.compile("^\\s*#\\s*(\\w+)\\s*(.*)$");
    private static final Pattern DEFINED = Pattern.compile("defined\\s*\\(?\\s*(\\w+)\\s*\\)?");
    private static final Pattern WORD = Pattern.compile("[A-Za-z_]\\w*");

    final Map<String, String> macros = new HashMap<>();

    Preprocessor(Map<String, String> macros) { this.macros.putAll(macros); }

    /**
     * The lines that survive the conditionals, comments kept. With {@code expand}, macros in
     * those lines are replaced by their values (for .properties files).
     */
    List<String> process(String source, boolean expand) {
        List<String> out = new ArrayList<>();
        // Each level: whether this branch is live, and whether any branch of it was taken yet.
        List<boolean[]> stack = new ArrayList<>();
        boolean live = true;
        String[] lines = source.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            while (line.endsWith("\\") && i + 1 < lines.length) line = line.substring(0, line.length() - 1) + lines[++i];
            Matcher m = DIRECTIVE.matcher(line);
            if (!m.matches()) {
                if (live) out.add(expand ? expand(line) : line);
                continue;
            }
            String directive = m.group(1), rest = stripComment(m.group(2)).trim();
            switch (directive) {
                case "if", "ifdef", "ifndef" -> {
                    boolean taken = live && switch (directive) {
                        case "ifdef" -> macros.containsKey(firstWord(rest));
                        case "ifndef" -> !macros.containsKey(firstWord(rest));
                        default -> evaluate(rest);
                    };
                    stack.add(new boolean[]{live, taken});
                    live = taken;
                }
                case "elif" -> {
                    if (stack.isEmpty()) continue;
                    boolean[] top = stack.get(stack.size() - 1);
                    live = top[0] && !top[1] && evaluate(rest);
                    top[1] |= live;
                }
                case "else" -> {
                    if (stack.isEmpty()) continue;
                    boolean[] top = stack.get(stack.size() - 1);
                    live = top[0] && !top[1];
                    top[1] = true;
                }
                case "endif" -> {
                    if (stack.isEmpty()) continue;
                    live = stack.remove(stack.size() - 1)[0];
                }
                case "define" -> {
                    if (!live) continue;
                    Matcher name = WORD.matcher(rest);
                    if (!name.lookingAt()) continue;
                    String value = rest.substring(name.end());
                    // Function-like macros are only remembered as defined.
                    macros.put(name.group(), value.startsWith("(") ? "1" : value.trim());
                    out.add(line);
                }
                case "undef" -> { if (live) macros.remove(firstWord(rest)); }
                default -> { if (live) out.add(line); }
            }
        }
        return out;
    }

    boolean evaluate(String condition) {
        Matcher m = DEFINED.matcher(condition);
        StringBuilder text = new StringBuilder();
        while (m.find()) m.appendReplacement(text, macros.containsKey(m.group(1)) ? "1" : "0");
        m.appendTail(text);
        try {
            return Expression.parse(expand(text.toString())).eval(name -> 0) != 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** {@code text} with every macro name replaced by its value, repeatedly (up to a depth). */
    String expand(String text) {
        for (int depth = 0; depth < 16; depth++) {
            Matcher m = WORD.matcher(text);
            StringBuilder out = new StringBuilder();
            boolean changed = false;
            while (m.find()) {
                String value = macros.get(m.group());
                if (value == null || value.equals(m.group())) continue;
                m.appendReplacement(out, Matcher.quoteReplacement(value));
                changed = true;
            }
            m.appendTail(out);
            text = out.toString();
            if (!changed) break;
        }
        return text;
    }

    static String stripComment(String text) {
        int line = text.indexOf("//");
        if (line >= 0) text = text.substring(0, line);
        return text.replaceAll("/\\*.*?\\*/", " ");
    }

    private static String firstWord(String text) {
        Matcher m = WORD.matcher(text);
        return m.find() ? m.group() : "";
    }
}

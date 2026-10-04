package net.legacycraft.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader/writer so the launcher stays dependency-free. */
final class Json {
    private final String text;
    private int pos;

    private Json(String text) { this.text = text; }

    static Object parse(String text) {
        Json json = new Json(text);
        json.skip();
        Object value = json.value();
        json.skip();
        if (json.pos != text.length()) throw json.error("Trailing characters");
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        return value instanceof List ? (List<Object>) value : new ArrayList<>();
    }

    static String string(Object value, String fallback) { return value instanceof String s ? s : fallback; }

    static Integer integer(Object value) { return value instanceof Number n ? n.intValue() : null; }

    static boolean bool(Object value, boolean fallback) { return value instanceof Boolean b ? b : fallback; }

    private Object value() {
        if (pos >= text.length()) throw error("Unexpected end");
        char c = text.charAt(pos);
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skip();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skip();
            String key = readString();
            skip();
            if (peek() != ':') throw error("Expected ':'");
            pos++;
            skip();
            map.put(key, value());
            skip();
            char c = text.charAt(pos++);
            if (c == '}') return map;
            if (c != ',') throw error("Expected ',' or '}'");
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++;
        skip();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            skip();
            list.add(value());
            skip();
            char c = text.charAt(pos++);
            if (c == ']') return list;
            if (c != ',') throw error("Expected ',' or ']'");
        }
    }

    private String readString() {
        if (peek() != '"') throw error("Expected string");
        pos++;
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = text.charAt(pos++);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            char e = text.charAt(pos++);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> { out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16)); pos += 4; }
                default -> out.append(e);
            }
        }
    }

    private Number readNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
        String number = text.substring(start, pos);
        if (number.isEmpty()) throw error("Unexpected character");
        if (number.contains(".") || number.contains("e") || number.contains("E")) return Double.parseDouble(number);
        return Long.parseLong(number);
    }

    private void expect(String word) {
        if (!text.startsWith(word, pos)) throw error("Expected " + word);
        pos += word.length();
    }

    private char peek() { return pos < text.length() ? text.charAt(pos) : '\0'; }

    private void skip() { while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++; }

    private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at " + pos); }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, 0);
        return out.append('\n').toString();
    }

    private static void write(StringBuilder out, Object value, int indent) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) { out.append("{}"); return; }
            out.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                pad(out, indent + 1);
                quote(out, String.valueOf(entry.getKey()));
                out.append(": ");
                write(out, entry.getValue(), indent + 1);
                if (++i < map.size()) out.append(',');
                out.append('\n');
            }
            pad(out, indent);
            out.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) { out.append("[]"); return; }
            out.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                pad(out, indent + 1);
                write(out, list.get(i), indent + 1);
                if (i + 1 < list.size()) out.append(',');
                out.append('\n');
            }
            pad(out, indent);
            out.append(']');
        } else if (value instanceof String s) {
            quote(out, s);
        } else {
            out.append(value);
        }
    }

    private static void pad(StringBuilder out, int indent) { out.append("  ".repeat(indent)); }

    private static void quote(StringBuilder out, String s) {
        out.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}

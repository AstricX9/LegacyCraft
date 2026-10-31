package net.legacycraft.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Additions catalog (one JSON file per addition in the game's
 * assets/legacycraft/additions folder, read from the LegacyCraft folder, or
 * the copy run-launcher.bat puts next to the classes; files starting with _,
 * like the template, are skipped) and the switches and settings the launcher
 * edits. Game additions are per instance, in additions.txt in its folder, the
 * file the game reads, with settings as {@code addition.setting:value} lines;
 * launcher additions are the launcher's own, in launcher_profiles.json.
 */
final class Additions {
    static final String CATALOG = "src/main/resources/assets/legacycraft/additions";
    /** Where the game's settings button image is, in the LegacyCraft folder. */
    static final String SETTINGS_ICON = "src/main/resources/assets/minecraft/textures/gui/settings.png";
    static final String INSTANCE_FILE = "additions.txt";
    static final String REMEMBER_TAB = "rememberTab", WHATS_NEW = "whatsNew", INSTANCE_SUMMARY = "instanceSummary";
    /** The additions.txt line that turns Additions on or off for an instance as a whole. */
    static final String ENABLED = "enabled";

    enum Category {
        GAMEPLAY("Gameplay"), GRAPHICS("Graphics"), INTERFACE("Interface"), ACCESSIBILITY("Accessibility"), LAUNCHER("Launcher");

        final String title;

        Category(String title) { this.title = title; }
    }

    record Entry(String id, Category category, boolean launcher, boolean defaultOn, String name, String description,
                 List<Setting> settings) {}

    /** One of an addition's settings; see the game's Addition.Setting, which this mirrors. */
    record Setting(String id, String type, String name, String description, String defaultValue, List<String> options,
                   double min, double max, double step, String unit, String folder, List<String> extensions) {
        static final String TOGGLE = "toggle", SLIDER = "slider", CHOICE = "choice", FILE = "file";

        /** {@code value} made valid for this setting; anything it can't use becomes the default. */
        String clean(String value) {
            if (value == null) return defaultValue;
            switch (type) {
                case TOGGLE: return value.equals("true") || value.equals("false") ? value : defaultValue;
                case SLIDER:
                    try { return number(snap(Double.parseDouble(value))); }
                    catch (NumberFormatException e) { return defaultValue; }
                case CHOICE: return options.contains(value) ? value : defaultValue;
                default: return value.contains("/") || value.contains("\\") || value.contains("..") ? defaultValue : value;
            }
        }

        double snap(double value) {
            double v = Math.max(min, Math.min(max, value));
            if (step > 0) v = Math.min(max, min + Math.round((v - min) / step) * step);
            return v;
        }

        String number(double value) {
            if (value == Math.rint(value)) return Long.toString((long) value);
            return Double.toString(Math.round(value * 1e6) / 1e6);
        }

        /** The files in the instance's folder for a file setting, by name. */
        List<String> files(Path gameDir) {
            List<String> names = new ArrayList<>();
            Path dir = gameDir.resolve(folder);
            if (!Files.isDirectory(dir)) return names;
            try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
                // An extension of "/" also lists folders (shader packs can be unzipped).
                stream.filter(p -> Files.isRegularFile(p) || extensions.contains("/") && Files.isDirectory(p))
                    .filter(p -> Files.isDirectory(p) || extensions.isEmpty()
                        || extensions.stream().anyMatch(x -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(x)))
                    .map(p -> p.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER).forEach(names::add);
            } catch (IOException e) {
                System.err.println("Couldn't list " + dir + ": " + e.getMessage());
            }
            return names;
        }
    }

    final List<Entry> entries;

    private Additions(List<Entry> entries) { this.entries = List.copyOf(entries); }

    /** The catalog from the LegacyCraft folder when there is one, else the launcher's copy; empty if neither can be read. */
    static Additions load(Path installDir) {
        try {
            Map<String, String> files = new java.util.TreeMap<>();
            Path folder = installDir == null ? null : installDir.resolve(CATALOG);
            if (folder != null && Files.isDirectory(folder)) {
                try (java.util.stream.Stream<Path> stream = Files.list(folder)) {
                    for (Path p : (Iterable<Path>) stream::iterator)
                        if (p.getFileName().toString().endsWith(".json"))
                            files.put(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8));
                }
            } else {
                files.putAll(bundled());
            }
            if (files.isEmpty()) throw new IOException("can't find " + CATALOG);
            List<Entry> list = new ArrayList<>();
            for (Map.Entry<String, String> file : files.entrySet()) {
                if (file.getKey().startsWith("_")) continue;
                try {
                    Entry e = parseEntry(Json.object(Json.parse(file.getValue())));
                    if (e != null) list.add(e);
                } catch (RuntimeException e) {
                    System.err.println("Skipping the addition " + file.getKey() + ": " + e);
                }
            }
            list.sort(java.util.Comparator.comparing(Entry::category).thenComparing(e -> e.name().toLowerCase(java.util.Locale.ROOT)));
            return new Additions(list);
        } catch (IOException | RuntimeException e) {
            System.err.println("Couldn't load the Additions: " + e.getMessage());
            return new Additions(List.of());
        }
    }

    /** The copy of the additions folder next to the launcher's classes (or in its jar). */
    private static Map<String, String> bundled() throws IOException {
        Map<String, String> files = new java.util.TreeMap<>();
        java.net.URL url = Additions.class.getResource("additions/");
        if (url == null) return files;
        try {
            if ("file".equals(url.getProtocol())) {
                try (java.util.stream.Stream<Path> stream = Files.list(Path.of(url.toURI()))) {
                    for (Path p : (Iterable<Path>) stream::iterator)
                        if (p.getFileName().toString().endsWith(".json"))
                            files.put(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8));
                }
            } else if ("jar".equals(url.getProtocol())) {
                java.net.JarURLConnection connection = (java.net.JarURLConnection) url.openConnection();
                connection.setUseCaches(false);
                String prefix = connection.getEntryName();
                try (java.util.jar.JarFile jar = connection.getJarFile()) {
                    for (java.util.jar.JarEntry entry : java.util.Collections.list(jar.entries())) {
                        String name = entry.getName();
                        if (!name.startsWith(prefix) || !name.endsWith(".json") || name.substring(prefix.length()).contains("/")) continue;
                        try (InputStream in = jar.getInputStream(entry)) {
                            files.put(name.substring(prefix.length()), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                        }
                    }
                }
            }
        } catch (java.net.URISyntaxException e) {
            throw new IOException(e);
        }
        return files;
    }

    private static Entry parseEntry(Map<String, Object> a) {
        String id = Json.string(a.get("id"), null);
        Category category;
        try { category = Category.valueOf(Json.string(a.get("category"), "").toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
        if (id == null) return null;
        List<Setting> settings = new ArrayList<>();
        if (a.get("settings") != null) for (Object o : Json.array(a.get("settings"))) {
            Setting s = parseSetting(Json.object(o));
            if (s != null) settings.add(s);
        }
        return new Entry(id, category, "launcher".equals(a.get("scope")), Json.bool(a.get("default"), false),
            Json.string(a.get("name"), id), Json.string(a.get("description"), ""), List.copyOf(settings));
    }

    private static Setting parseSetting(Map<String, Object> o) {
        String id = Json.string(o.get("id"), null), type = Json.string(o.get("type"), "").toLowerCase(java.util.Locale.ROOT);
        if (id == null || !List.of(Setting.TOGGLE, Setting.SLIDER, Setting.CHOICE, Setting.FILE).contains(type)) return null;
        List<String> options = strings(o.get("options")), extensions = strings(o.get("extensions"));
        double min = number(o.get("min"), 0), max = number(o.get("max"), 100);
        String fallback = switch (type) {
            case Setting.TOGGLE -> "false";
            case Setting.SLIDER -> Double.toString(min);
            case Setting.CHOICE -> options.isEmpty() ? "" : options.get(0);
            default -> "";
        };
        Object d = o.get("default");
        String value = d == null ? fallback : d instanceof Number n ? Double.toString(n.doubleValue()) : String.valueOf(d);
        Setting raw = new Setting(id, type, Json.string(o.get("name"), id), Json.string(o.get("description"), ""), fallback, options,
            min, max, number(o.get("step"), 1), Json.string(o.get("unit"), ""), Json.string(o.get("folder"), id), extensions);
        return new Setting(id, type, raw.name(), raw.description(), raw.clean(value), options, min, max, raw.step(), raw.unit(),
            raw.folder(), extensions);
    }

    private static double number(Object value, double fallback) { return value instanceof Number n ? n.doubleValue() : fallback; }

    private static List<String> strings(Object value) {
        List<String> list = new ArrayList<>();
        if (value != null) for (Object o : Json.array(value)) if (o instanceof String str) list.add(str);
        return List.copyOf(list);
    }

    List<Entry> in(Category category) {
        List<Entry> list = new ArrayList<>();
        for (Entry e : entries) if (e.category == category) list.add(e);
        return list;
    }

    List<Entry> game() {
        List<Entry> list = new ArrayList<>();
        for (Entry e : entries) if (!e.launcher) list.add(e);
        return list;
    }

    /** Whether an instance's switches have Additions on; files from before the switch count as on when something is on. */
    boolean enabled(Map<String, Boolean> on) {
        Boolean enabled = on.get(ENABLED);
        if (enabled != null) return enabled;
        return switchedOn(on) > 0;
    }

    /** How many game additions are switched on (whether or not Additions are). */
    int switchedOn(Map<String, Boolean> on) {
        int n = 0;
        for (Entry e : game()) if (on.getOrDefault(e.id, e.defaultOn)) n++;
        return n;
    }

    // ---- an instance's additions.txt ----

    /** The switches saved in an instance folder, the enabled line included (a missing file means Additions are off). */
    static Map<String, Boolean> readInstance(Path gameDir) {
        Map<String, Boolean> on = new LinkedHashMap<>();
        for (String line : lines(gameDir)) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).indexOf('.') < 0)
                on.put(line.substring(0, colon).trim(), Boolean.parseBoolean(line.substring(colon + 1).trim()));
        }
        return on;
    }

    /** The settings saved in an instance folder, keyed {@code addition.setting}. */
    static Map<String, String> readSettings(Path gameDir) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines(gameDir)) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).indexOf('.') > 0)
                values.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
        return values;
    }

    /** An addition's setting for an instance: the saved value if it's valid, else the default. */
    static String setting(Map<String, String> values, Entry e, Setting s) {
        return s.clean(values.get(e.id() + "." + s.id()));
    }

    /** Saves one setting (null removes it, back to the default), keeping every other line. */
    static void writeSetting(Path gameDir, String key, String value) throws IOException {
        StringBuilder out = new StringBuilder();
        boolean written = false;
        for (String line : lines(gameDir)) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equals(key)) {
                if (value != null && !written) out.append(key).append(':').append(value).append('\n');
                written = true;
            } else {
                out.append(line).append('\n');
            }
        }
        if (!written && value != null) out.append(key).append(':').append(value).append('\n');
        Files.createDirectories(gameDir);
        Files.writeString(gameDir.resolve(INSTANCE_FILE), out, StandardCharsets.UTF_8);
    }

    /** Writes the game switches, keeping lines for additions this catalog doesn't know (a newer game may). */
    void writeInstance(Path gameDir, Map<String, Boolean> on) throws IOException {
        StringBuilder out = new StringBuilder();
        List<String> known = new ArrayList<>();
        known.add(ENABLED);
        out.append(ENABLED).append(':').append(enabled(on)).append('\n');
        for (Entry e : game()) {
            known.add(e.id);
            out.append(e.id).append(':').append(on.getOrDefault(e.id, e.defaultOn)).append('\n');
        }
        for (String line : lines(gameDir)) {
            int colon = line.indexOf(':');
            if (colon > 0 && !known.contains(line.substring(0, colon).trim())) out.append(line).append('\n');
        }
        Files.createDirectories(gameDir);
        Files.writeString(gameDir.resolve(INSTANCE_FILE), out, StandardCharsets.UTF_8);
    }

    private static List<String> lines(Path gameDir) {
        Path file = gameDir.resolve(INSTANCE_FILE);
        try { return Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of(); }
        catch (IOException e) { return List.of(); }
    }
}

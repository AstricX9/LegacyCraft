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
 * The Additions catalog (the game's assets/legacycraft/additions.json, read
 * from the LegacyCraft folder, or the copy run-launcher.bat puts next to the
 * classes) and the switches the launcher edits. Game additions are per
 * instance, in additions.txt in its folder, the file the game reads;
 * launcher additions are the launcher's own, in launcher_profiles.json.
 */
final class Additions {
    static final String CATALOG = "src/main/resources/assets/legacycraft/additions.json";
    static final String INSTANCE_FILE = "additions.txt";
    static final String REMEMBER_TAB = "rememberTab", WHATS_NEW = "whatsNew", INSTANCE_SUMMARY = "instanceSummary";

    enum Category {
        GAMEPLAY("Gameplay"), GRAPHICS("Graphics"), INTERFACE("Interface"), ACCESSIBILITY("Accessibility"), LAUNCHER("Launcher");

        final String title;

        Category(String title) { this.title = title; }
    }

    enum Look {
        FAITHFUL("Faithful", "Plain 1.8.9: every addition off."),
        RECOMMENDED("Recommended", "A few additions that make 1.8.9 easier to play without changing how it looks."),
        CUSTOM("Custom", "Your own choice of additions.");

        final String title, description;

        Look(String title, String description) { this.title = title; this.description = description; }

        @Override public String toString() { return title; }
    }

    record Entry(String id, Category category, boolean launcher, boolean recommended, String name, String description) {}

    final List<Entry> entries;

    private Additions(List<Entry> entries) { this.entries = List.copyOf(entries); }

    /** The catalog from the LegacyCraft folder when there is one, else the launcher's copy; empty if neither can be read. */
    static Additions load(Path installDir) {
        try {
            String text = null;
            if (installDir != null && Files.isRegularFile(installDir.resolve(CATALOG)))
                text = Files.readString(installDir.resolve(CATALOG), StandardCharsets.UTF_8);
            else try (InputStream in = Additions.class.getResourceAsStream("additions.json")) {
                if (in != null) text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (text == null) throw new IOException("can't find " + CATALOG);
            return parse(text);
        } catch (IOException | RuntimeException e) {
            System.err.println("Couldn't load the Additions: " + e.getMessage());
            return new Additions(List.of());
        }
    }

    static Additions parse(String json) {
        List<Entry> list = new ArrayList<>();
        for (Object o : Json.array(Json.object(Json.parse(json)).get("additions"))) {
            Map<String, Object> a = Json.object(o);
            String id = Json.string(a.get("id"), null);
            Category category;
            try { category = Category.valueOf(Json.string(a.get("category"), "").toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException e) { continue; }
            if (id == null) continue;
            list.add(new Entry(id, category, "launcher".equals(a.get("scope")), Json.bool(a.get("recommended"), false),
                Json.string(a.get("name"), id), Json.string(a.get("description"), "")));
        }
        return new Additions(list);
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

    /** The look a set of game switches adds up to. */
    Look look(Map<String, Boolean> on) {
        boolean faithful = true, recommended = true;
        for (Entry e : game()) {
            boolean value = on.getOrDefault(e.id, false);
            if (value) faithful = false;
            if (value != e.recommended) recommended = false;
        }
        return faithful ? Look.FAITHFUL : recommended ? Look.RECOMMENDED : Look.CUSTOM;
    }

    /** Sets every game switch for a look (Custom leaves them). */
    void apply(Look look, Map<String, Boolean> on) {
        if (look == Look.CUSTOM) return;
        for (Entry e : game()) on.put(e.id, look == Look.RECOMMENDED && e.recommended);
    }

    // ---- an instance's additions.txt ----

    /** The game switches saved in an instance folder (missing means all off, the Faithful look). */
    static Map<String, Boolean> readInstance(Path gameDir) {
        Map<String, Boolean> on = new LinkedHashMap<>();
        for (String line : lines(gameDir)) {
            int colon = line.indexOf(':');
            if (colon > 0) on.put(line.substring(0, colon).trim(), Boolean.parseBoolean(line.substring(colon + 1).trim()));
        }
        return on;
    }

    /** Writes the game switches, keeping lines for additions this catalog doesn't know (a newer game may). */
    void writeInstance(Path gameDir, Map<String, Boolean> on) throws IOException {
        StringBuilder out = new StringBuilder();
        List<String> known = new ArrayList<>();
        for (Entry e : game()) {
            known.add(e.id);
            out.append(e.id).append(':').append(on.getOrDefault(e.id, false)).append('\n');
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

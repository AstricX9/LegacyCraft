package net.minecraft.client.additions;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which Additions are on for this instance (DEV-041), LegacyCraft's own
 * take on a mod loader. Each addition is defined by its own JSON file in
 * {@code assets/legacycraft/additions/} (files starting with {@code _}, like
 * the template, are skipped). The switches are saved to {@code additions.txt}
 * in the game folder as {@code id:true|false} lines, like options.txt, so the
 * launcher can set them before the game starts; an addition's settings follow
 * as {@code id.setting:value} lines.
 * The {@code enabled} line turns Additions on or off as a whole: off (the
 * default) plays as plain 1.8.9, with every addition inactive and no
 * Additions button on the title screen.
 */
public final class Additions {
    public static final String TOGGLE_SPRINT = "toggleSprint", TOGGLE_SNEAK = "toggleSneak", FULL_BRIGHT = "fullBright",
        COORDINATES = "coordinates", FPS_COUNTER = "fpsCounter", NO_HURT_TILT = "noHurtTilt", STEADY_FOV = "steadyFov",
        SOLID_CROSSHAIR = "solidCrosshair", SHADER_PACKS = "shaderPacks";

    static final String CATALOG = "/assets/legacycraft/additions/";
    /** The additions.txt line that turns Additions on or off for the instance. */
    static final String ENABLED = "enabled";

    private final List<Addition> catalog;
    private final Map<String, Boolean> on = new LinkedHashMap<>();
    /** Settings saved for this instance, keyed {@code addition.setting}. */
    private final Map<String, String> values = new LinkedHashMap<>();
    /** Lines of additions.txt for additions this build doesn't know, written back unchanged. */
    private final List<String> unknown = new ArrayList<>();
    private Path file;
    /** Null until set or read: then Additions count as on if any addition is switched on (files from before the switch). */
    private Boolean enabled;

    Additions(List<Addition> catalog) { this.catalog = List.copyOf(catalog); }

    /** The catalog with everything off, not tied to a file. */
    public static Additions defaults() { return new Additions(loadCatalog()); }

    /** Reads the switches from {@code file} (missing means all off); {@link #save()} writes them back there. */
    public static Additions load(Path file) {
        Additions additions = defaults();
        additions.file = file;
        if (Files.isRegularFile(file)) {
            try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                additions.read(in);
            } catch (IOException e) {
                System.err.println("Couldn't read " + file + ": " + e.getMessage());
            }
        }
        return additions;
    }

    static List<Addition> loadCatalog() {
        List<Addition> list = new ArrayList<>();
        try {
            for (Map.Entry<String, byte[]> file : Resources.list(CATALOG, ".json").entrySet()) {
                if (file.getKey().startsWith("_")) continue;
                try {
                    list.add(parse(new InputStreamReader(new java.io.ByteArrayInputStream(file.getValue()), StandardCharsets.UTF_8)));
                } catch (RuntimeException e) {
                    System.err.println("Skipping the addition " + file.getKey() + ": " + e);
                }
            }
        } catch (IOException e) {
            System.err.println("Couldn't load the Additions: " + e.getMessage());
        }
        list.sort(ORDER);
        return list;
    }

    /** Category order, then by name. */
    static final java.util.Comparator<Addition> ORDER = java.util.Comparator.comparing(Addition::category)
        .thenComparing(a -> a.name().toLowerCase(java.util.Locale.ROOT));

    /** One addition's file. */
    static Addition parse(Reader reader) {
        JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
        String id = o.get("id").getAsString();
        if (id.contains(".") || id.contains(":")) throw new IllegalArgumentException("an id can't contain '.' or ':'");
        List<Addition.Setting> settings = new ArrayList<>();
        if (o.has("settings")) for (JsonElement element : o.getAsJsonArray("settings")) settings.add(parseSetting(element.getAsJsonObject()));
        return new Addition(id, Addition.Category.byId(o.get("category").getAsString()),
            "launcher".equals(o.get("scope").getAsString()) ? Addition.Scope.LAUNCHER : Addition.Scope.GAME,
            o.has("default") && o.get("default").getAsBoolean(), o.get("name").getAsString(), text(o, "description", ""), settings);
    }

    private static Addition.Setting parseSetting(JsonObject o) {
        String id = o.get("id").getAsString();
        Addition.Setting.Type type = Addition.Setting.Type.valueOf(o.get("type").getAsString().toUpperCase(java.util.Locale.ROOT));
        List<String> options = strings(o, "options"), extensions = strings(o, "extensions");
        double min = o.has("min") ? o.get("min").getAsDouble() : 0, max = o.has("max") ? o.get("max").getAsDouble() : 100;
        String fallback = switch (type) {
            case TOGGLE -> "false";
            case SLIDER -> Double.toString(min);
            case CHOICE -> options.isEmpty() ? "" : options.get(0);
            case FILE -> "";
        };
        Addition.Setting raw = new Addition.Setting(id, type, text(o, "name", id), text(o, "description", ""), fallback, options,
            min, max, o.has("step") ? o.get("step").getAsDouble() : 1, text(o, "unit", ""), text(o, "folder", id), extensions);
        String value = o.has("default") ? o.get("default").getAsString() : fallback;
        return new Addition.Setting(id, type, raw.name(), raw.description(), raw.clean(value), options, raw.min(), raw.max(),
            raw.step(), raw.unit(), raw.folder(), extensions);
    }

    private static String text(JsonObject o, String key, String fallback) { return o.has(key) ? o.get(key).getAsString() : fallback; }

    private static List<String> strings(JsonObject o, String key) {
        List<String> list = new ArrayList<>();
        if (o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) list.add(e.getAsString());
        return list;
    }

    void read(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String id = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (id.equals(ENABLED)) { enabled = Boolean.parseBoolean(value); continue; }
            int dot = id.indexOf('.');
            Addition a = find(dot < 0 ? id : id.substring(0, dot));
            if (dot >= 0) {
                Addition.Setting s = a == null || a.scope() != Addition.Scope.GAME ? null : a.setting(id.substring(dot + 1));
                if (s == null) unknown.add(line);
                else values.put(id, s.clean(value));
                continue;
            }
            if (a == null || a.scope() != Addition.Scope.GAME) unknown.add(line);
            else on.put(id, Boolean.parseBoolean(value));
        }
    }

    public void save() {
        if (file == null) return;
        StringBuilder out = new StringBuilder();
        out.append(ENABLED).append(':').append(enabled()).append('\n');
        for (Addition a : game()) {
            out.append(a.id()).append(':').append(isSwitchedOn(a.id())).append('\n');
            for (Addition.Setting s : a.settings()) {
                String key = a.id() + '.' + s.id();
                if (values.containsKey(key)) out.append(key).append(':').append(values.get(key)).append('\n');
            }
        }
        for (String line : unknown) out.append(line).append('\n');
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Couldn't save " + file + ": " + e.getMessage());
        }
    }

    /** Whether Additions are on for this instance at all. */
    public boolean enabled() {
        if (enabled != null) return enabled;
        for (Addition a : game()) if (isSwitchedOn(a.id())) return true;
        return false;
    }

    public void setEnabled(boolean value) { enabled = value; }

    /** Whether an addition is active: Additions are on and so is its own switch. */
    public boolean isOn(String id) { return enabled() && isSwitchedOn(id); }

    /** An addition's own switch, whether or not Additions are on. */
    public boolean isSwitchedOn(String id) {
        Boolean value = on.get(id);
        if (value != null) return value;
        Addition a = find(id);
        return a != null && a.defaultOn();
    }

    public void set(String id, boolean value) { on.put(id, value); }

    /** An addition's setting as saved, or its default; null if the addition has no such setting. */
    public String setting(String id, String key) {
        Addition a = find(id);
        Addition.Setting s = a == null ? null : a.setting(key);
        if (s == null) return null;
        return values.getOrDefault(id + '.' + key, s.defaultValue());
    }

    /** A toggle setting. */
    public boolean settingOn(String id, String key) { return Boolean.parseBoolean(setting(id, key)); }

    /** A slider setting; 0 if it isn't a number. */
    public double number(String id, String key) {
        try { return Double.parseDouble(setting(id, key)); }
        catch (NumberFormatException | NullPointerException e) { return 0; }
    }

    public void setSetting(String id, String key, String value) {
        Addition a = find(id);
        Addition.Setting s = a == null ? null : a.setting(key);
        if (s != null && s.accepts(value)) values.put(id + '.' + key, s.clean(value));
    }

    /** Puts an addition's settings back to their defaults. */
    public void resetSettings(String id) { values.keySet().removeIf(k -> k.startsWith(id + '.')); }

    /** The folder a file setting picks from. */
    public Path folder(Addition.Setting s) {
        Path base = file == null || file.getParent() == null ? Path.of(".") : file.getParent();
        return base.resolve(s.folder());
    }

    /** The files a file setting can pick, by name. */
    public List<String> files(Addition.Setting s) {
        List<String> names = new ArrayList<>();
        Path dir = folder(s);
        if (!Files.isDirectory(dir)) return names;
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            // An extension of "/" also lists folders (shader packs can be unzipped).
            stream.filter(p -> Files.isRegularFile(p) || s.extensions().contains("/") && Files.isDirectory(p))
                .filter(p -> Files.isDirectory(p) || s.extensions().isEmpty()
                    || s.extensions().stream().anyMatch(x -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(x)))
                .map(p -> p.getFileName().toString())
                .sorted(String.CASE_INSENSITIVE_ORDER).forEach(names::add);
        } catch (IOException e) {
            System.err.println("Couldn't list " + dir + ": " + e.getMessage());
        }
        return names;
    }

    public Addition find(String id) {
        for (Addition a : catalog) if (a.id().equals(id)) return a;
        return null;
    }

    /** The additions the game can switch (launcher additions are the launcher's). */
    public List<Addition> game() {
        List<Addition> list = new ArrayList<>();
        for (Addition a : catalog) if (a.scope() == Addition.Scope.GAME) list.add(a);
        return list;
    }

    public List<Addition> game(Addition.Category category) {
        List<Addition> list = new ArrayList<>();
        for (Addition a : game()) if (a.category() == category) list.add(a);
        return list;
    }
}

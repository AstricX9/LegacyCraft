package net.minecraft.client.additions;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
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
 * Which Additions are on for this instance (DEV-041). The catalog comes from
 * {@code assets/legacycraft/additions.json}; the switches are saved to
 * {@code additions.txt} in the game folder as {@code id:true|false} lines,
 * like options.txt, so the launcher can set them before the game starts.
 * Everything is off unless switched on, which is the Faithful look: plain 1.8.9.
 */
public final class Additions {
    public static final String TOGGLE_SPRINT = "toggleSprint", TOGGLE_SNEAK = "toggleSneak", FULL_BRIGHT = "fullBright",
        COORDINATES = "coordinates", FPS_COUNTER = "fpsCounter", NO_HURT_TILT = "noHurtTilt", STEADY_FOV = "steadyFov",
        SOLID_CROSSHAIR = "solidCrosshair";

    static final String CATALOG = "/assets/legacycraft/additions.json";

    /** How close to vanilla the game looks: a summary of the switches, and a way to set them all at once. */
    public enum Look {
        FAITHFUL("Faithful"), RECOMMENDED("Recommended"), CUSTOM("Custom");

        public final String title;

        Look(String title) { this.title = title; }
    }

    private final List<Addition> catalog;
    private final Map<String, Boolean> on = new LinkedHashMap<>();
    /** Lines of additions.txt for additions this build doesn't know, written back unchanged. */
    private final List<String> unknown = new ArrayList<>();
    private Path file;

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
        try (InputStream in = Additions.class.getResourceAsStream(CATALOG)) {
            if (in == null) throw new IOException(CATALOG + " is missing");
            return parseCatalog(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Couldn't load the Additions: " + e.getMessage());
            return List.of();
        }
    }

    static List<Addition> parseCatalog(Reader reader) {
        List<Addition> list = new ArrayList<>();
        for (JsonElement element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("additions")) {
            JsonObject o = element.getAsJsonObject();
            list.add(new Addition(o.get("id").getAsString(), Addition.Category.byId(o.get("category").getAsString()),
                "launcher".equals(o.get("scope").getAsString()) ? Addition.Scope.LAUNCHER : Addition.Scope.GAME,
                o.has("recommended") && o.get("recommended").getAsBoolean(), o.get("name").getAsString(), o.get("description").getAsString()));
        }
        return list;
    }

    void read(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String id = line.substring(0, colon).trim();
            Addition a = find(id);
            if (a == null || a.scope() != Addition.Scope.GAME) unknown.add(line);
            else on.put(id, Boolean.parseBoolean(line.substring(colon + 1).trim()));
        }
    }

    public void save() {
        if (file == null) return;
        StringBuilder out = new StringBuilder();
        for (Addition a : game()) out.append(a.id()).append(':').append(isOn(a.id())).append('\n');
        for (String line : unknown) out.append(line).append('\n');
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Couldn't save " + file + ": " + e.getMessage());
        }
    }

    public boolean isOn(String id) { return on.getOrDefault(id, false); }

    public void set(String id, boolean value) { on.put(id, value); }

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

    public Look look() {
        boolean faithful = true, recommended = true;
        for (Addition a : game()) {
            if (isOn(a.id())) faithful = false;
            if (isOn(a.id()) != a.recommended()) recommended = false;
        }
        return faithful ? Look.FAITHFUL : recommended ? Look.RECOMMENDED : Look.CUSTOM;
    }

    /** Sets every game addition for a look; Custom leaves the switches as they are. */
    public void apply(Look look) {
        if (look == Look.CUSTOM) return;
        for (Addition a : game()) set(a.id(), look == Look.RECOMMENDED && a.recommended());
    }
}

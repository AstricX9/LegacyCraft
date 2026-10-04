package net.legacycraft.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Everything the launcher remembers: profiles (in the 1.6-era
 * launcher_profiles.json shape), the offline player name, the LegacyCraft
 * install folder and the skin library. Stored in ~/.legacycraft-launcher.
 */
final class LauncherData {
    static final String GAME_VERSION = "1.8.9";
    static final String STEVE = "steve", ALEX = "alex";

    final Path dataDir = Path.of(System.getProperty("user.home"), ".legacycraft-launcher");
    final Path profilesFile = dataDir.resolve("launcher_profiles.json");
    final Path skinsDir = dataDir.resolve("skins");
    final Path capesDir = dataDir.resolve("capes");

    final Map<String, Profile> profiles = new LinkedHashMap<>();
    final List<SkinEntry> skins = new ArrayList<>();
    final List<CapeEntry> capes = new ArrayList<>();
    String selectedCape; // null = no cape
    String selectedProfile;
    String username = "Player";
    String clientToken = UUID.randomUUID().toString();
    String selectedSkin = STEVE;
    Path installDir;

    static final class SkinEntry {
        final String id;
        String name, file, model;

        SkinEntry(String id, String name, String file, String model) {
            this.id = id; this.name = name; this.file = file; this.model = model;
        }

        boolean builtIn() { return file == null; }
        boolean slim() { return "slim".equals(model); }

        @Override public String toString() { return name; }
    }

    static final class CapeEntry {
        final String id;
        String name, file;

        CapeEntry(String id, String name, String file) { this.id = id; this.name = name; this.file = file; }

        @Override public String toString() { return name; }
    }

    void load() {
        skins.add(new SkinEntry(STEVE, "Steve (default)", null, "classic"));
        skins.add(new SkinEntry(ALEX, "Alex (default)", null, "slim"));
        Map<String, Object> root = new LinkedHashMap<>();
        try {
            if (Files.isRegularFile(profilesFile))
                root = Json.object(Json.parse(Files.readString(profilesFile, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException error) {
            System.err.println("Couldn't read " + profilesFile + ": " + error.getMessage());
        }
        for (Map.Entry<String, Object> entry : Json.object(root.get("profiles")).entrySet()) {
            Profile p = Profile.fromJson(entry.getKey(), Json.object(entry.getValue()));
            profiles.put(p.name, p);
        }
        selectedProfile = Json.string(root.get("selectedProfile"), null);
        clientToken = Json.string(root.get("clientToken"), clientToken);

        Map<String, Object> ours = Json.object(root.get("legacycraft"));
        username = Json.string(ours.get("username"), username);
        String install = Json.string(ours.get("installDir"), null);
        if (install != null) installDir = Path.of(install);
        selectedSkin = Json.string(ours.get("selectedSkin"), STEVE);
        for (Object o : Json.array(ours.get("skins"))) {
            Map<String, Object> s = Json.object(o);
            String id = Json.string(s.get("id"), null), file = Json.string(s.get("file"), null);
            if (id == null || file == null || !Files.isRegularFile(skinsDir.resolve(file))) continue;
            skins.add(new SkinEntry(id, Json.string(s.get("name"), id), file, Json.string(s.get("model"), "classic")));
        }

        selectedCape = Json.string(ours.get("selectedCape"), null);
        for (Object o : Json.array(ours.get("capes"))) {
            Map<String, Object> c = Json.object(o);
            String id = Json.string(c.get("id"), null), file = Json.string(c.get("file"), null);
            if (id == null || file == null || !Files.isRegularFile(capesDir.resolve(file))) continue;
            capes.add(new CapeEntry(id, Json.string(c.get("name"), id), file));
        }
        if (cape(selectedCape) == null) selectedCape = null;

        migrateOldSettings();
        if (installDir == null || !isInstall(installDir)) installDir = findInstall();
        if (profiles.isEmpty()) {
            Profile p = new Profile(username);
            profiles.put(p.name, p);
        }
        if (selectedProfile == null || !profiles.containsKey(selectedProfile)) selectedProfile = sortedProfiles().get(0).name;
        if (skin(selectedSkin) == null) selectedSkin = STEVE;
        save();
    }

    /** Picks up the game folder and skin chosen in the previous (classic-style) launcher. */
    private void migrateOldSettings() {
        Path old = dataDir.resolve("launcher.properties");
        if (!Files.isRegularFile(old)) return;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(old)) { props.load(in); } catch (IOException ignored) { return; }
        String folder = props.getProperty("gameFolder");
        if (installDir == null && folder != null && isInstall(Path.of(folder))) installDir = Path.of(folder);
        String skin = props.getProperty("skin");
        if (skin != null && Files.isRegularFile(Path.of(skin))) {
            try { selectedSkin = importSkin(Path.of(skin), null).id; } catch (IOException ignored) {}
        }
        try { Files.move(old, dataDir.resolve("launcher.properties.migrated"), StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) {}
    }

    void save() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> profileJson = new LinkedHashMap<>();
        for (Profile p : sortedProfiles()) profileJson.put(p.name, p.toJson());
        root.put("profiles", profileJson);
        root.put("selectedProfile", selectedProfile);
        root.put("clientToken", clientToken);
        Map<String, Object> ours = new LinkedHashMap<>();
        ours.put("username", username);
        if (installDir != null) ours.put("installDir", installDir.toString());
        ours.put("selectedSkin", selectedSkin);
        List<Object> skinJson = new ArrayList<>();
        for (SkinEntry s : skins) {
            if (s.builtIn()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id); m.put("name", s.name); m.put("file", s.file); m.put("model", s.model);
            skinJson.add(m);
        }
        ours.put("skins", skinJson);
        if (selectedCape != null) ours.put("selectedCape", selectedCape);
        List<Object> capeJson = new ArrayList<>();
        for (CapeEntry c : capes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id); m.put("name", c.name); m.put("file", c.file);
            capeJson.add(m);
        }
        ours.put("capes", capeJson);
        root.put("legacycraft", ours);
        try {
            Files.createDirectories(dataDir);
            Path temp = profilesFile.resolveSibling(profilesFile.getFileName() + ".tmp");
            Files.writeString(temp, Json.write(root), StandardCharsets.UTF_8);
            Files.move(temp, profilesFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            System.err.println("Couldn't save " + profilesFile + ": " + error.getMessage());
        }
    }

    List<Profile> sortedProfiles() {
        List<Profile> list = new ArrayList<>(profiles.values());
        list.sort(Comparator.comparing(p -> p.name.toLowerCase()));
        return list;
    }

    Profile selected() { return profiles.get(selectedProfile); }

    String uniqueProfileName(String base) {
        String name = base;
        for (int i = 2; profiles.containsKey(name); i++) name = base + " (" + i + ")";
        return name;
    }

    Path gameDirFor(Profile p) {
        if (p.gameDir != null && !p.gameDir.isBlank()) return Path.of(p.gameDir).toAbsolutePath().normalize();
        return installDir == null ? Path.of("").toAbsolutePath() : installDir;
    }

    // ---- skins ----

    SkinEntry skin(String id) {
        for (SkinEntry s : skins) if (s.id.equals(id)) return s;
        return null;
    }

    SkinEntry selectedSkinEntry() { SkinEntry s = skin(selectedSkin); return s == null ? skins.get(0) : s; }

    /** Converts the file to a 1.8-style 64x64 skin and stores a copy in the library. */
    SkinEntry importSkin(Path file, String model) throws IOException {
        SkinImages.Result result = SkinImages.read(file);
        Files.createDirectories(skinsDir);
        String id = UUID.randomUUID().toString().substring(0, 8);
        String fileName = id + ".png";
        SkinImages.write(result.image(), skinsDir.resolve(fileName));
        String name = file.getFileName().toString().replaceFirst("(?i)\\.png$", "");
        SkinEntry entry = new SkinEntry(id, name, fileName,
            model != null ? model : result.slim() ? "slim" : "classic");
        skins.add(entry);
        save();
        return entry;
    }

    void removeSkin(SkinEntry entry) {
        if (entry.builtIn()) return;
        skins.remove(entry);
        try { Files.deleteIfExists(skinsDir.resolve(entry.file)); } catch (IOException ignored) {}
        if (entry.id.equals(selectedSkin)) selectedSkin = STEVE;
        save();
    }

    // ---- capes ----

    CapeEntry cape(String id) {
        if (id == null) return null;
        for (CapeEntry c : capes) if (c.id.equals(id)) return c;
        return null;
    }

    CapeEntry selectedCapeEntry() { return cape(selectedCape); }

    Path capeFile(CapeEntry c) { return capesDir.resolve(c.file); }

    /** Stores a copy of a cape (normalised to the 64x32 layout's shape) in the library. */
    CapeEntry importCape(Path file) throws IOException {
        java.awt.image.BufferedImage img = SkinImages.readCape(file);
        Files.createDirectories(capesDir);
        String id = UUID.randomUUID().toString().substring(0, 8);
        SkinImages.write(img, capesDir.resolve(id + ".png"));
        CapeEntry entry = new CapeEntry(id, file.getFileName().toString().replaceFirst("(?i)\\.png$", ""), id + ".png");
        capes.add(entry);
        save();
        return entry;
    }

    void removeCape(CapeEntry c) {
        capes.remove(c);
        try { Files.deleteIfExists(capesDir.resolve(c.file)); } catch (IOException ignored) {}
        if (c.id.equals(selectedCape)) selectedCape = null;
        save();
    }

    /** The PNG for a skin entry, or null when a built-in skin's game texture can't be found. */
    Path skinFile(SkinEntry entry) {
        if (!entry.builtIn()) return skinsDir.resolve(entry.file);
        if (installDir == null) return null;
        Path p = installDir.resolve("src/main/resources/assets/minecraft/textures/entity").resolve(entry.id + ".png");
        return Files.isRegularFile(p) ? p : null;
    }

    Path dirtTexture() {
        if (installDir == null) return null;
        Path p = installDir.resolve("src/main/resources/assets/minecraft/textures/blocks/dirt.png");
        return Files.isRegularFile(p) ? p : null;
    }

    // ---- install folder ----

    static String wrapperName() { return isWindows() ? "gradlew.bat" : "gradlew"; }

    static boolean isInstall(Path dir) { return dir != null && Files.isRegularFile(dir.resolve(wrapperName())) && Files.isDirectory(dir.resolve("src")); }

    static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    private static Path findInstall() {
        List<Path> starts = new ArrayList<>();
        starts.add(Path.of("").toAbsolutePath().normalize());
        try {
            starts.add(Path.of(LauncherData.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath());
        } catch (Exception ignored) {}
        for (Path start : starts)
            for (Path p = start; p != null; p = p.getParent())
                if (isInstall(p)) return p;
        return null;
    }
}

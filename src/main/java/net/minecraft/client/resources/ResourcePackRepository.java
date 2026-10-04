package net.minecraft.client.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The resource pack stack (1.8.9 ResourcePackRepository + SimpleReloadableResourceManager):
 * the packs found in {@code resourcepacks/} (zip files, and folders holding a
 * pack.mcmeta), the ones the player selected layered over the built-in
 * default pack, and lookups that take each resource from the highest pack
 * that has it. Game data under the {@code legacycraft} namespace always comes
 * from the base pack, so a resource pack can only change how things look and
 * sound, as in vanilla.
 */
public final class ResourcePackRepository implements IResourceManager {
    /** The pack_format 1.8.9 expects; other formats are listed as incompatible. */
    public static final int PACK_FORMAT = 1;
    private static final String PROTECTED_NAMESPACE = "legacycraft";

    /** A pack in the folder, with what the selection screen shows for it. */
    public static final class Entry {
        public final String name;
        public final Path file;
        public final String description;
        public final int format;
        public final BufferedImage icon;

        Entry(String name, Path file, String description, int format, BufferedImage icon) {
            this.name = name; this.file = file; this.description = description; this.format = format; this.icon = icon;
        }

        public boolean compatible() { return format == PACK_FORMAT; }
    }

    private final IResourceManager base;
    private Path directory;
    private final List<ResourcePack> active = new ArrayList<>(); // lowest priority first

    public ResourcePackRepository(IResourceManager base) { this.base = base; }

    public void setDirectory(Path directory) { this.directory = directory; }

    public Path directory() { return directory; }

    /** The packs in the folder, sorted by name; unreadable ones are skipped (with a log line). */
    public List<Entry> scan() {
        List<Entry> entries = new ArrayList<>();
        if (directory == null) return entries;
        try {
            Files.createDirectories(directory);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    boolean zip = Files.isRegularFile(file) && name.toLowerCase().endsWith(".zip");
                    boolean folder = Files.isDirectory(file) && Files.isRegularFile(file.resolve("pack.mcmeta"));
                    if (!zip && !folder) continue;
                    try (FileResourcePack pack = new FileResourcePack(file)) {
                        entries.add(describe(pack, file));
                    } catch (IOException | RuntimeException error) {
                        System.err.println("Skipping resource pack " + name + ": " + error.getMessage());
                    }
                }
            }
        } catch (IOException error) {
            System.err.println("Couldn't list resource packs: " + error.getMessage());
        }
        entries.sort(Comparator.comparing(entry -> entry.name.toLowerCase()));
        return entries;
    }

    private static Entry describe(ResourcePack pack, Path file) throws IOException {
        if (!pack.has("pack.mcmeta")) throw new FileNotFoundException("no pack.mcmeta");
        JsonObject root;
        try (InputStream stream = pack.open("pack.mcmeta")) {
            root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonObject info = root.getAsJsonObject("pack");
        if (info == null) throw new IOException("pack.mcmeta has no \"pack\" section");
        int format = info.has("pack_format") ? info.get("pack_format").getAsInt() : 0;
        String description = info.has("description") ? text(info.get("description")) : "";
        BufferedImage icon = null;
        if (pack.has("pack.png")) {
            try (InputStream stream = pack.open("pack.png")) { icon = ImageIO.read(stream); } catch (IOException ignored) {}
        }
        return new Entry(pack.name(), file, description, format, icon);
    }

    /** A description written as a plain string or as a JSON chat component. */
    private static String text(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonPrimitive()) return element.getAsString();
        StringBuilder out = new StringBuilder();
        if (element.isJsonArray()) {
            for (JsonElement part : element.getAsJsonArray()) out.append(text(part));
        } else {
            JsonObject object = element.getAsJsonObject();
            if (object.has("text")) out.append(object.get("text").getAsString());
            if (object.has("extra")) out.append(text(object.get("extra")));
        }
        return out.toString();
    }

    /**
     * Opens the named packs, lowest priority first (the order options.txt
     * stores them in), closing whatever was open before. Returns the names
     * that could be opened.
     */
    public List<String> apply(List<String> names) {
        for (ResourcePack pack : active) pack.close();
        active.clear();
        List<String> opened = new ArrayList<>();
        if (directory == null) return opened;
        for (String name : names) {
            Path file = directory.resolve(name);
            if (!Files.exists(file) || !file.normalize().startsWith(directory.normalize())) continue;
            try {
                active.add(new FileResourcePack(file));
                opened.add(name);
            } catch (IOException error) {
                System.err.println("Couldn't open resource pack " + name + ": " + error.getMessage());
            }
        }
        return opened;
    }

    /** The open packs' names, lowest priority first. */
    public List<String> activeNames() {
        List<String> names = new ArrayList<>();
        for (ResourcePack pack : active) names.add(pack.name());
        return names;
    }

    public void close() {
        for (ResourcePack pack : active) pack.close();
        active.clear();
    }

    // ---- IResourceManager ----

    private static String path(ResourceLocation location) {
        return "assets/" + location.namespace() + "/" + location.path();
    }

    private boolean packsServe(ResourceLocation location) { return !location.namespace().equals(PROTECTED_NAMESPACE); }

    private ResourcePack top(ResourceLocation location) {
        if (!packsServe(location)) return null;
        String path = path(location);
        for (int i = active.size() - 1; i >= 0; i--) if (active.get(i).has(path)) return active.get(i);
        return null;
    }

    @Override
    public InputStream open(ResourceLocation location) throws IOException {
        ResourcePack pack = top(location);
        return pack != null ? pack.open(path(location)) : base.open(location);
    }

    @Override
    public boolean exists(ResourceLocation location) { return top(location) != null || base.exists(location); }

    @Override
    public List<ResourceLocation> list(ResourceLocation directory, String suffix) throws IOException {
        Map<String, ResourceLocation> found = new HashMap<>();
        for (ResourceLocation location : base.list(directory, suffix)) found.put(location.toString(), location);
        if (packsServe(directory)) {
            String dir = path(directory);
            while (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
            Set<String> paths = new TreeSet<>();
            for (ResourcePack pack : active) pack.list(dir, suffix, paths);
            String prefix = "assets/" + directory.namespace() + "/";
            for (String path : paths) {
                try {
                    ResourceLocation location = new ResourceLocation(directory.namespace(), path.substring(prefix.length()));
                    found.putIfAbsent(location.toString(), location);
                } catch (IllegalArgumentException invalid) {
                    // Upper-case or otherwise invalid names can't be addressed, as in vanilla.
                }
            }
        }
        List<ResourceLocation> results = new ArrayList<>(found.values());
        results.sort(Comparator.comparing(ResourceLocation::toString));
        return List.copyOf(results);
    }

    @Override
    public InputStream openMetadata(ResourceLocation location) throws IOException {
        ResourceLocation meta = new ResourceLocation(location.namespace(), location.path() + ".mcmeta");
        if (packsServe(location)) {
            String path = path(location), metaPath = path(meta);
            ResourcePack metaPack = null;
            for (int i = active.size() - 1; i >= 0; i--) {
                ResourcePack pack = active.get(i);
                if (metaPack == null && pack.has(metaPath)) metaPack = pack;
                if (pack.has(path)) return metaPack != null ? metaPack.open(metaPath) : null;
            }
            if (metaPack != null) return metaPack.open(metaPath);
        }
        return base.exists(meta) ? base.open(meta) : null;
    }

    @Override
    public List<InputStream> openAll(ResourceLocation location) throws IOException {
        List<InputStream> all = new ArrayList<>();
        if (base.exists(location)) all.add(base.open(location));
        if (packsServe(location)) {
            String path = path(location);
            for (ResourcePack pack : active) if (pack.has(path)) all.add(pack.open(path));
        }
        return all;
    }
}

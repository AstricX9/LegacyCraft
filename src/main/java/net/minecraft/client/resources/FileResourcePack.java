package net.minecraft.client.resources;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A resource pack read from a .zip file or an unpacked folder. The file list
 * is read once when the pack is opened (packs are reopened on every resource
 * reload). Lookups ignore case: LegacyCraft's resource locations are lower
 * case while 1.8.9 packs keep names such as {@code lang/en_US.lang}.
 *
 * <p>Packs as downloaded are often wrapped: the pack sits in a folder inside
 * the zip ({@code Plastic Craft/pack.mcmeta}), or the download is a zip holding
 * the real pack zip next to readme files. Vanilla rejects both; here the
 * shallowest folder holding a pack.mcmeta becomes the pack root, and failing
 * that a single nested pack zip is opened instead. macOS metadata
 * ({@code __MACOSX/}, {@code ._*}) is ignored and backslash-separated zip
 * entry names are read as paths.
 */
public final class FileResourcePack implements ResourcePack {
    /** Nested pack zips extracted this run, keyed by outer file, its size and time, and entry. */
    private static final Map<String, Path> EXTRACTED = new HashMap<>();

    private final String name;
    private Path root;
    /** Lower-case path relative to the pack root → the path as stored in the pack. */
    private final Map<String, String> files = new HashMap<>();
    private ZipFile zip;

    public FileResourcePack(Path file) throws IOException {
        this.name = file.getFileName().toString();
        if (Files.isDirectory(file)) {
            this.root = file;
            try (var walk = Files.walk(file)) {
                walk.filter(Files::isRegularFile)
                    .forEach(path -> add(file.relativize(path).toString().replace('\\', '/')));
            }
            rootAtPackMcmeta();
        } else {
            this.zip = new ZipFile(file.toFile());
            indexZip();
            if (!rootAtPackMcmeta()) openNestedPack(file);
        }
    }

    private void indexZip() {
        files.clear();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) add(entry.getName());
        }
    }

    private void add(String real) {
        String path = real.replace('\\', '/');
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.startsWith("__macosx/") || lower.endsWith("/") || lower.startsWith("._") || lower.contains("/._")) return;
        files.putIfAbsent(lower, real);
    }

    /**
     * Makes the shallowest folder holding a pack.mcmeta the pack root. Returns
     * whether the pack has a pack.mcmeta at all.
     */
    private boolean rootAtPackMcmeta() {
        if (files.containsKey("pack.mcmeta")) return true;
        String prefix = null;
        for (String path : files.keySet()) {
            if (!path.endsWith("/pack.mcmeta")) continue;
            String folder = path.substring(0, path.length() - "pack.mcmeta".length());
            if (prefix == null || depth(folder) < depth(prefix) || depth(folder) == depth(prefix) && folder.compareTo(prefix) < 0)
                prefix = folder;
        }
        if (prefix == null) return false;
        Map<String, String> inside = new HashMap<>();
        for (Map.Entry<String, String> entry : files.entrySet())
            if (entry.getKey().startsWith(prefix)) inside.put(entry.getKey().substring(prefix.length()), entry.getValue());
        files.clear();
        files.putAll(inside);
        return true;
    }

    private static int depth(String folder) { return (int) folder.chars().filter(c -> c == '/').count(); }

    /** Swaps a wrapper zip with no pack.mcmeta for the one pack zip inside it, if there is exactly one. */
    private void openNestedPack(Path file) throws IOException {
        String nested = null;
        for (String path : files.keySet()) {
            if (!path.endsWith(".zip")) continue;
            if (nested != null) return; // several candidates: leave it to the caller to reject
            nested = path;
        }
        if (nested == null) return;
        String key = file.toAbsolutePath() + "|" + Files.size(file) + "|" + Files.getLastModifiedTime(file).toMillis() + "|" + nested;
        Path extracted;
        synchronized (EXTRACTED) {
            extracted = EXTRACTED.get(key);
            if (extracted == null || !Files.isRegularFile(extracted)) {
                extracted = Files.createTempFile("legacycraft-pack-", ".zip");
                extracted.toFile().deleteOnExit();
                try (InputStream stream = zip.getInputStream(zip.getEntry(files.get(nested)))) {
                    Files.copy(stream, extracted, StandardCopyOption.REPLACE_EXISTING);
                }
                EXTRACTED.put(key, extracted);
            }
        }
        ZipFile inner = new ZipFile(extracted.toFile());
        ZipFile outer = zip;
        Map<String, String> outerFiles = new HashMap<>(files);
        zip = inner;
        indexZip();
        if (rootAtPackMcmeta()) {
            outer.close();
        } else {
            inner.close();
            zip = outer;
            files.clear();
            files.putAll(outerFiles);
        }
    }

    @Override public String name() { return name; }

    @Override public boolean has(String path) { return files.containsKey(path.toLowerCase(Locale.ROOT)); }

    @Override
    public InputStream open(String path) throws IOException {
        String real = files.get(path.toLowerCase(Locale.ROOT));
        if (real == null) throw new FileNotFoundException(name + "/" + path);
        if (zip != null) return zip.getInputStream(zip.getEntry(real));
        return Files.newInputStream(root.resolve(real));
    }

    @Override
    public void list(String directory, String suffix, Set<String> into) {
        String prefix = (directory + "/").toLowerCase(Locale.ROOT);
        String end = suffix.toLowerCase(Locale.ROOT);
        for (String file : files.keySet()) if (file.startsWith(prefix) && file.endsWith(end)) into.add(file);
    }

    @Override
    public void close() {
        if (zip != null) {
            try { zip.close(); } catch (IOException ignored) {}
            zip = null;
        }
    }
}

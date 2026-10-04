package net.minecraft.client.resources;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A resource pack read from a .zip file or an unpacked folder. The file list
 * is read once when the pack is opened (packs are reopened on every resource
 * reload). Lookups ignore case: LegacyCraft's resource locations are lower
 * case while 1.8.9 packs keep names such as {@code lang/en_US.lang}.
 */
public final class FileResourcePack implements ResourcePack {
    private final String name;
    private final Path root;
    /** Lower-case path → the path as stored in the pack. */
    private final java.util.Map<String, String> files = new java.util.HashMap<>();
    private ZipFile zip;

    public FileResourcePack(Path file) throws IOException {
        this.name = file.getFileName().toString();
        if (Files.isDirectory(file)) {
            this.root = file;
            try (var walk = Files.walk(file)) {
                walk.filter(Files::isRegularFile)
                    .forEach(path -> add(file.relativize(path).toString().replace('\\', '/')));
            }
        } else {
            this.root = null;
            this.zip = new ZipFile(file.toFile());
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) add(entry.getName());
            }
        }
    }

    private void add(String path) { files.putIfAbsent(path.toLowerCase(java.util.Locale.ROOT), path); }

    @Override public String name() { return name; }

    @Override public boolean has(String path) { return files.containsKey(path.toLowerCase(java.util.Locale.ROOT)); }

    @Override
    public InputStream open(String path) throws IOException {
        String real = files.get(path.toLowerCase(java.util.Locale.ROOT));
        if (real == null) throw new FileNotFoundException(name + "/" + path);
        if (zip != null) return zip.getInputStream(zip.getEntry(real));
        return Files.newInputStream(root.resolve(real));
    }

    @Override
    public void list(String directory, String suffix, Set<String> into) {
        String prefix = (directory + "/").toLowerCase(java.util.Locale.ROOT);
        String end = suffix.toLowerCase(java.util.Locale.ROOT);
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

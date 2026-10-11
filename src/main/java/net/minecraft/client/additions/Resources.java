package net.minecraft.client.additions;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Reads every file in a classpath folder, whether the game runs from a build folder or a jar. */
final class Resources {
    private Resources() {}

    /** The files directly in {@code folder} (a classpath path ending in /) whose names end in {@code suffix}, by name. */
    static Map<String, byte[]> list(String folder, String suffix) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        URL url = Resources.class.getResource(folder);
        if (url == null) throw new IOException(folder + " is missing");
        if ("file".equals(url.getProtocol())) {
            try (Stream<Path> stream = Files.list(Path.of(url.toURI()))) {
                for (Path p : (Iterable<Path>) stream::iterator)
                    if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(suffix))
                        files.put(p.getFileName().toString(), Files.readAllBytes(p));
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        } else if ("jar".equals(url.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            connection.setUseCaches(false);
            String prefix = connection.getEntryName() + (connection.getEntryName().endsWith("/") ? "" : "/");
            try (JarFile jar = connection.getJarFile()) {
                for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements(); ) {
                    JarEntry entry = e.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.startsWith(prefix) || !name.endsWith(suffix)) continue;
                    String rest = name.substring(prefix.length());
                    if (rest.contains("/")) continue;
                    try (InputStream in = jar.getInputStream(entry)) { files.put(rest, in.readAllBytes()); }
                }
            }
        } else {
            throw new IOException("can't list " + url);
        }
        return files;
    }
}

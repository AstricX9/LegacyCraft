package net.minecraft.client.resources;

import net.minecraft.util.ResourceLocation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Base resource pack backed by assets bundled on the application classpath.
 *
 * <p>When the game runs from a source checkout ({@code gradlew run}), the
 * {@code legacycraft.resourceDir} system property points at
 * {@code src/main/resources}. Assets found there are read straight from disk,
 * so edits to the built-in default pack show up on F3+T without a rebuild;
 * anything missing there (generated game data) still comes from the
 * classpath. Game data under the {@code legacycraft} namespace is always read
 * from the classpath so it can't drift from the registries loaded at startup.
 */
public final class ClasspathResourceManager implements IResourceManager {
    public static final String SOURCE_DIRECTORY_PROPERTY = "legacycraft.resourceDir";
    private static final String DATA_NAMESPACE = "legacycraft";

    private final Path sourceDirectory;

    public ClasspathResourceManager() { this(null); }

    /** A base pack that prefers loose files under {@code sourceDirectory} (null for classpath only). */
    public ClasspathResourceManager(Path sourceDirectory) {
        this.sourceDirectory = sourceDirectory != null && Files.isDirectory(sourceDirectory) ? sourceDirectory.toAbsolutePath().normalize() : null;
    }

    /** The client's base pack: the source checkout's resources if the run task named them. */
    public static ClasspathResourceManager fromSystemProperty() {
        String directory = System.getProperty(SOURCE_DIRECTORY_PROPERTY);
        return new ClasspathResourceManager(directory == null || directory.isBlank() ? null : Path.of(directory));
    }

    private Path sourceFile(ResourceLocation location) {
        if (sourceDirectory == null || location.namespace().equals(DATA_NAMESPACE)) return null;
        Path file = sourceDirectory.resolve("assets").resolve(location.namespace()).resolve(location.path()).normalize();
        return file.startsWith(sourceDirectory) && Files.isRegularFile(file) ? file : null;
    }

    @Override
    public InputStream open(ResourceLocation location) throws IOException {
        Path file = sourceFile(location);
        if (file != null) return Files.newInputStream(file);
        InputStream stream = openClasspath(location);
        if (stream == null) throw new FileNotFoundException(location.toString());
        return stream;
    }

    @Override
    public boolean exists(ResourceLocation location) {
        if (sourceFile(location) != null) return true;
        try (InputStream ignored = openClasspath(location)) {
            return ignored != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Resource locations are lower case, but some asset files aren't (lang/en_US.lang,
     * the Additions, a few sounds). A folder on Windows doesn't mind; a jar (the packaged
     * game) does, so a miss is retried under the file's real name.
     */
    private static InputStream openClasspath(ResourceLocation location) {
        String path = classpathPath(location);
        InputStream stream = ClasspathResourceManager.class.getResourceAsStream(path);
        if (stream != null) return stream;
        String actual = MixedCaseNames.INDEX.get(path);
        return actual == null ? null : ClasspathResourceManager.class.getResourceAsStream(actual);
    }

    /** Lower-cased classpath path to real path, for every asset whose name has capitals. Built on first miss. */
    private static final class MixedCaseNames {
        static final Map<String, String> INDEX = build();

        private static Map<String, String> build() {
            Map<String, String> index = new HashMap<>();
            try {
                Enumeration<URL> roots = ClasspathResourceManager.class.getClassLoader().getResources("assets");
                while (roots.hasMoreElements()) {
                    URL root = roots.nextElement();
                    if (root.getProtocol().equals("jar")) {
                        JarURLConnection connection = (JarURLConnection) root.openConnection();
                        connection.setUseCaches(false);
                        try (JarFile jar = connection.getJarFile()) {
                            Enumeration<JarEntry> entries = jar.entries();
                            while (entries.hasMoreElements()) add(index, entries.nextElement().getName());
                        }
                    } else if (root.getProtocol().equals("file")) {
                        Path assets = Path.of(root.toURI());
                        try (var paths = Files.walk(assets)) {
                            paths.filter(Files::isRegularFile)
                                .forEach(file -> add(index, "assets/" + assets.relativize(file).toString().replace('\\', '/')));
                        }
                    }
                }
            } catch (IOException | URISyntaxException | RuntimeException error) {
                System.err.println("Could not index mixed-case asset names: " + error);
            }
            return index;
        }

        private static void add(Map<String, String> index, String name) {
            if (!name.startsWith("assets/") || name.endsWith("/")) return;
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.equals(name)) index.putIfAbsent("/" + lower, "/" + name);
        }
    }

    @Override
    public List<ResourceLocation> list(ResourceLocation directory, String suffix) throws IOException {
        String rootName = "assets/" + directory.namespace() + "/" + trimSlashes(directory.path());
        Set<ResourceLocation> results = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        if (sourceDirectory != null && !directory.namespace().equals(DATA_NAMESPACE)) {
            Path rootPath = sourceDirectory.resolve(rootName).normalize();
            if (rootPath.startsWith(sourceDirectory) && Files.isDirectory(rootPath)) walk(rootPath, directory, suffix, results);
        }
        URL root = ClasspathResourceManager.class.getClassLoader().getResource(rootName);
        if (root == null) return List.copyOf(results);
        if (root.getProtocol().equals("file")) {
            try {
                walk(Path.of(root.toURI()), directory, suffix, results);
            } catch (URISyntaxException error) {
                throw new IOException("Invalid resource directory URI " + root, error);
            }
        } else if (root.getProtocol().equals("jar")) {
            JarURLConnection connection = (JarURLConnection) root.openConnection();
            connection.setUseCaches(false); // the jar is closed below, so it mustn't be the shared cached one
            try (JarFile jar = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith(rootName + "/") && name.endsWith(suffix)) {
                        String assetPath = name.substring(("assets/" + directory.namespace() + "/").length());
                        results.add(new ResourceLocation(directory.namespace(), assetPath));
                    }
                }
            }
        }
        return List.copyOf(results);
    }

    private static void walk(Path rootPath, ResourceLocation directory, String suffix, Set<ResourceLocation> into) throws IOException {
        try (var paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                String relative = rootPath.relativize(file).toString().replace('\\', '/');
                if (relative.endsWith(suffix))
                    into.add(new ResourceLocation(directory.namespace(), trimSlashes(directory.path()) + "/" + relative));
            });
        }
    }

    private static String classpathPath(ResourceLocation location) {
        return "/assets/" + location.namespace() + "/" + location.path();
    }

    private static String trimSlashes(String value) {
        int start = 0, end = value.length();
        while (start < end && value.charAt(start) == '/') start++;
        while (end > start && value.charAt(end - 1) == '/') end--;
        return value.substring(start, end);
    }
}

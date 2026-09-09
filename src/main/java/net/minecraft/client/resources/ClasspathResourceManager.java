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
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Base resource pack backed by assets bundled on the application classpath. */
public final class ClasspathResourceManager implements IResourceManager {
    @Override
    public InputStream open(ResourceLocation location) throws IOException {
        InputStream stream = ClasspathResourceManager.class.getResourceAsStream(classpathPath(location));
        if (stream == null) throw new FileNotFoundException(location.toString());
        return stream;
    }

    @Override
    public boolean exists(ResourceLocation location) {
        try (InputStream ignored = ClasspathResourceManager.class.getResourceAsStream(classpathPath(location))) {
            return ignored != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public List<ResourceLocation> list(ResourceLocation directory, String suffix) throws IOException {
        String rootName = "assets/" + directory.namespace() + "/" + trimSlashes(directory.path());
        URL root = ClasspathResourceManager.class.getClassLoader().getResource(rootName);
        if (root == null) return List.of();
        List<ResourceLocation> results = new ArrayList<>();
        if (root.getProtocol().equals("file")) {
            try {
                Path rootPath = Path.of(root.toURI());
                try (var paths = Files.walk(rootPath)) {
                    paths.filter(Files::isRegularFile).forEach(file -> {
                        String relative = rootPath.relativize(file).toString().replace('\\', '/');
                        if (relative.endsWith(suffix))
                            results.add(new ResourceLocation(directory.namespace(), trimSlashes(directory.path()) + "/" + relative));
                    });
                }
            } catch (URISyntaxException error) {
                throw new IOException("Invalid resource directory URI " + root, error);
            }
        } else if (root.getProtocol().equals("jar")) {
            JarURLConnection connection = (JarURLConnection) root.openConnection();
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
        results.sort(Comparator.comparing(ResourceLocation::toString));
        return List.copyOf(results);
    }

    private String classpathPath(ResourceLocation location) {
        return "/assets/" + location.namespace() + "/" + location.path();
    }

    private static String trimSlashes(String value) {
        int start = 0, end = value.length();
        while (start < end && value.charAt(start) == '/') start++;
        while (end > start && value.charAt(end - 1) == '/') end--;
        return value.substring(start, end);
    }
}

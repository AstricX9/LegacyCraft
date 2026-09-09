package net.minecraft.util;

import java.util.Locale;
import java.util.Objects;

/** Namespaced identifier used by registries and the resource manager. */
public record ResourceLocation(String namespace, String path) {
    public ResourceLocation {
        namespace = normalize(namespace == null || namespace.isBlank() ? "minecraft" : namespace);
        path = normalize(Objects.requireNonNull(path, "path"));
        if (!namespace.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Invalid namespace: " + namespace);
        if (!path.matches("[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid resource path: " + path);
    }

    public ResourceLocation(String combined) {
        this(splitNamespace(combined), splitPath(combined));
    }

    private static String splitNamespace(String combined) {
        Objects.requireNonNull(combined, "combined");
        int separator = combined.indexOf(':');
        return separator < 0 ? "minecraft" : combined.substring(0, separator);
    }

    private static String splitPath(String combined) {
        int separator = combined.indexOf(':');
        return separator < 0 ? combined : combined.substring(separator + 1);
    }

    private static String normalize(String value) {
        return value.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    public String assetPath(String category, String extension) {
        return "/assets/" + namespace + "/" + category + "/" + path + extension;
    }

    @Override public String toString() { return namespace + ':' + path; }
}

package net.minecraft.client.resources;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * One resource pack from the {@code resourcepacks} folder: a .zip file or a
 * folder holding {@code pack.mcmeta} and an {@code assets} tree
 * (FileResourcePack / FolderResourcePack). Paths are relative to the pack
 * root and use forward slashes, e.g. {@code assets/minecraft/textures/blocks/dirt.png}.
 */
public interface ResourcePack extends Closeable {
    /** The file or folder name, as stored in options.txt. */
    String name();

    boolean has(String path);

    InputStream open(String path) throws IOException;

    /** Adds the paths under {@code directory} (no trailing slash) that end in {@code suffix}. */
    void list(String directory, String suffix, Set<String> into) throws IOException;

    @Override void close();
}

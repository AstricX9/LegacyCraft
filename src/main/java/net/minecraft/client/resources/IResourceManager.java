package net.minecraft.client.resources;

import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Read side of the namespaced resource-pack stack. */
public interface IResourceManager {
    InputStream open(ResourceLocation location) throws IOException;
    boolean exists(ResourceLocation location);
    List<ResourceLocation> list(ResourceLocation directory, String suffix) throws IOException;

    /**
     * The {@code .mcmeta} file that belongs to a resource, or null. In a pack
     * stack this is the metadata from the highest pack at or above the pack
     * that supplies the resource (FallbackResourceManager.getResource), so a
     * pack's own texture never picks up a lower pack's animation.
     */
    default InputStream openMetadata(ResourceLocation location) throws IOException {
        ResourceLocation meta = new ResourceLocation(location.namespace(), location.path() + ".mcmeta");
        return exists(meta) ? open(meta) : null;
    }

    /**
     * Every copy of a resource, lowest priority first (getAllResources): lang
     * files and sounds.json are merged across packs rather than replaced.
     * The caller closes the streams.
     */
    default List<InputStream> openAll(ResourceLocation location) throws IOException {
        List<InputStream> all = new ArrayList<>();
        if (exists(location)) all.add(open(location));
        return all;
    }
}

package net.minecraft.client.resources;

import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Read side of the namespaced resource-pack stack. */
public interface IResourceManager {
    InputStream open(ResourceLocation location) throws IOException;
    boolean exists(ResourceLocation location);
    List<ResourceLocation> list(ResourceLocation directory, String suffix) throws IOException;
}

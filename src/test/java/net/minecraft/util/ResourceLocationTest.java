package net.minecraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Resource location namespaces. */
final class ResourceLocationTest {
    @Test
    void defaultsToMinecraftNamespace() {
        ResourceLocation location = new ResourceLocation("textures/blocks/stone");
        assertEquals("minecraft:textures/blocks/stone", location.toString());
    }

    @Test
    void preservesExplicitNamespace() {
        ResourceLocation location = new ResourceLocation("legacycraft:debug/checker");
        assertEquals("legacycraft", location.namespace());
        assertEquals("debug/checker", location.path());
    }
}

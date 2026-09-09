package net.minecraft.client.resources;

import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasspathResourceManagerTest {
    @Test
    void resolvesNamespacedAssetsFromTheBundledPack() throws IOException {
        ClasspathResourceManager resources = new ClasspathResourceManager();
        ResourceLocation stone = new ResourceLocation("minecraft:textures/blocks/stone.png");
        assertTrue(resources.exists(stone));
        try (var stream = resources.open(stone)) {
            assertTrue(stream.read() >= 0);
        }
    }

    @Test
    void enumeratesBlockstateResources() throws IOException {
        ClasspathResourceManager resources = new ClasspathResourceManager();
        var states = resources.list(new ResourceLocation("minecraft:blockstates"), ".json");
        assertTrue(states.size() > 300);
        assertTrue(states.contains(new ResourceLocation("minecraft:blockstates/stone.json")));
    }
}

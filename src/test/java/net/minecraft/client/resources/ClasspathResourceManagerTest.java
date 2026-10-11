package net.minecraft.client.resources;

import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The bundled resource pack: namespaced lookups and listing resources. */
final class ClasspathResourceManagerTest {
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

    @Test
    void readsEditedAssetsFromTheSourceFolderOnEveryOpen(@TempDir Path source) throws IOException {
        Path stone = source.resolve("assets/minecraft/textures/blocks/stone.png");
        Files.createDirectories(stone.getParent());
        Files.writeString(stone, "first");
        ClasspathResourceManager resources = new ClasspathResourceManager(source);
        ResourceLocation location = new ResourceLocation("minecraft:textures/blocks/stone.png");
        try (var stream = resources.open(location)) {
            assertEquals("first", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        Files.writeString(stone, "edited");
        try (var stream = resources.open(location)) {
            assertEquals("edited", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        // Anything the source folder lacks still comes from the classpath.
        assertTrue(resources.exists(new ResourceLocation("minecraft:textures/blocks/dirt.png")));
    }

    @Test
    void listsNewSourceFilesAlongsideBundledOnes(@TempDir Path source) throws IOException {
        Path added = source.resolve("assets/minecraft/blockstates/zz_new_block.json");
        Files.createDirectories(added.getParent());
        Files.writeString(added, "{}");
        var states = new ClasspathResourceManager(source).list(new ResourceLocation("minecraft:blockstates"), ".json");
        assertTrue(states.contains(new ResourceLocation("minecraft:blockstates/zz_new_block.json")));
        assertTrue(states.contains(new ResourceLocation("minecraft:blockstates/stone.json")));
    }

    @Test
    void gameDataNeverComesFromTheSourceFolder(@TempDir Path source) throws IOException {
        Path recipes = source.resolve("assets/legacycraft/recipes.json");
        Files.createDirectories(recipes.getParent());
        Files.writeString(recipes, "not the bundled recipes");
        try (var stream = new ClasspathResourceManager(source).open(new ResourceLocation("legacycraft:recipes.json"))) {
            assertNotEquals("not the bundled recipes", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}

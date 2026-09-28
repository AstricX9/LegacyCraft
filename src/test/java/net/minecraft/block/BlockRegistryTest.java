package net.minecraft.block;

import com.google.gson.JsonParser;
import net.minecraft.client.resources.ClasspathResourceManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The block registry loader: exactly the legacy 0-197 block table, with
 * properties from the block definitions.
 */
final class BlockRegistryTest {
    @Test
    void registryIsExactlyTheLegacyBlockTable() throws IOException {
        BlockRegistry registry = BlockRegistry.load(new ClasspathResourceManager());
        try (var reader = Files.newBufferedReader(Path.of("data/mc189/blocks.json"))) {
            var entries = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("entries");
            assertEquals(198, entries.size());
            assertEquals(entries.size(), registry.size());
            for (var raw : entries) {
                var entry = raw.getAsJsonObject();
                String name = entry.get("name").getAsString();
                int id = entry.get("id").getAsInt();
                assertEquals(id, registry.get(name).registryId(), name);
                assertEquals(name, registry.byId(id).name().toString());
            }
        }
        var names = new HashSet<String>();
        for (int id = 0; id < registry.size(); id++) {
            assertEquals(id, registry.byId(id).registryId());
            assertTrue(names.add(registry.byId(id).name().toString()), "Duplicate registry name");
        }
        // Species/colour blockstate files are metadata variants, not blocks.
        assertNull(registry.get(new net.minecraft.util.ResourceLocation("minecraft:oak_planks")));
    }

    @Test
    void loadsBlockPropertiesFromDefinitions() {
        BlockRegistry registry = BlockRegistry.load(new ClasspathResourceManager());
        assertEquals(0, registry.air().registryId());
        assertFalse(registry.get("minecraft:water").isSolid());
        assertEquals(-1f, registry.get("minecraft:bedrock").hardness());
        assertFalse(registry.get("minecraft:oak_stairs").isOpaque());
        assertFalse(registry.get("minecraft:barrier").isAir(), "barrier is invisible but not air");
        assertEquals(14, registry.get("minecraft:torch").lightLevel());
    }
}

package net.minecraft.world.storage;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStorageTest {
    @TempDir Path directory;

    @Test
    void persistsChunkDataAndGenerationIndexes() throws Exception {
        BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());
        BiomeRegistry biomes = BiomeRegistry.load(new ClasspathResourceManager(), blocks);
        World original = new World(blocks, biomes, 42L);
        original.set(34, 90, -18, blocks.get("minecraft:brick_block"));
        var state = BlockState.of(blocks.byId(blocks.size() - 1), 15);
        original.setState(35, 90, -18, state);
        long index = original.getChunk(2, -2).generationIndex();
        WorldStorage.save(directory, original);

        assertTrue(WorldStorage.exists(directory));
        World restored = WorldStorage.load(directory, blocks, biomes);
        assertEquals(42L, restored.seed());
        assertEquals(index, restored.getChunk(2, -2).generationIndex());
        assertEquals(blocks.get("minecraft:brick_block"), restored.get(34, 90, -18));
        assertEquals(state, restored.getState(35, 90, -18));
    }

    @Test
    void rejectsPackedStatesWithUnknownBlockIds() throws Exception {
        BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());
        BiomeRegistry biomes = BiomeRegistry.load(new ClasspathResourceManager(), blocks);
        World world = new World(blocks, biomes, 42L);
        world.getChunk(0, 0).set(0, 90, 0,
            (blocks.size() << BlockState.METADATA_BITS) | 15);
        WorldStorage.save(directory, world);

        IOException error = assertThrows(IOException.class, () -> WorldStorage.load(directory, blocks, biomes));
        assertTrue(error.getMessage().contains("Unknown block ID " + blocks.size()));
    }
}

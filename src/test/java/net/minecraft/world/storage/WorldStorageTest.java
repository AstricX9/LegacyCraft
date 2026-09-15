package net.minecraft.world.storage;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStorageTest {
    @TempDir Path directory;

    @Test
    void persistsChunkDataAndGenerationIndexes() throws Exception {
        BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());
        World original = new World(blocks, 42L);
        original.set(34, 90, -18, blocks.get("minecraft:brick_block"));
        long index = original.getChunk(2, -2).generationIndex();
        WorldStorage.save(directory, original);

        assertTrue(WorldStorage.exists(directory));
        World restored = WorldStorage.load(directory, blocks);
        assertEquals(42L, restored.seed());
        assertEquals(index, restored.getChunk(2, -2).generationIndex());
        assertEquals(blocks.get("minecraft:brick_block"), restored.get(34, 90, -18));
    }
}

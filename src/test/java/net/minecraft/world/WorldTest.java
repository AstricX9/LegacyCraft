package net.minecraft.world;

import net.minecraft.block.BlockRegistry;
import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The world: spawn placement, deterministic generation, block edits and chunk generation order. */
final class WorldTest {
    private final BlockRegistry blocks = GameData.blocks();

    @Test
    void spawnIsOnGrassAboveSeaLevel() {
        World world = GameData.newWorld(889L);
        int[] spawn = world.findSpawn();
        int surface = world.surfaceHeight(spawn[0], spawn[1]);
        assertTrue(surface >= World.SEA_LEVEL, "spawn ground at " + surface);
        assertEquals(blocks.get("minecraft:grass"), world.get(spawn[0], surface, spawn[1]));
    }

    @Test
    void spawnSearchIsDeterministic() {
        int[] first = GameData.newWorld(889L).findSpawn();
        int[] second = GameData.newWorld(889L).findSpawn();
        assertEquals(first[0], second[0]);
        assertEquals(first[1], second[1]);
    }

    @Test
    void worldMutationRoundTrips() {
        World world = GameData.newWorld(889L);
        world.set(-10, 140, -10, blocks.get("minecraft:brick_block"));
        assertEquals(blocks.get("minecraft:brick_block"), world.get(-10, 140, -10));
    }

    @Test
    void terrainGenerationIsDeterministic() {
        World first = GameData.newWorld(889L);
        World second = GameData.newWorld(889L);
        for (int x = -40; x < 40; x += 17)
            for (int z = -40; z < 40; z += 19)
                assertEquals(first.surfaceHeight(x, z), second.surfaceHeight(x, z));
    }

    @Test
    void assignsGenerationIndexesInCreationOrder() {
        World world = GameData.newWorld(889L);
        assertEquals(0, world.getChunk(4, 2).generationIndex());
        assertEquals(1, world.getChunk(-3, 8).generationIndex());
        assertEquals(0, world.getChunk(4, 2).generationIndex());
    }
}

package net.minecraft.world;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTest {
    private final BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());

    @Test
    void spawnClearingIsDryAndWalkable() {
        World world = new World(blocks, 889L);
        int x = 0;
        int z = 0;
        int surface = world.surfaceHeight(x, z);
        assertTrue(surface > World.SEA_LEVEL);
        assertEquals(blocks.get("minecraft:grass"), world.get(x, surface, z));
        assertTrue(world.get(x, surface + 1, z).isAir());
    }

    @Test
    void worldMutationRoundTrips() {
        World world = new World(blocks, 889L);
        world.set(-10, 140, -10, blocks.get("minecraft:brick_block"));
        assertEquals(blocks.get("minecraft:brick_block"), world.get(-10, 140, -10));
    }

    @Test
    void terrainGenerationIsDeterministic() {
        World first = new World(blocks, 889L);
        World second = new World(blocks, 889L);
        for (int x = -40; x < 40; x += 17)
            for (int z = -40; z < 40; z += 19)
                assertEquals(first.surfaceHeight(x, z), second.surfaceHeight(x, z));
    }
}

package net.minecraft.world;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Sky and block light spreading, built high above the generated terrain where the sky is open. */
final class LightEngineTest {
    private final BlockRegistry blocks = GameData.blocks();

    private World world() { return GameData.newWorld(1L); }
    private void set(World world, int x, int y, int z, String block, int meta) {
        world.setState(x, y, z, BlockState.of(blocks.get(block), meta));
    }

    @Test
    void openSkyIsFullyLitAndSolidBlocksAreDark() {
        World world = world();
        assertEquals(15, world.getSkyLight(0, 210, 0));
        assertEquals(0, world.getSkyLight(8, 2, 8), "deep underground");
    }

    @Test
    void roofsShadeWhatIsBelowThemAndLightCreepsInFromTheSides() {
        World world = world();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) set(world, x, 205, z, "minecraft:stone", 0);
        assertEquals(12, world.getSkyLight(0, 204, 0), "three blocks in from the open edge");
        assertEquals(14, world.getSkyLight(2, 204, 0), "one block in from the open edge");
        assertEquals(15, world.getSkyLight(3, 204, 0), "just outside the roof");

        set(world, 0, 205, 0, "minecraft:air", 0);
        assertEquals(15, world.getSkyLight(0, 204, 0), "a hole in the roof lets full sky light straight down");
        set(world, 0, 205, 0, "minecraft:stone", 0);
        assertEquals(12, world.getSkyLight(0, 204, 0), "closing it again restores the shadow");
    }

    @Test
    void torchLightFadesOneLevelPerBlockAndGoesAwayWithTheTorch() {
        World world = world();
        set(world, 0, 230, 0, "minecraft:torch", 5);
        assertEquals(14, world.getBlockLight(0, 230, 0));
        assertEquals(13, world.getBlockLight(1, 230, 0));
        assertEquals(11, world.getBlockLight(0, 233, 0));
        assertEquals(10, world.getBlockLight(2, 232, 0), "Manhattan distance 4");

        set(world, 0, 230, 0, "minecraft:air", 0);
        assertEquals(0, world.getBlockLight(1, 230, 0));
        assertEquals(0, world.getBlockLight(0, 233, 0));
    }

    @Test
    void lightCrossesChunkBorders() {
        World world = world();
        set(world, 15, 230, 0, "minecraft:glowstone", 0);
        assertEquals(14, world.getBlockLight(16, 230, 0), "the neighbouring chunk is lit too");
        assertEquals(12, world.getBlockLight(18, 230, 0));
    }

    @Test
    void leavesDimSkyLightByOne() {
        World world = world();
        set(world, 5, 220, 5, "minecraft:leaves", 0);
        assertEquals(14, world.getSkyLight(5, 219, 5));
        assertEquals(15, world.getSkyLight(5, 221, 5));
    }

    @Test
    void generationOrderDoesNotChangeTheLight() {
        World forward = world(), backward = world();
        // Population is order-dependent in vanilla too (features compete for space); this is about light.
        forward.disablePopulation();
        backward.disablePopulation();
        for (int cz = -2; cz <= 2; cz++) for (int cx = -2; cx <= 2; cx++) forward.getChunk(cx, cz);
        for (int cz = 2; cz >= -2; cz--) for (int cx = 2; cx >= -2; cx--) backward.getChunk(cx, cz);
        for (int x = -32; x < 48; x += 3) {
            for (int z = -32; z < 48; z += 3) {
                for (int y = 40; y < 110; y++) {
                    assertEquals(forward.getSkyLight(x, y, z), backward.getSkyLight(x, y, z), "sky at " + x + "," + y + "," + z);
                    assertEquals(forward.getBlockLight(x, y, z), backward.getBlockLight(x, y, z), "block at " + x + "," + y + "," + z);
                }
            }
        }
    }
}

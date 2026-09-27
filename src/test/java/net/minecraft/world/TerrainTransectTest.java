package net.minecraft.world;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.world.biome.BiomeRegistry;
import org.junit.jupiter.api.Test;

/**
 * Not a check — a probe. Prints our generated surface height along a line, so
 * the shape of the terrain (and any cliff at a biome boundary) can be read as
 * numbers rather than guessed at from a screenshot.
 */
class TerrainTransectTest {
    private static final long SEED = -412575085214022153L;

    @Test
    void heightAlongALineThroughTheDeepOcean() {
        BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());
        BiomeRegistry biomes = BiomeRegistry.load(new ClasspathResourceManager(), blocks);
        World world = new World(blocks, biomes, SEED);
        int x = 336;
        System.out.println("z\theight\tstep\tbiome");
        int previous = world.surfaceHeight(x, -200);
        for (int z = -200; z <= -40; z += 4) {
            int height = world.surfaceHeight(x, z);
            System.out.println(z + "\t" + height + "\t" + (height - previous) + "\t" + world.biomeAt(x, z).name().path());
            previous = height;
        }
    }
}

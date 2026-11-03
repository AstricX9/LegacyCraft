package net.minecraft.world.gen;

import net.minecraft.testing.GameData;
import net.minecraft.world.Dimension;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The End's main island, void beyond it, and the ten obsidian spikes. */
final class EndGeneratorTest {
    private static World end() { return new World(GameData.blocks(), GameData.biomes(), 42L, null, Dimension.END); }

    @Test
    void mainIslandIsEndStoneOverTheVoid() {
        World world = end();
        int top = world.surfaceHeight(0, 0);
        assertTrue(top > 45 && top < 75, "island surface at " + top);
        assertEquals("end_stone", world.get(0, top, 0).name().path());
        assertTrue(world.get(0, 2, 0).isAir(), "no floor under the island");
        assertEquals(0, world.surfaceHeight(400, 400), "void between the islands");
        assertEquals("sky", world.biomeAt(0, 0).name().path());
    }

    @Test
    void tenSpikesRingTheCentre() {
        World world = end();
        var spikes = world.endGenerator().spikes();
        assertEquals(10, spikes.size());
        for (var spike : spikes)
            for (int dx = -16; dx <= 16; dx += 16)
                for (int dz = -16; dz <= 16; dz += 16) world.get(spike.x() + dx, 64, spike.z() + dz);
        for (var spike : spikes) {
            assertEquals(42, Math.round(Math.sqrt(spike.x() * spike.x() + spike.z() * spike.z())), 1.5);
            assertEquals("obsidian", world.get(spike.x(), spike.height() - 1, spike.z()).name().path(), spike.toString());
            assertEquals("bedrock", world.get(spike.x(), spike.height(), spike.z()).name().path());
        }
        assertEquals(2, spikes.stream().filter(EndGenerator.Spike::guarded).count());
    }
}

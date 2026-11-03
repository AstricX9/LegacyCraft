package net.minecraft.world.gen;

import net.minecraft.testing.GameData;
import net.minecraft.world.Dimension;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The 1.8.9 End: one island over the void, random obsidian spikes with crystals, and the dragon over 0,0. */
final class EndGeneratorTest {
    private static World end() { return new World(GameData.blocks(), GameData.biomes(), 42L, null, Dimension.END); }

    @Test
    void mainIslandIsEndStoneOverTheVoid() {
        World world = end();
        int top = world.surfaceHeight(0, 0);
        assertTrue(top > 45 && top < 75, "island surface at " + top);
        assertTrue(world.get(0, 2, 0).isAir(), "no floor under the island");
        assertEquals(0, world.surfaceHeight(400, 400), "nothing beyond the one island");
        assertEquals("sky", world.biomeAt(0, 0).name().path());
    }

    @Test
    void spikesStandOnTheIslandWithBedrockAndACrystal() {
        World world = end();
        int spikes = 0;
        for (int x = -80; x <= 80; x++)
            for (int z = -80; z <= 80; z++) {
                int top = world.surfaceHeight(x, z);
                if (top <= 0 || !world.get(x, top, z).name().path().equals("bedrock")) continue;
                spikes++;
                assertEquals("obsidian", world.get(x, top - 1, z).name().path());
            }
        assertTrue(spikes >= 3, "spikes: " + spikes);
        var spawns = world.takeCreatureSpawns();
        assertEquals(1, spawns.stream().filter(s -> s.entity().equals("EnderDragon")).count());
        assertTrue(spawns.stream().filter(s -> s.entity().equals("EnderCrystal")).count() >= spikes);
        var dragon = spawns.stream().filter(s -> s.entity().equals("EnderDragon")).findFirst().orElseThrow();
        assertEquals(128.0, dragon.y());
    }
}

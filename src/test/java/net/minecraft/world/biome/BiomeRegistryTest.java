package net.minecraft.world.biome;

import net.minecraft.block.BlockRegistry;
import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The biome registry: legacy IDs, climate and terrain values, surface blocks and flags. */
final class BiomeRegistryTest {
    private final BlockRegistry blocks = GameData.blocks();
    private final BiomeRegistry biomes = GameData.biomes();

    @Test
    void loadsTheOverworldBiomesWithTheirLegacyIds() {
        assertEquals(61, biomes.size(), "40 base biomes plus the mutated variants");
        assertEquals("minecraft:sunflower_plains", biomes.byId(129).name().toString());
        assertEquals("minecraft:mesa_bryce", biomes.byId(165).name().toString());
        assertEquals("minecraft:plains", biomes.byId(1).name().toString());
        assertEquals("minecraft:desert", biomes.byId(2).name().toString());
        assertEquals("minecraft:swampland", biomes.byId(6).name().toString());
        assertEquals("minecraft:jungle", biomes.byId(21).name().toString());
        assertEquals("minecraft:mesa", biomes.byId(37).name().toString());
    }

    @Test
    void climateAndTerrainMatchTheDocumentedValues() {
        Biome plains = biomes.get("minecraft:plains");
        assertEquals(.8f, plains.temperature(), 1e-6);
        assertEquals(.4f, plains.rainfall(), 1e-6);
        assertEquals(.125, plains.depth(), 1e-6);

        Biome hills = biomes.get("minecraft:extreme_hills");
        assertEquals(1.0, hills.depth(), 1e-6);
        assertEquals(.5, hills.scale(), 1e-6);

        Biome ocean = biomes.get("minecraft:deep_ocean");
        assertTrue(ocean.depth() < -1, "oceans sit below sea level");
    }

    @Test
    void surfaceBlocksAndClimateFlags() {
        assertEquals("minecraft:sand", biomes.get("minecraft:desert").topBlock().block().name().toString());
        assertEquals("minecraft:mycelium", biomes.get("minecraft:mushroom_island").topBlock().block().name().toString());
        // Mesa is red sand over orange stained clay.
        assertEquals(1, biomes.get("minecraft:mesa").topBlock().metadata());
        assertEquals("minecraft:stained_hardened_clay", biomes.get("minecraft:mesa").fillerBlock().block().name().toString());

        assertTrue(biomes.get("minecraft:ice_plains").isCold());
        assertTrue(biomes.get("minecraft:cold_taiga").isCold());
        assertFalse(biomes.get("minecraft:plains").isCold());
        assertFalse(biomes.get("minecraft:desert").rain(), "it never rains in the desert");
    }
}

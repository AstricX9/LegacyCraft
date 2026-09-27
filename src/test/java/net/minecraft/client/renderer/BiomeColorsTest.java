package net.minecraft.client.renderer;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.world.biome.BiomeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The colour-map lookup is checked against the grass and foliage colours 1.8
 * is documented to show in each biome (VERIFIED_HISTORY, minecraft.wiki
 * "Grass colour" / "Foliage colour"): the supplied colour maps plus this
 * lookup must reproduce them exactly.
 */
class BiomeColorsTest {
    private final ClasspathResourceManager resources = new ClasspathResourceManager();
    private final BlockRegistry blocks = BlockRegistry.load(resources);
    private final BiomeRegistry biomes = BiomeRegistry.load(resources, blocks);
    private final BiomeColors colors = BiomeColors.load(resources);

    private void assertGrass(int expected, String biome) {
        assertEquals(expected, colors.grass(biomes.get(biome)), () -> biome + " grass "
            + Integer.toHexString(colors.grass(biomes.get(biome))));
    }

    @Test
    void grassColoursMatchTheDocumentedBiomeColours() {
        assertGrass(0x91BD59, "minecraft:plains");
        assertGrass(0xBFB755, "minecraft:desert");
        assertGrass(0x79C05A, "minecraft:forest");
        assertGrass(0x59C93C, "minecraft:jungle");
        assertGrass(0x86B783, "minecraft:taiga");
        assertGrass(0x80B497, "minecraft:ice_plains");
        assertGrass(0x8AB689, "minecraft:extreme_hills");
    }

    @Test
    void foliageColoursMatchTheDocumentedBiomeColours() {
        assertEquals(0x77AB2F, colors.foliage(biomes.get("minecraft:plains")));
        assertEquals(0xAEA42A, colors.foliage(biomes.get("minecraft:desert")));
        assertEquals(0x59AE30, colors.foliage(biomes.get("minecraft:forest")));
        assertEquals(0x30BB0B, colors.foliage(biomes.get("minecraft:jungle")));
        assertEquals(0x60A17B, colors.foliage(biomes.get("minecraft:ice_plains")));
    }

    @Test
    void biomesWithFixedColoursOverrideTheMap() {
        assertEquals(0x6A7039, colors.grass(biomes.get("minecraft:swampland")));
        assertEquals(0x6A7039, colors.foliage(biomes.get("minecraft:swampland")));
        assertEquals(0xE0FF70, colors.water(biomes.get("minecraft:swampland")));
        assertEquals(0x90814D, colors.grass(biomes.get("minecraft:mesa")));
        assertEquals(0xFFFFFF, colors.water(biomes.get("minecraft:plains")), "normal water is untinted");
        // Roofed forest darkens the map colour towards its own tint.
        assertEquals(0x507A32, colors.grass(biomes.get("minecraft:roofed_forest")));
    }
}

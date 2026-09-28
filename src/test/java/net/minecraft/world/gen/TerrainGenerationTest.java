package net.minecraft.world.gen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.layer.BiomeLayers;
import net.minecraft.world.storage.WorldStorage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Self-consistency and sanity checks for the vanilla-shaped generator:
 * determinism, no hidden state between chunks in the terrain stage, the
 * bedrock floor, carved caves, and population actually placing features.
 * Fidelity to real 1.8.9 worlds is the oracle tests' job (unknowns.yml).
 */
final class TerrainGenerationTest {
    private static final long SEED = 20260926L;
    private final BlockRegistry blocks = GameData.blocks();
    private final BiomeRegistry biomes = GameData.biomes();
    private final GenBlocks gen = GameData.genBlocks();

    private ChunkGenerator generator() { return new ChunkGenerator(gen, biomes, new BiomeLayers(SEED), SEED); }

    private static int[] snapshot(ChunkPrimer primer) {
        int[] states = new int[16 * 16 * 256];
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                for (int y = 0; y < 256; y++) states[x << 12 | z << 8 | y] = primer.get(x, y, z);
        return states;
    }

    @Test
    void terrainStageDependsOnlyOnSeedAndPosition() {
        ChunkGenerator first = generator(), second = generator();
        int[] direct = snapshot(first.generate(5, -3));
        // Generate unrelated chunks first (including mesa and ravine-prone ones) to expose leaked state.
        for (int i = 0; i < 6; i++) second.generate(i * 7 - 20, 11 - i * 5);
        assertArrayEquals(direct, snapshot(second.generate(5, -3)));
    }

    @Test
    void everyColumnHasABedrockFloorAndNothingButBedrockAtTheBottom() {
        ChunkGenerator generator = generator();
        for (int chunk = 0; chunk < 4; chunk++) {
            ChunkPrimer primer = generator.generate(chunk * 3, -chunk);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    assertEquals(gen.BEDROCK, primer.get(x, 0, z), "bedrock at y=0");
                    for (int y = 5; y < 256; y++) assertFalse(primer.get(x, y, z) == gen.BEDROCK, "bedrock at y=" + y);
                }
            }
        }
    }

    @Test
    void cavesAreCarvedUnderground() {
        ChunkGenerator generator = generator();
        int air = 0;
        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                ChunkPrimer primer = generator.generate(cx, cz);
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++)
                        for (int y = 12; y < 40; y++) if (primer.get(x, y, z) == 0) air++;
            }
        }
        assertTrue(air > 500, "expected caves below y=40 in 64 chunks, found " + air + " air blocks");
    }

    @Test
    void terrainHasSeaLevelWaterAndLandInAWideArea() {
        ChunkGenerator generator = generator();
        int water = 0, land = 0;
        for (int cx = -6; cx < 6; cx++) {
            for (int cz = -6; cz < 6; cz++) {
                ChunkPrimer primer = generator.generate(cx * 4, cz * 4);
                for (int x = 0; x < 16; x += 5)
                    for (int z = 0; z < 16; z += 5) {
                        int state = primer.get(x, 62, z);
                        if (gen.isWater(state) || state >>> 4 == gen.iceId) water++;
                        else if (gen.isSolid(state)) land++;
                    }
            }
        }
        assertTrue(water > 0 && land > 0, "water " + water + ", land " + land);
    }

    @Test
    void populationPlacesOresAndMarksChunks() {
        World world = GameData.newWorld(SEED);
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) world.getChunk(cx, cz);
        Chunk centre = world.getChunk(0, 0);
        assertTrue(centre.isPopulated());
        assertFalse(world.getChunk(2, 2).isPopulated(), "the corner's east/south neighbours don't exist yet");
        Map<String, Integer> counts = new HashMap<>();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                for (int y = 0; y < 128; y++) {
                    Block block = blocks.byId(centre.get(x, y, z) >>> 4);
                    counts.merge(block.name().path(), 1, Integer::sum);
                }
        assertTrue(counts.getOrDefault("coal_ore", 0) > 0, "coal in " + counts);
        assertTrue(counts.getOrDefault("iron_ore", 0) > 0, "iron in " + counts);
    }

    @Test
    void populatedFlagSurvivesSaving() throws Exception {
        Path directory = Files.createTempDirectory("lc-populated");
        World world = GameData.newWorld(SEED);
        for (int cx = 0; cx <= 1; cx++) for (int cz = 0; cz <= 1; cz++) world.getChunk(cx, cz);
        WorldStorage.save(directory, world);
        World restored = WorldStorage.load(directory, blocks, biomes);
        assertTrue(restored.getChunk(0, 0).isPopulated());
        assertFalse(restored.getChunk(1, 1).isPopulated());
    }

    @Test
    void mesaClayBandsDependOnTheSeedOnly() {
        // Two surface builders from the same seed paint identical mesa columns.
        int[] first = mesaColumn(new SurfaceBuilder(gen, SEED, new NoiseGeneratorPerlin(new java.util.Random(1), 4)));
        int[] second = mesaColumn(new SurfaceBuilder(gen, SEED, new NoiseGeneratorPerlin(new java.util.Random(1), 4)));
        assertArrayEquals(first, second);
        assertTrue(Arrays.stream(first).anyMatch(state -> state >>> 4 == gen.stainedClayId), "mesa clay");
    }

    private int[] mesaColumn(SurfaceBuilder surface) {
        ChunkPrimer primer = new ChunkPrimer();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                for (int y = 0; y < 100; y++) primer.set(x, y, z, gen.STONE);
        var mesa = biomes.byId(37);
        var columns = new net.minecraft.world.biome.Biome[256];
        Arrays.fill(columns, mesa);
        surface.build(0, 0, primer, new java.util.Random(7), columns);
        int[] column = new int[256];
        for (int y = 0; y < 256; y++) column[y] = primer.get(3, y, 5);
        return column;
    }
}

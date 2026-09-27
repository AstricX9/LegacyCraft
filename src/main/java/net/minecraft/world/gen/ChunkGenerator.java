package net.minecraft.world.gen;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.gen.layer.BiomeLayers;

import java.util.Random;

/**
 * The first stage of 1.8.9 overworld generation (vanilla
 * ChunkProviderGenerate.provideChunk): the density-field shape, the biome
 * surface, then caves and ravines. The result depends only on the seed and
 * the chunk position, never on what else has been generated.
 */
public final class ChunkGenerator {
    private final GenBlocks blocks;
    private final BiomeRegistry biomes;
    private final BiomeLayers layers;
    private final TerrainGenerator terrain;
    private final SurfaceBuilder surface;
    private final MapGenCaves caves;
    private final MapGenRavine ravines;
    private final Random rand = new Random();

    public ChunkGenerator(GenBlocks blocks, BiomeRegistry biomes, BiomeLayers layers, long seed) {
        this.blocks = blocks;
        this.biomes = biomes;
        this.layers = layers;
        terrain = new TerrainGenerator(seed, layers, biomes);
        surface = new SurfaceBuilder(blocks, seed, terrain.surfaceNoise());
        caves = new MapGenCaves(blocks, seed);
        ravines = new MapGenRavine(blocks, seed);
    }

    /** The chunk's column biomes, index {@code z * 16 + x}. */
    public Biome[] columnBiomes(int chunkX, int chunkZ) {
        byte[] ids = layers.chunkBiomes(chunkX, chunkZ);
        Biome[] result = new Biome[256];
        Biome ocean = biomes.get("minecraft:ocean");
        for (int i = 0; i < 256; i++) {
            Biome biome = biomes.byId(ids[i] & 0xff);
            result[i] = biome != null ? biome : ocean;
        }
        return result;
    }

    public synchronized ChunkPrimer generate(int chunkX, int chunkZ) {
        rand.setSeed((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L);
        ChunkPrimer primer = new ChunkPrimer();
        terrain.fillChunk(chunkX, chunkZ, primer, blocks.STONE, blocks.WATER);
        Biome[] columns = columnBiomes(chunkX, chunkZ);
        surface.build(chunkX, chunkZ, primer, rand, columns);
        caves.generate(chunkX, chunkZ, primer, columns);
        ravines.generate(chunkX, chunkZ, primer, columns);
        return primer;
    }
}

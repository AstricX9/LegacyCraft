package net.minecraft.world.biome;

import net.minecraft.world.gen.layer.BiomeLayers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Picks a biome per column using the 1.8.9 layer chain
 * ({@link BiomeLayers}), seeded from the world seed. VERIFIED_VANILLA: for the
 * oracle world's seed this reproduces every biome byte the real 1.8.9 server
 * stored (VanillaBiomeMapTest, 160000/160000 columns).
 */
public final class BiomeSource {
    /** Chunks to keep; the layer chain is deterministic, so this is only about speed. */
    private static final int CACHED_CHUNKS = 512;

    private final BiomeRegistry biomes;
    private final BiomeLayers layers;
    private final Map<Long, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) { return size() > CACHED_CHUNKS; }
    };

    public BiomeSource(BiomeRegistry biomes, long seed) {
        this.biomes = biomes;
        this.layers = new BiomeLayers(seed);
    }

    public Biome sample(int blockX, int blockZ) {
        int chunkX = blockX >> 4, chunkZ = blockZ >> 4;
        byte[] ids;
        synchronized (cache) {
            ids = cache.computeIfAbsent((long) chunkX << 32 | chunkZ & 0xffffffffL,
                key -> layers.chunkBiomes(chunkX, chunkZ));
        }
        int id = ids[(blockZ & 15) * 16 + (blockX & 15)] & 0xff;
        Biome biome = biomes.byId(id);
        return biome != null ? biome : biomes.get("minecraft:ocean");
    }

    /** The layer chain itself, for world generation and spawn selection. */
    public BiomeLayers layers() { return layers; }

    /** The whole 16x16 biome map of a chunk, index {@code z * 16 + x}, as Anvil stores it. */
    public byte[] chunkBiomes(int chunkX, int chunkZ) { return layers.chunkBiomes(chunkX, chunkZ); }
}

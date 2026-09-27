package net.minecraft.world.gen.layer;

/**
 * Builds the 1.8.9 default-world-type layer chain (master context §14). The
 * order and the per-layer base seeds are what make one seed give the same
 * biome map as vanilla, so nothing here may be reordered.
 *
 * <p>Two outputs come out of it: {@link #coarse()} at one value per four
 * blocks, used by terrain generation, and {@link #perBlock()} at one value per
 * block, which is what a chunk stores in its Biomes array.
 */
public final class BiomeLayers {
    /** Vanilla "normal" biome size; larger values stretch the map. */
    private static final int BIOME_SIZE = 4;

    private final GenLayer coarse;
    private final GenLayer perBlock;

    public BiomeLayers(long worldSeed) {
        GenLayer sketch = new LayerIsland(1L);
        sketch = new LayerFuzzyZoom(2000L, sketch);
        sketch = new LayerAddIsland(1L, sketch);
        sketch = new LayerZoom(2001L, sketch);
        sketch = new LayerAddIsland(2L, sketch);
        sketch = new LayerAddIsland(50L, sketch);
        sketch = new LayerAddIsland(70L, sketch);
        sketch = new LayerOcean(2L, sketch, LayerOcean.Mode.REMOVE_TOO_MUCH);
        sketch = new LayerClimate(2L, sketch, LayerClimate.Mode.ADD_SNOW);
        sketch = new LayerAddIsland(3L, sketch);
        sketch = new LayerClimate(2L, sketch, LayerClimate.Mode.COOL_WARM);
        sketch = new LayerClimate(2L, sketch, LayerClimate.Mode.HEAT_ICE);
        sketch = new LayerClimate(3L, sketch, LayerClimate.Mode.SPECIAL);
        sketch = new LayerZoom(2002L, sketch);
        sketch = new LayerZoom(2003L, sketch);
        sketch = new LayerAddIsland(4L, sketch);
        sketch = new LayerOcean(5L, sketch, LayerOcean.Mode.MUSHROOM_ISLAND);
        sketch = new LayerOcean(4L, sketch, LayerOcean.Mode.DEEP);

        // The river network is seeded from the same sketch, then zoomed alongside the biomes.
        GenLayer riverSeeds = new LayerRiver(100L, LayerZoom.magnify(1000L, sketch, 0), LayerRiver.Mode.INIT);

        GenLayer biomes = new LayerBiome(200L, sketch);
        biomes = new LayerBiomeEdge(1000L, LayerZoom.magnify(1000L, biomes, 2));
        biomes = new LayerHills(1000L, biomes, LayerZoom.magnify(1000L, riverSeeds, 2));

        GenLayer rivers = LayerZoom.magnify(1000L, riverSeeds, 2);
        rivers = LayerZoom.magnify(1000L, rivers, BIOME_SIZE);
        rivers = new LayerRiver(1L, rivers, LayerRiver.Mode.TRACE);
        rivers = new LayerSmooth(1000L, rivers);

        biomes = new LayerRareBiome(1001L, biomes);
        for (int i = 0; i < BIOME_SIZE; i++) {
            biomes = new LayerZoom(1000 + i, biomes);
            if (i == 0) biomes = new LayerAddIsland(3L, biomes);
            if (i == 1 || BIOME_SIZE == 1) biomes = new LayerShore(1000L, biomes);
        }
        biomes = new LayerSmooth(1000L, biomes);

        coarse = new LayerRiver(100L, biomes, LayerRiver.Mode.MIX, rivers);
        perBlock = new LayerVoronoiZoom(10L, coarse);
        coarse.initWorldGenSeed(worldSeed);
        perBlock.initWorldGenSeed(worldSeed);
    }

    /** One biome ID per four blocks, as terrain height generation reads it. */
    public GenLayer coarse() { return coarse; }

    /** One biome ID per block, as a chunk stores it. */
    public GenLayer perBlock() { return perBlock; }

    /** The 16x16 biome IDs a chunk stores, in the Anvil order (index = z * 16 + x). */
    public byte[] chunkBiomes(int chunkX, int chunkZ) {
        int[] ids = perBlock.getInts(chunkX << 4, chunkZ << 4, 16, 16);
        byte[] biomes = new byte[256];
        for (int i = 0; i < 256; i++) biomes[i] = (byte) ids[i];
        return biomes;
    }
}

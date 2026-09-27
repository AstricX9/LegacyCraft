package net.minecraft.world.gen.layer;

/** The starting sketch: one cell in ten is land, and the cell at the origin always is. */
public final class LayerIsland extends GenLayer {
    public LayerIsland(long baseSeed) { super(baseSeed); }

    @Override
    public int[] getInts(int areaX, int areaZ, int width, int height) {
        int[] result = new int[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                initChunkSeed(areaX + x, areaZ + z);
                result[x + z * width] = nextInt(10) == 0 ? Biomes.PLAINS : Biomes.OCEAN;
            }
        }
        // The world always starts on land.
        if (areaX > -width && areaX <= 0 && areaZ > -height && areaZ <= 0) result[-areaX + -areaZ * width] = Biomes.PLAINS;
        return result;
    }
}

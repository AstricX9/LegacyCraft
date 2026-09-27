package net.minecraft.world.gen.layer;

/** Scatters rare patches: roughly one plains cell in 57 becomes sunflower plains. */
public final class LayerRareBiome extends GenLayer {
    public LayerRareBiome(long baseSeed, GenLayer parent) {
        super(baseSeed);
        this.parent = parent;
    }

    @Override
    public int[] getInts(int areaX, int areaZ, int width, int height) {
        int[] parentValues = parent.getInts(areaX, areaZ, width, height);
        int[] result = new int[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                initChunkSeed(areaX + x, areaZ + z);
                int value = parentValues[x + z * width];
                result[x + z * width] = nextInt(57) == 0 && value == Biomes.PLAINS ? Biomes.PLAINS + 128 : value;
            }
        }
        return result;
    }
}

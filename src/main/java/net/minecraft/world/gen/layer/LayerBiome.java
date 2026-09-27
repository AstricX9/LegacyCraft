package net.minecraft.world.gen.layer;

/** Turns each climate category into an actual biome; cells marked special become the rare biomes. */
public final class LayerBiome extends GenLayer {
    private static final int[] WARM = {Biomes.DESERT, Biomes.DESERT, Biomes.DESERT, Biomes.SAVANNA, Biomes.SAVANNA, Biomes.PLAINS};
    private static final int[] MEDIUM = {Biomes.FOREST, Biomes.ROOFED_FOREST, Biomes.EXTREME_HILLS, Biomes.PLAINS,
        Biomes.BIRCH_FOREST, Biomes.SWAMPLAND};
    private static final int[] COLD = {Biomes.FOREST, Biomes.EXTREME_HILLS, Biomes.TAIGA, Biomes.PLAINS};
    private static final int[] ICY = {Biomes.ICE_PLAINS, Biomes.ICE_PLAINS, Biomes.ICE_PLAINS, Biomes.COLD_TAIGA};

    public LayerBiome(long baseSeed, GenLayer parent) {
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
                int special = (value & 0xF00) >> 8;
                value &= ~0xF00;
                if (isOcean(value) || value == Biomes.MUSHROOM_ISLAND) {
                    result[x + z * width] = value;
                } else if (value == Biomes.WARM) {
                    result[x + z * width] = special > 0
                        ? (nextInt(3) == 0 ? Biomes.MESA_PLATEAU : Biomes.MESA_PLATEAU_F)
                        : WARM[nextInt(WARM.length)];
                } else if (value == Biomes.MEDIUM) {
                    result[x + z * width] = special > 0 ? Biomes.JUNGLE : MEDIUM[nextInt(MEDIUM.length)];
                } else if (value == Biomes.COLD) {
                    result[x + z * width] = special > 0 ? Biomes.MEGA_TAIGA : COLD[nextInt(COLD.length)];
                } else if (value == Biomes.ICY) {
                    result[x + z * width] = ICY[nextInt(ICY.length)];
                } else {
                    result[x + z * width] = Biomes.MUSHROOM_ISLAND;
                }
            }
        }
        return result;
    }
}

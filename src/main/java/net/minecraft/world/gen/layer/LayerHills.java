package net.minecraft.world.gen.layer;

/**
 * Raises hill variants inside biomes (forest to forest hills, desert to
 * desert hills) and turns a few patches into the rare mutated variants,
 * driven by the same random field the rivers use.
 */
public final class LayerHills extends GenLayer {
    private final GenLayer hillSource;

    public LayerHills(long baseSeed, GenLayer biomes, GenLayer hillSource) {
        super(baseSeed);
        this.parent = biomes;
        this.hillSource = hillSource;
    }

    @Override
    public void initWorldGenSeed(long worldSeed) {
        super.initWorldGenSeed(worldSeed);
        hillSource.initWorldGenSeed(worldSeed);
    }

    @Override
    public int[] getInts(int areaX, int areaZ, int width, int height) {
        int parentX = areaX - 1, parentZ = areaZ - 1;
        int parentWidth = width + 2, parentHeight = height + 2;
        int[] biomeValues = parent.getInts(parentX, parentZ, parentWidth, parentHeight);
        int[] hillValues = hillSource.getInts(parentX, parentZ, parentWidth, parentHeight);
        int[] result = new int[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                initChunkSeed(areaX + x, areaZ + z);
                int biome = biomeValues[x + 1 + (z + 1) * parentWidth];
                int source = hillValues[x + 1 + (z + 1) * parentWidth];
                boolean mutate = source >= 2 && (source - 2) % 29 == 0;
                if (source >= 2 && (source - 2) % 29 == 1 && !isMutated(biome)) {
                    result[x + z * width] = hasMutation(biome) ? biome + 128 : biome;
                    continue;
                }
                if (nextInt(3) != 0 && !mutate) { result[x + z * width] = biome; continue; }

                int hill = hillOf(biome);
                if (mutate && hill != biome) hill = hasMutation(hill) ? hill + 128 : biome;
                if (hill == biome) { result[x + z * width] = biome; continue; }
                // A hill only forms where its biome surrounds it, so hills do not speckle the border.
                int matching = 0;
                if (alike(biomeValues[x + 1 + z * parentWidth], biome)) matching++;
                if (alike(biomeValues[x + 2 + (z + 1) * parentWidth], biome)) matching++;
                if (alike(biomeValues[x + (z + 1) * parentWidth], biome)) matching++;
                if (alike(biomeValues[x + 1 + (z + 2) * parentWidth], biome)) matching++;
                result[x + z * width] = matching >= 3 ? hill : biome;
            }
        }
        return result;
    }

    private int hillOf(int biome) {
        return switch (biome) {
            case Biomes.DESERT -> Biomes.DESERT_HILLS;
            case Biomes.FOREST -> Biomes.FOREST_HILLS;
            case Biomes.BIRCH_FOREST -> Biomes.BIRCH_FOREST_HILLS;
            case Biomes.ROOFED_FOREST -> Biomes.PLAINS;
            case Biomes.TAIGA -> Biomes.TAIGA_HILLS;
            case Biomes.MEGA_TAIGA -> Biomes.MEGA_TAIGA_HILLS;
            case Biomes.COLD_TAIGA -> Biomes.COLD_TAIGA_HILLS;
            case Biomes.PLAINS -> nextInt(3) == 0 ? Biomes.FOREST_HILLS : Biomes.FOREST;
            case Biomes.ICE_PLAINS -> Biomes.ICE_MOUNTAINS;
            case Biomes.JUNGLE -> Biomes.JUNGLE_HILLS;
            case Biomes.OCEAN -> Biomes.DEEP_OCEAN;
            case Biomes.EXTREME_HILLS -> Biomes.EXTREME_HILLS_PLUS;
            case Biomes.SAVANNA -> Biomes.SAVANNA_PLATEAU;
            case Biomes.DEEP_OCEAN -> nextInt(3) == 0 ? (nextInt(2) == 0 ? Biomes.PLAINS : Biomes.FOREST) : biome;
            case Biomes.MESA_PLATEAU_F, Biomes.MESA_PLATEAU -> Biomes.MESA;
            default -> biome;
        };
    }

    private static boolean isMutated(int biome) { return biome >= 128; }
    /** Only some biomes have a mutated variant, at their ID plus 128. */
    private static boolean hasMutation(int biome) {
        return switch (biome) {
            case Biomes.PLAINS, Biomes.DESERT, Biomes.EXTREME_HILLS, Biomes.FOREST, Biomes.TAIGA, Biomes.SWAMPLAND,
                 Biomes.ICE_PLAINS, Biomes.JUNGLE, Biomes.JUNGLE_EDGE, Biomes.BIRCH_FOREST, Biomes.BIRCH_FOREST_HILLS,
                 Biomes.ROOFED_FOREST, Biomes.COLD_TAIGA, Biomes.MEGA_TAIGA, Biomes.MEGA_TAIGA_HILLS,
                 Biomes.EXTREME_HILLS_PLUS, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.MESA,
                 Biomes.MESA_PLATEAU_F, Biomes.MESA_PLATEAU -> true;
            default -> false;
        };
    }

    /** Hill variants count as their own biome when deciding whether a hill fits. */
    private static boolean alike(int value, int biome) {
        return value == biome || value == biome + 128 || biome == value + 128;
    }
}

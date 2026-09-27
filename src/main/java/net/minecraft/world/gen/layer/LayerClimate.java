package net.minecraft.world.gen.layer;

/**
 * The climate steps between the land sketch and real biomes. Land starts as
 * warm, cold or icy; the edge passes then soften impossible neighbours (warm
 * next to cold becomes medium, icy next to warm becomes cold) and mark rare
 * cells that later turn into mesa, jungle or mega taiga.
 */
public final class LayerClimate extends GenLayer {
    public enum Mode {
        /** One in six land cells becomes icy, one in six cold, the rest warm. */
        ADD_SNOW,
        /** Warm beside cold or icy softens to medium. */
        COOL_WARM,
        /** Icy beside warm or medium softens to cold. */
        HEAT_ICE,
        /** Marks one cell in thirteen as special, which the biome layer turns into a rare biome. */
        SPECIAL
    }

    private final Mode mode;

    public LayerClimate(long baseSeed, GenLayer parent, Mode mode) {
        super(baseSeed);
        this.parent = parent;
        this.mode = mode;
    }

    @Override
    public int[] getInts(int areaX, int areaZ, int width, int height) {
        if (mode == Mode.ADD_SNOW || mode == Mode.SPECIAL) return simple(areaX, areaZ, width, height);
        return edge(areaX, areaZ, width, height);
    }

    private int[] simple(int areaX, int areaZ, int width, int height) {
        int[] parentValues = parent.getInts(areaX, areaZ, width, height);
        int[] result = new int[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int value = parentValues[x + z * width];
                initChunkSeed(areaX + x, areaZ + z);
                if (mode == Mode.ADD_SNOW) {
                    if (isOcean(value)) { result[x + z * width] = value; continue; }
                    int roll = nextInt(6);
                    result[x + z * width] = roll == 0 ? Biomes.ICY : roll <= 1 ? Biomes.COLD : Biomes.WARM;
                } else {
                    if (value == 0) { result[x + z * width] = 0; continue; }
                    result[x + z * width] = nextInt(13) == 0 ? value | (1 + nextInt(15)) << 8 : value;
                }
            }
        }
        return result;
    }

    private int[] edge(int areaX, int areaZ, int width, int height) {
        int parentX = areaX - 1, parentZ = areaZ - 1;
        int parentWidth = width + 2, parentHeight = height + 2;
        int[] parentValues = parent.getInts(parentX, parentZ, parentWidth, parentHeight);
        int[] result = new int[width * height];
        int looking = mode == Mode.COOL_WARM ? Biomes.WARM : Biomes.ICY;
        int becomes = mode == Mode.COOL_WARM ? Biomes.MEDIUM : Biomes.COLD;
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int centre = parentValues[x + 1 + (z + 1) * parentWidth];
                if (centre != looking) { result[x + z * width] = centre; continue; }
                int north = parentValues[x + 1 + z * parentWidth];
                int east = parentValues[x + 2 + (z + 1) * parentWidth];
                int west = parentValues[x + (z + 1) * parentWidth];
                int south = parentValues[x + 1 + (z + 2) * parentWidth];
                boolean clash = mode == Mode.COOL_WARM
                    ? clashesWithCold(north) || clashesWithCold(east) || clashesWithCold(west) || clashesWithCold(south)
                    : clashesWithWarm(north) || clashesWithWarm(east) || clashesWithWarm(west) || clashesWithWarm(south);
                result[x + z * width] = clash ? becomes : centre;
            }
        }
        return result;
    }

    private static boolean clashesWithCold(int value) { return value == Biomes.COLD || value == Biomes.ICY; }
    private static boolean clashesWithWarm(int value) { return value == Biomes.WARM || value == Biomes.MEDIUM; }
}

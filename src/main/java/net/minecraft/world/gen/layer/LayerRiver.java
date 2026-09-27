package net.minecraft.world.gen.layer;

/**
 * Rivers. {@link Mode#INIT} gives every land cell a large random number;
 * {@link Mode#TRACE} draws a river wherever those numbers disagree across a
 * cell boundary, which makes winding lines; {@link Mode#MIX} lays the rivers
 * over the biome map, freezing them in cold biomes.
 */
public final class LayerRiver extends GenLayer {
    public enum Mode { INIT, TRACE, MIX }

    /** Marks a cell as "no river here". */
    private static final int NO_RIVER = -1;

    private final Mode mode;
    private final GenLayer biomes;

    public LayerRiver(long baseSeed, GenLayer parent, Mode mode) { this(baseSeed, parent, mode, null); }

    /** The mixing layer needs both the biome map ({@code parent}) and the river map. */
    public LayerRiver(long baseSeed, GenLayer parent, Mode mode, GenLayer rivers) {
        super(baseSeed);
        this.parent = parent;
        this.mode = mode;
        this.biomes = rivers;
    }

    @Override
    public void initWorldGenSeed(long worldSeed) {
        super.initWorldGenSeed(worldSeed);
        if (biomes != null) biomes.initWorldGenSeed(worldSeed);
    }

    @Override
    public int[] getInts(int areaX, int areaZ, int width, int height) {
        return switch (mode) {
            case INIT -> init(areaX, areaZ, width, height);
            case TRACE -> trace(areaX, areaZ, width, height);
            case MIX -> mix(areaX, areaZ, width, height);
        };
    }

    private int[] init(int areaX, int areaZ, int width, int height) {
        int[] parentValues = parent.getInts(areaX, areaZ, width, height);
        int[] result = new int[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                initChunkSeed(areaX + x, areaZ + z);
                result[x + z * width] = parentValues[x + z * width] > 0 ? nextInt(299999) + 2 : 0;
            }
        }
        return result;
    }

    private int[] trace(int areaX, int areaZ, int width, int height) {
        int parentX = areaX - 1, parentZ = areaZ - 1;
        int parentWidth = width + 2, parentHeight = height + 2;
        int[] parentValues = parent.getInts(parentX, parentZ, parentWidth, parentHeight);
        int[] result = new int[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int centre = riverValue(parentValues[x + 1 + (z + 1) * parentWidth]);
                int north = riverValue(parentValues[x + 1 + z * parentWidth]);
                int east = riverValue(parentValues[x + 2 + (z + 1) * parentWidth]);
                int west = riverValue(parentValues[x + (z + 1) * parentWidth]);
                int south = riverValue(parentValues[x + 1 + (z + 2) * parentWidth]);
                result[x + z * width] = centre == north && centre == east && centre == west && centre == south
                    ? NO_RIVER : Biomes.RIVER;
            }
        }
        return result;
    }

    /** Only the lowest bit of the random value matters, so rivers form where it flips. */
    private static int riverValue(int value) { return value >= 2 ? 2 + (value & 1) : value; }

    private int[] mix(int areaX, int areaZ, int width, int height) {
        int[] biomeValues = parent.getInts(areaX, areaZ, width, height);
        int[] riverValues = biomes.getInts(areaX, areaZ, width, height);
        int[] result = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            int biome = biomeValues[i];
            if (isOcean(biome)) { result[i] = biome; continue; }
            if (riverValues[i] != Biomes.RIVER) { result[i] = biome; continue; }
            if (biome == Biomes.ICE_PLAINS) result[i] = Biomes.FROZEN_RIVER;
            else if (biome == Biomes.MUSHROOM_ISLAND || biome == Biomes.MUSHROOM_ISLAND_SHORE)
                result[i] = Biomes.MUSHROOM_ISLAND_SHORE;
            else result[i] = Biomes.RIVER;
        }
        return result;
    }
}

package net.minecraft.world.gen.layer;

/** Ocean housekeeping: break up endless ocean, deepen open water, and drop the odd mushroom island into it. */
public final class LayerOcean extends GenLayer {
    public enum Mode { REMOVE_TOO_MUCH, DEEP, MUSHROOM_ISLAND }

    private final Mode mode;

    public LayerOcean(long baseSeed, GenLayer parent, Mode mode) {
        super(baseSeed);
        this.parent = parent;
        this.mode = mode;
    }

    @Override
    public int[] getInts(int areaX, int areaZ, int width, int height) {
        int parentX = areaX - 1, parentZ = areaZ - 1;
        int parentWidth = width + 2, parentHeight = height + 2;
        int[] parent = this.parent.getInts(parentX, parentZ, parentWidth, parentHeight);
        int[] result = new int[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int centre = parent[x + 1 + (z + 1) * parentWidth];
                initChunkSeed(areaX + x, areaZ + z);
                result[x + z * width] = switch (mode) {
                    case REMOVE_TOO_MUCH -> {
                        int north = parent[x + 1 + z * parentWidth], east = parent[x + 2 + (z + 1) * parentWidth];
                        int west = parent[x + (z + 1) * parentWidth], south = parent[x + 1 + (z + 2) * parentWidth];
                        yield centre == 0 && north == 0 && east == 0 && west == 0 && south == 0 && nextInt(2) == 0
                            ? Biomes.WARM : centre;
                    }
                    case DEEP -> {
                        int oceans = 0;
                        if (parent[x + 1 + z * parentWidth] == 0) oceans++;
                        if (parent[x + 2 + (z + 1) * parentWidth] == 0) oceans++;
                        if (parent[x + (z + 1) * parentWidth] == 0) oceans++;
                        if (parent[x + 1 + (z + 2) * parentWidth] == 0) oceans++;
                        yield centre == 0 && oceans > 3 ? Biomes.DEEP_OCEAN : centre;
                    }
                    case MUSHROOM_ISLAND -> {
                        int northWest = parent[x + z * parentWidth], northEast = parent[x + 2 + z * parentWidth];
                        int southWest = parent[x + (z + 2) * parentWidth], southEast = parent[x + 2 + (z + 2) * parentWidth];
                        yield centre == 0 && northWest == 0 && northEast == 0 && southWest == 0 && southEast == 0
                            && nextInt(100) == 0 ? Biomes.MUSHROOM_ISLAND : centre;
                    }
                };
            }
        }
        return result;
    }
}

package net.minecraft.world.gen.layer;

/**
 * Erodes and grows coastlines: ocean surrounded by land tends to become land,
 * land surrounded by ocean tends to wash away, and lone ocean cells beside
 * land sprout islands.
 */
public final class LayerAddIsland extends GenLayer {
    public LayerAddIsland(long baseSeed, GenLayer parent) {
        super(baseSeed);
        this.parent = parent;
    }

    @Override
    public int[] getInts(int areaX, int areaZ, int width, int height) {
        int parentX = areaX - 1, parentZ = areaZ - 1;
        int parentWidth = width + 2, parentHeight = height + 2;
        int[] parentValues = parent.getInts(parentX, parentZ, parentWidth, parentHeight);
        int[] result = new int[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int northWest = parentValues[x + z * parentWidth];
                int northEast = parentValues[x + 2 + z * parentWidth];
                int southWest = parentValues[x + (z + 2) * parentWidth];
                int southEast = parentValues[x + 2 + (z + 2) * parentWidth];
                int centre = parentValues[x + 1 + (z + 1) * parentWidth];
                initChunkSeed(areaX + x, areaZ + z);
                if (centre != 0 || northWest == 0 && northEast == 0 && southWest == 0 && southEast == 0) {
                    if (centre > 0 && (northWest == 0 || northEast == 0 || southWest == 0 || southEast == 0)) {
                        // Land with ocean beside it: sometimes washed away.
                        result[x + z * width] = nextInt(5) == 0 ? (centre == Biomes.ICY ? Biomes.ICY : Biomes.OCEAN) : centre;
                    } else {
                        result[x + z * width] = centre;
                    }
                } else {
                    // Ocean with land beside it: the land may spread into it.
                    int chance = 1, selected = 1;
                    if (northWest != 0 && nextInt(chance++) == 0) selected = northWest;
                    if (northEast != 0 && nextInt(chance++) == 0) selected = northEast;
                    if (southWest != 0 && nextInt(chance++) == 0) selected = southWest;
                    if (southEast != 0 && nextInt(chance++) == 0) selected = southEast;
                    result[x + z * width] = nextInt(3) == 0 ? selected : (selected == Biomes.ICY ? Biomes.ICY : Biomes.OCEAN);
                }
            }
        }
        return result;
    }
}

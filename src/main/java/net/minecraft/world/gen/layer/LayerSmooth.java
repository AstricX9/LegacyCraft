package net.minecraft.world.gen.layer;

/** Removes single-cell jaggedness: a cell pinched between two matching sides takes their value. */
public final class LayerSmooth extends GenLayer {
    public LayerSmooth(long baseSeed, GenLayer parent) {
        super(baseSeed);
        this.parent = parent;
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
                int north = parent[x + 1 + z * parentWidth], east = parent[x + 2 + (z + 1) * parentWidth];
                int west = parent[x + (z + 1) * parentWidth], south = parent[x + 1 + (z + 2) * parentWidth];
                boolean sameEastWest = west == east, sameNorthSouth = north == south;
                if (sameEastWest && sameNorthSouth) {
                    initChunkSeed(areaX + x, areaZ + z);
                    centre = nextInt(2) == 0 ? west : north;
                } else if (sameEastWest) {
                    centre = west;
                } else if (sameNorthSouth) {
                    centre = north;
                }
                result[x + z * width] = centre;
            }
        }
        return result;
    }
}

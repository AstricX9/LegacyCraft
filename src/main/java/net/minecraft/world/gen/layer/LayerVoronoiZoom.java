package net.minecraft.world.gen.layer;

/**
 * The final zoom, from one value per four blocks to one per block. Each
 * coarse cell gets a randomly nudged centre point and every block takes the
 * value of the nearest centre, which gives biome borders their wobble.
 */
public final class LayerVoronoiZoom extends GenLayer {
    public LayerVoronoiZoom(long baseSeed, GenLayer parent) {
        super(baseSeed);
        this.parent = parent;
    }

    @Override
    public int[] getInts(int areaX, int areaZ, int width, int height) {
        int shiftedX = areaX - 2, shiftedZ = areaZ - 2;
        int parentX = shiftedX >> 2, parentZ = shiftedZ >> 2;
        int parentWidth = (width >> 2) + 2, parentHeight = (height >> 2) + 2;
        int[] parentValues = parent.getInts(parentX, parentZ, parentWidth, parentHeight);
        int zoomedWidth = parentWidth - 1 << 2, zoomedHeight = parentHeight - 1 << 2;
        int[] zoomed = new int[zoomedWidth * zoomedHeight];
        for (int z = 0; z < parentHeight - 1; z++) {
            for (int x = 0; x < parentWidth - 1; x++) {
                // Each corner draws its x nudge first, then its z nudge; the far corners sit 4 cells away.
                initChunkSeed(x + parentX << 2, z + parentZ << 2);
                double northWestX = offset(), northWestZ = offset();
                initChunkSeed(x + parentX + 1 << 2, z + parentZ << 2);
                double northEastX = offset() + 4, northEastZ = offset();
                initChunkSeed(x + parentX << 2, z + parentZ + 1 << 2);
                double southWestX = offset(), southWestZ = offset() + 4;
                initChunkSeed(x + parentX + 1 << 2, z + parentZ + 1 << 2);
                double southEastX = offset() + 4, southEastZ = offset() + 4;

                int northWest = parentValues[x + z * parentWidth];
                int northEast = parentValues[x + 1 + z * parentWidth];
                int southWest = parentValues[x + (z + 1) * parentWidth];
                int southEast = parentValues[x + 1 + (z + 1) * parentWidth];
                for (int cellZ = 0; cellZ < 4; cellZ++) {
                    int index = ((z << 2) + cellZ) * zoomedWidth + (x << 2);
                    for (int cellX = 0; cellX < 4; cellX++) {
                        double toNorthWest = distance(cellZ, northWestZ, cellX, northWestX);
                        double toNorthEast = distance(cellZ, northEastZ, cellX, northEastX);
                        double toSouthWest = distance(cellZ, southWestZ, cellX, southWestX);
                        double toSouthEast = distance(cellZ, southEastZ, cellX, southEastX);
                        int nearest;
                        if (toNorthWest < toNorthEast && toNorthWest < toSouthWest && toNorthWest < toSouthEast) nearest = northWest;
                        else if (toNorthEast < toNorthWest && toNorthEast < toSouthWest && toNorthEast < toSouthEast) nearest = northEast;
                        else if (toSouthWest < toNorthWest && toSouthWest < toNorthEast && toSouthWest < toSouthEast) nearest = southWest;
                        else nearest = southEast;
                        zoomed[index++] = nearest;
                    }
                }
            }
        }
        int[] result = new int[width * height];
        for (int z = 0; z < height; z++)
            System.arraycopy(zoomed, (z + (shiftedZ & 3)) * zoomedWidth + (shiftedX & 3), result, z * width, width);
        return result;
    }

    /** How far a cell centre is nudged, in blocks. */
    private double offset() { return (nextInt(1024) / 1024.0 - 0.5) * 3.6; }

    private static double distance(int cellZ, double centreZ, int cellX, double centreX) {
        double dz = cellZ - centreZ, dx = cellX - centreX;
        return dz * dz + dx * dx;
    }
}

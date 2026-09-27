package net.minecraft.world.gen.layer;

/**
 * Doubles the resolution of its parent. Each parent cell becomes four: the
 * corner keeps the parent's value, the two edges pick randomly between the two
 * neighbouring parents, and the diagonal takes whichever value appears twice
 * among the four surrounding parents (or a random one).
 */
public class LayerZoom extends GenLayer {
    public LayerZoom(long baseSeed, GenLayer parent) {
        super(baseSeed);
        this.parent = parent;
    }

    /** Applies {@code times} zoom layers in a row, seeds counting up from {@code baseSeed}. */
    public static GenLayer magnify(long baseSeed, GenLayer parent, int times) {
        GenLayer layer = parent;
        for (int i = 0; i < times; i++) layer = new LayerZoom(baseSeed + i, layer);
        return layer;
    }

    @Override
    public int[] getInts(int areaX, int areaZ, int width, int height) {
        int parentX = areaX >> 1, parentZ = areaZ >> 1;
        int parentWidth = (width >> 1) + 2, parentHeight = (height >> 1) + 2;
        int[] parentValues = parent.getInts(parentX, parentZ, parentWidth, parentHeight);
        int zoomedWidth = parentWidth - 1 << 1, zoomedHeight = parentHeight - 1 << 1;
        int[] zoomed = new int[zoomedWidth * zoomedHeight];
        for (int z = 0; z < parentHeight - 1; z++) {
            int topLeft = parentValues[z * parentWidth];
            int bottomLeft = parentValues[(z + 1) * parentWidth];
            for (int x = 0; x < parentWidth - 1; x++) {
                initChunkSeed(x + parentX << 1, z + parentZ << 1);
                int topRight = parentValues[x + 1 + z * parentWidth];
                int bottomRight = parentValues[x + 1 + (z + 1) * parentWidth];
                zoomed[(x << 1) + (z << 1) * zoomedWidth] = topLeft;
                zoomed[(x << 1) + ((z << 1) + 1) * zoomedWidth] = selectRandom(topLeft, bottomLeft);
                zoomed[(x << 1) + 1 + (z << 1) * zoomedWidth] = selectRandom(topLeft, topRight);
                zoomed[(x << 1) + 1 + ((z << 1) + 1) * zoomedWidth] = pick(topLeft, topRight, bottomLeft, bottomRight);
                topLeft = topRight;
                bottomLeft = bottomRight;
            }
        }
        int[] result = new int[width * height];
        for (int z = 0; z < height; z++)
            System.arraycopy(zoomed, (z + (areaZ & 1)) * zoomedWidth + (areaX & 1), result, z * width, width);
        return result;
    }

    /** The diagonal cell; the fuzzy zoom overrides this to pick at random instead. */
    protected int pick(int topLeft, int topRight, int bottomLeft, int bottomRight) {
        return selectModeOrRandom(topLeft, topRight, bottomLeft, bottomRight);
    }
}

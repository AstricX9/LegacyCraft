package net.minecraft.world.gen.layer;

/** Softens biomes that must not touch: hill edges, mesa borders, and swamps beside deserts or jungles. */
public final class LayerBiomeEdge extends GenLayer {
    public LayerBiomeEdge(long baseSeed, GenLayer parent) {
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
                initChunkSeed(areaX + x, areaZ + z);
                int centre = parent[x + 1 + (z + 1) * parentWidth];
                int north = parent[x + 1 + z * parentWidth], east = parent[x + 2 + (z + 1) * parentWidth];
                int west = parent[x + (z + 1) * parentWidth], south = parent[x + 1 + (z + 2) * parentWidth];
                Integer edged = edge(centre, north, east, west, south, Biomes.EXTREME_HILLS, Biomes.EXTREME_HILLS_EDGE);
                if (edged == null) edged = edgeUnlessRelated(centre, north, east, west, south, Biomes.MESA_PLATEAU_F, Biomes.DESERT);
                if (edged == null) edged = edgeUnlessRelated(centre, north, east, west, south, Biomes.MESA_PLATEAU, Biomes.DESERT);
                if (edged == null) edged = edgeUnlessRelated(centre, north, east, west, south, Biomes.MEGA_TAIGA, Biomes.TAIGA);
                if (edged == null) {
                    if (centre == Biomes.DESERT && anyIs(Biomes.ICE_PLAINS, north, east, west, south)) edged = Biomes.COLD_TAIGA;
                    else if (centre == Biomes.SWAMPLAND && (anyIs(Biomes.DESERT, north, east, west, south)
                        || anyIs(Biomes.COLD_TAIGA, north, east, west, south) || anyIs(Biomes.ICE_PLAINS, north, east, west, south)))
                        edged = Biomes.PLAINS;
                    else if (centre == Biomes.SWAMPLAND && anyIs(Biomes.JUNGLE, north, east, west, south)) edged = Biomes.JUNGLE_EDGE;
                    else edged = centre;
                }
                result[x + z * width] = edged;
            }
        }
        return result;
    }

    /** {@code from} bordering anything unlike it becomes {@code to}. */
    private static Integer edge(int centre, int north, int east, int west, int south, int from, int to) {
        if (centre != from) return null;
        return north != from || east != from || west != from || south != from ? to : from;
    }

    /** Like {@link #edge} but tolerating related biomes (mesa beside mesa, mega taiga beside taiga). */
    private static Integer edgeUnlessRelated(int centre, int north, int east, int west, int south, int from, int to) {
        if (centre != from) return null;
        boolean bordersUnrelated = !related(north, from) || !related(east, from) || !related(west, from) || !related(south, from);
        return bordersUnrelated ? to : from;
    }

    private static boolean related(int value, int from) {
        if (from == Biomes.MESA_PLATEAU_F || from == Biomes.MESA_PLATEAU)
            return value == Biomes.MESA || value == Biomes.MESA_PLATEAU_F || value == Biomes.MESA_PLATEAU;
        if (from == Biomes.MEGA_TAIGA) return value == Biomes.MEGA_TAIGA || value == Biomes.MEGA_TAIGA_HILLS
            || value == Biomes.TAIGA || value == Biomes.COLD_TAIGA;
        return value == from;
    }

    private static boolean anyIs(int wanted, int north, int east, int west, int south) {
        return north == wanted || east == wanted || west == wanted || south == wanted;
    }
}

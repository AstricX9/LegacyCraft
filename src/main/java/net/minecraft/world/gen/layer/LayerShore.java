package net.minecraft.world.gen.layer;

/** Puts the right kind of shore between land and water: sand, gravel, snow, or a jungle edge. */
public final class LayerShore extends GenLayer {
    public LayerShore(long baseSeed, GenLayer parent) {
        super(baseSeed);
        this.parent = parent;
    }

    @Override
    public int[] getInts(int areaX, int areaZ, int width, int height) {
        int parentWidth = width + 2, parentHeight = height + 2;
        int[] parent = this.parent.getInts(areaX - 1, areaZ - 1, parentWidth, parentHeight);
        int[] result = new int[width * height];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                initChunkSeed(areaX + x, areaZ + z);
                int centre = parent[x + 1 + (z + 1) * parentWidth];
                int north = parent[x + 1 + z * parentWidth], east = parent[x + 2 + (z + 1) * parentWidth];
                int west = parent[x + (z + 1) * parentWidth], south = parent[x + 1 + (z + 2) * parentWidth];
                int out;
                if (centre == Biomes.MUSHROOM_ISLAND) {
                    boolean touchesOcean = north == Biomes.OCEAN || east == Biomes.OCEAN
                        || west == Biomes.OCEAN || south == Biomes.OCEAN;
                    out = touchesOcean ? Biomes.MUSHROOM_ISLAND_SHORE : centre;
                } else if (isJungle(centre)) {
                    if (suitsJungle(north) && suitsJungle(east) && suitsJungle(west) && suitsJungle(south)) {
                        out = anyOcean(north, east, west, south) ? Biomes.BEACH : centre;
                    } else {
                        out = Biomes.JUNGLE_EDGE;
                    }
                } else if (isExtremeHills(centre)) {
                    out = anyOcean(north, east, west, south) ? Biomes.STONE_BEACH : centre;
                } else if (isSnowy(centre)) {
                    out = anyOcean(north, east, west, south) ? Biomes.COLD_BEACH : centre;
                } else if (isMesa(centre)) {
                    // Mesa wears a desert ring wherever it meets something that is not mesa.
                    if (anyOcean(north, east, west, south)) out = centre;
                    else out = isMesa(north) && isMesa(east) && isMesa(west) && isMesa(south) ? centre : Biomes.DESERT;
                } else if (centre != Biomes.OCEAN && centre != Biomes.DEEP_OCEAN && centre != Biomes.FROZEN_OCEAN
                    && centre != Biomes.RIVER && centre != Biomes.SWAMPLAND) {
                    out = anyOcean(north, east, west, south) ? Biomes.BEACH : centre;
                } else {
                    out = centre;
                }
                result[x + z * width] = out;
            }
        }
        return result;
    }

    private static boolean anyOcean(int north, int east, int west, int south) {
        return isOcean(north) || isOcean(east) || isOcean(west) || isOcean(south);
    }

    private static boolean isJungle(int id) {
        return plain(id) == Biomes.JUNGLE || plain(id) == Biomes.JUNGLE_HILLS || plain(id) == Biomes.JUNGLE_EDGE;
    }
    private static boolean suitsJungle(int id) {
        return isJungle(id) || id == Biomes.FOREST || id == Biomes.TAIGA || isOcean(id);
    }
    private static boolean isExtremeHills(int id) {
        return id == Biomes.EXTREME_HILLS || id == Biomes.EXTREME_HILLS_PLUS || id == Biomes.EXTREME_HILLS_EDGE;
    }
    /** The biomes that let snow settle, which get a frozen beach instead of a sandy one. */
    private static boolean isSnowy(int id) {
        int plain = plain(id);
        return plain == Biomes.ICE_PLAINS || plain == Biomes.ICE_MOUNTAINS || plain == Biomes.COLD_TAIGA
            || plain == Biomes.COLD_TAIGA_HILLS || id == Biomes.FROZEN_OCEAN || id == Biomes.FROZEN_RIVER
            || id == Biomes.COLD_BEACH;
    }
    private static boolean isMesa(int id) {
        int plain = plain(id);
        return plain == Biomes.MESA || plain == Biomes.MESA_PLATEAU_F || plain == Biomes.MESA_PLATEAU;
    }

    /** Mutated variants sit 128 above their base biome and behave the same here. */
    private static int plain(int id) { return id >= 128 ? id - 128 : id; }
}

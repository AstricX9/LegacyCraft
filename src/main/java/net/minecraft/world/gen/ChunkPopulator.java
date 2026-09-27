package net.minecraft.world.gen;

import net.minecraft.world.biome.Biome;

import java.util.Random;

/**
 * Population, the second stage of 1.8.9 overworld generation (vanilla
 * ChunkProviderGenerate.populate): once a chunk and its east, south and
 * south-east neighbours exist, lakes, dungeons and the biome's decoration
 * are placed in the 16x16 area offset by +8, so features can spill across
 * chunk borders; then water freezes and snow falls where it is cold enough.
 *
 * <p>Structures (villages, mineshafts, strongholds, temples, monuments) are
 * not generated, nor the animals vanilla spawns after decoration
 * (deviations.yml). HYPOTHESIS, recalled.
 */
public final class ChunkPopulator {
    private final long seed;
    private final GenBlocks b;
    private final BiomeDecorator decorator;
    private final Random rand = new Random();

    public ChunkPopulator(GenBlocks blocks, long seed) {
        this.seed = seed;
        this.b = blocks;
        this.decorator = new BiomeDecorator(blocks);
    }

    public void populate(GenWorld w, int chunkX, int chunkZ) {
        int x = chunkX * 16, z = chunkZ * 16;
        Biome biome = w.biome(x + 16, z + 16);
        rand.setSeed(seed);
        long xMultiplier = rand.nextLong() / 2L * 2L + 1L;
        long zMultiplier = rand.nextLong() / 2L * 2L + 1L;
        rand.setSeed((long) chunkX * xMultiplier + (long) chunkZ * zMultiplier ^ seed);

        int id = biome.id();
        if (id != 2 && id != 17 && rand.nextInt(4) == 0) {
            int lx = rand.nextInt(16) + 8, ly = rand.nextInt(256), lz = rand.nextInt(16) + 8;
            Features.lake(w, rand, x + lx, ly, z + lz, b.WATER);
        }
        if (rand.nextInt(8) == 0) {
            int lx = rand.nextInt(16) + 8, ly = rand.nextInt(rand.nextInt(248) + 8), lz = rand.nextInt(16) + 8;
            if (ly < 63 || rand.nextInt(10) == 0) Features.lake(w, rand, x + lx, ly, z + lz, b.LAVA);
        }
        for (int i = 0; i < 8; i++) {
            int dx = rand.nextInt(16) + 8, dy = rand.nextInt(256), dz = rand.nextInt(16) + 8;
            Features.dungeon(w, rand, x + dx, dy, z + dz);
        }
        decorator.decorate(w, rand, biome, x, z);

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int bx = x + 8 + dx, bz = z + 8 + dz;
                int top = precipitationHeight(w, bx, bz);
                if (canFreeze(w, bx, top - 1, bz, true)) w.set(bx, top - 1, bz, b.ICE);
                if (canSnow(w, bx, top, bz)) w.set(bx, top, bz, b.SNOW_LAYER);
            }
        }
    }

    /** The lowest y above which snow can fall freely: above the highest block that blocks movement or is liquid. */
    static int precipitationHeight(GenWorld w, int x, int z) {
        GenBlocks b = w.blocks();
        int y = w.height(x, z) + 1;
        while (y > 0) {
            int state = w.get(x, y, z);
            if (b.blocksMovement(state) || b.isLiquid(state)) return y + 1;
            y--;
        }
        return -1;
    }

    /** BiomeGenBase.getFloatTemperature: above y=64 it gets colder with height, with some noise. */
    public static float temperature(Biome biome, int x, int y, int z) {
        if (y > 64) {
            float noise = (float) (SurfaceBuilder.TEMPERATURE_NOISE.getValue(x * 1.0 / 8.0, z * 1.0 / 8.0) * 4.0);
            return biome.temperature() - (noise + (float) y - 64.0F) * 0.05F / 30.0F;
        }
        return biome.temperature();
    }

    /**
     * Still water at a position cold enough to freeze. With {@code edgesOnly}
     * (the population pass), water surrounded by water on all four sides stays
     * liquid; since the pass works row by row, ice spreads from shores and
     * from ice already placed.
     */
    static boolean canFreeze(GenWorld w, int x, int y, int z, boolean edgesOnly) {
        if (y < 0 || y >= 256) return false;
        GenBlocks b = w.blocks();
        if (temperature(w.biome(x, z), x, y, z) > 0.15F) return false;
        if (w.blockLight(x, y, z) >= 10) return false;
        int state = w.get(x, y, z);
        int id = state >>> 4;
        if ((id != b.waterId && id != b.flowingWaterId) || (state & 15) != 0) return false;
        if (!edgesOnly) return true;
        boolean surrounded = b.isWater(w.get(x - 1, y, z)) && b.isWater(w.get(x + 1, y, z))
            && b.isWater(w.get(x, y, z - 1)) && b.isWater(w.get(x, y, z + 1));
        return !surrounded;
    }

    static boolean canSnow(GenWorld w, int x, int y, int z) {
        if (y < 0 || y >= 256) return false;
        GenBlocks b = w.blocks();
        if (temperature(w.biome(x, z), x, y, z) > 0.15F) return false;
        if (w.blockLight(x, y, z) >= 10) return false;
        if (!b.isAir(w.get(x, y, z))) return false;
        int below = w.get(x, y - 1, z);
        int id = below >>> 4;
        if (id == b.iceId || id == b.packedIceId) return false;
        if (b.isLeaves(below)) return true;
        if (id == b.snowLayerId && (below & 15) >= 6) return true;
        return b.isFullBlock(below) && b.blocksMovement(below);
    }
}

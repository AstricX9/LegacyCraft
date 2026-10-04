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
 * <p>After decoration the biome's animals are placed in groups
 * ({@link #spawnAnimals}). Structures (villages, mineshafts, strongholds,
 * temples, monuments) are not generated (deviations.yml). HYPOTHESIS,
 * recalled.
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
        spawnAnimals(w, biome, x + 8, z + 8, 16, 16, rand);

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int bx = x + 8 + dx, bz = z + 8 + dz;
                int top = precipitationHeight(w, bx, bz);
                if (canFreeze(w, bx, top - 1, bz, true)) w.set(bx, top - 1, bz, b.ICE);
                if (canSnow(w, bx, top, bz)) w.set(bx, top, bz, b.SNOW_LAYER);
            }
        }
    }

    /**
     * SpawnerAnimals.performWorldGenSpawning: while a random float is below
     * the biome's spawning chance (0.1), a group from its creature list
     * (weighted) of its group size is placed around a random column of the
     * area, each animal trying up to 4 spots (moving up to 4 blocks each
     * try, kept inside the area) on top of the highest block that blocks
     * movement (leaves don't), where an animal could stand.
     */
    static void spawnAnimals(GenWorld w, Biome biome, int centerX, int centerZ, int diameterX, int diameterZ, Random rand) {
        java.util.List<net.minecraft.world.MobSpawner.SpawnEntry> list = net.minecraft.world.MobSpawner.creatures(biome.id());
        if (list.isEmpty()) return;
        while (rand.nextFloat() < 0.1f) {
            net.minecraft.world.MobSpawner.SpawnEntry entry = net.minecraft.world.MobSpawner.pick(list, rand);
            int count = entry.min() + rand.nextInt(1 + entry.max() - entry.min());
            int j = centerX + rand.nextInt(diameterX), k = centerZ + rand.nextInt(diameterZ);
            int startX = j, startZ = k;
            for (int n = 0; n < count; n++) {
                boolean placed = false;
                for (int attempt = 0; !placed && attempt < 4; attempt++) {
                    int y = topSolidOrLiquid(w, j, k);
                    if (canStand(w, j, y, k)) {
                        float yaw = rand.nextFloat() * 360f;
                        if (net.minecraft.world.MobSpawner.exists(entry.entity())) w.spawnCreature(entry.entity(), j + 0.5, y, k + 0.5, yaw);
                        placed = true;
                    }
                    j += rand.nextInt(5) - rand.nextInt(5);
                    for (k += rand.nextInt(5) - rand.nextInt(5); j < centerX || j >= centerX + diameterX || k < centerZ
                        || k >= centerZ + diameterX; k = startZ + rand.nextInt(5) - rand.nextInt(5))
                        j = startX + rand.nextInt(5) - rand.nextInt(5);
                }
            }
        }
    }

    /** World.getTopSolidOrLiquidBlock: just above the highest block that blocks movement and isn't leaves. */
    private static int topSolidOrLiquid(GenWorld w, int x, int z) {
        GenBlocks b = w.blocks();
        int y = 255;
        while (y > 0) {
            int below = w.get(x, y - 1, z);
            if (b.blocksMovement(below) && !b.isLeaves(below)) break;
            y--;
        }
        return y;
    }

    /** SpawnerAnimals.canCreatureTypeSpawnAtLocation on the ground, read through population's view. */
    private static boolean canStand(GenWorld w, int x, int y, int z) {
        if (y < 1 || y >= 255) return false;
        net.minecraft.block.BlockRegistry registry = w.blocks().registry();
        int below = w.get(x, y - 1, z), here = w.get(x, y, z), above = w.get(x, y + 1, z);
        net.minecraft.block.Block belowBlock = registry.byId(GenBlocks.id(below));
        if (belowBlock == null || !net.minecraft.block.Block.hasSolidTop(net.minecraft.block.BlockState.of(belowBlock, GenBlocks.meta(below))))
            return false;
        String name = belowBlock.name().path();
        if (name.equals("bedrock") || name.equals("barrier")) return false;
        net.minecraft.block.Block hereBlock = registry.byId(GenBlocks.id(here)), aboveBlock = registry.byId(GenBlocks.id(above));
        return hereBlock != null && !hereBlock.isNormalCube() && !w.blocks().isLiquid(here)
            && (aboveBlock == null || !aboveBlock.isNormalCube());
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

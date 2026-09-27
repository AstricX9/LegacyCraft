package net.minecraft.world.gen;

import net.minecraft.world.biome.Biome;

import java.util.Random;

/**
 * Decorates one chunk for its biome (vanilla BiomeDecorator plus each biome
 * class's decorate override): ores, sand/clay/gravel disks, trees, huge
 * mushrooms, flowers, grass, dead bushes, lily pads, mushrooms, sugar cane,
 * pumpkins, cacti and springs, then the biome's extras (double plants,
 * boulders, ice spikes, melons and vines, emeralds...).
 *
 * <p>The counts and tree choices are a table keyed by biome ID rather than
 * data-file fields: vanilla spreads them over constructors, mutated-biome
 * wrappers (which keep the base biome's counts and trees but drop its extras
 * and its grass/flower choice) and per-chunk overrides. HYPOTHESIS, recalled.
 */
public final class BiomeDecorator {
    private final GenBlocks b;
    private final Trees.Tree oak, bigOak, birch, tallBirch, pine, spruce, swamp, acacia, darkOak, megaPine, megaSpruce;
    private final boolean mesaGold = true;

    public BiomeDecorator(GenBlocks blocks) {
        this.b = blocks;
        oak = new Trees.Oak(blocks);
        bigOak = new Trees.BigOak();
        birch = new Trees.Birch(false);
        tallBirch = new Trees.Birch(true);
        pine = new Trees.Pine();
        spruce = new Trees.Spruce();
        swamp = new Trees.Swamp();
        acacia = new Trees.Acacia();
        darkOak = new Trees.DarkOak();
        megaPine = new Trees.MegaPine(blocks, false);
        megaSpruce = new Trees.MegaPine(blocks, true);
    }

    /** Per-chunk decoration counts (BiomeDecorator's fields). */
    private static final class Counts {
        int trees, flowers = 2, grass = 1, deadBush, mushrooms, reeds, cacti, sand = 1, sandDisks = 3, clay = 1,
            bigMushrooms, waterlily;
    }

    private Counts counts(int id) {
        Counts c = new Counts();
        switch (id) {
            case 1, 129 -> { c.trees = -999; c.flowers = 4; c.grass = 10; }
            case 2, 17, 130 -> { c.trees = -999; c.deadBush = 2; c.reeds = 50; c.cacti = 10; }
            case 20, 34 -> c.trees = 3;
            case 4, 18, 27, 28, 155, 156 -> { c.trees = 10; c.grass = 2; }
            case 132 -> { c.trees = 6; c.flowers = 100; c.grass = 1; }
            case 29, 157 -> { c.trees = -999; c.grass = 2; }
            case 5, 19, 30, 31, 133, 158 -> { c.trees = 10; c.grass = 1; c.mushrooms = 1; }
            case 32, 33, 160, 161 -> { c.trees = 10; c.grass = 7; c.deadBush = 1; c.mushrooms = 3; }
            case 6, 134 -> {
                c.trees = 2; c.flowers = 1; c.deadBush = 1; c.mushrooms = 8; c.reeds = 10; c.clay = 1;
                c.waterlily = 4; c.sandDisks = 0; c.sand = 0; c.grass = 5;
            }
            case 14, 15 -> { c.trees = -100; c.flowers = -100; c.grass = -100; c.mushrooms = 1; c.bigMushrooms = 1; }
            case 16, 26, 25 -> { c.trees = -999; c.deadBush = 0; c.reeds = 0; c.cacti = 0; }
            case 21, 22, 149 -> { c.trees = 50; c.grass = 25; c.flowers = 4; }
            case 23, 151 -> { c.trees = 2; c.grass = 25; c.flowers = 4; }
            case 35, 36 -> { c.trees = 1; c.flowers = 4; c.grass = 20; }
            case 163, 164 -> { c.trees = 2; c.flowers = 2; c.grass = 5; }
            case 37, 39, 165, 167 -> { c.trees = -999; c.deadBush = 20; c.reeds = 3; c.cacti = 5; c.flowers = 0; }
            case 38, 166 -> { c.trees = 5; c.deadBush = 20; c.reeds = 3; c.cacti = 5; c.flowers = 0; }
            default -> { }
        }
        return c;
    }

    private static boolean isMesa(int id) { return id >= 37 && id <= 39 || id >= 165 && id <= 167; }

    /** Decorates the chunk whose corner is (x, z); decoration lands in the 16x16 area offset by +8. */
    public void decorate(GenWorld w, Random rand, Biome biome, int x, int z) {
        int id = biome.id();
        Counts c = counts(id);
        // Biome extras that come before the shared decoration.
        switch (id) {
            case 1, 129 -> plainsExtras(w, rand, id, x, z, c);
            case 4, 18, 27, 28, 29, 132, 157 -> forestExtras(w, rand, id, x, z);
            case 5, 19, 30, 31, 32, 33, 160 -> taigaExtras(w, rand, id, x, z);
            case 140 -> iceSpikeExtras(w, rand, x, z);
            case 35, 36 -> {
                for (int i = 0; i < 7; i++) {
                    int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
                    int dy = rand.nextInt(w.height(x + dx, z + dz) + 32);
                    Features.doublePlant(w, rand, x + dx, dy, z + dz, 2);
                }
            }
            default -> { }
        }
        decorateShared(w, rand, biome, id, x, z, c);
        // Extras after.
        switch (id) {
            case 2, 17 -> {
                if (rand.nextInt(1000) == 0) {
                    int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
                    Features.desertWell(w, x + dx, w.height(x + dx, z + dz) + 1, z + dz);
                }
            }
            case 3, 20, 34, 131, 162 -> {
                int emeralds = 3 + rand.nextInt(6);
                for (int i = 0; i < emeralds; i++) {
                    int ex = x + rand.nextInt(16), ey = rand.nextInt(28) + 4, ez = z + rand.nextInt(16);
                    if ((w.get(ex, ey, ez) >>> 4) == b.stoneId) w.set(ex, ey, ez, b.EMERALD_ORE);
                }
                for (int i = 0; i < 7; i++) {
                    int sx = rand.nextInt(16), sy = rand.nextInt(64), sz = rand.nextInt(16);
                    Features.minable(w, rand, x + sx, sy, z + sz, b.SILVERFISH_STONE, 9, b.stoneId);
                }
            }
            case 21, 22, 23 -> {
                int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
                int dy = rand.nextInt(w.height(x + dx, z + dz) * 2);
                Features.melons(w, rand, x + dx, dy, z + dz);
                for (int i = 0; i < 50; i++) {
                    int vx = rand.nextInt(16) + 8, vz = rand.nextInt(16) + 8;
                    Features.vines(w, rand, x + vx, 128, z + vz);
                }
            }
            default -> { }
        }
    }

    private void decorateShared(GenWorld w, Random rand, Biome biome, int id, int x, int z, Counts c) {
        generateOres(w, rand, x, z, isMesa(id) && mesaGold);
        for (int i = 0; i < c.sandDisks; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            Features.sandDisk(w, rand, x + dx, topSolidOrLiquid(w, x + dx, z + dz), z + dz, b.SAND, 7);
        }
        for (int i = 0; i < c.clay; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            Features.clayDisk(w, rand, x + dx, topSolidOrLiquid(w, x + dx, z + dz), z + dz, 4);
        }
        for (int i = 0; i < c.sand; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            Features.sandDisk(w, rand, x + dx, topSolidOrLiquid(w, x + dx, z + dz), z + dz, b.GRAVEL, 6);
        }
        int trees = c.trees;
        if (rand.nextInt(10) == 0) trees++;
        for (int i = 0; i < trees; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            Trees.Tree tree = pickTree(id, rand);
            tree.prepare();
            int tx = x + dx, tz = z + dz, ty = w.height(tx, tz);
            if (tree.generate(w, rand, tx, ty, tz)) tree.afterGrow(w, rand, tx, ty, tz);
        }
        for (int i = 0; i < c.bigMushrooms; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            Features.bigMushroom(w, rand, x + dx, w.height(x + dx, z + dz), z + dz, -1);
        }
        for (int i = 0; i < c.flowers; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            int range = w.height(x + dx, z + dz) + 32;
            if (range > 0) {
                int dy = rand.nextInt(range);
                int flower = pickFlower(id, rand, x + dx, dy, z + dz);
                Features.flowers(w, rand, x + dx, dy, z + dz, flower);
            }
        }
        for (int i = 0; i < c.grass; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            int range = w.height(x + dx, z + dz) * 2;
            if (range > 0) {
                int dy = rand.nextInt(range);
                int grass = pickGrass(id, rand);
                Features.tallGrass(w, rand, x + dx, dy, z + dz, grass);
            }
        }
        for (int i = 0; i < c.deadBush; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            int range = w.height(x + dx, z + dz) * 2;
            if (range > 0) Features.deadBush(w, rand, x + dx, rand.nextInt(range), z + dz);
        }
        for (int i = 0; i < c.waterlily; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            int range = w.height(x + dx, z + dz) * 2;
            if (range > 0) {
                int ly = rand.nextInt(range);
                while (ly > 0 && Features.isAir(w, x + dx, ly - 1, z + dz)) ly--;
                Features.waterlily(w, rand, x + dx, ly, z + dz);
            }
        }
        for (int i = 0; i < c.mushrooms; i++) {
            if (rand.nextInt(4) == 0) {
                int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
                Features.mushroom(w, rand, x + dx, w.height(x + dx, z + dz), z + dz, b.BROWN_MUSHROOM);
            }
            if (rand.nextInt(8) == 0) {
                int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
                int range = w.height(x + dx, z + dz) * 2;
                if (range > 0) Features.mushroom(w, rand, x + dx, rand.nextInt(range), z + dz, b.RED_MUSHROOM);
            }
        }
        if (rand.nextInt(4) == 0) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            int range = w.height(x + dx, z + dz) * 2;
            if (range > 0) Features.mushroom(w, rand, x + dx, rand.nextInt(range), z + dz, b.BROWN_MUSHROOM);
        }
        if (rand.nextInt(8) == 0) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            int range = w.height(x + dx, z + dz) * 2;
            if (range > 0) Features.mushroom(w, rand, x + dx, rand.nextInt(range), z + dz, b.RED_MUSHROOM);
        }
        for (int i = 0; i < c.reeds + 10; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            int range = w.height(x + dx, z + dz) * 2;
            if (range > 0) Features.reeds(w, rand, x + dx, rand.nextInt(range), z + dz);
        }
        if (rand.nextInt(32) == 0) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            int range = w.height(x + dx, z + dz) * 2;
            if (range > 0) Features.pumpkins(w, rand, x + dx, rand.nextInt(range), z + dz);
        }
        for (int i = 0; i < c.cacti; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            int range = w.height(x + dx, z + dz) * 2;
            if (range > 0) Features.cactus(w, rand, x + dx, rand.nextInt(range), z + dz);
        }
        for (int i = 0; i < 50; i++) {
            int sx = rand.nextInt(16) + 8, sy = rand.nextInt(rand.nextInt(248) + 8), sz = rand.nextInt(16) + 8;
            Features.spring(w, x + sx, sy, z + sz, b.WATER);
        }
        for (int i = 0; i < 20; i++) {
            int sx = rand.nextInt(16) + 8, sy = rand.nextInt(rand.nextInt(rand.nextInt(240) + 8) + 8), sz = rand.nextInt(16) + 8;
            Features.spring(w, x + sx, sy, z + sz, b.LAVA);
        }
    }

    private void generateOres(GenWorld w, Random rand, int x, int z, boolean mesaGold) {
        ores(w, rand, x, z, 10, b.DIRT, 33, 0, 256);
        ores(w, rand, x, z, 8, b.GRAVEL, 33, 0, 256);
        ores(w, rand, x, z, 10, GenBlocks.withMeta(b.STONE, 3), 33, 0, 80); // diorite
        ores(w, rand, x, z, 10, GenBlocks.withMeta(b.STONE, 1), 33, 0, 80); // granite
        ores(w, rand, x, z, 10, GenBlocks.withMeta(b.STONE, 5), 33, 0, 80); // andesite
        ores(w, rand, x, z, 20, b.COAL_ORE, 17, 0, 128);
        ores(w, rand, x, z, 20, b.IRON_ORE, 9, 0, 64);
        ores(w, rand, x, z, 2, b.GOLD_ORE, 9, 0, 32);
        ores(w, rand, x, z, 8, b.REDSTONE_ORE, 8, 0, 16);
        ores(w, rand, x, z, 1, b.DIAMOND_ORE, 8, 0, 16);
        for (int i = 0; i < 1; i++) { // lapis: centred on y=16, spread 16
            int ox = rand.nextInt(16), oy = rand.nextInt(16) + rand.nextInt(16) + 16 - 16, oz = rand.nextInt(16);
            Features.minable(w, rand, x + ox, oy, z + oz, b.LAPIS_ORE, 7, b.stoneId);
        }
        if (mesaGold) ores(w, rand, x, z, 20, b.GOLD_ORE, 9, 32, 80);
    }

    private void ores(GenWorld w, Random rand, int x, int z, int count, int ore, int size, int minY, int maxY) {
        for (int i = 0; i < count; i++) {
            int ox = rand.nextInt(16), oy = rand.nextInt(maxY - minY) + minY, oz = rand.nextInt(16);
            Features.minable(w, rand, x + ox, oy, z + oz, ore, size, b.stoneId);
        }
    }

    /** Vanilla getTopSolidOrLiquidBlock: the position above the highest block that blocks movement, leaves excepted. */
    static int topSolidOrLiquid(GenWorld w, int x, int z) {
        GenBlocks b = w.blocks();
        int y = w.height(x, z) + 1;
        for (; y >= 0; y--) {
            int below = w.get(x, y - 1, z);
            if (b.blocksMovement(below) && !b.isLeaves(below)) break;
        }
        return y;
    }

    // ---- Per-biome choices -----------------------------------------------------------------------

    private Trees.Tree defaultTree(Random rand) { return rand.nextInt(10) == 0 ? bigOak : oak; }

    private Trees.Tree pickTree(int id, Random rand) {
        switch (id) {
            case 6, 134: return swamp;
            case 37, 38, 39, 165, 166, 167: return oak;
            case 4, 18, 132: return rand.nextInt(5) != 0 ? defaultTree(rand) : birch;
            case 27, 28: return birch;
            case 29, 157:
                if (rand.nextInt(3) > 0) return darkOak;
                return rand.nextInt(5) != 0 ? defaultTree(rand) : birch;
            case 155, 156: return rand.nextBoolean() ? tallBirch : birch;
            case 5, 19, 30, 31, 133, 158: return rand.nextInt(3) == 0 ? pine : spruce;
            case 32, 33, 161:
                if (rand.nextInt(3) == 0) return rand.nextInt(13) != 0 ? megaPine : megaSpruce;
                return rand.nextInt(3) == 0 ? pine : spruce;
            case 160:
                if (rand.nextInt(3) == 0) return megaSpruce;
                return rand.nextInt(3) == 0 ? pine : spruce;
            case 3, 20, 34, 131, 162: return rand.nextInt(3) > 0 ? spruce : defaultTree(rand);
            case 12, 13, 140: return spruce;
            case 21, 22, 149, 23, 151: return jungleTree(rand, id == 23 || id == 151);
            case 35, 36, 163, 164: return rand.nextInt(5) > 0 ? acacia : oak;
            default: return defaultTree(rand);
        }
    }

    private Trees.Tree jungleTree(Random rand, boolean edge) {
        if (rand.nextInt(10) == 0) return bigOak;
        if (rand.nextInt(2) == 0) return new Trees.Bush(Trees.log(b, Trees.JUNGLE), Trees.leaves(b, Trees.OAK));
        if (!edge && rand.nextInt(3) == 0) return new Trees.MegaJungle(b);
        return new Trees.Oak(4 + rand.nextInt(7), Trees.log(b, Trees.JUNGLE), Trees.leaves(b, Trees.JUNGLE), true);
    }

    /** Tall grass type: shrub 0, grass 1, fern 2. */
    private int pickGrass(int id, Random rand) {
        int type = switch (id) {
            case 5, 19, 30, 31, 32, 33, 160 -> rand.nextInt(5) > 0 ? 2 : 1;
            case 21, 22, 23 -> rand.nextInt(4) == 0 ? 2 : 1;
            default -> 1;
        };
        return GenBlocks.withMeta(b.TALLGRASS, type);
    }

    /** Flower state: dandelion (yellow_flower 0) or red_flower poppy 0 ... oxeye daisy 8. */
    private int pickFlower(int id, Random rand, int x, int y, int z) {
        switch (id) {
            case 6: return GenBlocks.withMeta(b.RED_FLOWER, 1); // blue orchid
            case 1, 129: {
                double noise = SurfaceBuilder.GRASS_COLOR_NOISE.getValue(x / 200.0, z / 200.0);
                if (noise < -0.8) {
                    int[] tulips = {5, 4, 7, 6}; // orange, red, pink, white
                    return GenBlocks.withMeta(b.RED_FLOWER, tulips[rand.nextInt(4)]);
                }
                if (rand.nextInt(3) > 0) {
                    int pick = rand.nextInt(3);
                    return GenBlocks.withMeta(b.RED_FLOWER, pick == 0 ? 0 : pick == 1 ? 3 : 8);
                }
                return b.YELLOW_FLOWER;
            }
            case 132: {
                double value = Math.max(0.0, Math.min(0.9999,
                    (1.0 + SurfaceBuilder.GRASS_COLOR_NOISE.getValue(x / 48.0, z / 48.0)) / 2.0));
                // All ten flower types in order; blue orchid (index 2) is swapped for a poppy.
                int index = (int) (value * 10);
                if (index == 0) return b.YELLOW_FLOWER;
                int meta = index - 1;
                return GenBlocks.withMeta(b.RED_FLOWER, meta == 1 ? 0 : meta);
            }
            default:
                return rand.nextInt(3) > 0 ? b.YELLOW_FLOWER : b.RED_FLOWER;
        }
    }

    // ---- Biome extras ----------------------------------------------------------------------------

    private void plainsExtras(GenWorld w, Random rand, int id, int x, int z, Counts c) {
        double noise = SurfaceBuilder.GRASS_COLOR_NOISE.getValue((x + 8) / 200.0, (z + 8) / 200.0);
        if (noise < -0.8) {
            c.flowers = 15;
            c.grass = 5;
        } else {
            c.flowers = 4;
            c.grass = 10;
            for (int i = 0; i < 7; i++) {
                int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
                int dy = rand.nextInt(w.height(x + dx, z + dz) + 32);
                Features.doublePlant(w, rand, x + dx, dy, z + dz, 2);
            }
        }
        if (id == 129) {
            for (int i = 0; i < 10; i++) {
                int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
                int dy = rand.nextInt(w.height(x + dx, z + dz) + 32);
                Features.doublePlant(w, rand, x + dx, dy, z + dz, 0);
            }
        }
    }

    private void forestExtras(GenWorld w, Random rand, int id, int x, int z) {
        boolean roofed = id == 29 || id == 157;
        if (roofed) {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    int dx = i * 4 + 1 + 8 + rand.nextInt(3), dz = j * 4 + 1 + 8 + rand.nextInt(3);
                    int tx = x + dx, tz = z + dz, ty = w.height(tx, tz);
                    if (rand.nextInt(20) == 0) {
                        Features.bigMushroom(w, rand, tx, ty, tz, -1);
                    } else {
                        Trees.Tree tree = pickTree(29, rand);
                        tree.prepare();
                        if (tree.generate(w, rand, tx, ty, tz)) tree.afterGrow(w, rand, tx, ty, tz);
                    }
                }
            }
        }
        int attempts = rand.nextInt(5) - 3;
        if (id == 132) attempts += 2;
        for (int i = 0; i < attempts; i++) {
            int pick = rand.nextInt(3);
            int type = pick == 0 ? 1 : pick == 1 ? 4 : 5; // lilac, rose bush, peony
            for (int k = 0; k < 5; k++) {
                int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
                int dy = rand.nextInt(w.height(x + dx, z + dz) + 32);
                if (Features.doublePlant(w, rand, x + dx, dy, z + dz, type)) break;
            }
        }
    }

    private void taigaExtras(GenWorld w, Random rand, int id, int x, int z) {
        if (id == 32 || id == 33 || id == 160) {
            int boulders = rand.nextInt(3);
            for (int i = 0; i < boulders; i++) {
                int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
                Features.boulder(w, rand, x + dx, w.height(x + dx, z + dz), z + dz, b.MOSSY_COBBLESTONE);
            }
        }
        for (int i = 0; i < 7; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            int dy = rand.nextInt(w.height(x + dx, z + dz) + 32);
            Features.doublePlant(w, rand, x + dx, dy, z + dz, 3);
        }
    }

    private void iceSpikeExtras(GenWorld w, Random rand, int x, int z) {
        for (int i = 0; i < 3; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            Features.iceSpike(w, rand, x + dx, w.height(x + dx, z + dz), z + dz);
        }
        for (int i = 0; i < 2; i++) {
            int dx = rand.nextInt(16) + 8, dz = rand.nextInt(16) + 8;
            Features.icePath(w, rand, x + dx, w.height(x + dx, z + dz), z + dz, 4);
        }
    }
}

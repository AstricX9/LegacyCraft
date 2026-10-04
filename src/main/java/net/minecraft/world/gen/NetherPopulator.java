package net.minecraft.world.gen;

import java.util.Random;

/**
 * Nether population (1.8.9 ChunkProviderHell.populate, HYPOTHESIS:
 * recalled): fortress pieces first, then 8 lava springs in netherrack
 * walls, patches of fire on netherrack, glowstone clusters hanging from the
 * ceiling (two kinds, 10 always plus a few more), brown and red mushrooms,
 * 16 quartz veins and 16 hidden lava pockets inside the rock. Vanilla
 * continues the terrain generator's Random here, so its decoration depends
 * on generation order; LegacyCraft seeds it per chunk like the overworld.
 */
public final class NetherPopulator {
    private final GenBlocks b;
    private final long seed;
    private final Random rand = new Random();

    public NetherPopulator(GenBlocks blocks, long seed) {
        this.b = blocks;
        this.seed = seed;
    }

    public void populate(GenWorld w, NetherGenerator generator, int chunkX, int chunkZ) {
        int x = chunkX * 16, z = chunkZ * 16;
        rand.setSeed(seed);
        long xMultiplier = rand.nextLong() / 2L * 2L + 1L;
        long zMultiplier = rand.nextLong() / 2L * 2L + 1L;
        rand.setSeed((long) chunkX * xMultiplier + (long) chunkZ * zMultiplier ^ seed);
        generator.fortresses().generate(w, chunkX, chunkZ);
        for (int i = 0; i < 8; i++) hellLava(w, x + rand.nextInt(16) + 8, rand.nextInt(120) + 4, z + rand.nextInt(16) + 8, false);
        int fires = rand.nextInt(rand.nextInt(10) + 1) + 1;
        for (int i = 0; i < fires; i++) fire(w, x + rand.nextInt(16) + 8, rand.nextInt(120) + 4, z + rand.nextInt(16) + 8);
        int glow = rand.nextInt(rand.nextInt(10) + 1);
        for (int i = 0; i < glow; i++) glowstone(w, x + rand.nextInt(16) + 8, rand.nextInt(120) + 4, z + rand.nextInt(16) + 8);
        for (int i = 0; i < 10; i++) glowstone(w, x + rand.nextInt(16) + 8, rand.nextInt(128), z + rand.nextInt(16) + 8);
        if (rand.nextBoolean()) mushrooms(w, x + rand.nextInt(16) + 8, rand.nextInt(128), z + rand.nextInt(16) + 8, b.BROWN_MUSHROOM);
        if (rand.nextBoolean()) mushrooms(w, x + rand.nextInt(16) + 8, rand.nextInt(128), z + rand.nextInt(16) + 8, b.RED_MUSHROOM);
        for (int i = 0; i < 16; i++)
            Features.minable(w, rand, x + rand.nextInt(16), rand.nextInt(108) + 10, z + rand.nextInt(16), b.QUARTZ_ORE, 14, b.netherrackId);
        for (int i = 0; i < 16; i++) hellLava(w, x + rand.nextInt(16), rand.nextInt(108) + 10, z + rand.nextInt(16), true);
    }

    private int id(GenWorld w, int x, int y, int z) { return y < 0 || y > 255 ? 0 : GenBlocks.id(w.get(x, y, z)); }
    private boolean isAir(GenWorld w, int x, int y, int z) { return y < 0 || y > 255 || b.isAir(w.get(x, y, z)); }

    /**
     * WorldGenHellLava: under netherrack, in air or netherrack, a flowing
     * lava source where 4 of its 5 sides (not the top) are netherrack and one
     * is open, or (hidden pockets) all 5 are rock; it starts flowing at once.
     */
    private void hellLava(GenWorld w, int x, int y, int z, boolean insideRock) {
        if (id(w, x, y + 1, z) != b.netherrackId) return;
        if (!isAir(w, x, y, z) && id(w, x, y, z) != b.netherrackId) return;
        int rock = 0, open = 0;
        int[][] sides = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, -1, 0}};
        for (int[] s : sides) {
            if (id(w, x + s[0], y + s[1], z + s[2]) == b.netherrackId) rock++;
            if (isAir(w, x + s[0], y + s[1], z + s[2])) open++;
        }
        if (!insideRock && rock == 4 && open == 1 || rock == 5) {
            w.set(x, y, z, b.FLOWING_LAVA);
            w.scheduleBlockTick(x, y, z);
        }
    }

    /** WorldGenFire: 64 tries within 8 across and 4 up or down, fire in air on netherrack. */
    private void fire(GenWorld w, int x, int y, int z) {
        for (int i = 0; i < 64; i++) {
            int px = x + rand.nextInt(8) - rand.nextInt(8), py = y + rand.nextInt(4) - rand.nextInt(4), pz = z + rand.nextInt(8) - rand.nextInt(8);
            if (isAir(w, px, py, pz) && id(w, px, py - 1, pz) == b.netherrackId) w.set(px, py, pz, b.FIRE);
        }
    }

    /**
     * WorldGenGlowStone1/2: from air under netherrack, 1500 tries to grow
     * glowstone down and around, only where exactly one neighbour is
     * glowstone already.
     */
    private void glowstone(GenWorld w, int x, int y, int z) {
        if (!isAir(w, x, y, z) || id(w, x, y + 1, z) != b.netherrackId) return;
        w.set(x, y, z, b.GLOWSTONE);
        int[][] faces = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
        for (int i = 0; i < 1500; i++) {
            int px = x + rand.nextInt(8) - rand.nextInt(8), py = y - rand.nextInt(12), pz = z + rand.nextInt(8) - rand.nextInt(8);
            if (!isAir(w, px, py, pz)) continue;
            int count = 0;
            for (int[] f : faces) {
                if (id(w, px + f[0], py + f[1], pz + f[2]) == b.glowstoneId) count++;
                if (count > 1) break;
            }
            if (count == 1) w.set(px, py, pz, b.GLOWSTONE);
        }
    }

    /** WorldGenBush for mushrooms: 64 tries, in air on a full block where the light is below 13. */
    private void mushrooms(GenWorld w, int x, int y, int z, int mushroom) {
        for (int i = 0; i < 64; i++) {
            int px = x + rand.nextInt(8) - rand.nextInt(8), py = y + rand.nextInt(4) - rand.nextInt(4), pz = z + rand.nextInt(8) - rand.nextInt(8);
            if (py < 1 || py >= 255 || !isAir(w, px, py, pz)) continue;
            int below = w.get(px, py - 1, pz);
            boolean stays = GenBlocks.id(below) == b.myceliumId || below == b.PODZOL
                || w.light(px, py, pz) < 13 && b.isFullBlock(below);
            if (stays) w.set(px, py, pz, mushroom);
        }
    }
}

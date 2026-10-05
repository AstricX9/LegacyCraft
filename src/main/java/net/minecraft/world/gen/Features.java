package net.minecraft.world.gen;

import net.minecraft.util.MathHelper;

import java.util.Random;

/**
 * The small population features (vanilla's WorldGen* classes other than
 * trees): ore veins, sand/clay disks, lakes, dungeons, springs, plants,
 * ice spikes, boulders, desert wells and huge mushrooms.
 *
 * <p>HYPOTHESIS: each is recalled from 1.8.9 with its Random draws in the
 * original order, so that one feature drawing differently would not shift
 * everything after it. Where a feature needs something LegacyCraft does not
 * simulate (chest loot, spawner entities, flowing liquids), the block is
 * placed and the rest is left out (deviations.yml).
 */
public final class Features {
    private Features() {}

    // Horizontal directions in vanilla's EnumFacing.Plane.HORIZONTAL order: north, east, south, west.
    static final int[] HORIZONTAL_X = {0, 1, 0, -1};
    static final int[] HORIZONTAL_Z = {-1, 0, 1, 0};

    private static int id(GenWorld w, int x, int y, int z) { return w.get(x, y, z) >>> 4; }

    /** A world position is air (positions outside 0-255 are air too, as in vanilla). */
    static boolean isAir(GenWorld w, int x, int y, int z) {
        return y < 0 || y > 255 || w.blocks().isAir(w.get(x, y, z));
    }

    // ---- Ore veins (WorldGenMinable) -------------------------------------------------------------

    /** An ellipsoidal vein of {@code size} blocks replacing {@code target} (stone for every ore). */
    public static void minable(GenWorld w, Random rand, int x, int y, int z, int ore, int size, int targetId) {
        float angle = rand.nextFloat() * (float) Math.PI;
        double x0 = (float) (x + 8) + MathHelper.sin(angle) * size / 8.0F;
        double x1 = (float) (x + 8) - MathHelper.sin(angle) * size / 8.0F;
        double z0 = (float) (z + 8) + MathHelper.cos(angle) * size / 8.0F;
        double z1 = (float) (z + 8) - MathHelper.cos(angle) * size / 8.0F;
        double y0 = y + rand.nextInt(3) - 2;
        double y1 = y + rand.nextInt(3) - 2;
        for (int i = 0; i < size; i++) {
            float t = (float) i / (float) size;
            double cx = x0 + (x1 - x0) * t;
            double cy = y0 + (y1 - y0) * t;
            double cz = z0 + (z1 - z0) * t;
            double spread = rand.nextDouble() * size / 16.0;
            double width = (MathHelper.sin((float) Math.PI * t) + 1.0F) * spread + 1.0;
            double height = (MathHelper.sin((float) Math.PI * t) + 1.0F) * spread + 1.0;
            int minX = MathHelper.floor(cx - width / 2.0), maxX = MathHelper.floor(cx + width / 2.0);
            int minY = MathHelper.floor(cy - height / 2.0), maxY = MathHelper.floor(cy + height / 2.0);
            int minZ = MathHelper.floor(cz - width / 2.0), maxZ = MathHelper.floor(cz + width / 2.0);
            for (int bx = minX; bx <= maxX; bx++) {
                double dx = (bx + 0.5 - cx) / (width / 2.0);
                if (dx * dx >= 1.0) continue;
                for (int by = minY; by <= maxY; by++) {
                    double dy = (by + 0.5 - cy) / (height / 2.0);
                    if (dx * dx + dy * dy >= 1.0) continue;
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        double dz = (bz + 0.5 - cz) / (width / 2.0);
                        if (dx * dx + dy * dy + dz * dz < 1.0 && by >= 0 && by < 256 && id(w, bx, by, bz) == targetId)
                            w.set(bx, by, bz, ore);
                    }
                }
            }
        }
    }

    // ---- Disks (WorldGenSand, WorldGenClay) ------------------------------------------------------

    /** A disk of sand or gravel on a sea or river bed, replacing dirt and grass. */
    public static boolean sandDisk(GenWorld w, Random rand, int x, int y, int z, int block, int radius) {
        GenBlocks b = w.blocks();
        if (!b.isWater(w.get(x, y, z))) return false;
        int r = rand.nextInt(radius - 2) + 2;
        for (int bx = x - r; bx <= x + r; bx++) {
            for (int bz = z - r; bz <= z + r; bz++) {
                int dx = bx - x, dz = bz - z;
                if (dx * dx + dz * dz > r * r) continue;
                for (int by = y - 2; by <= y + 2; by++) {
                    int id = id(w, bx, by, bz);
                    if (id == b.dirtId || id == b.grassId) w.set(bx, by, bz, block);
                }
            }
        }
        return true;
    }

    public static boolean clayDisk(GenWorld w, Random rand, int x, int y, int z, int radius) {
        GenBlocks b = w.blocks();
        if (!b.isWater(w.get(x, y, z))) return false;
        int r = rand.nextInt(radius - 2) + 2;
        for (int bx = x - r; bx <= x + r; bx++) {
            for (int bz = z - r; bz <= z + r; bz++) {
                int dx = bx - x, dz = bz - z;
                if (dx * dx + dz * dz > r * r) continue;
                for (int by = y - 1; by <= y + 1; by++) {
                    int id = id(w, bx, by, bz);
                    if (id == b.dirtId || id == b.clayId) w.set(bx, by, bz, b.CLAY);
                }
            }
        }
        return true;
    }

    // ---- Lakes (WorldGenLakes) -------------------------------------------------------------------

    public static boolean lake(GenWorld w, Random rand, int x, int y, int z, int liquid) {
        GenBlocks b = w.blocks();
        x -= 8;
        z -= 8;
        while (y > 5 && isAir(w, x, y, z)) y--;
        if (y <= 4) return false;
        y -= 4;
        boolean[] shape = new boolean[2048];
        int blobs = rand.nextInt(4) + 4;
        for (int i = 0; i < blobs; i++) {
            double sizeX = rand.nextDouble() * 6.0 + 3.0;
            double sizeY = rand.nextDouble() * 4.0 + 2.0;
            double sizeZ = rand.nextDouble() * 6.0 + 3.0;
            double centreX = rand.nextDouble() * (16.0 - sizeX - 2.0) + 1.0 + sizeX / 2.0;
            double centreY = rand.nextDouble() * (8.0 - sizeY - 4.0) + 2.0 + sizeY / 2.0;
            double centreZ = rand.nextDouble() * (16.0 - sizeZ - 2.0) + 1.0 + sizeZ / 2.0;
            for (int lx = 1; lx < 15; lx++) {
                for (int lz = 1; lz < 15; lz++) {
                    for (int ly = 1; ly < 7; ly++) {
                        double dx = (lx - centreX) / (sizeX / 2.0);
                        double dy = (ly - centreY) / (sizeY / 2.0);
                        double dz = (lz - centreZ) / (sizeZ / 2.0);
                        if (dx * dx + dy * dy + dz * dz < 1.0) shape[(lx * 16 + lz) * 8 + ly] = true;
                    }
                }
            }
        }
        int liquidId = liquid >>> 4;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int ly = 0; ly < 8; ly++) {
                    if (!lakeEdge(shape, lx, lz, ly)) continue;
                    int state = w.get(x + lx, y + ly, z + lz);
                    if (ly >= 4 && b.isLiquid(state)) return false;
                    if (ly < 4 && !b.isSolid(state) && (state >>> 4) != liquidId) return false;
                }
            }
        }
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++)
                for (int ly = 0; ly < 8; ly++)
                    if (shape[(lx * 16 + lz) * 8 + ly]) w.set(x + lx, y + ly, z + lz, ly >= 4 ? b.AIR : liquid);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int ly = 4; ly < 8; ly++) {
                    if (!shape[(lx * 16 + lz) * 8 + ly]) continue;
                    int bx = x + lx, by = y + ly - 1, bz = z + lz;
                    if (id(w, bx, by, bz) == b.dirtId && w.seesSky(bx, by + 1, bz))
                        w.set(bx, by, bz, (w.biome(bx, bz).topBlock().packed() >>> 4) == b.myceliumId ? b.MYCELIUM : b.GRASS);
                }
            }
        }
        if (b.isLava(liquid)) {
            for (int lx = 0; lx < 16; lx++)
                for (int lz = 0; lz < 16; lz++)
                    for (int ly = 0; ly < 8; ly++)
                        if (lakeEdge(shape, lx, lz, ly) && (ly < 4 || rand.nextInt(2) != 0)
                            && b.isSolid(w.get(x + lx, y + ly, z + lz)))
                            w.set(x + lx, y + ly, z + lz, b.STONE);
        }
        if (b.isWater(liquid)) {
            for (int lx = 0; lx < 16; lx++)
                for (int lz = 0; lz < 16; lz++)
                    if (ChunkPopulator.canFreeze(w, x + lx, y + 4, z + lz, false)) w.set(x + lx, y + 4, z + lz, b.ICE);
        }
        return true;
    }

    /** A cell outside the lake that touches it: the lake's wall, which must hold. */
    private static boolean lakeEdge(boolean[] shape, int x, int z, int y) {
        return !shape[(x * 16 + z) * 8 + y]
            && (x < 15 && shape[((x + 1) * 16 + z) * 8 + y] || x > 0 && shape[((x - 1) * 16 + z) * 8 + y]
            || z < 15 && shape[(x * 16 + z + 1) * 8 + y] || z > 0 && shape[(x * 16 + (z - 1)) * 8 + y]
            || y < 7 && shape[(x * 16 + z) * 8 + y + 1] || y > 0 && shape[(x * 16 + z) * 8 + (y - 1)]);
    }

    // ---- Dungeons (WorldGenDungeons) -------------------------------------------------------------

    public static boolean dungeon(GenWorld w, Random rand, int x, int y, int z) {
        GenBlocks b = w.blocks();
        int radiusX = rand.nextInt(2) + 2;
        int minX = -radiusX - 1, maxX = radiusX + 1;
        int radiusZ = rand.nextInt(2) + 2;
        int minZ = -radiusZ - 1, maxZ = radiusZ + 1;
        int openings = 0;
        for (int dx = minX; dx <= maxX; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    boolean solid = b.isSolid(w.get(x + dx, y + dy, z + dz));
                    if (dy == -1 && !solid) return false;
                    if (dy == 4 && !solid) return false;
                    if ((dx == minX || dx == maxX || dz == minZ || dz == maxZ) && dy == 0
                        && isAir(w, x + dx, y, z + dz) && isAir(w, x + dx, y + 1, z + dz)) openings++;
                }
            }
        }
        if (openings < 1 || openings > 5) return false;
        for (int dx = minX; dx <= maxX; dx++) {
            for (int dy = 3; dy >= -1; dy--) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    int bx = x + dx, by = y + dy, bz = z + dz;
                    if (dx != minX && dy != -1 && dz != minZ && dx != maxX && dy != 4 && dz != maxZ) {
                        if (id(w, bx, by, bz) != b.chestId) w.set(bx, by, bz, b.AIR);
                    } else if (by >= 0 && !b.isSolid(w.get(bx, by - 1, bz))) {
                        w.set(bx, by, bz, b.AIR);
                    } else if (b.isSolid(w.get(bx, by, bz)) && id(w, bx, by, bz) != b.chestId) {
                        w.set(bx, by, bz, dy == -1 && rand.nextInt(4) != 0 ? b.MOSSY_COBBLESTONE : b.COBBLESTONE);
                    }
                }
            }
        }
        chests:
        for (int attempt = 0; attempt < 2; attempt++) {
            for (int tries = 0; tries < 3; tries++) {
                int cx = x + rand.nextInt(radiusX * 2 + 1) - radiusX;
                int cz = z + rand.nextInt(radiusZ * 2 + 1) - radiusZ;
                if (!isAir(w, cx, y, cz)) continue;
                int walls = 0;
                for (int side = 0; side < 4; side++)
                    if (b.isSolid(w.get(cx + HORIZONTAL_X[side], y, cz + HORIZONTAL_Z[side]))) walls++;
                if (walls == 1) {
                    // The chest's loot is not generated, so the draws vanilla spends on it are not either.
                    w.set(cx, y, cz, GenBlocks.withMeta(b.CHEST, chestFacing(w, cx, y, cz)));
                    continue chests;
                }
            }
        }
        w.set(x, y, z, b.MOB_SPAWNER);
        rand.nextInt(4); // the spawner's mob (skeleton, zombie x2, spider)
        return true;
    }

    /** BlockChest.correctFacing: face away from the single full block beside it. Metadata N2 S3 W4 E5. */
    private static int chestFacing(GenWorld w, int x, int y, int z) {
        GenBlocks b = w.blocks();
        int[] meta = {2, 5, 3, 4}; // N, E, S, W
        int[] opposite = {2, 3, 0, 1};
        int found = -1;
        for (int side = 0; side < 4; side++) {
            int state = w.get(x + HORIZONTAL_X[side], y, z + HORIZONTAL_Z[side]);
            if ((state >>> 4) == b.chestId) return 2;
            if (b.isFullBlock(state)) {
                if (found != -1) { found = -1; break; }
                found = side;
            }
        }
        if (found != -1) return meta[opposite[found]];
        int facing = 0; // north, the default
        int[] rotateY = {1, 2, 3, 0};
        if (b.isFullBlock(w.get(x + HORIZONTAL_X[facing], y, z + HORIZONTAL_Z[facing]))) facing = opposite[facing];
        if (b.isFullBlock(w.get(x + HORIZONTAL_X[facing], y, z + HORIZONTAL_Z[facing]))) facing = rotateY[facing];
        if (b.isFullBlock(w.get(x + HORIZONTAL_X[facing], y, z + HORIZONTAL_Z[facing]))) facing = opposite[facing];
        return meta[facing];
    }

    // ---- Springs (WorldGenLiquids) ---------------------------------------------------------------

    /**
     * A source in a stone wall with exactly one open side (WorldGenLiquids):
     * placed as flowing liquid and ticked at once (World.forceBlockUpdateTick)
     * so it pours out of the wall as soon as its chunk is in the world.
     */
    public static void spring(GenWorld w, int x, int y, int z, int liquid) {
        GenBlocks b = w.blocks();
        if (id(w, x, y + 1, z) != b.stoneId || id(w, x, y - 1, z) != b.stoneId) return;
        int here = w.get(x, y, z);
        if (!b.isAir(here) && (here >>> 4) != b.stoneId) return;
        int stone = 0, open = 0;
        int[] dx = {-1, 1, 0, 0}, dz = {0, 0, -1, 1};
        for (int i = 0; i < 4; i++) if (id(w, x + dx[i], y, z + dz[i]) == b.stoneId) stone++;
        for (int i = 0; i < 4; i++) if (isAir(w, x + dx[i], y, z + dz[i])) open++;
        if (stone != 3 || open != 1) return;
        w.set(x, y, z, liquid == b.WATER ? b.FLOWING_WATER : liquid == b.LAVA ? b.FLOWING_LAVA : liquid);
        w.scheduleBlockTick(x, y, z);
    }

    // ---- Plants ----------------------------------------------------------------------------------

    /** Flowers, grass and other plants need grass, dirt or farmland under them (BlockBush). */
    static boolean bushCanStay(GenWorld w, int x, int y, int z) {
        GenBlocks b = w.blocks();
        int below = id(w, x, y - 1, z);
        return below == b.grassId || below == b.dirtId || below == b.farmlandId;
    }

    public static void flowers(GenWorld w, Random rand, int x, int y, int z, int flower) {
        for (int i = 0; i < 64; i++) {
            int bx = x + rand.nextInt(8) - rand.nextInt(8);
            int by = y + rand.nextInt(4) - rand.nextInt(4);
            int bz = z + rand.nextInt(8) - rand.nextInt(8);
            if (isAir(w, bx, by, bz) && bushCanStay(w, bx, by, bz)) w.set(bx, by, bz, flower);
        }
    }

    public static void tallGrass(GenWorld w, Random rand, int x, int y, int z, int grass) {
        GenBlocks b = w.blocks();
        while (y > 0) {
            int state = w.get(x, y, z);
            if (!b.isAir(state) && !b.isLeaves(state)) break;
            y--;
        }
        for (int i = 0; i < 128; i++) {
            int bx = x + rand.nextInt(8) - rand.nextInt(8);
            int by = y + rand.nextInt(4) - rand.nextInt(4);
            int bz = z + rand.nextInt(8) - rand.nextInt(8);
            if (isAir(w, bx, by, bz) && bushCanStay(w, bx, by, bz)) w.set(bx, by, bz, grass);
        }
    }

    public static void deadBush(GenWorld w, Random rand, int x, int y, int z) {
        GenBlocks b = w.blocks();
        while (y > 0) {
            int state = w.get(x, y, z);
            if (!b.isAir(state) && !b.isLeaves(state)) break;
            y--;
        }
        for (int i = 0; i < 4; i++) {
            int bx = x + rand.nextInt(8) - rand.nextInt(8);
            int by = y + rand.nextInt(4) - rand.nextInt(4);
            int bz = z + rand.nextInt(8) - rand.nextInt(8);
            if (!isAir(w, bx, by, bz)) continue;
            int below = id(w, bx, by - 1, bz);
            if (below == b.sandId || below == b.hardenedClayId || below == b.stainedClayId || below == b.dirtId)
                w.set(bx, by, bz, b.DEADBUSH);
        }
    }

    public static void waterlily(GenWorld w, Random rand, int x, int y, int z) {
        GenBlocks b = w.blocks();
        for (int i = 0; i < 10; i++) {
            int bx = x + rand.nextInt(8) - rand.nextInt(8);
            int by = y + rand.nextInt(4) - rand.nextInt(4);
            int bz = z + rand.nextInt(8) - rand.nextInt(8);
            if (isAir(w, bx, by, bz) && by > 0 && by < 256 && w.get(bx, by - 1, bz) == b.WATER)
                w.set(bx, by, bz, b.WATERLILY);
        }
    }

    /** Small mushrooms: on mycelium or podzol anywhere, otherwise only in the shade (light below 13). */
    public static void mushroom(GenWorld w, Random rand, int x, int y, int z, int mushroom) {
        for (int i = 0; i < 64; i++) {
            int bx = x + rand.nextInt(8) - rand.nextInt(8);
            int by = y + rand.nextInt(4) - rand.nextInt(4);
            int bz = z + rand.nextInt(8) - rand.nextInt(8);
            if (isAir(w, bx, by, bz) && mushroomCanStay(w, bx, by, bz)) w.set(bx, by, bz, mushroom);
        }
    }

    private static boolean mushroomCanStay(GenWorld w, int x, int y, int z) {
        if (y < 0 || y >= 256) return false;
        GenBlocks b = w.blocks();
        int below = w.get(x, y - 1, z);
        if ((below >>> 4) == b.myceliumId || below == b.PODZOL) return true;
        return w.light(x, y, z) < 13 && b.isFullBlock(below);
    }

    public static void reeds(GenWorld w, Random rand, int x, int y, int z) {
        GenBlocks b = w.blocks();
        for (int i = 0; i < 20; i++) {
            int bx = x + rand.nextInt(4) - rand.nextInt(4);
            int bz = z + rand.nextInt(4) - rand.nextInt(4);
            if (!isAir(w, bx, y, bz)) continue;
            if (!b.isWater(w.get(bx - 1, y - 1, bz)) && !b.isWater(w.get(bx + 1, y - 1, bz))
                && !b.isWater(w.get(bx, y - 1, bz - 1)) && !b.isWater(w.get(bx, y - 1, bz + 1))) continue;
            int height = 2 + rand.nextInt(rand.nextInt(3) + 1);
            // Vanilla checks the bottom position every time, so the whole stalk goes in or none of it.
            for (int k = 0; k < height; k++) if (reedCanStay(w, bx, y, bz)) w.set(bx, y + k, bz, b.REEDS);
        }
    }

    private static boolean reedCanStay(GenWorld w, int x, int y, int z) {
        GenBlocks b = w.blocks();
        int below = id(w, x, y - 1, z);
        if (below == b.reedsId) return true;
        if (below != b.grassId && below != b.dirtId && below != b.sandId) return false;
        for (int side = 0; side < 4; side++)
            if (b.isWater(w.get(x + HORIZONTAL_X[side], y - 1, z + HORIZONTAL_Z[side]))) return true;
        return false;
    }

    public static void pumpkins(GenWorld w, Random rand, int x, int y, int z) {
        GenBlocks b = w.blocks();
        int[] facingMeta = {2, 3, 0, 1}; // N, E, S, W as BlockPumpkin metadata
        for (int i = 0; i < 64; i++) {
            int bx = x + rand.nextInt(8) - rand.nextInt(8);
            int by = y + rand.nextInt(4) - rand.nextInt(4);
            int bz = z + rand.nextInt(8) - rand.nextInt(8);
            if (isAir(w, bx, by, bz) && id(w, bx, by - 1, bz) == b.grassId && by < 256)
                w.set(bx, by, bz, GenBlocks.withMeta(b.PUMPKIN, facingMeta[rand.nextInt(4)]));
        }
    }

    public static void melons(GenWorld w, Random rand, int x, int y, int z) {
        GenBlocks b = w.blocks();
        for (int i = 0; i < 64; i++) {
            int bx = x + rand.nextInt(8) - rand.nextInt(8);
            int by = y + rand.nextInt(4) - rand.nextInt(4);
            int bz = z + rand.nextInt(8) - rand.nextInt(8);
            if (by >= 0 && by < 256 && b.isReplaceable(w.get(bx, by, bz)) && id(w, bx, by - 1, bz) == b.grassId)
                w.set(bx, by, bz, b.MELON);
        }
    }

    public static void cactus(GenWorld w, Random rand, int x, int y, int z) {
        GenBlocks b = w.blocks();
        for (int i = 0; i < 10; i++) {
            int bx = x + rand.nextInt(8) - rand.nextInt(8);
            int by = y + rand.nextInt(4) - rand.nextInt(4);
            int bz = z + rand.nextInt(8) - rand.nextInt(8);
            if (!isAir(w, bx, by, bz)) continue;
            int height = 1 + rand.nextInt(rand.nextInt(3) + 1);
            for (int k = 0; k < height; k++) if (cactusCanStay(w, bx, by, bz)) w.set(bx, by + k, bz, b.CACTUS);
        }
    }

    private static boolean cactusCanStay(GenWorld w, int x, int y, int z) {
        GenBlocks b = w.blocks();
        for (int side = 0; side < 4; side++)
            if (b.isSolid(w.get(x + HORIZONTAL_X[side], y, z + HORIZONTAL_Z[side]))) return false;
        int below = id(w, x, y - 1, z);
        return below == b.cactusId || below == b.sandId;
    }

    /** Vine metadata for the side its support is on: south 1, west 2, north 4, east 8. */
    static final int[] VINE_META = {4, 8, 1, 2}; // N, E, S, W

    /** Jungle vines: climb from y up to 128, hanging on whatever full block is beside each air cell. */
    public static void vines(GenWorld w, Random rand, int x, int y, int z) {
        GenBlocks b = w.blocks();
        for (; y < 128; y++) {
            if (isAir(w, x, y, z)) {
                for (int side = 0; side < 4; side++) {
                    int support = w.get(x + HORIZONTAL_X[side], y, z + HORIZONTAL_Z[side]);
                    if (b.isFullCube(support) && b.blocksMovement(support)) {
                        w.set(x, y, z, GenBlocks.withMeta(b.VINE, VINE_META[side]));
                        break;
                    }
                }
            } else {
                x += rand.nextInt(4) - rand.nextInt(4);
                z += rand.nextInt(4) - rand.nextInt(4);
            }
        }
    }

    /** Double plant types: sunflower 0, lilac 1, double grass 2, large fern 3, rose bush 4, peony 5. */
    public static boolean doublePlant(GenWorld w, Random rand, int x, int y, int z, int type) {
        GenBlocks b = w.blocks();
        boolean placed = false;
        for (int i = 0; i < 64; i++) {
            int bx = x + rand.nextInt(8) - rand.nextInt(8);
            int by = y + rand.nextInt(4) - rand.nextInt(4);
            int bz = z + rand.nextInt(8) - rand.nextInt(8);
            if (isAir(w, bx, by, bz) && b.isReplaceable(w.get(bx, by, bz)) && bushCanStay(w, bx, by, bz)
                && isAir(w, bx, by + 1, bz)) {
                w.set(bx, by, bz, GenBlocks.withMeta(b.DOUBLE_PLANT, type));
                // The upper half: half=upper (8) plus the default facing, north (2).
                w.set(bx, by + 1, bz, GenBlocks.withMeta(b.DOUBLE_PLANT, 10));
                placed = true;
            }
        }
        return placed;
    }

    // ---- Ice spikes biome ------------------------------------------------------------------------

    private static boolean iceReplaceable(GenWorld w, int x, int y, int z) {
        GenBlocks b = w.blocks();
        int state = w.get(x, y, z);
        int id = state >>> 4;
        return b.isAir(state) || id == b.dirtId || id == b.snowId || id == b.iceId;
    }

    public static boolean iceSpike(GenWorld w, Random rand, int x, int y, int z) {
        GenBlocks b = w.blocks();
        while (isAir(w, x, y, z) && y > 2) y--;
        if (id(w, x, y, z) != b.snowId) return false;
        y += rand.nextInt(4);
        int height = rand.nextInt(4) + 7;
        int width = height / 4 + rand.nextInt(2);
        if (width > 1 && rand.nextInt(60) == 0) y += 10 + rand.nextInt(30);
        for (int k = 0; k < height; k++) {
            float radius = (1.0F - (float) k / (float) height) * width;
            int r = MathHelper.ceil(radius);
            for (int dx = -r; dx <= r; dx++) {
                float fx = Math.abs(dx) - 0.25F;
                for (int dz = -r; dz <= r; dz++) {
                    float fz = Math.abs(dz) - 0.25F;
                    if ((dx == 0 && dz == 0 || fx * fx + fz * fz <= radius * radius)
                        && (dx != -r && dx != r && dz != -r && dz != r || rand.nextFloat() <= 0.75F)) {
                        if (iceReplaceable(w, x + dx, y + k, z + dz)) w.set(x + dx, y + k, z + dz, b.PACKED_ICE);
                        if (k != 0 && r > 1 && iceReplaceable(w, x + dx, y - k, z + dz))
                            w.set(x + dx, y - k, z + dz, b.PACKED_ICE);
                    }
                }
            }
        }
        int stem = width - 1;
        if (stem < 0) stem = 0;
        else if (stem > 1) stem = 1;
        for (int dx = -stem; dx <= stem; dx++) {
            for (int dz = -stem; dz <= stem; dz++) {
                int by = y - 1;
                int run = 50;
                if (Math.abs(dx) == 1 && Math.abs(dz) == 1) run = rand.nextInt(5);
                while (by > 50) {
                    int state = w.get(x + dx, by, z + dz);
                    int id = state >>> 4;
                    if (!b.isAir(state) && id != b.dirtId && id != b.snowId && id != b.iceId && id != b.packedIceId) break;
                    w.set(x + dx, by, z + dz, b.PACKED_ICE);
                    by--;
                    run--;
                    if (run <= 0) {
                        by -= rand.nextInt(5) + 1;
                        run = rand.nextInt(5);
                    }
                }
            }
        }
        return true;
    }

    public static boolean icePath(GenWorld w, Random rand, int x, int y, int z, int baseWidth) {
        GenBlocks b = w.blocks();
        while (isAir(w, x, y, z) && y > 2) y--;
        if (id(w, x, y, z) != b.snowId) return false;
        int r = rand.nextInt(baseWidth - 2) + 2;
        for (int bx = x - r; bx <= x + r; bx++) {
            for (int bz = z - r; bz <= z + r; bz++) {
                int dx = bx - x, dz = bz - z;
                if (dx * dx + dz * dz > r * r) continue;
                for (int by = y - 1; by <= y + 1; by++) {
                    int id = id(w, bx, by, bz);
                    if (id == b.dirtId || id == b.snowId || id == b.iceId) w.set(bx, by, bz, b.PACKED_ICE);
                }
            }
        }
        return true;
    }

    // ---- Mega taiga boulders (WorldGenBlockBlob, radius 0) ---------------------------------------

    public static boolean boulder(GenWorld w, Random rand, int x, int y, int z, int block) {
        GenBlocks b = w.blocks();
        while (true) {
            if (y > 3) {
                if (isAir(w, x, y - 1, z)) { y--; continue; }
                int below = id(w, x, y - 1, z);
                if (below != b.grassId && below != b.dirtId && below != b.stoneId) { y--; continue; }
            }
            if (y <= 3) return false;
            int base = 0;
            for (int i = 0; base >= 0 && i < 3; i++) {
                int rx = base + rand.nextInt(2);
                int ry = base + rand.nextInt(2);
                int rz = base + rand.nextInt(2);
                float radius = (rx + ry + rz) * 0.333F + 0.5F;
                for (int bx = x - rx; bx <= x + rx; bx++)
                    for (int by = y - ry; by <= y + ry; by++)
                        for (int bz = z - rz; bz <= z + rz; bz++) {
                            double dx = bx - x, dy = by - y, dz = bz - z;
                            if (dx * dx + dy * dy + dz * dz <= (double) (radius * radius)) w.set(bx, by, bz, block);
                        }
                x += -(base + 1) + rand.nextInt(2 + base * 2);
                y -= rand.nextInt(2);
                z += -(base + 1) + rand.nextInt(2 + base * 2);
            }
            return true;
        }
    }

    // ---- Desert well -----------------------------------------------------------------------------

    public static boolean desertWell(GenWorld w, int x, int y, int z) {
        GenBlocks b = w.blocks();
        while (isAir(w, x, y, z) && y > 2) y--;
        if (id(w, x, y, z) != b.sandId) return false;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (isAir(w, x + dx, y - 1, z + dz) && isAir(w, x + dx, y - 2, z + dz)) return false;
        for (int dy = -1; dy <= 0; dy++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) w.set(x + dx, y + dy, z + dz, b.SANDSTONE);
        w.set(x, y, z, b.WATER);
        for (int side = 0; side < 4; side++) w.set(x + HORIZONTAL_X[side], y, z + HORIZONTAL_Z[side], b.WATER);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (dx == -2 || dx == 2 || dz == -2 || dz == 2) w.set(x + dx, y + 1, z + dz, b.SANDSTONE);
        w.set(x + 2, y + 1, z, b.SANDSTONE_SLAB);
        w.set(x - 2, y + 1, z, b.SANDSTONE_SLAB);
        w.set(x, y + 1, z + 2, b.SANDSTONE_SLAB);
        w.set(x, y + 1, z - 2, b.SANDSTONE_SLAB);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                w.set(x + dx, y + 4, z + dz, dx == 0 && dz == 0 ? b.SANDSTONE : b.SANDSTONE_SLAB);
        for (int dy = 1; dy <= 3; dy++) {
            w.set(x - 1, y + dy, z - 1, b.SANDSTONE);
            w.set(x - 1, y + dy, z + 1, b.SANDSTONE);
            w.set(x + 1, y + dy, z - 1, b.SANDSTONE);
            w.set(x + 1, y + dy, z + 1, b.SANDSTONE);
        }
        return true;
    }

    // ---- Huge mushrooms (WorldGenBigMushroom) ----------------------------------------------------

    /** @param block the brown or red huge mushroom block, or -1 to pick one at random */
    public static boolean bigMushroom(GenWorld w, Random rand, int x, int y, int z, int block) {
        GenBlocks b = w.blocks();
        if (block == -1) block = rand.nextBoolean() ? b.BROWN_MUSHROOM_BLOCK : b.RED_MUSHROOM_BLOCK;
        boolean brown = block == b.BROWN_MUSHROOM_BLOCK;
        int height = rand.nextInt(3) + 4;
        if (y < 1 || y + height + 1 >= 256) return false;
        for (int by = y; by <= y + 1 + height; by++) {
            int r = by <= y + 3 ? 0 : 3;
            for (int bx = x - r; bx <= x + r; bx++)
                for (int bz = z - r; bz <= z + r; bz++) {
                    if (by < 0 || by >= 256) return false;
                    int state = w.get(bx, by, bz);
                    if (!b.isAir(state) && !b.isLeaves(state)) return false;
                }
        }
        int below = id(w, x, y - 1, z);
        if (below != b.dirtId && below != b.grassId && below != b.myceliumId) return false;
        int capBottom = y + height;
        if (!brown) capBottom = y + height - 3;
        for (int by = capBottom; by <= y + height; by++) {
            int r = 1;
            if (by < y + height) r++;
            if (brown) r = 3;
            int minX = x - r, maxX = x + r, minZ = z - r, maxZ = z + r;
            for (int bx = minX; bx <= maxX; bx++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    int type = 5;
                    if (bx == minX) type--;
                    else if (bx == maxX) type++;
                    if (bz == minZ) type -= 3;
                    else if (bz == maxZ) type += 3;
                    if (brown || by < y + height) {
                        if ((bx == minX || bx == maxX) && (bz == minZ || bz == maxZ)) continue;
                        if (bx == x - (r - 1) && bz == minZ) type = 1;
                        if (bx == minX && bz == z - (r - 1)) type = 1;
                        if (bx == x + (r - 1) && bz == minZ) type = 3;
                        if (bx == maxX && bz == z - (r - 1)) type = 3;
                        if (bx == x - (r - 1) && bz == maxZ) type = 7;
                        if (bx == minX && bz == z + (r - 1)) type = 7;
                        if (bx == x + (r - 1) && bz == maxZ) type = 9;
                        if (bx == maxX && bz == z + (r - 1)) type = 9;
                    }
                    if (type == 5 && by < y + height) type = 0;
                    if (y >= y + height - 1 || type != 0) {
                        if (!b.isFullBlock(w.get(bx, by, bz))) w.set(bx, by, bz, GenBlocks.withMeta(block, type));
                    }
                }
            }
        }
        for (int k = 0; k < height; k++)
            if (!b.isFullBlock(w.get(x, y + k, z))) w.set(x, y + k, z, GenBlocks.withMeta(block, 10));
        return true;
    }
}

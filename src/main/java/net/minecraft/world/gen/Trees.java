package net.minecraft.world.gen;

import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The tree generators of 1.8.9 world generation: oak (with optional jungle
 * vines and cocoa), big oak, birch, the two spruces, swamp oak, acacia, dark
 * oak, the 2x2 mega spruce/pine and jungle trees, and jungle bushes.
 *
 * <p>HYPOTHESIS: recalled from the vanilla WorldGen*Tree classes with their
 * Random draws in the original order. Logs are written with vanilla's
 * metadata (variant, axis in bits 2-3); leaves as the plain variant, which
 * is what generation stores (no decay flags set).
 */
public final class Trees {
    private Trees() {}

    public abstract static class Tree {
        /** Grows the tree with its base block at (x, y, z); false when there is no room or soil. */
        public abstract boolean generate(GenWorld w, Random rand, int x, int y, int z);
        /** Work after a successful grow (the mega spruce's podzol). */
        public void afterGrow(GenWorld w, Random rand, int x, int y, int z) {}
        /** Called before each decoration attempt (vanilla func_175904_e). */
        public void prepare() {}
    }

    static final int OAK = 0, SPRUCE = 1, BIRCH = 2, JUNGLE = 3;

    static int log(GenBlocks b, int variant) { return GenBlocks.withMeta(b.LOG, variant); }
    static int leaves(GenBlocks b, int variant) { return GenBlocks.withMeta(b.LEAVES, variant); }

    /** Replaces the soil under a trunk with plain dirt. */
    static void setDirt(GenWorld w, int x, int y, int z) {
        if ((w.get(x, y, z) >>> 4) != w.blocks().dirtId) w.set(x, y, z, w.blocks().DIRT);
    }

    static boolean isSoil(GenWorld w, int x, int y, int z, boolean farmland) {
        GenBlocks b = w.blocks();
        int id = w.get(x, y, z) >>> 4;
        return id == b.grassId || id == b.dirtId || farmland && id == b.farmlandId;
    }

    /** The usual room check: radius 0 at the base, 1 up the trunk, {@code crown} in the top two layers. */
    static boolean hasRoom(GenWorld w, int x, int y, int z, int height, int crown) {
        GenBlocks b = w.blocks();
        for (int by = y; by <= y + 1 + height; by++) {
            int r = 1;
            if (by == y) r = 0;
            if (by >= y + 1 + height - 2) r = crown;
            for (int bx = x - r; bx <= x + r; bx++)
                for (int bz = z - r; bz <= z + r; bz++)
                    if (by < 0 || by >= 256 || !b.canTreeGrowInto(w.get(bx, by, bz))) return false;
        }
        return true;
    }

    static boolean airOrLeaves(GenWorld w, int x, int y, int z) {
        int state = w.get(x, y, z);
        return w.blocks().isAir(state) || w.blocks().isLeaves(state);
    }

    // ---- Oak / jungle (WorldGenTrees) ------------------------------------------------------------

    public static final class Oak extends Tree {
        private final int minHeight, wood, leaves;
        private final boolean vines;

        public Oak(GenBlocks b) { this(4, log(b, OAK), leaves(b, OAK), false); }
        public Oak(int minHeight, int wood, int leaves, boolean vines) {
            this.minHeight = minHeight; this.wood = wood; this.leaves = leaves; this.vines = vines;
        }

        @Override
        public boolean generate(GenWorld w, Random rand, int x, int y, int z) {
            GenBlocks b = w.blocks();
            int height = rand.nextInt(3) + minHeight;
            if (y < 1 || y + height + 1 > 256) return false;
            if (!hasRoom(w, x, y, z, height, 2)) return false;
            if (!isSoil(w, x, y - 1, z, true) || y >= 256 - height - 1) return false;
            setDirt(w, x, y - 1, z);
            for (int by = y - 3 + height; by <= y + height; by++) {
                int fromTop = by - (y + height);
                int r = 1 - fromTop / 2;
                for (int bx = x - r; bx <= x + r; bx++) {
                    for (int bz = z - r; bz <= z + r; bz++) {
                        if (Math.abs(bx - x) != r || Math.abs(bz - z) != r || rand.nextInt(2) != 0 && fromTop != 0) {
                            int state = w.get(bx, by, bz);
                            if (b.isAir(state) || b.isLeaves(state) || b.isVineMaterial(state)) w.set(bx, by, bz, leaves);
                        }
                    }
                }
            }
            for (int k = 0; k < height; k++) {
                int state = w.get(x, y + k, z);
                if (!b.isAir(state) && !b.isLeaves(state) && !b.isVineMaterial(state)) continue;
                w.set(x, y + k, z, wood);
                if (vines && k > 0) {
                    if (rand.nextInt(3) > 0 && Features.isAir(w, x - 1, y + k, z)) vine(w, x - 1, y + k, z, 8);
                    if (rand.nextInt(3) > 0 && Features.isAir(w, x + 1, y + k, z)) vine(w, x + 1, y + k, z, 2);
                    if (rand.nextInt(3) > 0 && Features.isAir(w, x, y + k, z - 1)) vine(w, x, y + k, z - 1, 1);
                    if (rand.nextInt(3) > 0 && Features.isAir(w, x, y + k, z + 1)) vine(w, x, y + k, z + 1, 4);
                }
            }
            if (vines) {
                for (int by = y - 3 + height; by <= y + height; by++) {
                    int fromTop = by - (y + height);
                    int r = 2 - fromTop / 2;
                    for (int bx = x - r; bx <= x + r; bx++) {
                        for (int bz = z - r; bz <= z + r; bz++) {
                            if (!b.isLeaves(w.get(bx, by, bz))) continue;
                            if (rand.nextInt(4) == 0 && b.isAir(w.get(bx - 1, by, bz))) hangingVine(w, bx - 1, by, bz, 8);
                            if (rand.nextInt(4) == 0 && b.isAir(w.get(bx + 1, by, bz))) hangingVine(w, bx + 1, by, bz, 2);
                            if (rand.nextInt(4) == 0 && b.isAir(w.get(bx, by, bz - 1))) hangingVine(w, bx, by, bz - 1, 1);
                            if (rand.nextInt(4) == 0 && b.isAir(w.get(bx, by, bz + 1))) hangingVine(w, bx, by, bz + 1, 4);
                        }
                    }
                }
                if (rand.nextInt(5) == 0 && height > 5) {
                    // Cocoa pods on the trunk; metadata = facing (S0 W1 N2 E3) | age << 2, facing the trunk.
                    int[] facingMeta = {2, 3, 0, 1}; // N, E, S, W
                    for (int level = 0; level < 2; level++) {
                        for (int side = 0; side < 4; side++) {
                            if (rand.nextInt(4 - level) == 0) {
                                int age = rand.nextInt(3);
                                w.set(x - Features.HORIZONTAL_X[side], y + height - 5 + level, z - Features.HORIZONTAL_Z[side],
                                    GenBlocks.withMeta(b.COCOA, facingMeta[side] | age << 2));
                            }
                        }
                    }
                }
            }
            return true;
        }
    }

    static void vine(GenWorld w, int x, int y, int z, int meta) { w.set(x, y, z, GenBlocks.withMeta(w.blocks().VINE, meta)); }

    /** A vine and up to four more hanging below it through air. */
    static void hangingVine(GenWorld w, int x, int y, int z, int meta) {
        vine(w, x, y, z, meta);
        int remaining = 4;
        for (y--; w.blocks().isAir(w.get(x, y, z)) && remaining > 0; remaining--) {
            vine(w, x, y, z, meta);
            y--;
        }
    }

    // ---- Birch (WorldGenForest) ------------------------------------------------------------------

    public static final class Birch extends Tree {
        private final boolean tall;
        public Birch(boolean tall) { this.tall = tall; }

        @Override
        public boolean generate(GenWorld w, Random rand, int x, int y, int z) {
            GenBlocks b = w.blocks();
            int height = rand.nextInt(3) + 5;
            if (tall) height += rand.nextInt(7);
            if (y < 1 || y + height + 1 > 256) return false;
            if (!hasRoom(w, x, y, z, height, 2)) return false;
            if (!isSoil(w, x, y - 1, z, true) || y >= 256 - height - 1) return false;
            setDirt(w, x, y - 1, z);
            int leaves = leaves(b, BIRCH);
            for (int by = y - 3 + height; by <= y + height; by++) {
                int fromTop = by - (y + height);
                int r = 1 - fromTop / 2;
                for (int bx = x - r; bx <= x + r; bx++)
                    for (int bz = z - r; bz <= z + r; bz++)
                        if (Math.abs(bx - x) != r || Math.abs(bz - z) != r || rand.nextInt(2) != 0 && fromTop != 0)
                            if (airOrLeaves(w, bx, by, bz)) w.set(bx, by, bz, leaves);
            }
            for (int k = 0; k < height; k++) if (airOrLeaves(w, x, y + k, z)) w.set(x, y + k, z, log(b, BIRCH));
            return true;
        }
    }

    // ---- Spruces (WorldGenTaiga1 "pine", WorldGenTaiga2 "spruce") --------------------------------

    public static final class Pine extends Tree {
        @Override
        public boolean generate(GenWorld w, Random rand, int x, int y, int z) {
            GenBlocks b = w.blocks();
            int height = rand.nextInt(5) + 7;
            int bare = height - rand.nextInt(2) - 3;
            int crown = height - bare;
            int radius = 1 + rand.nextInt(crown + 1);
            if (y < 1 || y + height + 1 > 256) return false;
            for (int by = y; by <= y + 1 + height; by++) {
                int r = by - y < bare ? 0 : radius;
                for (int bx = x - r; bx <= x + r; bx++)
                    for (int bz = z - r; bz <= z + r; bz++)
                        if (by < 0 || by >= 256 || !b.canTreeGrowInto(w.get(bx, by, bz))) return false;
            }
            if (!isSoil(w, x, y - 1, z, false) || y >= 256 - height - 1) return false;
            setDirt(w, x, y - 1, z);
            int leaves = leaves(b, SPRUCE);
            int r = 0;
            for (int by = y + height; by >= y + bare; by--) {
                for (int bx = x - r; bx <= x + r; bx++)
                    for (int bz = z - r; bz <= z + r; bz++)
                        if ((Math.abs(bx - x) != r || Math.abs(bz - z) != r || r <= 0) && !b.isFullBlock(w.get(bx, by, bz)))
                            w.set(bx, by, bz, leaves);
                if (r >= 1 && by == y + bare + 1) r--;
                else if (r < radius) r++;
            }
            for (int k = 0; k < height - 1; k++) if (airOrLeaves(w, x, y + k, z)) w.set(x, y + k, z, log(b, SPRUCE));
            return true;
        }
    }

    public static final class Spruce extends Tree {
        @Override
        public boolean generate(GenWorld w, Random rand, int x, int y, int z) {
            GenBlocks b = w.blocks();
            int height = rand.nextInt(4) + 6;
            int bare = 1 + rand.nextInt(2);
            int crown = height - bare;
            int maxRadius = 2 + rand.nextInt(2);
            if (y < 1 || y + height + 1 > 256) return false;
            for (int by = y; by <= y + 1 + height; by++) {
                int r = by - y < bare ? 0 : maxRadius;
                for (int bx = x - r; bx <= x + r; bx++)
                    for (int bz = z - r; bz <= z + r; bz++)
                        if (by < 0 || by >= 256 || !b.canTreeGrowInto(w.get(bx, by, bz))) return false;
            }
            if (!isSoil(w, x, y - 1, z, true) || y >= 256 - height - 1) return false;
            setDirt(w, x, y - 1, z);
            int leaves = leaves(b, SPRUCE);
            int r = rand.nextInt(2);
            int limit = 1, next = 0;
            for (int level = 0; level <= crown; level++) {
                int by = y + height - level;
                for (int bx = x - r; bx <= x + r; bx++)
                    for (int bz = z - r; bz <= z + r; bz++)
                        if ((Math.abs(bx - x) != r || Math.abs(bz - z) != r || r <= 0) && !b.isFullBlock(w.get(bx, by, bz)))
                            w.set(bx, by, bz, leaves);
                if (r >= limit) {
                    r = next;
                    next = 1;
                    limit++;
                    if (limit > maxRadius) limit = maxRadius;
                } else {
                    r++;
                }
            }
            int topGap = rand.nextInt(3);
            for (int k = 0; k < height - topGap; k++) if (airOrLeaves(w, x, y + k, z)) w.set(x, y + k, z, log(b, SPRUCE));
            return true;
        }
    }

    // ---- Swamp oak (WorldGenSwamp) ---------------------------------------------------------------

    public static final class Swamp extends Tree {
        @Override
        public boolean generate(GenWorld w, Random rand, int x, int y, int z) {
            GenBlocks b = w.blocks();
            int height = rand.nextInt(4) + 5;
            while (b.isWater(w.get(x, y - 1, z))) y--;
            if (y < 1 || y + height + 1 > 256) return false;
            for (int by = y; by <= y + 1 + height; by++) {
                int r = 1;
                if (by == y) r = 0;
                if (by >= y + 1 + height - 2) r = 3;
                for (int bx = x - r; bx <= x + r; bx++) {
                    for (int bz = z - r; bz <= z + r; bz++) {
                        if (by < 0 || by >= 256) return false;
                        int state = w.get(bx, by, bz);
                        if (b.isAir(state) || b.isLeaves(state)) continue;
                        int id = state >>> 4;
                        if (id != b.waterId && id != b.flowingWaterId) return false;
                        if (by > y) return false;
                    }
                }
            }
            if (!isSoil(w, x, y - 1, z, false) || y >= 256 - height - 1) return false;
            setDirt(w, x, y - 1, z);
            int leaves = leaves(b, OAK);
            for (int by = y - 3 + height; by <= y + height; by++) {
                int fromTop = by - (y + height);
                int r = 2 - fromTop / 2;
                for (int bx = x - r; bx <= x + r; bx++)
                    for (int bz = z - r; bz <= z + r; bz++)
                        if (Math.abs(bx - x) != r || Math.abs(bz - z) != r || rand.nextInt(2) != 0 && fromTop != 0)
                            if (!b.isFullBlock(w.get(bx, by, bz))) w.set(bx, by, bz, leaves);
            }
            for (int k = 0; k < height; k++) {
                int state = w.get(x, y + k, z);
                int id = state >>> 4;
                if (b.isAir(state) || b.isLeaves(state) || id == b.flowingWaterId || id == b.waterId)
                    w.set(x, y + k, z, log(b, OAK));
            }
            for (int by = y - 3 + height; by <= y + height; by++) {
                int fromTop = by - (y + height);
                int r = 2 - fromTop / 2;
                for (int bx = x - r; bx <= x + r; bx++) {
                    for (int bz = z - r; bz <= z + r; bz++) {
                        if (!b.isLeaves(w.get(bx, by, bz))) continue;
                        if (rand.nextInt(4) == 0 && b.isAir(w.get(bx - 1, by, bz))) hangingVine(w, bx - 1, by, bz, 8);
                        if (rand.nextInt(4) == 0 && b.isAir(w.get(bx + 1, by, bz))) hangingVine(w, bx + 1, by, bz, 2);
                        if (rand.nextInt(4) == 0 && b.isAir(w.get(bx, by, bz - 1))) hangingVine(w, bx, by, bz - 1, 1);
                        if (rand.nextInt(4) == 0 && b.isAir(w.get(bx, by, bz + 1))) hangingVine(w, bx, by, bz + 1, 4);
                    }
                }
            }
            return true;
        }
    }

    // ---- Acacia (WorldGenSavannaTree) ------------------------------------------------------------

    public static final class Acacia extends Tree {
        @Override
        public boolean generate(GenWorld w, Random rand, int x, int y, int z) {
            GenBlocks b = w.blocks();
            int height = rand.nextInt(3) + rand.nextInt(3) + 5;
            if (y < 1 || y + height + 1 > 256) return false;
            if (!hasRoom(w, x, y, z, height, 2)) return false;
            if (!isSoil(w, x, y - 1, z, false) || y >= 256 - height - 1) return false;
            setDirt(w, x, y - 1, z);
            int side = rand.nextInt(4);
            int bendStart = height - rand.nextInt(4) - 1;
            int bend = 3 - rand.nextInt(3);
            int tx = x, tz = z, top = 0;
            for (int k = 0; k < height; k++) {
                int by = y + k;
                if (k >= bendStart && bend > 0) {
                    tx += Features.HORIZONTAL_X[side];
                    tz += Features.HORIZONTAL_Z[side];
                    bend--;
                }
                if (airOrLeaves(w, tx, by, tz)) {
                    w.set(tx, by, tz, b.LOG2);
                    top = by;
                }
            }
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++)
                    if (Math.abs(dx) != 3 || Math.abs(dz) != 3) acaciaLeaves(w, tx + dx, top, tz + dz);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) acaciaLeaves(w, tx + dx, top + 1, tz + dz);
            acaciaLeaves(w, tx + 2, top + 1, tz);
            acaciaLeaves(w, tx - 2, top + 1, tz);
            acaciaLeaves(w, tx, top + 1, tz + 2);
            acaciaLeaves(w, tx, top + 1, tz - 2);
            tx = x;
            tz = z;
            int branchSide = rand.nextInt(4);
            if (branchSide != side) {
                int start = bendStart - rand.nextInt(2) - 1;
                int length = 1 + rand.nextInt(3);
                top = 0;
                for (int k = start; k < height && length > 0; length--) {
                    if (k >= 1) {
                        int by = y + k;
                        tx += Features.HORIZONTAL_X[branchSide];
                        tz += Features.HORIZONTAL_Z[branchSide];
                        if (airOrLeaves(w, tx, by, tz)) {
                            w.set(tx, by, tz, b.LOG2);
                            top = by;
                        }
                    }
                    k++;
                }
                if (top > 0) {
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++)
                            if (Math.abs(dx) != 2 || Math.abs(dz) != 2) acaciaLeaves(w, tx + dx, top, tz + dz);
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++) acaciaLeaves(w, tx + dx, top + 1, tz + dz);
                }
            }
            return true;
        }

        private static void acaciaLeaves(GenWorld w, int x, int y, int z) {
            if (airOrLeaves(w, x, y, z)) w.set(x, y, z, w.blocks().LEAVES2);
        }
    }

    // ---- Dark oak (WorldGenCanopyTree) -----------------------------------------------------------

    public static final class DarkOak extends Tree {
        @Override
        public boolean generate(GenWorld w, Random rand, int x, int y, int z) {
            GenBlocks b = w.blocks();
            int height = rand.nextInt(3) + rand.nextInt(2) + 6;
            if (y < 1 || y + height + 1 >= 256) return false;
            if (!isSoil(w, x, y - 1, z, false)) return false;
            for (int level = 0; level <= height + 1; level++) {
                int r = 1;
                if (level == 0) r = 0;
                if (level >= height - 1) r = 2;
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++)
                        if (!b.canTreeGrowInto(w.get(x + dx, y + level, z + dz))) return false;
            }
            setDirt(w, x, y - 1, z);
            setDirt(w, x + 1, y - 1, z);
            setDirt(w, x, y - 1, z + 1);
            setDirt(w, x + 1, y - 1, z + 1);
            int side = rand.nextInt(4);
            int bendStart = height - rand.nextInt(4);
            int bend = 2 - rand.nextInt(3);
            int tx = x, tz = z;
            int top = y + height - 1;
            for (int k = 0; k < height; k++) {
                if (k >= bendStart && bend > 0) {
                    tx += Features.HORIZONTAL_X[side];
                    tz += Features.HORIZONTAL_Z[side];
                    bend--;
                }
                int by = y + k;
                if (airOrLeaves(w, tx, by, tz)) {
                    darkLog(w, tx, by, tz);
                    darkLog(w, tx + 1, by, tz);
                    darkLog(w, tx, by, tz + 1);
                    darkLog(w, tx + 1, by, tz + 1);
                }
            }
            for (int dx = -2; dx <= 0; dx++) {
                for (int dz = -2; dz <= 0; dz++) {
                    int dy = -1;
                    darkLeaves(w, tx + dx, top + dy, tz + dz);
                    darkLeaves(w, 1 + tx - dx, top + dy, tz + dz);
                    darkLeaves(w, tx + dx, top + dy, 1 + tz - dz);
                    darkLeaves(w, 1 + tx - dx, top + dy, 1 + tz - dz);
                    if ((dx > -2 || dz > -1) && (dx != -1 || dz != -2)) {
                        dy = 1;
                        darkLeaves(w, tx + dx, top + dy, tz + dz);
                        darkLeaves(w, 1 + tx - dx, top + dy, tz + dz);
                        darkLeaves(w, tx + dx, top + dy, 1 + tz - dz);
                        darkLeaves(w, 1 + tx - dx, top + dy, 1 + tz - dz);
                    }
                }
            }
            if (rand.nextBoolean()) {
                darkLeaves(w, tx, top + 2, tz);
                darkLeaves(w, tx + 1, top + 2, tz);
                darkLeaves(w, tx + 1, top + 2, tz + 1);
                darkLeaves(w, tx, top + 2, tz + 1);
            }
            for (int dx = -3; dx <= 4; dx++)
                for (int dz = -3; dz <= 4; dz++)
                    if ((dx != -3 || dz != -3) && (dx != -3 || dz != 4) && (dx != 4 || dz != -3) && (dx != 4 || dz != 4)
                        && (Math.abs(dx) < 3 || Math.abs(dz) < 3))
                        darkLeaves(w, tx + dx, top, tz + dz);
            for (int dx = -1; dx <= 2; dx++) {
                for (int dz = -1; dz <= 2; dz++) {
                    if ((dx < 0 || dx > 1 || dz < 0 || dz > 1) && rand.nextInt(3) <= 0) {
                        int length = rand.nextInt(3) + 2;
                        for (int k = 0; k < length; k++) darkLog(w, x + dx, top - k - 1, z + dz);
                        for (int lx = -1; lx <= 1; lx++)
                            for (int lz = -1; lz <= 1; lz++) darkLeaves(w, tx + dx + lx, top, tz + dz + lz);
                        for (int lx = -2; lx <= 2; lx++)
                            for (int lz = -2; lz <= 2; lz++)
                                if (Math.abs(lx) != 2 || Math.abs(lz) != 2) darkLeaves(w, tx + dx + lx, top - 1, tz + dz + lz);
                    }
                }
            }
            return true;
        }

        private static void darkLog(GenWorld w, int x, int y, int z) {
            if (w.blocks().canTreeGrowInto(w.get(x, y, z))) w.set(x, y, z, GenBlocks.withMeta(w.blocks().LOG2, 1));
        }

        private static void darkLeaves(GenWorld w, int x, int y, int z) {
            if (w.blocks().isAir(w.get(x, y, z))) w.set(x, y, z, GenBlocks.withMeta(w.blocks().LEAVES2, 1));
        }
    }

    // ---- 2x2 trees (WorldGenHugeTrees and its subclasses) ----------------------------------------

    abstract static class HugeTree extends Tree {
        final int baseHeight, extraHeight, wood, leaves;

        HugeTree(int baseHeight, int extraHeight, int wood, int leaves) {
            this.baseHeight = baseHeight; this.extraHeight = extraHeight; this.wood = wood; this.leaves = leaves;
        }

        int pickHeight(Random rand) {
            int height = rand.nextInt(3) + baseHeight;
            if (extraHeight > 1) height += rand.nextInt(extraHeight);
            return height;
        }

        boolean prepareGround(GenWorld w, int x, int y, int z, int height) {
            GenBlocks b = w.blocks();
            if (y < 1 || y + height + 1 > 256) return false;
            for (int level = 0; level <= 1 + height; level++) {
                int r = 2;
                if (level == 0) r = 1;
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++)
                        if (y + level < 0 || y + level >= 256 || !b.canTreeGrowInto(w.get(x + dx, y + level, z + dz)))
                            return false;
            }
            if (!isSoil(w, x, y - 1, z, false) || y < 2) return false;
            setDirt(w, x, y - 1, z);
            setDirt(w, x + 1, y - 1, z);
            setDirt(w, x, y - 1, z + 1);
            setDirt(w, x + 1, y - 1, z + 1);
            return true;
        }

        /** A leaf disc centred on the 2x2 trunk. */
        void wideLayer(GenWorld w, int x, int y, int z, int radius) {
            int r2 = radius * radius;
            for (int dx = -radius; dx <= radius + 1; dx++) {
                for (int dz = -radius; dz <= radius + 1; dz++) {
                    int ox = dx - 1, oz = dz - 1;
                    if (dx * dx + dz * dz <= r2 || ox * ox + oz * oz <= r2 || dx * dx + oz * oz <= r2 || ox * ox + dz * dz <= r2)
                        if (airOrLeaves(w, x + dx, y, z + dz)) w.set(x + dx, y, z + dz, leaves);
                }
            }
        }

        /** A leaf disc centred on one block. */
        void layer(GenWorld w, int x, int y, int z, int radius) {
            int r2 = radius * radius;
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++)
                    if (dx * dx + dz * dz <= r2 && airOrLeaves(w, x + dx, y, z + dz)) w.set(x + dx, y, z + dz, leaves);
        }
    }

    /** Mega spruce ({@code spruce} true: long cone) or mega pine (short cone). Heights 13-29. */
    public static final class MegaPine extends HugeTree {
        private final boolean spruce;

        public MegaPine(GenBlocks b, boolean spruce) {
            super(13, 15, log(b, SPRUCE), leaves(b, SPRUCE));
            this.spruce = spruce;
        }

        @Override
        public boolean generate(GenWorld w, Random rand, int x, int y, int z) {
            int height = pickHeight(rand);
            if (!prepareGround(w, x, y, z, height)) return false;
            crown(w, x, z, y + height, rand);
            for (int k = 0; k < height; k++) {
                if (airOrLeaves(w, x, y + k, z)) w.set(x, y + k, z, wood);
                if (k < height - 1) {
                    if (airOrLeaves(w, x + 1, y + k, z)) w.set(x + 1, y + k, z, wood);
                    if (airOrLeaves(w, x + 1, y + k, z + 1)) w.set(x + 1, y + k, z + 1, wood);
                    if (airOrLeaves(w, x, y + k, z + 1)) w.set(x, y + k, z + 1, wood);
                }
            }
            return true;
        }

        private void crown(GenWorld w, int x, int z, int top, Random rand) {
            int length = rand.nextInt(5) + (spruce ? baseHeight : 3);
            int previous = 0;
            for (int by = top - length; by <= top; by++) {
                int fromTop = top - by;
                int radius = MathHelper.floor((float) fromTop / (float) length * 3.5F);
                wideLayer(w, x, by, z, radius + (fromTop > 0 && radius == previous && (by & 1) == 0 ? 1 : 0));
                previous = radius;
            }
        }

        /** Podzol patches around the trunk. */
        @Override
        public void afterGrow(GenWorld w, Random rand, int x, int y, int z) {
            podzolPatch(w, x - 1, y, z - 1);
            podzolPatch(w, x + 2, y, z - 1);
            podzolPatch(w, x - 1, y, z + 2);
            podzolPatch(w, x + 2, y, z + 2);
            for (int i = 0; i < 5; i++) {
                int cell = rand.nextInt(64);
                int cx = cell % 8, cz = cell / 8;
                if (cx == 0 || cx == 7 || cz == 0 || cz == 7) podzolPatch(w, x - 3 + cx, y, z - 3 + cz);
            }
        }

        private static void podzolPatch(GenWorld w, int x, int y, int z) {
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (Math.abs(dx) != 2 || Math.abs(dz) != 2) podzolColumn(w, x + dx, y, z + dz);
        }

        private static void podzolColumn(GenWorld w, int x, int y, int z) {
            GenBlocks b = w.blocks();
            for (int dy = 2; dy >= -3; dy--) {
                int state = w.get(x, y + dy, z);
                int id = state >>> 4;
                if (id == b.grassId || id == b.dirtId) {
                    w.set(x, y + dy, z, b.PODZOL);
                    return;
                }
                if (!b.isAir(state) && dy < 0) return;
            }
        }
    }

    /** The 2x2 jungle giant with side branches and vines. */
    public static final class MegaJungle extends HugeTree {
        public MegaJungle(GenBlocks b) { super(10, 20, log(b, JUNGLE), leaves(b, JUNGLE)); }

        @Override
        public boolean generate(GenWorld w, Random rand, int x, int y, int z) {
            GenBlocks b = w.blocks();
            int height = pickHeight(rand);
            if (!prepareGround(w, x, y, z, height)) return false;
            for (int dy = -2; dy <= 0; dy++) wideLayer(w, x, y + height + dy, z, 2 + 1 - dy);
            for (int by = y + height - 2 - rand.nextInt(4); by > y + height / 2; by -= 2 + rand.nextInt(4)) {
                float angle = rand.nextFloat() * (float) Math.PI * 2.0F;
                int bx = x + (int) (0.5F + MathHelper.cos(angle) * 4.0F);
                int bz = z + (int) (0.5F + MathHelper.sin(angle) * 4.0F);
                for (int i = 0; i < 5; i++) {
                    bx = x + (int) (1.5F + MathHelper.cos(angle) * i);
                    bz = z + (int) (1.5F + MathHelper.sin(angle) * i);
                    w.set(bx, by - 3 + i / 2, bz, wood);
                }
                int layers = 1 + rand.nextInt(2);
                for (int ly = by - layers; ly <= by; ly++) layer(w, bx, ly, bz, 1 - (ly - by));
            }
            for (int k = 0; k < height; k++) {
                int by = y + k;
                if (b.canTreeGrowInto(w.get(x, by, z))) {
                    w.set(x, by, z, wood);
                    if (k > 0) {
                        trunkVine(w, rand, x - 1, by, z, 8);
                        trunkVine(w, rand, x, by, z - 1, 1);
                    }
                }
                if (k < height - 1) {
                    if (b.canTreeGrowInto(w.get(x + 1, by, z))) {
                        w.set(x + 1, by, z, wood);
                        if (k > 0) {
                            trunkVine(w, rand, x + 2, by, z, 2);
                            trunkVine(w, rand, x + 1, by, z - 1, 1);
                        }
                    }
                    if (b.canTreeGrowInto(w.get(x + 1, by, z + 1))) {
                        w.set(x + 1, by, z + 1, wood);
                        if (k > 0) {
                            trunkVine(w, rand, x + 2, by, z + 1, 2);
                            trunkVine(w, rand, x + 1, by, z + 2, 4);
                        }
                    }
                    if (b.canTreeGrowInto(w.get(x, by, z + 1))) {
                        w.set(x, by, z + 1, wood);
                        if (k > 0) {
                            trunkVine(w, rand, x - 1, by, z + 1, 8);
                            trunkVine(w, rand, x, by, z + 2, 4);
                        }
                    }
                }
            }
            return true;
        }

        private static void trunkVine(GenWorld w, Random rand, int x, int y, int z, int meta) {
            if (rand.nextInt(3) > 0 && Features.isAir(w, x, y, z)) vine(w, x, y, z, meta);
        }
    }

    // ---- Jungle bush (WorldGenShrub) -------------------------------------------------------------

    public static final class Bush extends Tree {
        private final int wood, leaves;
        public Bush(int wood, int leaves) { this.wood = wood; this.leaves = leaves; }

        @Override
        public boolean generate(GenWorld w, Random rand, int x, int y, int z) {
            GenBlocks b = w.blocks();
            while (y > 0 && airOrLeaves(w, x, y, z)) y--;
            int ground = w.get(x, y, z) >>> 4;
            if (ground == b.dirtId || ground == b.grassId) {
                y++;
                w.set(x, y, z, wood);
                for (int by = y; by <= y + 2; by++) {
                    int r = 2 - (by - y);
                    for (int bx = x - r; bx <= x + r; bx++)
                        for (int bz = z - r; bz <= z + r; bz++)
                            if (Math.abs(bx - x) != r || Math.abs(bz - z) != r || rand.nextInt(2) != 0)
                                if (!b.isFullBlock(w.get(bx, by, bz))) w.set(bx, by, bz, leaves);
                }
            }
            return true;
        }
    }

    // ---- Big oak (WorldGenBigTree) ---------------------------------------------------------------

    /**
     * The branching "fancy" oak. Vanilla keeps the height limit on the shared
     * generator between trees (only the first tree of a session rolls one);
     * here every tree rolls its own (deviations.yml).
     */
    public static final class BigOak extends Tree {
        private static final double HEIGHT_ATTENUATION = 0.618, BRANCH_SLOPE = 0.381, SCALE_WIDTH = 1.0, LEAF_DENSITY = 1.0;
        private int leafDistanceLimit = 4;
        private GenWorld w;
        private Random rand;
        private int baseX, baseY, baseZ, heightLimit, height;
        /** Leaf clusters: x, y, z and the y of the trunk point the branch leaves from. */
        private List<int[]> clusters;

        @Override
        public void prepare() { leafDistanceLimit = 5; }

        @Override
        public boolean generate(GenWorld world, Random random, int x, int y, int z) {
            this.w = world;
            baseX = x; baseY = y; baseZ = z;
            this.rand = new Random(random.nextLong());
            heightLimit = 5 + rand.nextInt(12);
            try {
                if (!validLocation()) return false;
                leafClusters();
                for (int[] cluster : clusters)
                    for (int i = 0; i < leafDistanceLimit; i++)
                        crossSection(cluster[0], cluster[1] + i, cluster[2], leafSize(i), leaves(w.blocks(), OAK));
                limb(baseX, baseY, baseZ, baseX, baseY + height, baseZ);
                for (int[] cluster : clusters) {
                    int branchY = cluster[3];
                    if ((cluster[0] != baseX || cluster[1] != branchY || cluster[2] != baseZ)
                        && branchY - baseY >= heightLimit * 0.2)
                        limb(baseX, branchY, baseZ, cluster[0], cluster[1], cluster[2]);
                }
                return true;
            } finally {
                this.w = null;
            }
        }

        private boolean validLocation() {
            GenBlocks b = w.blocks();
            int below = w.get(baseX, baseY - 1, baseZ) >>> 4;
            if (below != b.dirtId && below != b.grassId && below != b.farmlandId) return false;
            int clear = checkLine(baseX, baseY, baseZ, baseX, baseY + heightLimit - 1, baseZ);
            if (clear == -1) return true;
            if (clear < 6) return false;
            heightLimit = clear;
            return true;
        }

        private void leafClusters() {
            height = (int) (heightLimit * HEIGHT_ATTENUATION);
            if (height >= heightLimit) height = heightLimit - 1;
            int perLayer = (int) (1.382 + Math.pow(LEAF_DENSITY * heightLimit / 13.0, 2.0));
            if (perLayer < 1) perLayer = 1;
            int trunkTop = baseY + height;
            int k = heightLimit - leafDistanceLimit;
            clusters = new ArrayList<>();
            clusters.add(new int[]{baseX, baseY + k, baseZ, trunkTop});
            for (; k >= 0; k--) {
                float size = layerSize(k);
                if (size < 0.0F) continue;
                for (int i = 0; i < perLayer; i++) {
                    double distance = SCALE_WIDTH * size * (rand.nextFloat() + 0.328);
                    double angle = rand.nextFloat() * 2.0F * Math.PI;
                    double ox = distance * Math.sin(angle) + 0.5;
                    double oz = distance * Math.cos(angle) + 0.5;
                    int cx = MathHelper.floor(baseX + ox), cy = MathHelper.floor(baseY + (double) (k - 1)), cz = MathHelper.floor(baseZ + oz);
                    if (checkLine(cx, cy, cz, cx, cy + leafDistanceLimit, cz) != -1) continue;
                    int dx = baseX - cx, dz = baseZ - cz;
                    double branchY = cy - Math.sqrt(dx * dx + dz * dz) * BRANCH_SLOPE;
                    int by = branchY > trunkTop ? trunkTop : (int) branchY;
                    if (checkLine(baseX, by, baseZ, cx, cy, cz) == -1) clusters.add(new int[]{cx, cy, cz, by});
                }
            }
        }

        private float layerSize(int y) {
            if ((float) y < heightLimit * 0.3F) return -1.0F;
            float half = heightLimit / 2.0F;
            float offset = half - y;
            float size = MathHelper.sqrt(half * half - offset * offset);
            if (offset == 0.0F) size = half;
            else if (Math.abs(offset) >= half) return 0.0F;
            return size * 0.5F;
        }

        private float leafSize(int y) {
            return y >= 0 && y < leafDistanceLimit ? (y != 0 && y != leafDistanceLimit - 1 ? 3.0F : 2.0F) : -1.0F;
        }

        private void crossSection(int x, int y, int z, float radius, int state) {
            int r = (int) (radius + 0.618);
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    if (Math.pow(Math.abs(dx) + 0.5, 2.0) + Math.pow(Math.abs(dz) + 0.5, 2.0) <= radius * radius
                        && airOrLeaves(w, x + dx, y, z + dz))
                        w.set(x + dx, y, z + dz, state);
        }

        private static int greatest(int dx, int dy, int dz) {
            int ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
            return az > ax && az > ay ? az : ay > ax ? ay : ax;
        }

        /** A straight log line from one point to another, logs turned along its main axis. */
        private void limb(int ax, int ay, int az, int bx, int by, int bz) {
            int dx = bx - ax, dy = by - ay, dz = bz - az;
            int steps = greatest(dx, dy, dz);
            float fx = (float) dx / steps, fy = (float) dy / steps, fz = (float) dz / steps;
            for (int j = 0; j <= steps; j++) {
                int px = MathHelper.floor(ax + (double) (0.5F + j * fx));
                int py = MathHelper.floor(ay + (double) (0.5F + j * fy));
                int pz = MathHelper.floor(az + (double) (0.5F + j * fz));
                int ox = Math.abs(px - ax), oz = Math.abs(pz - az), most = Math.max(ox, oz);
                int axis = 0; // Y
                if (most > 0) axis = ox == most ? 4 : oz == most ? 8 : 0;
                w.set(px, py, pz, GenBlocks.withMeta(w.blocks().LOG, axis));
            }
        }

        /** -1 if trees can grow along the whole line, otherwise how far along it the first obstacle is. */
        private int checkLine(int ax, int ay, int az, int bx, int by, int bz) {
            int dx = bx - ax, dy = by - ay, dz = bz - az;
            int steps = greatest(dx, dy, dz);
            if (steps == 0) return -1;
            float fx = (float) dx / steps, fy = (float) dy / steps, fz = (float) dz / steps;
            for (int j = 0; j <= steps; j++) {
                int px = MathHelper.floor(ax + (double) (0.5F + j * fx));
                int py = MathHelper.floor(ay + (double) (0.5F + j * fy));
                int pz = MathHelper.floor(az + (double) (0.5F + j * fz));
                if (!w.blocks().canTreeGrowInto(w.get(px, py, pz))) return j;
            }
            return -1;
        }
    }
}

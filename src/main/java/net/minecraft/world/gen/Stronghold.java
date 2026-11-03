package net.minecraft.world.gen;

import net.minecraft.world.gen.NetherFortress.Box;
import net.minecraft.world.gen.NetherFortress.Facing;
import net.minecraft.world.gen.layer.BiomeLayers;
import net.minecraft.world.gen.layer.GenLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;

/**
 * Strongholds (1.8.9 MapGenStronghold and StructureStrongholdPieces,
 * HYPOTHESIS: recalled). A world has three, a third of a turn apart at a
 * random angle, 40-72 chunks from the origin, each moved to a land biome
 * within 112 blocks. Each grows from a spiral staircase that always leads
 * into a five-way crossing: corridors, prison halls, turns, room crossings
 * (pillar, fountain or storeroom), stairs, chest corridors, libraries and
 * one portal room, picked by weight with per-piece limits and never the
 * same piece twice in a row, within 112 blocks and 50 pieces deep; where
 * nothing fits, a short corridor joins the next piece or the way stays shut.
 * It is rebuilt until it has a portal room, then sunk so its top is below
 * y=53. Walls are random stone bricks (mossy, cracked, silverfish) and only
 * replace blocks that are there, so caves cut through.
 *
 * <p>shortcut: the chests are empty like dungeon chests, and strongholds are
 * not saved to data/Stronghold.dat (they are rebuilt from the seed, which
 * gives the same ones); upgrade with chest loot. Block randomness comes
 * from its own per-chunk Random, not the population one, so the other
 * features of a chunk come out the same with or without a stronghold.
 */
public final class Stronghold {
    private final GenBlocks b;
    private final long seed;
    private final IntPredicate land;
    private int[][] coords;
    private List<Start> starts;

    final int stonebrick, mossy, cracked, silverfish, slab, brickSlab, doubleSlab, cobble, planks, bookshelf, web, fence,
        ironBars, torch, ladder, woodDoor, ironDoor, button, stoneStairs, brickStairs, frame, water, lava;

    /** @param land whether a biome id can hold a stronghold (vanilla: base height above 0) */
    public Stronghold(GenBlocks blocks, long seed, IntPredicate land) {
        this.b = blocks;
        this.seed = seed;
        this.land = land;
        stonebrick = blocks.state("stonebrick", 0); mossy = blocks.state("stonebrick", 1); cracked = blocks.state("stonebrick", 2);
        silverfish = blocks.state("monster_egg", 2); slab = blocks.state("stone_slab", 0); brickSlab = blocks.state("stone_slab", 5);
        doubleSlab = blocks.state("double_stone_slab", 0); cobble = blocks.state("cobblestone", 0); planks = blocks.state("planks", 0);
        bookshelf = blocks.state("bookshelf", 0); web = blocks.state("web", 0); fence = blocks.state("fence", 0);
        ironBars = blocks.state("iron_bars", 0); torch = blocks.state("torch", 0); ladder = blocks.state("ladder", 0);
        woodDoor = blocks.state("wooden_door", 0); ironDoor = blocks.state("iron_door", 0); button = blocks.state("stone_button", 0);
        stoneStairs = blocks.state("stone_stairs", 0); brickStairs = blocks.state("stone_brick_stairs", 0);
        frame = blocks.state("end_portal_frame", 0); water = blocks.state("flowing_water", 0); lava = blocks.state("flowing_lava", 0);
    }

    // ---- where ---------------------------------------------------------------------------------

    /** MapGenStronghold.getCoordList: the three start chunks. */
    public synchronized int[][] chunkCoords() {
        if (coords != null) return coords;
        GenLayer biomes = new BiomeLayers(seed).coarse(); // its own chain: layers keep per-call state
        Random random = new Random(seed);
        double angle = random.nextDouble() * Math.PI * 2.0;
        coords = new int[3][];
        for (int i = 0; i < 3; i++) {
            double distance = (1.25 + random.nextDouble()) * 32.0;
            int cx = (int) Math.round(Math.cos(angle) * distance), cz = (int) Math.round(Math.sin(angle) * distance);
            int[] pos = findBiomePosition(biomes, (cx << 4) + 8, (cz << 4) + 8, 112, random);
            if (pos != null) { cx = pos[0] >> 4; cz = pos[1] >> 4; }
            coords[i] = new int[] {cx, cz};
            angle += Math.PI * 2.0 / 3.0;
        }
        return coords;
    }

    /** WorldChunkManager.findBiomePosition: a random allowed spot of the 4-block biome grid around (x, z), or null. */
    private int[] findBiomePosition(GenLayer biomes, int x, int z, int range, Random random) {
        int x0 = x - range >> 2, z0 = z - range >> 2, x1 = x + range >> 2, z1 = z + range >> 2;
        int w = x1 - x0 + 1, h = z1 - z0 + 1;
        int[] ids = biomes.getInts(x0, z0, w, h);
        int[] found = null;
        int count = 0;
        for (int i = 0; i < w * h; i++) {
            if (land.test(ids[i]) && (found == null || random.nextInt(count + 1) == 0)) {
                found = new int[] {x0 + i % w << 2, z0 + i / w << 2};
                count++;
            }
        }
        return found;
    }

    /** The three strongholds, built once. */
    public synchronized List<Start> starts() {
        if (starts != null) return starts;
        long a, c;
        Random rand = new Random(seed);
        a = rand.nextLong();
        c = rand.nextLong();
        List<Start> built = new ArrayList<>();
        for (int[] chunk : chunkCoords()) {
            // MapGenBase.generate seeds per chunk; MapGenStructure.recursiveGenerate spends one int first.
            rand.setSeed((long) chunk[0] * a ^ (long) chunk[1] * c ^ seed);
            rand.nextInt();
            Start start;
            do start = build(rand, chunk[0], chunk[1]); while (start.portalRoom == null);
            built.add(start);
        }
        return starts = built;
    }

    /** MapGenStronghold.getClosestStrongholdPos: the middle of the nearest stronghold's staircase. */
    public int[] nearest(double x, double y, double z) {
        int[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Start start : starts()) {
            Box box = start.pieces.get(0).box;
            int[] centre = {box.minX + (box.maxX - box.minX + 1) / 2, box.minY + (box.maxY - box.minY + 1) / 2,
                box.minZ + (box.maxZ - box.minZ + 1) / 2};
            double dx = centre[0] - x, dy = centre[1] - y, dz = centre[2] - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) { bestDistance = distance; best = centre; }
        }
        return best;
    }

    // ---- pieces --------------------------------------------------------------------------------

    enum Type {
        STAIRS(-1, -7, 0, 5, 11, 5), STRAIGHT(-1, -1, 0, 5, 5, 7), PRISON(-1, -1, 0, 9, 5, 11),
        LEFT_TURN(-1, -1, 0, 5, 5, 5), RIGHT_TURN(-1, -1, 0, 5, 5, 5), ROOM_CROSSING(-4, -1, 0, 11, 7, 11),
        STAIRS_STRAIGHT(-1, -7, 0, 5, 11, 8), CROSSING(-4, -3, 0, 10, 9, 11), CHEST_CORRIDOR(-1, -1, 0, 5, 5, 7),
        LIBRARY(-4, -1, 0, 14, 11, 15), PORTAL_ROOM(-4, -1, 0, 11, 8, 16), CORRIDOR(-1, -1, 0, 5, 5, 4);
        final int offA, offB, offC, sizeA, sizeB, sizeC;
        Type(int offA, int offB, int offC, int sizeA, int sizeB, int sizeC) {
            this.offA = offA; this.offB = offB; this.offC = offC;
            this.sizeA = sizeA; this.sizeB = sizeB; this.sizeC = sizeC;
        }
    }

    /** StructureStrongholdPieces.PieceWeight; the library needs depth 5+, the portal room 6+. */
    private static final class Weight {
        final Type type;
        final int weight, max;
        int placed;
        Weight(Type type, int weight, int max) { this.type = type; this.weight = weight; this.max = max; }
        boolean canPlace() { return max == 0 || placed < max; }
        boolean canPlaceAt(int depth) {
            return canPlace() && (type != Type.LIBRARY || depth > 4) && (type != Type.PORTAL_ROOM || depth > 5);
        }
    }

    private static List<Weight> weights() {
        return new ArrayList<>(List.of(new Weight(Type.STRAIGHT, 40, 0), new Weight(Type.PRISON, 5, 5),
            new Weight(Type.LEFT_TURN, 20, 0), new Weight(Type.RIGHT_TURN, 20, 0), new Weight(Type.ROOM_CROSSING, 10, 6),
            new Weight(Type.STAIRS_STRAIGHT, 5, 5), new Weight(Type.STAIRS, 5, 5), new Weight(Type.CROSSING, 5, 4),
            new Weight(Type.CHEST_CORRIDOR, 5, 4), new Weight(Type.LIBRARY, 10, 2), new Weight(Type.PORTAL_ROOM, 20, 1)));
    }

    /** Stronghold.Door. */
    static final int OPENING = 0, WOOD_DOOR = 1, GRATES = 2, IRON_DOOR = 3;

    /** One placed piece and the random choices its constructor made. */
    public static final class Piece {
        final Type type;
        public final Box box;
        final Facing facing;
        final int depth;
        int door;
        /** Straight: side openings; Crossing: left low, left high, right low, right high. */
        boolean a, b, c, d;
        /** RoomCrossing: 0 pillar, 1 fountain, 2 storeroom, else empty. */
        int roomType;
        boolean start;
        Piece(Type type, Box box, Facing facing, int depth) {
            this.type = type; this.box = box; this.facing = facing; this.depth = depth;
        }
    }

    /** MapGenStronghold.Start: the pieces grown from the staircase (Stairs2). */
    public static final class Start {
        public final List<Piece> pieces = new ArrayList<>();
        public Box box;
        final List<Weight> weights = weights();
        final List<Piece> pending = new ArrayList<>();
        Weight last;
        Type strong;
        public Piece portalRoom;
    }

    /** EnumFacing.Plane.HORIZONTAL.random: north, east, south, west. */
    private static final Facing[] PLANE = {Facing.NORTH, Facing.EAST, Facing.SOUTH, Facing.WEST};

    private static int randomDoor(Random rand) {
        return switch (rand.nextInt(5)) { case 2 -> WOOD_DOOR; case 3 -> GRATES; case 4 -> IRON_DOOR; default -> OPENING; };
    }

    private Start build(Random rand, int chunkX, int chunkZ) {
        Start start = new Start();
        int x = (chunkX << 4) + 2, z = (chunkZ << 4) + 2;
        Piece first = new Piece(Type.STAIRS, new Box(x, 64, z, x + 4, 74, z + 4), PLANE[rand.nextInt(4)], 0);
        first.start = true;
        first.door = OPENING;
        start.pieces.add(first);
        buildComponent(start, first, rand);
        while (!start.pending.isEmpty()) buildComponent(start, start.pending.remove(rand.nextInt(start.pending.size())), rand);
        Box bounds = new Box(first.box.minX, first.box.minY, first.box.minZ, first.box.maxX, first.box.maxY, first.box.maxZ);
        for (Piece piece : start.pieces) bounds.expandTo(piece.box);
        // StructureStart.markAvailableHeight(world, rand, 10): the top sinks below sea level - 10.
        int limit = 63 - 10, top = bounds.ySize() + 1;
        if (top < limit) top += rand.nextInt(limit - top);
        int dy = top - bounds.maxY;
        bounds.offset(0, dy, 0);
        for (Piece piece : start.pieces) piece.box.offset(0, dy, 0);
        start.box = bounds;
        return start;
    }

    /** Each piece's buildComponent: where its exits lead. */
    private void buildComponent(Start start, Piece p, Random rand) {
        boolean northOrEast = p.facing == Facing.NORTH || p.facing == Facing.EAST;
        switch (p.type) {
            case STAIRS -> {
                if (p.start) start.strong = Type.CROSSING;
                normal(start, p, rand, 1, 1);
            }
            case STRAIGHT -> {
                normal(start, p, rand, 1, 1);
                if (p.a) left(start, p, rand, 1, 2);
                if (p.b) right(start, p, rand, 1, 2);
            }
            case PRISON, STAIRS_STRAIGHT, CHEST_CORRIDOR -> normal(start, p, rand, 1, 1);
            case LEFT_TURN -> { if (northOrEast) left(start, p, rand, 1, 1); else right(start, p, rand, 1, 1); }
            case RIGHT_TURN -> { if (northOrEast) right(start, p, rand, 1, 1); else left(start, p, rand, 1, 1); }
            case ROOM_CROSSING -> {
                normal(start, p, rand, 4, 1);
                left(start, p, rand, 1, 4);
                right(start, p, rand, 1, 4);
            }
            case CROSSING -> {
                int low = 3, high = 5;
                // Vanilla swaps the heights facing west or north, so those side exits miss their openings.
                if (p.facing == Facing.WEST || p.facing == Facing.NORTH) { low = 8 - low; high = 8 - high; }
                normal(start, p, rand, 5, 1);
                if (p.a) left(start, p, rand, low, 1);
                if (p.b) left(start, p, rand, high, 7);
                if (p.c) right(start, p, rand, low, 1);
                if (p.d) right(start, p, rand, high, 7);
            }
            case PORTAL_ROOM -> start.portalRoom = p;
            default -> { }
        }
    }

    private void normal(Start start, Piece p, Random rand, int offX, int offY) {
        Box bb = p.box;
        switch (p.facing) {
            case NORTH -> next(start, rand, bb.minX + offX, bb.minY + offY, bb.minZ - 1, p.facing, p.depth);
            case SOUTH -> next(start, rand, bb.minX + offX, bb.minY + offY, bb.maxZ + 1, p.facing, p.depth);
            case WEST -> next(start, rand, bb.minX - 1, bb.minY + offY, bb.minZ + offX, p.facing, p.depth);
            case EAST -> next(start, rand, bb.maxX + 1, bb.minY + offY, bb.minZ + offX, p.facing, p.depth);
        }
    }

    private void left(Start start, Piece p, Random rand, int offY, int offZ) {
        Box bb = p.box;
        switch (p.facing) {
            case NORTH, SOUTH -> next(start, rand, bb.minX - 1, bb.minY + offY, bb.minZ + offZ, Facing.WEST, p.depth);
            case WEST, EAST -> next(start, rand, bb.minX + offZ, bb.minY + offY, bb.minZ - 1, Facing.NORTH, p.depth);
        }
    }

    private void right(Start start, Piece p, Random rand, int offY, int offZ) {
        Box bb = p.box;
        switch (p.facing) {
            case NORTH, SOUTH -> next(start, rand, bb.maxX + 1, bb.minY + offY, bb.minZ + offZ, Facing.EAST, p.depth);
            case WEST, EAST -> next(start, rand, bb.minX + offZ, bb.minY + offY, bb.maxZ + 1, Facing.SOUTH, p.depth);
        }
    }

    /** StructureStrongholdPieces.func_175953_c. */
    private void next(Start start, Random rand, int x, int y, int z, Facing facing, int depth) {
        if (depth > 50) return;
        Box first = start.pieces.get(0).box;
        if (Math.abs(x - first.minX) > 112 || Math.abs(z - first.minZ) > 112) return;
        Piece piece = pick(start, rand, x, y, z, facing, depth + 1);
        if (piece != null) {
            start.pieces.add(piece);
            start.pending.add(piece);
        }
    }

    /** StructureStrongholdPieces.func_175954_a. */
    private Piece pick(Start start, Random rand, int x, int y, int z, Facing facing, int depth) {
        int total = 0;
        boolean any = false;
        for (Weight w : start.weights) {
            if (w.max > 0 && w.placed < w.max) any = true;
            total += w.weight;
        }
        if (!any) return null;
        if (start.strong != null) {
            Piece piece = create(start, start.strong, rand, x, y, z, facing, depth);
            start.strong = null;
            if (piece != null) return piece;
        }
        for (int tries = 0; tries < 5; tries++) {
            int k = rand.nextInt(total);
            for (Weight w : start.weights) {
                k -= w.weight;
                if (k < 0) {
                    if (!w.canPlaceAt(depth) || w == start.last) break;
                    Piece piece = create(start, w.type, rand, x, y, z, facing, depth);
                    if (piece != null) {
                        w.placed++;
                        start.last = w;
                        if (!w.canPlace()) start.weights.remove(w);
                        return piece;
                    }
                }
            }
        }
        Box bb = corridorBox(start, x, y, z, facing);
        return bb != null && bb.minY > 1 ? new Piece(Type.CORRIDOR, bb, facing, depth) : null;
    }

    /** Corridor.func_175869_a: a corridor just long enough to reach the piece it would run into, or null. */
    private static Box corridorBox(Start start, int x, int y, int z, Facing facing) {
        Box bb = Box.component(x, y, z, -1, -1, 0, 5, 5, 4, facing);
        Piece hit = intersecting(start, bb);
        if (hit == null || hit.box.minY != bb.minY) return null;
        for (int j = 3; j >= 1; j--) {
            bb = Box.component(x, y, z, -1, -1, 0, 5, 5, j - 1, facing);
            if (!hit.box.intersects(bb)) return Box.component(x, y, z, -1, -1, 0, 5, 5, j, facing);
        }
        return null;
    }

    private static Piece intersecting(Start start, Box bb) {
        for (Piece other : start.pieces) if (other.box.intersects(bb)) return other;
        return null;
    }

    /** Each piece's static constructor: its box must be above y=10 and clear of every other piece. */
    private Piece create(Start start, Type type, Random rand, int x, int y, int z, Facing facing, int depth) {
        Box box = Box.component(x, y, z, type.offA, type.offB, type.offC, type.sizeA, type.sizeB, type.sizeC, facing);
        if (type == Type.LIBRARY && (box.minY <= 10 || intersecting(start, box) != null))
            box = Box.component(x, y, z, type.offA, type.offB, type.offC, type.sizeA, 6, type.sizeC, facing);
        if (box.minY <= 10 || intersecting(start, box) != null) return null;
        Piece piece = new Piece(type, box, facing, depth);
        if (type != Type.PORTAL_ROOM) piece.door = randomDoor(rand);
        switch (type) {
            case STRAIGHT -> { piece.a = rand.nextInt(2) == 0; piece.b = rand.nextInt(2) == 0; }
            case ROOM_CROSSING -> piece.roomType = rand.nextInt(5);
            case CROSSING -> {
                piece.a = rand.nextBoolean();
                piece.b = rand.nextBoolean();
                piece.c = rand.nextBoolean();
                piece.d = rand.nextInt(3) > 0;
            }
            default -> { }
        }
        return piece;
    }

    // ---- building blocks ---------------------------------------------------------------------

    /**
     * MapGenStructure.generateStructure: the pieces of each stronghold that
     * reach into the population area (the chunk shifted by 8) are built there.
     */
    public void generate(GenWorld w, int chunkX, int chunkZ) {
        int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8;
        Box clip = new Box(x0, 1, z0, x0 + 15, 512, z0 + 15);
        Random rand = null;
        for (Start start : starts()) {
            if (!start.box.intersectsXZ(clip.minX, clip.minZ, clip.maxX, clip.maxZ)) continue;
            if (rand == null) rand = new Random((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L ^ seed);
            for (Piece piece : start.pieces)
                if (piece.box.intersectsXZ(clip.minX, clip.minZ, clip.maxX, clip.maxZ)) new Builder(w, piece, clip, rand).build();
        }
    }

    /** One piece's addComponentParts, in its own coordinates (x across, y up, z along its facing). */
    private final class Builder {
        final GenWorld w;
        final Piece p;
        final Box clip;
        final Random rand;

        Builder(GenWorld w, Piece p, Box clip, Random rand) {
            this.w = w; this.p = p; this.clip = clip; this.rand = rand;
        }

        int wx(int x, int z) {
            return switch (p.facing) {
                case NORTH, SOUTH -> p.box.minX + x;
                case WEST -> p.box.maxX - z;
                case EAST -> p.box.minX + z;
            };
        }
        int wy(int y) { return p.box.minY + y; }
        int wz(int x, int z) {
            return switch (p.facing) {
                case NORTH -> p.box.maxZ - z;
                case SOUTH -> p.box.minZ + z;
                case WEST, EAST -> p.box.minZ + x;
            };
        }

        boolean inside(int x, int y, int z) { return clip.contains(wx(x, z), wy(y), wz(x, z)); }
        boolean isAir(int x, int y, int z) { return !inside(x, y, z) || b.isAir(w.get(wx(x, z), wy(y), wz(x, z))); }

        void set(int state, int x, int y, int z) {
            int X = wx(x, z), Y = wy(y), Z = wz(x, z);
            if (clip.contains(X, Y, Z)) w.set(X, Y, Z, state);
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, int state) {
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++) set(state, x, y, z);
        }

        /** fillWithRandomizedBlocks with the Stones selector: random bricks on the edges, air inside. */
        void stones(int x0, int y0, int z0, int x1, int y1, int z1, boolean existingOnly) {
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++) {
                        if (existingOnly && isAir(x, y, z)) continue;
                        boolean edge = y == y0 || y == y1 || x == x0 || x == x1 || z == z0 || z == z1;
                        int state = b.AIR;
                        if (edge) {
                            float f = rand.nextFloat();
                            state = f < 0.2f ? cracked : f < 0.5f ? mossy : f < 0.55f ? silverfish : stonebrick;
                        }
                        set(state, x, y, z);
                    }
        }

        /** func_175805_a: each block by chance, skipping air when {@code existingOnly}. */
        void scatter(float chance, int x0, int y0, int z0, int x1, int y1, int z1, int state) {
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++)
                        if (rand.nextFloat() <= chance) set(state, x, y, z);
        }

        /** randomlyPlaceBlock. */
        void maybe(float chance, int x, int y, int z, int state) {
            if (inside(x, y, z) && rand.nextFloat() < chance) set(state, x, y, z);
        }

        /**
         * A metadata for a facing given as the piece faces north
         * (getMetadataWithOffset): N, S, E or W turned with the piece.
         */
        char turn(char dir) {
            int lx = dir == 'E' ? 1 : dir == 'W' ? -1 : 0, lz = dir == 'N' ? 1 : dir == 'S' ? -1 : 0;
            int dx, dz;
            switch (p.facing) {
                case NORTH -> { dx = lx; dz = -lz; }
                case SOUTH -> { dx = lx; dz = lz; }
                case WEST -> { dx = -lz; dz = lx; }
                default -> { dx = lz; dz = lx; }
            }
            return dx > 0 ? 'E' : dx < 0 ? 'W' : dz > 0 ? 'S' : 'N';
        }
        int stairs(int base, char dir) { return base | "EWSN".indexOf(turn(dir)); }
        int wallTorch(char dir) { return torch | "?EWSN".indexOf(turn(dir)); }
        int ladder(char dir) { return ladder | "??NSWE".indexOf(turn(dir)); }
        int doorBottom(int door, char dir) { return door | "ESWN".indexOf(turn(dir)); }
        int button(char dir) { return button | "?EWSN".indexOf(turn(dir)); }
        int frame(char dir, boolean eye) { return frame | "SWNE".indexOf(turn(dir)) | (eye ? 4 : 0); }

        void chest(int x, int y, int z) {
            int X = wx(x, z), Y = wy(y), Z = wz(x, z);
            if (clip.contains(X, Y, Z)) w.set(X, Y, Z, GenBlocks.withMeta(b.CHEST, Features.chestFacing(w, X, Y, Z)));
        }

        /** Stronghold.placeDoor: a 3x3 doorway of the given kind. */
        void door(int kind, int x, int y, int z) {
            switch (kind) {
                case OPENING -> fill(x, y, z, x + 2, y + 2, z, b.AIR);
                case WOOD_DOOR, IRON_DOOR -> {
                    for (int[] at : new int[][] {{0, 0}, {0, 1}, {0, 2}, {1, 2}, {2, 2}, {2, 1}, {2, 0}}) set(stonebrick, x + at[0], y + at[1], z);
                    int door = kind == WOOD_DOOR ? woodDoor : ironDoor;
                    set(doorBottom(door, 'N'), x + 1, y, z);
                    set(door | 8, x + 1, y + 1, z);
                    if (kind == IRON_DOOR) {
                        set(button('N'), x + 2, y + 1, z + 1);
                        set(button('S'), x + 2, y + 1, z - 1);
                    }
                }
                case GRATES -> {
                    set(b.AIR, x + 1, y, z);
                    set(b.AIR, x + 1, y + 1, z);
                    for (int[] at : new int[][] {{0, 0}, {0, 1}, {0, 2}, {1, 2}, {2, 2}, {2, 1}, {2, 0}}) set(ironBars, x + at[0], y + at[1], z);
                }
                default -> { }
            }
        }

        void build() {
            switch (p.type) {
                case STAIRS -> stairs();
                case STRAIGHT -> straight();
                case PRISON -> prison();
                case LEFT_TURN -> turn(p.facing == Facing.NORTH || p.facing == Facing.EAST ? 0 : 4);
                case RIGHT_TURN -> turn(p.facing == Facing.NORTH || p.facing == Facing.EAST ? 4 : 0);
                case ROOM_CROSSING -> roomCrossing();
                case STAIRS_STRAIGHT -> stairsStraight();
                case CROSSING -> crossing();
                case CHEST_CORRIDOR -> chestCorridor();
                case LIBRARY -> library();
                case PORTAL_ROOM -> portalRoom();
                case CORRIDOR -> corridor();
            }
        }

        /** Stairs: a spiral staircase seven blocks down. */
        void stairs() {
            stones(0, 0, 0, 4, 10, 4, true);
            door(p.door, 1, 7, 0);
            door(OPENING, 1, 1, 4);
            int[][] bricks = {{2, 6, 1}, {1, 5, 1}, {1, 5, 2}, {1, 4, 3}, {2, 4, 3}, {3, 3, 3}, {3, 3, 2}, {3, 2, 1}, {2, 2, 1},
                {1, 1, 1}, {1, 1, 2}};
            int[][] slabs = {{1, 6, 1}, {1, 5, 3}, {3, 4, 3}, {3, 3, 1}, {1, 2, 1}, {1, 1, 3}};
            for (int[] at : bricks) set(stonebrick, at[0], at[1], at[2]);
            for (int[] at : slabs) set(slab, at[0], at[1], at[2]);
        }

        void straight() {
            stones(0, 0, 0, 4, 4, 6, true);
            door(p.door, 1, 1, 0);
            door(OPENING, 1, 1, 6);
            maybe(0.1f, 1, 2, 1, torch | 5);
            maybe(0.1f, 3, 2, 1, torch | 5);
            maybe(0.1f, 1, 2, 5, torch | 5);
            maybe(0.1f, 3, 2, 5, torch | 5);
            if (p.a) fill(0, 1, 2, 0, 3, 4, b.AIR);
            if (p.b) fill(4, 1, 2, 4, 3, 4, b.AIR);
        }

        void prison() {
            stones(0, 0, 0, 8, 4, 10, true);
            door(p.door, 1, 1, 0);
            fill(1, 1, 10, 3, 3, 10, b.AIR);
            stones(4, 1, 1, 4, 3, 1, false);
            stones(4, 1, 3, 4, 3, 3, false);
            stones(4, 1, 7, 4, 3, 7, false);
            stones(4, 1, 9, 4, 3, 9, false);
            fill(4, 1, 4, 4, 3, 6, ironBars);
            fill(5, 1, 5, 7, 3, 5, ironBars);
            set(ironBars, 4, 3, 2);
            set(ironBars, 4, 3, 8);
            set(doorBottom(ironDoor, 'N'), 4, 1, 2);
            set(ironDoor | 8, 4, 2, 2);
            set(doorBottom(ironDoor, 'N'), 4, 1, 8);
            set(ironDoor | 8, 4, 2, 8);
        }

        /** LeftTurn / RightTurn: the exit on the side at {@code side} (0 or 4). */
        void turn(int side) {
            stones(0, 0, 0, 4, 4, 4, true);
            door(p.door, 1, 1, 0);
            fill(side, 1, 1, side, 3, 3, b.AIR);
        }

        void roomCrossing() {
            stones(0, 0, 0, 10, 6, 10, true);
            door(p.door, 4, 1, 0);
            fill(4, 1, 10, 6, 3, 10, b.AIR);
            fill(0, 1, 4, 0, 3, 6, b.AIR);
            fill(10, 1, 4, 10, 3, 6, b.AIR);
            switch (p.roomType) {
                case 0 -> {
                    fill(5, 1, 5, 5, 3, 5, stonebrick);
                    set(wallTorch('W'), 4, 3, 5);
                    set(wallTorch('E'), 6, 3, 5);
                    set(wallTorch('S'), 5, 3, 4);
                    set(wallTorch('N'), 5, 3, 6);
                    for (int[] at : new int[][] {{4, 4}, {4, 5}, {4, 6}, {6, 4}, {6, 5}, {6, 6}, {5, 4}, {5, 6}}) set(slab, at[0], 1, at[1]);
                }
                case 1 -> {
                    for (int i = 0; i < 5; i++) {
                        set(stonebrick, 3, 1, 3 + i);
                        set(stonebrick, 7, 1, 3 + i);
                        set(stonebrick, 3 + i, 1, 3);
                        set(stonebrick, 3 + i, 1, 7);
                    }
                    fill(5, 1, 5, 5, 3, 5, stonebrick);
                    set(water, 5, 4, 5);
                }
                case 2 -> {
                    for (int i = 1; i <= 9; i++) {
                        set(cobble, 1, 3, i);
                        set(cobble, 9, 3, i);
                        set(cobble, i, 3, 1);
                        set(cobble, i, 3, 9);
                    }
                    for (int[] at : new int[][] {{5, 1, 4}, {5, 1, 6}, {5, 3, 4}, {5, 3, 6}, {4, 1, 5}, {6, 1, 5}, {4, 3, 5}, {6, 3, 5}})
                        set(cobble, at[0], at[1], at[2]);
                    for (int y = 1; y <= 3; y++) {
                        set(cobble, 4, y, 4);
                        set(cobble, 6, y, 4);
                        set(cobble, 4, y, 6);
                        set(cobble, 6, y, 6);
                    }
                    set(torch | 5, 5, 3, 5);
                    for (int z = 2; z <= 8; z++) {
                        set(planks, 2, 3, z);
                        set(planks, 3, 3, z);
                        if (z <= 3 || z >= 7) {
                            set(planks, 4, 3, z);
                            set(planks, 5, 3, z);
                            set(planks, 6, 3, z);
                        }
                        set(planks, 7, 3, z);
                        set(planks, 8, 3, z);
                    }
                    for (int y = 1; y <= 3; y++) set(ladder('W'), 9, y, 3);
                    chest(3, 4, 8);
                }
                default -> { }
            }
        }

        void stairsStraight() {
            stones(0, 0, 0, 4, 10, 7, true);
            door(p.door, 1, 7, 0);
            door(OPENING, 1, 1, 7);
            int step = stairs(stoneStairs, 'S');
            for (int j = 0; j < 6; j++) {
                for (int x = 1; x <= 3; x++) set(step, x, 6 - j, 1 + j);
                if (j < 5) for (int x = 1; x <= 3; x++) set(stonebrick, x, 5 - j, 1 + j);
            }
        }

        /** Crossing: the five-way crossing with its raised floor and slab balcony. */
        void crossing() {
            stones(0, 0, 0, 9, 8, 10, true);
            door(p.door, 4, 3, 0);
            if (p.a) fill(0, 3, 1, 0, 5, 3, b.AIR);
            if (p.c) fill(9, 3, 1, 9, 5, 3, b.AIR);
            if (p.b) fill(0, 5, 7, 0, 7, 9, b.AIR);
            if (p.d) fill(9, 5, 7, 9, 7, 9, b.AIR);
            fill(5, 1, 10, 7, 3, 10, b.AIR);
            stones(1, 2, 1, 8, 2, 6, false);
            stones(4, 1, 5, 4, 4, 9, false);
            stones(8, 1, 5, 8, 4, 9, false);
            stones(1, 4, 7, 3, 4, 9, false);
            stones(1, 3, 5, 3, 3, 6, false);
            fill(1, 3, 4, 3, 3, 4, slab);
            fill(1, 4, 6, 3, 4, 6, slab);
            stones(5, 1, 7, 7, 1, 8, false);
            fill(5, 1, 9, 7, 1, 9, slab);
            fill(5, 2, 7, 7, 2, 7, slab);
            fill(4, 5, 7, 4, 5, 9, slab);
            fill(8, 5, 7, 8, 5, 9, slab);
            fill(5, 5, 7, 7, 5, 9, doubleSlab);
            set(wallTorch('S'), 6, 5, 6);
        }

        void chestCorridor() {
            stones(0, 0, 0, 4, 4, 6, true);
            door(p.door, 1, 1, 0);
            door(OPENING, 1, 1, 6);
            fill(3, 1, 2, 3, 1, 4, stonebrick);
            set(brickSlab, 3, 1, 1);
            set(brickSlab, 3, 1, 5);
            set(brickSlab, 3, 2, 2);
            set(brickSlab, 3, 2, 4);
            for (int z = 2; z <= 4; z++) set(brickSlab, 2, 1, z);
            chest(3, 2, 3);
        }

        void library() {
            boolean large = p.box.ySize() > 6;
            int top = large ? 11 : 6;
            stones(0, 0, 0, 13, top - 1, 14, true);
            door(p.door, 4, 1, 0);
            scatter(0.07f, 2, 1, 1, 11, 4, 13, web);
            for (int z = 1; z <= 13; z++) {
                int shelf = (z - 1) % 4 == 0 ? planks : bookshelf;
                fill(1, 1, z, 1, 4, z, shelf);
                fill(12, 1, z, 12, 4, z, shelf);
                if (shelf == planks) {
                    set(wallTorch('E'), 2, 3, z);
                    set(wallTorch('W'), 11, 3, z);
                }
                if (large) {
                    fill(1, 6, z, 1, 9, z, shelf);
                    fill(12, 6, z, 12, 9, z, shelf);
                }
            }
            for (int z = 3; z < 12; z += 2) {
                fill(3, 1, z, 4, 3, z, bookshelf);
                fill(6, 1, z, 7, 3, z, bookshelf);
                fill(9, 1, z, 10, 3, z, bookshelf);
            }
            if (large) {
                fill(1, 5, 1, 3, 5, 13, planks);
                fill(10, 5, 1, 12, 5, 13, planks);
                fill(4, 5, 1, 9, 5, 2, planks);
                fill(4, 5, 12, 9, 5, 13, planks);
                set(planks, 9, 5, 11);
                set(planks, 8, 5, 11);
                set(planks, 9, 5, 10);
                fill(3, 6, 2, 3, 6, 12, fence);
                fill(10, 6, 2, 10, 6, 9, fence);
                fill(4, 6, 2, 9, 6, 2, fence);
                fill(4, 6, 12, 8, 6, 12, fence);
                set(fence, 9, 6, 11);
                set(fence, 8, 6, 11);
                set(fence, 9, 6, 10);
                for (int y = 1; y <= 7; y++) set(ladder('S'), 10, y, 13);
                // The chandelier.
                int cx = 7, cz = 7;
                for (int y = 7; y <= 9; y++) { set(fence, cx - 1, y, cz); set(fence, cx, y, cz); }
                for (int[] at : new int[][] {{cx - 2, cz}, {cx + 1, cz}, {cx - 1, cz - 1}, {cx, cz - 1}, {cx - 1, cz + 1}, {cx, cz + 1}}) {
                    set(fence, at[0], 7, at[1]);
                    set(torch | 5, at[0], 8, at[1]);
                }
            }
            chest(3, 3, 5);
            if (large) {
                set(b.AIR, 12, 9, 1);
                chest(12, 8, 1);
            }
        }

        /** PortalRoom: the end portal frames over a lava pool, a silverfish spawner on the stairs. */
        void portalRoom() {
            stones(0, 0, 0, 10, 7, 15, false);
            door(GRATES, 4, 1, 0);
            stones(1, 6, 1, 1, 6, 14, false);
            stones(9, 6, 1, 9, 6, 14, false);
            stones(2, 6, 1, 8, 6, 2, false);
            stones(2, 6, 14, 8, 6, 14, false);
            stones(1, 1, 1, 2, 1, 4, false);
            stones(8, 1, 1, 9, 1, 4, false);
            fill(1, 1, 1, 1, 1, 3, lava);
            fill(9, 1, 1, 9, 1, 3, lava);
            stones(3, 1, 8, 7, 1, 12, false);
            fill(4, 1, 9, 6, 1, 11, lava);
            for (int z = 3; z < 14; z += 2) {
                fill(0, 3, z, 0, 4, z, ironBars);
                fill(10, 3, z, 10, 4, z, ironBars);
            }
            for (int x = 2; x < 9; x += 2) fill(x, 3, 15, x, 4, 15, ironBars);
            int step = stairs(brickStairs, 'N');
            stones(4, 1, 5, 6, 1, 7, false);
            stones(4, 2, 6, 6, 2, 7, false);
            stones(4, 3, 7, 6, 3, 7, false);
            for (int x = 4; x <= 6; x++) {
                set(step, x, 1, 4);
                set(step, x, 2, 5);
                set(step, x, 3, 6);
            }
            boolean[] eyes = new boolean[12];
            for (int i = 0; i < 12; i++) eyes[i] = rand.nextFloat() > 0.9f;
            for (int i = 0; i < 3; i++) {
                set(frame('N', eyes[i]), 4 + i, 3, 8);
                set(frame('S', eyes[3 + i]), 4 + i, 3, 12);
                set(frame('E', eyes[6 + i]), 3, 3, 9 + i);
                set(frame('W', eyes[9 + i]), 7, 3, 9 + i);
            }
            int X = wx(5, 6), Y = wy(3), Z = wz(5, 6);
            if (clip.contains(X, Y, Z)) {
                w.set(X, Y, Z, b.MOB_SPAWNER);
                w.setSpawner(X, Y, Z, "Silverfish");
            }
        }

        /** Corridor: a plain stretch joining two pieces. */
        void corridor() {
            int steps = p.facing == Facing.NORTH || p.facing == Facing.SOUTH ? p.box.maxZ - p.box.minZ + 1 : p.box.maxX - p.box.minX + 1;
            for (int i = 0; i < steps; i++) {
                fill(0, 0, i, 4, 0, i, stonebrick);
                for (int y = 1; y <= 3; y++) {
                    set(stonebrick, 0, y, i);
                    fill(1, y, i, 3, y, i, b.AIR);
                    set(stonebrick, 4, y, i);
                }
                fill(0, 4, i, 4, 4, i, stonebrick);
            }
        }
    }
}

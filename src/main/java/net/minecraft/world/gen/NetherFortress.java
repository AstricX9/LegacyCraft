package net.minecraft.world.gen;

import net.minecraft.nbt.NbtTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Nether fortresses (1.8.9 MapGenNetherBridge and StructureNetherBridgePieces,
 * HYPOTHESIS: recalled). In each 16x16-chunk region one chunk in three (at a
 * random spot 4-11 chunks into the region) starts a fortress: a bridge
 * crossing grows bridges, crossings, stair rooms, blaze thrones and lava-well
 * entrances (the "primary" pieces) and, past an entrance, castle corridors,
 * corridor crossings, turns, stairs, T rooms and nether wart rooms (the
 * "secondary" pieces), picked by weight with per-piece limits, never
 * overlapping, within 112 blocks of the start and at most 30 pieces deep;
 * where a piece can't go a broken bridge end is placed. The whole fortress
 * is then moved to a random height between 48 and 70. Blocks are placed by
 * population, clipped to each populated area; supports run down through air
 * and lava to the ground. Fortresses are saved like 1.8.9's
 * MapGenStructureData in data/Fortress.dat (see {@link #writeNbt}).
 */
public final class NetherFortress {
    private final GenBlocks b;
    private final long seed;
    private final Map<Long, Start> starts = new HashMap<>();
    private final int stairsId, chestState;

    NetherFortress(GenBlocks blocks, long seed) {
        this.b = blocks;
        this.seed = seed;
        this.stairsId = blocks.registry().get("minecraft:nether_brick_stairs").registryId();
        this.chestState = blocks.CHEST;
    }

    // ---- geometry -----------------------------------------------------------------------------

    /** StructureBoundingBox. */
    public static final class Box {
        public int minX, minY, minZ, maxX, maxY, maxZ;
        Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        boolean intersects(Box o) {
            return maxX >= o.minX && minX <= o.maxX && maxZ >= o.minZ && minZ <= o.maxZ && maxY >= o.minY && minY <= o.maxY;
        }
        boolean intersectsXZ(int x0, int z0, int x1, int z1) { return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1; }
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
        }
        void offset(int dx, int dy, int dz) {
            minX += dx; minY += dy; minZ += dz; maxX += dx; maxY += dy; maxZ += dz;
        }
        void expandTo(Box o) {
            minX = Math.min(minX, o.minX); minY = Math.min(minY, o.minY); minZ = Math.min(minZ, o.minZ);
            maxX = Math.max(maxX, o.maxX); maxY = Math.max(maxY, o.maxY); maxZ = Math.max(maxZ, o.maxZ);
        }
        int ySize() { return maxY - minY + 1; }

        /** StructureBoundingBox.getComponentToAddBoundingBox. */
        static Box component(int x, int y, int z, int offA, int offB, int offC, int sizeA, int sizeB, int sizeC, Facing facing) {
            return switch (facing) {
                case NORTH -> new Box(x + offA, y + offB, z - sizeC + 1 + offC, x + sizeA - 1 + offA, y + sizeB - 1 + offB, z + offC);
                case SOUTH -> new Box(x + offA, y + offB, z + offC, x + sizeA - 1 + offA, y + sizeB - 1 + offB, z + sizeC - 1 + offC);
                case WEST -> new Box(x - sizeC + 1 + offC, y + offB, z + offA, x + offC, y + sizeB - 1 + offB, z + sizeA - 1 + offA);
                case EAST -> new Box(x + offC, y + offB, z + offA, x + sizeC - 1 + offC, y + sizeB - 1 + offB, z + sizeA - 1 + offA);
            };
        }
    }

    /** EnumFacing.Plane.HORIZONTAL in index order (south, west, north, east). */
    enum Facing { SOUTH, WEST, NORTH, EAST }

    // ---- pieces --------------------------------------------------------------------------------

    enum Type {
        STRAIGHT(-1, -3, 0, 5, 10, 19), CROSSING3(-8, -3, 0, 19, 10, 19), CROSSING(-2, 0, 0, 7, 9, 7),
        STAIRS(-2, 0, 0, 7, 11, 7), THRONE(-2, 0, 0, 7, 8, 9), ENTRANCE(-5, -3, 0, 13, 14, 13),
        CORRIDOR5(-1, 0, 0, 5, 7, 5), CROSSING2(-1, 0, 0, 5, 7, 5), CORRIDOR(-1, 0, 0, 5, 7, 5),
        CORRIDOR2(-1, 0, 0, 5, 7, 5), CORRIDOR3(-1, -7, 0, 5, 14, 10), CORRIDOR4(-3, 0, 0, 9, 7, 9),
        STALK_ROOM(-5, -3, 0, 13, 14, 13), END(-1, -3, 0, 5, 10, 8);
        final int offA, offB, offC, sizeA, sizeB, sizeC;
        Type(int offA, int offB, int offC, int sizeA, int sizeB, int sizeC) {
            this.offA = offA; this.offB = offB; this.offC = offC;
            this.sizeA = sizeA; this.sizeB = sizeB; this.sizeC = sizeC;
        }

        /** The piece's id in saved structures (StructureNetherBridgePieces.registerNetherFortressPieces). */
        String id() {
            return switch (this) {
                case CROSSING3 -> "NeBCr"; case END -> "NeBEF"; case STRAIGHT -> "NeBS"; case CORRIDOR3 -> "NeCCS";
                case CORRIDOR4 -> "NeCTB"; case ENTRANCE -> "NeCE"; case CROSSING2 -> "NeSCSC"; case CORRIDOR -> "NeSCLT";
                case CORRIDOR5 -> "NeSC"; case CORRIDOR2 -> "NeSCRT"; case STALK_ROOM -> "NeCSR"; case THRONE -> "NeMT";
                case CROSSING -> "NeRC"; case STAIRS -> "NeSR";
            };
        }

        static Type byId(String id) {
            if (id.equals("NeStart")) return CROSSING3;
            for (Type type : values()) if (type.id().equals(id)) return type;
            return null;
        }
    }

    /** StructureNetherBridgePieces.PieceWeight: weight, how many may be placed, whether it may follow itself. */
    private static final class Weight {
        final Type type;
        final int weight, max;
        final boolean inRow;
        int placed;
        Weight(Type type, int weight, int max, boolean inRow) {
            this.type = type; this.weight = weight; this.max = max; this.inRow = inRow;
        }
        Weight(Type type, int weight, int max) { this(type, weight, max, false); }
        boolean canPlace() { return max == 0 || placed < max; }
    }

    private static List<Weight> primary() {
        return new ArrayList<>(List.of(new Weight(Type.STRAIGHT, 30, 0, true), new Weight(Type.CROSSING3, 10, 4),
            new Weight(Type.CROSSING, 10, 4), new Weight(Type.STAIRS, 10, 3), new Weight(Type.THRONE, 5, 2),
            new Weight(Type.ENTRANCE, 5, 1)));
    }

    private static List<Weight> secondary() {
        return new ArrayList<>(List.of(new Weight(Type.CORRIDOR5, 25, 0, true), new Weight(Type.CROSSING2, 15, 5),
            new Weight(Type.CORRIDOR2, 5, 10), new Weight(Type.CORRIDOR, 5, 10), new Weight(Type.CORRIDOR3, 10, 3, true),
            new Weight(Type.CORRIDOR4, 7, 2), new Weight(Type.STALK_ROOM, 5, 2)));
    }

    /** One placed piece (StructureComponent): its box, facing and depth, and per-piece state. */
    public static final class Piece {
        final Type type;
        public final Box box;
        final Facing facing;
        final int depth;
        /** End: the seed of its ragged edges. */
        int fillSeed;
        /** Corridor/Corridor2: a chest still to place. Throne: a spawner still to place. */
        boolean pending;
        Piece(Type type, Box box, Facing facing, int depth) {
            this.type = type; this.box = box; this.facing = facing; this.depth = depth;
        }
    }

    /** StructureNetherBridgePieces.Start (a bridge crossing) and the fortress it grew. */
    public static final class Start {
        public final List<Piece> pieces = new ArrayList<>();
        public final Box box;
        final List<Weight> primary = primary(), secondary = secondary();
        final List<Piece> pending = new ArrayList<>();
        Weight last;
        Start(Box box) { this.box = box; }
    }

    // ---- placement ------------------------------------------------------------------------------

    /** MapGenNetherBridge.canSpawnStructureAtCoords (the random draws continue into the start). */
    private boolean canSpawnAt(Random rand, int chunkX, int chunkZ) {
        int i = chunkX >> 4, j = chunkZ >> 4;
        rand.setSeed((long) (i ^ j << 4) ^ seed);
        rand.nextInt();
        if (rand.nextInt(3) != 0) return false;
        if (chunkX != (i << 4) + 4 + rand.nextInt(8)) return false;
        return chunkZ == (j << 4) + 4 + rand.nextInt(8);
    }

    /** The fortress started in this chunk, or null; computed once (MapGenStructure.structureMap). */
    public synchronized Start startAt(int chunkX, int chunkZ) {
        long key = (long) chunkX << 32 ^ (chunkZ & 0xffffffffL);
        if (starts.containsKey(key)) return starts.get(key);
        Random rand = new Random();
        Start start = canSpawnAt(rand, chunkX, chunkZ) ? build(rand, chunkX, chunkZ) : null;
        starts.put(key, start);
        return start;
    }

    /** {@link #startsNear} by chunk, remembered (spawning asks for every spot it tries). */
    private final Map<Long, List<Start>> nearCache = new HashMap<>();

    /** The fortresses whose start lies within 8 chunks (and so may reach this chunk). */
    public synchronized List<Start> startsNear(int chunkX, int chunkZ) {
        long key = (long) chunkX << 32 ^ (chunkZ & 0xffffffffL);
        List<Start> cached = nearCache.get(key);
        if (cached != null) return cached;
        List<Start> result = new ArrayList<>();
        for (int x = chunkX - 8; x <= chunkX + 8; x++)
            for (int z = chunkZ - 8; z <= chunkZ + 8; z++) {
                Start start = startAt(x, z);
                if (start != null) result.add(start);
            }
        if (nearCache.size() > 65536) nearCache.clear();
        nearCache.put(key, result);
        return result;
    }

    // ---- saving (MapGenStructureData, data/Fortress.dat) ------------------------------------------

    /**
     * The fortresses as 1.8.9 saves them: {data: {Features: {"[x,z]": start}}},
     * each start {id: "Fortress", ChunkX, ChunkZ, BB: [minX, minY, minZ,
     * maxX, maxY, maxZ], Children: [piece...]}, each piece {id, BB, O (facing:
     * 0 south, 1 west, 2 north, 3 east), GD (depth)} plus Chest on the turning
     * corridors, Mob on the throne (spawner placed) and Seed on broken ends.
     * The first piece is "NeStart".
     */
    public synchronized NbtTag.Compound writeNbt() {
        NbtTag.Compound features = new NbtTag.Compound();
        for (Map.Entry<Long, Start> entry : starts.entrySet()) {
            Start start = entry.getValue();
            if (start == null) continue;
            int chunkX = (int) (entry.getKey() >> 32), chunkZ = (int) (long) entry.getKey();
            NbtTag.Compound tag = new NbtTag.Compound();
            tag.put("id", new NbtTag.Str("Fortress"));
            tag.put("ChunkX", new NbtTag.Int(chunkX));
            tag.put("ChunkZ", new NbtTag.Int(chunkZ));
            tag.put("BB", boxNbt(start.box));
            List<NbtTag> children = new ArrayList<>();
            for (int i = 0; i < start.pieces.size(); i++) children.add(pieceNbt(start.pieces.get(i), i == 0));
            tag.put("Children", new NbtTag.NbtList(NbtTag.COMPOUND, children));
            features.put("[" + chunkX + "," + chunkZ + "]", tag);
        }
        NbtTag.Compound data = new NbtTag.Compound();
        data.put("Features", features);
        NbtTag.Compound root = new NbtTag.Compound();
        root.put("data", data);
        return root;
    }

    /** Whether any fortress is known (so there is something to save). */
    public synchronized boolean hasStarts() {
        for (Start start : starts.values()) if (start != null) return true;
        return false;
    }

    /**
     * Reads a saved data/Fortress.dat (ours or vanilla 1.8.9's). Saved
     * fortresses replace the generated ones; pieces with unknown ids are
     * skipped like MapGenStructureIO does.
     */
    public synchronized void readNbt(NbtTag.Compound root) {
        if (!(root.get("data") instanceof NbtTag.Compound data) || !(data.get("Features") instanceof NbtTag.Compound features)) return;
        for (NbtTag value : features.entries().values()) {
            if (!(value instanceof NbtTag.Compound tag) || !tag.has("ChunkX") || !tag.has("ChunkZ")) continue;
            if (tag.get("id") instanceof NbtTag.Str id && !id.value().equals("Fortress")) continue;
            NbtTag.NbtList children = tag.getList("Children");
            List<Piece> pieces = new ArrayList<>();
            for (NbtTag child : children.values()) {
                if (!(child instanceof NbtTag.Compound c) || !(c.get("id") instanceof NbtTag.Str pid)) continue;
                Type type = Type.byId(pid.value());
                Box box = readBox(c.get("BB"));
                if (type == null || box == null) continue;
                int o = c.get("O") instanceof NbtTag.Int i ? i.value() : 0;
                Facing facing = Facing.values()[Math.floorMod(o, 4)];
                int depth = c.get("GD") instanceof NbtTag.Int i ? i.value() : 0;
                Piece piece = new Piece(type, box, facing, depth);
                if (type == Type.CORRIDOR || type == Type.CORRIDOR2) piece.pending = flag(c, "Chest");
                if (type == Type.THRONE) piece.pending = !flag(c, "Mob");
                if (type == Type.END && c.get("Seed") instanceof NbtTag.Int seedTag) piece.fillSeed = seedTag.value();
                pieces.add(piece);
            }
            Box box = readBox(tag.get("BB"));
            if (pieces.isEmpty() || box == null) continue;
            Start start = new Start(box);
            start.pieces.addAll(pieces);
            int chunkX = tag.getInt("ChunkX"), chunkZ = tag.getInt("ChunkZ");
            starts.put((long) chunkX << 32 ^ (chunkZ & 0xffffffffL), start);
        }
        nearCache.clear();
    }

    private static NbtTag.Compound pieceNbt(Piece piece, boolean first) {
        NbtTag.Compound tag = new NbtTag.Compound();
        tag.put("id", new NbtTag.Str(first ? "NeStart" : piece.type.id()));
        tag.put("BB", boxNbt(piece.box));
        tag.put("O", new NbtTag.Int(piece.facing.ordinal()));
        tag.put("GD", new NbtTag.Int(piece.depth));
        if (piece.type == Type.CORRIDOR || piece.type == Type.CORRIDOR2) tag.put("Chest", new NbtTag.Byte((byte) (piece.pending ? 1 : 0)));
        if (piece.type == Type.THRONE) tag.put("Mob", new NbtTag.Byte((byte) (piece.pending ? 0 : 1)));
        if (piece.type == Type.END) tag.put("Seed", new NbtTag.Int(piece.fillSeed));
        return tag;
    }

    private static NbtTag.IntArray boxNbt(Box box) {
        return new NbtTag.IntArray(new int[] {box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ});
    }

    private static Box readBox(NbtTag tag) {
        if (!(tag instanceof NbtTag.IntArray array) || array.value().length != 6) return null;
        int[] v = array.value();
        return new Box(v[0], v[1], v[2], v[3], v[4], v[5]);
    }

    private static boolean flag(NbtTag.Compound tag, String name) {
        return tag.get(name) instanceof NbtTag.Byte b && b.value() != 0;
    }

    /** ChunkProviderHell.getPossibleCreatures: inside a piece, or inside the fortress and on nether brick. */
    public boolean isInside(int x, int y, int z, boolean onNetherBrick) {
        for (Start start : startsNear(x >> 4, z >> 4)) {
            if (!start.box.contains(x, y, z)) continue;
            if (onNetherBrick) return true;
            for (Piece piece : start.pieces) if (piece.box.contains(x, y, z)) return true;
        }
        return false;
    }

    private Start build(Random rand, int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 2, z = (chunkZ << 4) + 2;
        Facing facing = Facing.values()[rand.nextInt(4)];
        Start start = new Start(new Box(x, 64, z, x + 19 - 1, 73, z + 19 - 1));
        Piece first = new Piece(Type.CROSSING3, new Box(x, 64, z, x + 18, 73, z + 18), facing, 0);
        start.pieces.add(first);
        buildComponent(start, first, rand);
        while (!start.pending.isEmpty()) {
            Piece next = start.pending.remove(rand.nextInt(start.pending.size()));
            buildComponent(start, next, rand);
        }
        Box bounds = new Box(first.box.minX, first.box.minY, first.box.minZ, first.box.maxX, first.box.maxY, first.box.maxZ);
        for (Piece piece : start.pieces) bounds.expandTo(piece.box);
        // StructureStart.setRandomHeight(world, rand, 48, 70).
        int range = 70 - 48 + 1 - bounds.ySize();
        int y = range > 1 ? 48 + rand.nextInt(range) : 48;
        int dy = y - bounds.minY;
        bounds.offset(0, dy, 0);
        for (Piece piece : start.pieces) piece.box.offset(0, dy, 0);
        start.box.minX = bounds.minX; start.box.minY = bounds.minY; start.box.minZ = bounds.minZ;
        start.box.maxX = bounds.maxX; start.box.maxY = bounds.maxY; start.box.maxZ = bounds.maxZ;
        return start;
    }

    /** Each piece's buildComponent: where its exits lead. */
    private void buildComponent(Start start, Piece piece, Random rand) {
        switch (piece.type) {
            case CROSSING3 -> {
                normal(start, piece, rand, 8, 3, false);
                left(start, piece, rand, 3, 8, false);
                right(start, piece, rand, 3, 8, false);
            }
            case STRAIGHT -> normal(start, piece, rand, 1, 3, false);
            case ENTRANCE -> normal(start, piece, rand, 5, 3, true);
            case CROSSING -> {
                normal(start, piece, rand, 2, 0, false);
                left(start, piece, rand, 0, 2, false);
                right(start, piece, rand, 0, 2, false);
            }
            case STAIRS -> right(start, piece, rand, 6, 2, false);
            case CORRIDOR5, CORRIDOR3 -> normal(start, piece, rand, 1, 0, true);
            case CROSSING2 -> {
                normal(start, piece, rand, 1, 0, true);
                left(start, piece, rand, 0, 1, true);
                right(start, piece, rand, 0, 1, true);
            }
            case CORRIDOR -> left(start, piece, rand, 0, 1, true);
            case CORRIDOR2 -> right(start, piece, rand, 0, 1, true);
            case CORRIDOR4 -> {
                int i = piece.facing == Facing.WEST || piece.facing == Facing.NORTH ? 5 : 1;
                left(start, piece, rand, 0, i, rand.nextInt(8) > 0);
                right(start, piece, rand, 0, i, rand.nextInt(8) > 0);
            }
            case STALK_ROOM -> {
                normal(start, piece, rand, 5, 3, true);
                normal(start, piece, rand, 5, 11, true);
            }
            default -> { }
        }
    }

    /** StructureComponent.getNextComponentNormal: straight ahead. */
    private void normal(Start start, Piece p, Random rand, int offX, int offY, boolean castle) {
        Box bb = p.box;
        switch (p.facing) {
            case NORTH -> next(start, rand, bb.minX + offX, bb.minY + offY, bb.minZ - 1, p.facing, p.depth, castle);
            case SOUTH -> next(start, rand, bb.minX + offX, bb.minY + offY, bb.maxZ + 1, p.facing, p.depth, castle);
            case WEST -> next(start, rand, bb.minX - 1, bb.minY + offY, bb.minZ + offX, p.facing, p.depth, castle);
            case EAST -> next(start, rand, bb.maxX + 1, bb.minY + offY, bb.minZ + offX, p.facing, p.depth, castle);
        }
    }

    /** getNextComponentX: to the piece's -x side. */
    private void left(Start start, Piece p, Random rand, int offY, int offZ, boolean castle) {
        Box bb = p.box;
        switch (p.facing) {
            case NORTH, SOUTH -> next(start, rand, bb.minX - 1, bb.minY + offY, bb.minZ + offZ, Facing.WEST, p.depth, castle);
            case WEST, EAST -> next(start, rand, bb.minX + offZ, bb.minY + offY, bb.minZ - 1, Facing.NORTH, p.depth, castle);
        }
    }

    /** getNextComponentZ: to the piece's +x side. */
    private void right(Start start, Piece p, Random rand, int offY, int offZ, boolean castle) {
        Box bb = p.box;
        switch (p.facing) {
            case NORTH, SOUTH -> next(start, rand, bb.maxX + 1, bb.minY + offY, bb.minZ + offZ, Facing.EAST, p.depth, castle);
            case WEST, EAST -> next(start, rand, bb.minX + offZ, bb.minY + offY, bb.maxZ + 1, Facing.SOUTH, p.depth, castle);
        }
    }

    /** StructureNetherBridgePieces.Piece.func_175870_a: a weighted piece, or a broken end past 112 blocks. */
    private void next(Start start, Random rand, int x, int y, int z, Facing facing, int depth, boolean castle) {
        Piece piece;
        if (Math.abs(x - start.pieces.get(0).box.minX) <= 112 && Math.abs(z - start.pieces.get(0).box.minZ) <= 112)
            piece = pick(start, castle ? start.secondary : start.primary, rand, x, y, z, facing, depth + 1);
        else piece = end(start, rand, x, y, z, facing, depth);
        if (piece != null) {
            start.pieces.add(piece);
            start.pending.add(piece);
        }
    }

    private Piece pick(Start start, List<Weight> weights, Random rand, int x, int y, int z, Facing facing, int depth) {
        int total = 0;
        boolean any = false;
        for (Weight w : weights) {
            if (w.max > 0 && w.placed < w.max) any = true;
            total += w.weight;
        }
        if (!any) total = -1;
        boolean allowed = total > 0 && depth <= 30;
        for (int tries = 0; tries < 5 && allowed; tries++) {
            int k = rand.nextInt(total);
            for (Weight w : weights) {
                k -= w.weight;
                if (k < 0) {
                    if (!w.canPlace() || w == start.last && !w.inRow) break;
                    Piece piece = create(start, w.type, rand, x, y, z, facing, depth);
                    if (piece != null) {
                        w.placed++;
                        start.last = w;
                        if (!w.canPlace()) weights.remove(w);
                        return piece;
                    }
                }
            }
        }
        return end(start, rand, x, y, z, facing, depth);
    }

    private Piece end(Start start, Random rand, int x, int y, int z, Facing facing, int depth) {
        Piece piece = create(start, Type.END, rand, x, y, z, facing, depth);
        if (piece != null) piece.fillSeed = rand.nextInt();
        return piece;
    }

    /** Each piece's static constructor: its box must be above y=10 and clear of every other piece. */
    private Piece create(Start start, Type type, Random rand, int x, int y, int z, Facing facing, int depth) {
        Box box = Box.component(x, y, z, type.offA, type.offB, type.offC, type.sizeA, type.sizeB, type.sizeC, facing);
        if (box.minY <= 10) return null;
        for (Piece other : start.pieces) if (other.box.intersects(box)) return null;
        Piece piece = new Piece(type, box, facing, depth);
        if (type == Type.CORRIDOR || type == Type.CORRIDOR2) piece.pending = rand.nextInt(3) == 0;
        if (type == Type.THRONE) piece.pending = true;
        return piece;
    }

    // ---- building blocks ---------------------------------------------------------------------

    /**
     * MapGenStructure.generateStructure: every piece of every nearby fortress
     * that reaches into the population area (the chunk shifted by 8) is
     * built there.
     */
    void generate(GenWorld w, int chunkX, int chunkZ) {
        int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8;
        Box clip = new Box(x0, 1, z0, x0 + 15, 512, z0 + 15);
        for (Start start : startsNear(chunkX, chunkZ)) {
            if (!start.box.intersectsXZ(clip.minX, clip.minZ, clip.maxX, clip.maxZ)) continue;
            for (Piece piece : start.pieces)
                if (piece.box.intersectsXZ(clip.minX, clip.minZ, clip.maxX, clip.maxZ)) new Builder(w, piece, clip).build();
        }
    }

    /** One piece's addComponentParts, in its own coordinates (x across, y up, z along its facing). */
    private final class Builder {
        final GenWorld w;
        final Piece p;
        final Box clip;
        final int brick = b.NETHER_BRICK, fence = b.NETHER_BRICK_FENCE, air = b.AIR;

        Builder(GenWorld w, Piece p, Box clip) {
            this.w = w;
            this.p = p;
            this.clip = clip;
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

        void set(int state, int x, int y, int z) {
            int X = wx(x, z), Y = wy(y), Z = wz(x, z);
            if (clip.contains(X, Y, Z)) w.set(X, Y, Z, state);
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, int state) {
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++) set(state, x, y, z);
        }

        /** replaceAirAndLiquidDownwards: a support column down through air and liquids to y=2. */
        void down(int x, int y, int z) {
            int X = wx(x, z), Y = wy(y), Z = wz(x, z);
            if (!clip.contains(X, Y, Z)) return;
            while (Y > 1) {
                int here = w.get(X, Y, Z);
                if (!b.isAir(here) && !b.isLiquid(here)) break;
                w.set(X, Y, Z, brick);
                Y--;
            }
        }

        /** StructureComponent.getMetadataWithOffset for stairs. */
        int stairs(int meta) {
            switch (p.facing) {
                case SOUTH -> { if (meta == 2) meta = 3; else if (meta == 3) meta = 2; }
                case WEST -> meta = switch (meta) { case 0 -> 2; case 1 -> 3; case 2 -> 0; default -> 1; };
                case EAST -> meta = switch (meta) { case 0 -> 2; case 1 -> 3; case 2 -> 1; default -> 0; };
                default -> { }
            }
            return stairsId << 4 | meta;
        }

        void build() {
            switch (p.type) {
                case STRAIGHT -> straight();
                case CROSSING3 -> crossing3();
                case CROSSING -> crossing();
                case STAIRS -> stairsRoom();
                case THRONE -> throne();
                case ENTRANCE -> bigRoom(false);
                case STALK_ROOM -> bigRoom(true);
                case CORRIDOR5 -> corridor5();
                case CROSSING2 -> crossing2();
                case CORRIDOR -> corridorTurn(true);
                case CORRIDOR2 -> corridorTurn(false);
                case CORRIDOR3 -> corridorStairs();
                case CORRIDOR4 -> corridorT();
                case END -> end();
            }
        }

        void straight() {
            fill(0, 3, 0, 4, 4, 18, brick);
            fill(1, 5, 0, 3, 7, 18, air);
            fill(0, 5, 0, 0, 5, 18, brick);
            fill(4, 5, 0, 4, 5, 18, brick);
            fill(0, 2, 0, 4, 2, 5, brick);
            fill(0, 2, 13, 4, 2, 18, brick);
            fill(0, 0, 0, 4, 1, 3, brick);
            fill(0, 0, 15, 4, 1, 18, brick);
            for (int i = 0; i <= 4; i++)
                for (int j = 0; j <= 2; j++) {
                    down(i, -1, j);
                    down(i, -1, 18 - j);
                }
            fill(0, 1, 1, 0, 4, 1, fence);
            fill(0, 3, 4, 0, 4, 4, fence);
            fill(0, 3, 14, 0, 4, 14, fence);
            fill(0, 1, 17, 0, 4, 17, fence);
            fill(4, 1, 1, 4, 4, 1, fence);
            fill(4, 3, 4, 4, 4, 4, fence);
            fill(4, 3, 14, 4, 4, 14, fence);
            fill(4, 1, 17, 4, 4, 17, fence);
        }

        void crossing3() {
            fill(7, 3, 0, 11, 4, 18, brick);
            fill(0, 3, 7, 18, 4, 11, brick);
            fill(8, 5, 0, 10, 7, 18, air);
            fill(0, 5, 8, 18, 7, 10, air);
            fill(7, 5, 0, 7, 5, 7, brick);
            fill(7, 5, 11, 7, 5, 18, brick);
            fill(11, 5, 0, 11, 5, 7, brick);
            fill(11, 5, 11, 11, 5, 18, brick);
            fill(0, 5, 7, 7, 5, 7, brick);
            fill(11, 5, 7, 18, 5, 7, brick);
            fill(0, 5, 11, 7, 5, 11, brick);
            fill(11, 5, 11, 18, 5, 11, brick);
            fill(7, 2, 0, 11, 2, 5, brick);
            fill(7, 2, 13, 11, 2, 18, brick);
            fill(7, 0, 0, 11, 1, 3, brick);
            fill(7, 0, 15, 11, 1, 18, brick);
            for (int i = 7; i <= 11; i++)
                for (int j = 0; j <= 2; j++) {
                    down(i, -1, j);
                    down(i, -1, 18 - j);
                }
            fill(0, 2, 7, 5, 2, 11, brick);
            fill(13, 2, 7, 18, 2, 11, brick);
            fill(0, 0, 7, 3, 1, 11, brick);
            fill(15, 0, 7, 18, 1, 11, brick);
            for (int k = 0; k <= 2; k++)
                for (int l = 7; l <= 11; l++) {
                    down(k, -1, l);
                    down(18 - k, -1, l);
                }
        }

        void crossing() {
            fill(0, 0, 0, 6, 1, 6, brick);
            fill(0, 2, 0, 6, 7, 6, air);
            fill(0, 2, 0, 1, 6, 0, brick);
            fill(0, 2, 6, 1, 6, 6, brick);
            fill(5, 2, 0, 6, 6, 0, brick);
            fill(5, 2, 6, 6, 6, 6, brick);
            fill(0, 2, 0, 0, 6, 1, brick);
            fill(0, 2, 5, 0, 6, 6, brick);
            fill(6, 2, 0, 6, 6, 1, brick);
            fill(6, 2, 5, 6, 6, 6, brick);
            fill(2, 6, 0, 4, 6, 0, brick);
            fill(2, 5, 0, 4, 5, 0, fence);
            fill(2, 6, 6, 4, 6, 6, brick);
            fill(2, 5, 6, 4, 5, 6, fence);
            fill(0, 6, 2, 0, 6, 4, brick);
            fill(0, 5, 2, 0, 5, 4, fence);
            fill(6, 6, 2, 6, 6, 4, brick);
            fill(6, 5, 2, 6, 5, 4, fence);
            supports(6, 6);
        }

        void stairsRoom() {
            fill(0, 0, 0, 6, 1, 6, brick);
            fill(0, 2, 0, 6, 10, 6, air);
            fill(0, 2, 0, 1, 8, 0, brick);
            fill(5, 2, 0, 6, 8, 0, brick);
            fill(0, 2, 1, 0, 8, 6, brick);
            fill(6, 2, 1, 6, 8, 6, brick);
            fill(1, 2, 6, 5, 8, 6, brick);
            fill(0, 3, 2, 0, 5, 4, fence);
            fill(6, 3, 2, 6, 5, 2, fence);
            fill(6, 3, 4, 6, 5, 4, fence);
            set(brick, 5, 2, 5);
            fill(4, 2, 5, 4, 3, 5, brick);
            fill(3, 2, 5, 3, 4, 5, brick);
            fill(2, 2, 5, 2, 5, 5, brick);
            fill(1, 2, 5, 1, 6, 5, brick);
            fill(1, 7, 1, 5, 7, 4, brick);
            fill(6, 8, 2, 6, 8, 4, air);
            fill(2, 6, 0, 4, 8, 0, brick);
            fill(2, 5, 0, 4, 5, 0, fence);
            supports(6, 6);
        }

        void throne() {
            fill(0, 2, 0, 6, 7, 7, air);
            fill(1, 0, 0, 5, 1, 7, brick);
            fill(1, 2, 1, 5, 2, 7, brick);
            fill(1, 3, 2, 5, 3, 7, brick);
            fill(1, 4, 3, 5, 4, 7, brick);
            fill(1, 2, 0, 1, 4, 2, brick);
            fill(5, 2, 0, 5, 4, 2, brick);
            fill(1, 5, 2, 1, 5, 3, brick);
            fill(5, 5, 2, 5, 5, 3, brick);
            fill(0, 5, 3, 0, 5, 8, brick);
            fill(6, 5, 3, 6, 5, 8, brick);
            fill(1, 5, 8, 5, 5, 8, brick);
            fill(1, 6, 3, 1, 6, 3, fence);
            fill(5, 6, 3, 5, 6, 3, fence);
            fill(0, 6, 3, 0, 6, 8, fence);
            fill(6, 6, 3, 6, 6, 8, fence);
            fill(1, 6, 8, 5, 7, 8, fence);
            fill(2, 8, 8, 4, 8, 8, fence);
            if (p.pending) {
                int X = wx(3, 5), Y = wy(5), Z = wz(3, 5);
                if (clip.contains(X, Y, Z)) {
                    p.pending = false;
                    w.set(X, Y, Z, b.MOB_SPAWNER);
                    w.setSpawner(X, Y, Z, "Blaze");
                }
            }
            supports(6, 6);
        }

        /** The lava-well entrance and the nether wart room share their shell. */
        void bigRoom(boolean stalks) {
            fill(0, 3, 0, 12, 4, 12, brick);
            fill(0, 5, 0, 12, 13, 12, air);
            fill(0, 5, 0, 1, 12, 12, brick);
            fill(11, 5, 0, 12, 12, 12, brick);
            fill(2, 5, 11, 4, 12, 12, brick);
            fill(8, 5, 11, 10, 12, 12, brick);
            fill(5, 9, 11, 7, 12, 12, brick);
            fill(2, 5, 0, 4, 12, 1, brick);
            fill(8, 5, 0, 10, 12, 1, brick);
            fill(5, 9, 0, 7, 12, 1, brick);
            fill(2, 11, 2, 10, 12, 10, brick);
            if (!stalks) fill(5, 8, 0, 7, 8, 0, fence);
            for (int i = 1; i <= 11; i += 2) {
                fill(i, 10, 0, i, 11, 0, fence);
                fill(i, 10, 12, i, 11, 12, fence);
                fill(0, 10, i, 0, 11, i, fence);
                fill(12, 10, i, 12, 11, i, fence);
                set(brick, i, 13, 0);
                set(brick, i, 13, 12);
                set(brick, 0, 13, i);
                set(brick, 12, 13, i);
                if (i != 11) {
                    set(fence, i + 1, 13, 0);
                    set(fence, i + 1, 13, 12);
                    set(fence, 0, 13, i + 1);
                    set(fence, 12, 13, i + 1);
                }
            }
            set(fence, 0, 13, 0);
            set(fence, 0, 13, 12);
            set(fence, 12, 13, 12);
            set(fence, 12, 13, 0);
            for (int k = 3; k <= 9; k += 2) {
                fill(1, 7, k, 1, 8, k, fence);
                fill(11, 7, k, 11, 8, k, fence);
            }
            if (stalks) {
                int up = stairs(3);
                for (int i = 0; i <= 6; i++) {
                    int z = i + 4;
                    for (int x = 5; x <= 7; x++) set(up, x, 5 + i, z);
                    if (z >= 5 && z <= 8) fill(5, 5, z, 7, i + 4, z, brick);
                    else if (z >= 9 && z <= 10) fill(5, 8, z, 7, i + 4, z, brick);
                    if (i >= 1) fill(5, 6 + i, z, 7, 9 + i, z, air);
                }
                for (int x = 5; x <= 7; x++) set(up, x, 12, 11);
                fill(5, 6, 7, 5, 7, 7, fence);
                fill(7, 6, 7, 7, 7, 7, fence);
                fill(5, 13, 12, 7, 13, 12, air);
                fill(2, 5, 2, 3, 5, 3, brick);
                fill(2, 5, 9, 3, 5, 10, brick);
                fill(9, 5, 2, 10, 5, 3, brick);
                fill(9, 5, 9, 10, 5, 10, brick);
                fill(10, 5, 4, 10, 5, 8, brick);
                int east = stairs(0), west = stairs(1);
                set(west, 4, 5, 2);
                set(west, 4, 5, 3);
                set(west, 4, 5, 9);
                set(west, 4, 5, 10);
                set(east, 8, 5, 2);
                set(east, 8, 5, 3);
                set(east, 8, 5, 9);
                set(east, 8, 5, 10);
                fill(3, 4, 4, 4, 4, 8, b.SOUL_SAND);
                fill(8, 4, 4, 9, 4, 8, b.SOUL_SAND);
                fill(3, 5, 4, 4, 5, 8, b.NETHER_WART);
                fill(8, 5, 4, 9, 5, 8, b.NETHER_WART);
            }
            fill(4, 2, 0, 8, 2, 12, brick);
            fill(0, 2, 4, 12, 2, 8, brick);
            fill(4, 0, 0, 8, 1, 3, brick);
            fill(4, 0, 9, 8, 1, 12, brick);
            fill(0, 0, 4, 3, 1, 8, brick);
            fill(9, 0, 4, 12, 1, 8, brick);
            for (int x = 4; x <= 8; x++)
                for (int z = 0; z <= 2; z++) {
                    down(x, -1, z);
                    down(x, -1, 12 - z);
                }
            for (int x = 0; x <= 2; x++)
                for (int z = 4; z <= 8; z++) {
                    down(x, -1, z);
                    down(12 - x, -1, z);
                }
            if (!stalks) {
                fill(5, 5, 5, 7, 5, 7, brick);
                fill(6, 1, 6, 6, 4, 6, air);
                set(brick, 6, 0, 6);
                int X = wx(6, 6), Y = wy(5), Z = wz(6, 6);
                if (clip.contains(X, Y, Z)) {
                    w.set(X, Y, Z, b.FLOWING_LAVA);
                    w.scheduleBlockTick(X, Y, Z);
                }
            }
        }

        void corridor5() {
            fill(0, 0, 0, 4, 1, 4, brick);
            fill(0, 2, 0, 4, 5, 4, air);
            fill(0, 2, 0, 0, 5, 4, brick);
            fill(4, 2, 0, 4, 5, 4, brick);
            fill(0, 3, 1, 0, 4, 1, fence);
            fill(0, 3, 3, 0, 4, 3, fence);
            fill(4, 3, 1, 4, 4, 1, fence);
            fill(4, 3, 3, 4, 4, 3, fence);
            fill(0, 6, 0, 4, 6, 4, brick);
            supports(4, 4);
        }

        void crossing2() {
            fill(0, 0, 0, 4, 1, 4, brick);
            fill(0, 2, 0, 4, 5, 4, air);
            fill(0, 2, 0, 0, 5, 0, brick);
            fill(4, 2, 0, 4, 5, 0, brick);
            fill(0, 2, 4, 0, 5, 4, brick);
            fill(4, 2, 4, 4, 5, 4, brick);
            fill(0, 6, 0, 4, 6, 4, brick);
            supports(4, 4);
        }

        /** Corridor (opening to the -x side) and Corridor2 (to the +x side), sometimes with a chest. */
        void corridorTurn(boolean toLeft) {
            int wall = toLeft ? 4 : 0, open = toLeft ? 0 : 4;
            fill(0, 0, 0, 4, 1, 4, brick);
            fill(0, 2, 0, 4, 5, 4, air);
            fill(wall, 2, 0, wall, 5, 4, brick);
            fill(wall, 3, 1, wall, 4, 1, fence);
            fill(wall, 3, 3, wall, 4, 3, fence);
            fill(open, 2, 0, open, 5, 0, brick);
            if (toLeft) fill(0, 2, 4, 3, 5, 4, brick);
            else fill(1, 2, 4, 4, 5, 4, brick);
            fill(toLeft ? 1 : 1, 3, 4, toLeft ? 1 : 1, 4, 4, fence);
            fill(3, 3, 4, 3, 4, 4, fence);
            if (p.pending) {
                int cx = toLeft ? 3 : 1;
                int X = wx(cx, 3), Y = wy(2), Z = wz(cx, 3);
                if (clip.contains(X, Y, Z)) {
                    p.pending = false;
                    w.set(X, Y, Z, chestState);
                }
            }
            fill(0, 6, 0, 4, 6, 4, brick);
            supports(4, 4);
        }

        /** Corridor3: a corridor stepping 7 blocks down over 10. */
        void corridorStairs() {
            int down = stairs(2);
            for (int j = 0; j <= 9; j++) {
                int k = Math.max(1, 7 - j);
                int l = Math.min(Math.max(k + 5, 14 - j), 13);
                fill(0, 0, j, 4, k, j, brick);
                fill(1, k + 1, j, 3, l - 1, j, air);
                if (j <= 6) {
                    set(down, 1, k + 1, j);
                    set(down, 2, k + 1, j);
                    set(down, 3, k + 1, j);
                }
                fill(0, l, j, 4, l, j, brick);
                fill(0, k + 1, j, 0, l - 1, j, brick);
                fill(4, k + 1, j, 4, l - 1, j, brick);
                if ((j & 1) == 0) {
                    fill(0, k + 2, j, 0, k + 3, j, fence);
                    fill(4, k + 2, j, 4, k + 3, j, fence);
                }
                for (int x = 0; x <= 4; x++) down(x, -1, j);
            }
        }

        /** Corridor4: a T-shaped room opening to both sides. */
        void corridorT() {
            fill(0, 0, 0, 8, 1, 8, brick);
            fill(0, 2, 0, 8, 5, 8, air);
            fill(0, 6, 0, 8, 6, 5, brick);
            fill(0, 2, 0, 2, 5, 0, brick);
            fill(6, 2, 0, 8, 5, 0, brick);
            fill(1, 3, 0, 1, 4, 0, fence);
            fill(7, 3, 0, 7, 4, 0, fence);
            fill(0, 2, 4, 8, 2, 8, brick);
            fill(1, 1, 4, 2, 2, 4, air);
            fill(6, 1, 4, 7, 2, 4, air);
            fill(0, 3, 8, 8, 3, 8, fence);
            fill(0, 3, 6, 0, 3, 7, fence);
            fill(8, 3, 6, 8, 3, 7, fence);
            fill(0, 3, 4, 0, 5, 5, brick);
            fill(8, 3, 4, 8, 5, 5, brick);
            fill(1, 3, 5, 2, 5, 5, brick);
            fill(6, 3, 5, 7, 5, 5, brick);
            fill(1, 4, 5, 1, 5, 5, fence);
            fill(7, 4, 5, 7, 5, 5, fence);
            supports(8, 8);
        }

        /** End: a broken-off bridge, ragged by its own seed. */
        void end() {
            Random random = new Random(p.fillSeed);
            for (int i = 0; i <= 4; i++)
                for (int j = 3; j <= 4; j++) fill(i, j, 0, i, j, random.nextInt(8), brick);
            fill(0, 5, 0, 0, 5, random.nextInt(8), brick);
            fill(4, 5, 0, 4, 5, random.nextInt(8), brick);
            for (int l = 0; l <= 4; l++) fill(l, 2, 0, l, 2, random.nextInt(5), brick);
            for (int l = 0; l <= 4; l++)
                for (int j = 0; j <= 1; j++) fill(l, j, 0, l, j, random.nextInt(3), brick);
        }

        void supports(int maxX, int maxZ) {
            for (int x = 0; x <= maxX; x++)
                for (int z = 0; z <= maxZ; z++) down(x, -1, z);
        }
    }
}

package net.minecraft.world.gen;

import net.minecraft.world.gen.StructureTemplate.Rotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * End cities (1.9 MapGenEndCity and StructureEndCityPieces, HYPOTHESIS:
 * recalled). One chunk in each 20x20-chunk region (at least 11 apart) on an
 * outer island at least 60 high starts a city: a three-floor house, then a
 * tower that grows bridges to more houses, more towers and fat towers, and
 * at most one end ship, never overlapping, at most 8 levels deep. Each piece
 * is a structure file turned with the city; air in floor pieces doesn't
 * clear what's there. Data markers place shulkers ("Sentry"), chests' loot
 * ("Chest") and the ship's elytra ("Elytra"). Blocks are placed by
 * population, clipped to each populated area.
 */
public final class EndCity {
    private final GenBlocks b;
    private final long seed;
    private final EndGenerator generator;
    private final Map<Long, List<Piece>> starts = new HashMap<>();

    EndCity(GenBlocks blocks, long seed, EndGenerator generator) {
        this.b = blocks;
        this.seed = seed;
        this.generator = generator;
    }

    /** StructureBoundingBox. */
    record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean intersects(Box o) {
            return maxX >= o.minX && minX <= o.maxX && maxZ >= o.minZ && minZ <= o.maxZ && maxY >= o.minY && minY <= o.maxY;
        }
    }

    /** A CityTemplate: a structure file placed at a position with a rotation. */
    static final class Piece {
        final String name;
        final StructureTemplate template;
        final Rotation rotation;
        final boolean overwrite;
        int x, y, z;
        int componentType;

        Piece(String name, int x, int y, int z, Rotation rotation, boolean overwrite) {
            this.name = name;
            this.template = StructureTemplate.get("endcity/" + name);
            this.rotation = rotation;
            this.overwrite = overwrite;
            this.x = x; this.y = y; this.z = z;
        }

        /** StructureComponentTemplate.setBoundingBoxFromTemplate (max x/z one past the size, as vanilla has it). */
        Box box() {
            boolean turned = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
            int sx = turned ? template.sizeZ : template.sizeX, sz = turned ? template.sizeX : template.sizeZ;
            int ox = 0, oz = 0;
            switch (rotation) {
                case CLOCKWISE_90 -> ox = -sx;
                case COUNTERCLOCKWISE_90 -> oz = -sz;
                case CLOCKWISE_180 -> { ox = -sx; oz = -sz; }
                default -> { }
            }
            return new Box(x + ox, y, z + oz, x + ox + sx, y + template.sizeY - 1, z + oz + sz);
        }
    }

    // ---- where cities are --------------------------------------------------------------------

    /** MapGenEndCity.canSpawnStructureAtCoords. */
    boolean isStart(int chunkX, int chunkZ) {
        int cx = chunkX < 0 ? chunkX - 19 : chunkX, cz = chunkZ < 0 ? chunkZ - 19 : chunkZ;
        int rx = cx / 20, rz = cz / 20;
        Random random = new Random((long) rx * 341873128712L + (long) rz * 132897987541L + seed + 10387313L);
        int sx = rx * 20 + (random.nextInt(9) + random.nextInt(9)) / 2;
        int sz = rz * 20 + (random.nextInt(9) + random.nextInt(9)) / 2;
        if (chunkX != sx || chunkZ != sz) return false;
        if ((long) chunkX * chunkX + (long) chunkZ * chunkZ <= 4096L || generator.islandHeight(chunkX, chunkZ, 1, 1) < 0f) return false;
        return groundY(chunkX, chunkZ, rotation(chunkX, chunkZ)) >= 60;
    }

    private static Rotation rotation(int chunkX, int chunkZ) {
        return Rotation.values()[new Random((long) (chunkX + chunkZ * 10387313)).nextInt(4)];
    }

    /** MapGenEndCity.getYPosForStructure: the lowest ground under the four corners of the first house. */
    private int groundY(int chunkX, int chunkZ, Rotation rotation) {
        ChunkPrimer primer = generator.generate(chunkX, chunkZ);
        int i = 5, j = 5;
        if (rotation == Rotation.CLOCKWISE_90) i = -5;
        else if (rotation == Rotation.CLOCKWISE_180) { i = -5; j = -5; }
        else if (rotation == Rotation.COUNTERCLOCKWISE_90) j = -5;
        return Math.min(Math.min(ground(primer, 7, 7), ground(primer, 7, 7 + j)), Math.min(ground(primer, 7 + i, 7), ground(primer, 7 + i, 7 + j)));
    }

    private int ground(ChunkPrimer primer, int x, int z) {
        for (int y = 255; y >= 0; y--) if (!b.isAir(primer.get(x, y, z))) return y;
        return 0;
    }

    /** The city starting in this chunk (its pieces), or an empty list. */
    public synchronized List<Piece> startAt(int chunkX, int chunkZ) {
        long key = (long) chunkX << 32 | chunkZ & 0xffffffffL;
        List<Piece> cached = starts.get(key);
        if (cached != null) return cached;
        List<Piece> pieces = new ArrayList<>();
        if (isStart(chunkX, chunkZ)) {
            Rotation rotation = rotation(chunkX, chunkZ);
            int y = groundY(chunkX, chunkZ, rotation);
            Random random = new Random(seed ^ key * 341873128712L);
            beginTower(chunkX * 16 + 8, y, chunkZ * 16 + 8, rotation, pieces, random);
        }
        starts.put(key, pieces);
        return pieces;
    }

    // ---- pieces (StructureEndCityPieces) -----------------------------------------------------

    private interface Generator {
        default void init() {}
        boolean generate(int depth, Piece parent, int[] offset, List<Piece> list, Random rand);
    }

    private static Piece add(List<Piece> list, Piece piece) {
        list.add(piece);
        return piece;
    }

    /** addPiece: a template joined to its parent at a parent-relative offset (Template.calculateConnectedPos). */
    private static Piece addPiece(Piece parent, int ox, int oy, int oz, String name, Rotation rotation, boolean overwrite) {
        Piece piece = new Piece(name, parent.x, parent.y, parent.z, rotation, overwrite);
        int[] at = StructureTemplate.transform(parent.rotation, ox, oy, oz);
        piece.x += at[0];
        piece.y += at[1];
        piece.z += at[2];
        return piece;
    }

    private final Generator houseTower = (depth, parent, offset, list, rand) -> {
        if (depth > 8) return false;
        Rotation r = parent.rotation;
        Piece base = add(list, addPiece(parent, offset[0], offset[1], offset[2], "base_floor", r, true));
        int kind = rand.nextInt(3);
        if (kind == 0) {
            add(list, addPiece(base, -1, 4, -1, "base_roof", r, true));
        } else if (kind == 1) {
            base = add(list, addPiece(base, -1, 0, -1, "second_floor_2", r, false));
            base = add(list, addPiece(base, -1, 8, -1, "second_roof", r, false));
            recursiveChildren(this.tower, depth + 1, base, null, list, rand);
        } else {
            base = add(list, addPiece(base, -1, 0, -1, "second_floor_2", r, false));
            base = add(list, addPiece(base, -1, 4, -1, "third_floor_c", r, false));
            base = add(list, addPiece(base, -1, 8, -1, "third_roof", r, true));
            recursiveChildren(this.tower, depth + 1, base, null, list, rand);
        }
        return true;
    };

    private record Branch(Rotation rotation, int x, int y, int z) {}

    private static final Branch[] TOWER_BRIDGES = {
        new Branch(Rotation.NONE, 1, -1, 0), new Branch(Rotation.CLOCKWISE_90, 6, -1, 1),
        new Branch(Rotation.COUNTERCLOCKWISE_90, 0, -1, 5), new Branch(Rotation.CLOCKWISE_180, 5, -1, 6)};
    private static final Branch[] FAT_TOWER_BRIDGES = {
        new Branch(Rotation.NONE, 4, -1, 0), new Branch(Rotation.CLOCKWISE_90, 12, -1, 4),
        new Branch(Rotation.COUNTERCLOCKWISE_90, 0, -1, 8), new Branch(Rotation.CLOCKWISE_180, 8, -1, 12)};

    private final Generator tower = (depth, parent, offset, list, rand) -> {
        Rotation r = parent.rotation;
        Piece piece = add(list, addPiece(parent, 3 + rand.nextInt(2), -3, 3 + rand.nextInt(2), "tower_base", r, true));
        piece = add(list, addPiece(piece, 0, 7, 0, "tower_piece", r, true));
        Piece bridgeFrom = rand.nextInt(3) == 0 ? piece : null;
        int floors = 1 + rand.nextInt(3);
        for (int i = 0; i < floors; i++) {
            piece = add(list, addPiece(piece, 0, 4, 0, "tower_piece", r, true));
            if (i < floors - 1 && rand.nextBoolean()) bridgeFrom = piece;
        }
        if (bridgeFrom != null) {
            for (Branch branch : TOWER_BRIDGES)
                if (rand.nextBoolean()) {
                    Piece end = add(list, addPiece(bridgeFrom, branch.x(), branch.y(), branch.z(), "bridge_end", r.add(branch.rotation()), true));
                    recursiveChildren(this.towerBridge, depth + 1, end, null, list, rand);
                }
            add(list, addPiece(piece, -1, 4, -1, "tower_top", r, true));
        } else {
            if (depth != 7) return recursiveChildren(this.fatTower, depth + 1, piece, null, list, rand);
            add(list, addPiece(piece, -1, 4, -1, "tower_top", r, true));
        }
        return true;
    };

    private boolean shipCreated;

    private final Generator towerBridge = new Generator() {
        @Override public void init() { shipCreated = false; }
        @Override public boolean generate(int depth, Piece parent, int[] offset, List<Piece> list, Random rand) {
            Rotation r = parent.rotation;
            int count = rand.nextInt(4) + 1;
            Piece piece = add(list, addPiece(parent, 0, 0, -4, "bridge_piece", r, true));
            piece.componentType = -1;
            int rise = 0;
            for (int i = 0; i < count; i++) {
                if (rand.nextBoolean()) {
                    piece = add(list, addPiece(piece, 0, rise, -4, "bridge_piece", r, true));
                    rise = 0;
                } else {
                    if (rand.nextBoolean()) piece = add(list, addPiece(piece, 0, rise, -4, "bridge_steep_stairs", r, true));
                    else piece = add(list, addPiece(piece, 0, rise, -8, "bridge_gentle_stairs", r, true));
                    rise = 4;
                }
            }
            if (!shipCreated && rand.nextInt(10 - depth) == 0) {
                add(list, addPiece(piece, -8 + rand.nextInt(8), rise, -70 + rand.nextInt(10), "ship", r, true));
                shipCreated = true;
            } else if (!recursiveChildren(houseTower, depth + 1, piece, new int[]{-3, rise + 1, -11}, list, rand)) {
                return false;
            }
            piece = add(list, addPiece(piece, 4, rise, 0, "bridge_end", r.add(Rotation.CLOCKWISE_180), true));
            piece.componentType = -1;
            return true;
        }
    };

    private final Generator fatTower = (depth, parent, offset, list, rand) -> {
        Rotation r = parent.rotation;
        Piece piece = add(list, addPiece(parent, -3, 4, -3, "fat_tower_base", r, true));
        piece = add(list, addPiece(piece, 0, 4, 0, "fat_tower_middle", r, true));
        for (int i = 0; i < 2 && rand.nextInt(3) != 0; i++) {
            piece = add(list, addPiece(piece, 0, 8, 0, "fat_tower_middle", r, true));
            for (Branch branch : FAT_TOWER_BRIDGES)
                if (rand.nextBoolean()) {
                    Piece end = add(list, addPiece(piece, branch.x(), branch.y(), branch.z(), "bridge_end", r.add(branch.rotation()), true));
                    recursiveChildren(this.towerBridge, depth + 1, end, null, list, rand);
                }
        }
        add(list, addPiece(piece, -2, 8, -2, "fat_tower_top", r, true));
        return true;
    };

    /** recursiveChildren: a generator's pieces are kept only if none overlaps an earlier branch's. */
    private static boolean recursiveChildren(Generator generator, int depth, Piece parent, int[] offset, List<Piece> list, Random rand) {
        if (depth > 8) return false;
        List<Piece> added = new ArrayList<>();
        if (!generator.generate(depth, parent, offset, added, rand)) return false;
        int type = rand.nextInt();
        for (Piece piece : added) {
            piece.componentType = type;
            Box box = piece.box();
            // StructureComponent.findIntersecting: only the first piece it meets counts.
            for (Piece other : list)
                if (other.box().intersects(box)) {
                    if (other.componentType != parent.componentType) return false;
                    break;
                }
        }
        list.addAll(added);
        return true;
    }

    /** beginTower: the first house's three floors and roof, then a tower. */
    private void beginTower(int x, int y, int z, Rotation rotation, List<Piece> list, Random rand) {
        fatTower.init();
        houseTower.init();
        towerBridge.init();
        tower.init();
        Piece piece = add(list, new Piece("base_floor", x, y, z, rotation, true));
        piece = add(list, addPiece(piece, -1, 0, -1, "second_floor", rotation, false));
        piece = add(list, addPiece(piece, -1, 4, -1, "third_floor", rotation, false));
        piece = add(list, addPiece(piece, -1, 8, -1, "third_roof", rotation, true));
        recursiveChildren(tower, 1, piece, null, list, rand);
    }

    // ---- placing -----------------------------------------------------------------------------

    /**
     * MapGenStructure.generateStructure for the End: every city within 8
     * chunks places the parts of its pieces inside this chunk's population
     * area (x and z from +8 to +23).
     */
    public void populate(GenWorld w, int chunkX, int chunkZ) {
        int minX = chunkX * 16 + 8, minZ = chunkZ * 16 + 8, maxX = minX + 15, maxZ = minZ + 15;
        for (int sx = chunkX - 8; sx <= chunkX + 8; sx++)
            for (int sz = chunkZ - 8; sz <= chunkZ + 8; sz++)
                for (Piece piece : startAt(sx, sz)) {
                    Box box = piece.box();
                    if (box.maxX() < minX || box.minX() > maxX || box.maxZ() < minZ || box.minZ() > maxZ) continue;
                    place(w, piece, minX, minZ, maxX, maxZ);
                }
    }

    /** Template.addBlocksToWorld plus the data markers (CityTemplate.handleDataMarker). */
    private void place(GenWorld w, Piece piece, int minX, int minZ, int maxX, int maxZ) {
        for (StructureTemplate.Block block : piece.template.blocks) {
            int[] at = StructureTemplate.transform(piece.rotation, block.x(), block.y(), block.z());
            int x = piece.x + at[0], y = piece.y + at[1], z = piece.z + at[2];
            if (x < minX || x > maxX || z < minZ || z > maxZ || y < 0 || y > 255) continue;
            // Vanilla id 255 is the structure block, which LegacyCraft doesn't have: only its data markers matter.
            if (block.id() == 255) {
                if (block.nbt() != null && block.nbt().get("metadata") instanceof net.minecraft.nbt.NbtTag.Str marker)
                    dataMarker(w, marker.value(), x, y, z, piece.rotation);
                continue;
            }
            if (block.id() >= b.registry().size()) continue;
            String name = b.registry().byId(block.id()).name().path();
            if (name.equals("air") && !piece.overwrite) continue;
            int meta = StructureTemplate.rotateMeta(name, block.meta(), piece.rotation);
            w.set(x, y, z, block.id() << 4 | meta);
            if (name.equals("skull") && block.nbt() != null) {
                int rot = block.nbt().get("Rot") instanceof net.minecraft.nbt.NbtTag.Byte r ? r.value() : 0;
                w.setSkull(x, y, z, block.nbt().get("SkullType") instanceof net.minecraft.nbt.NbtTag.Byte t ? t.value() : 0,
                    (rot + piece.rotation.ordinal() * 4) & 15);
            }
        }
    }

    private void dataMarker(GenWorld w, String marker, int x, int y, int z, Rotation rotation) {
        if (marker.startsWith("Sentry")) {
            w.spawnCreature("Shulker", x + 0.5, y + 0.5, z + 0.5, 0f);
        } else if (marker.startsWith("Elytra")) {
            // EntityItemFrame facing SOUTH turned with the piece, holding an elytra.
            int facing = rotation.rotateFacing(3);
            w.spawnCreature("ItemFrame:elytra:" + facing, x, y, z, 0f);
        }
        // shortcut: "Chest" markers would set the chest below to the end_city_treasure loot table; chests hold nothing yet.
    }
}

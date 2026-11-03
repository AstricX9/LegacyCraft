package net.minecraft.world.gen;

import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTag;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * A 1.9 structure file (Template, assets/minecraft/structures/*.nbt): its
 * size and blocks, each a vanilla block state id (id + meta &lt;&lt; 12) at a
 * position with optional tile entity data. Placing one turns it by a
 * {@link Rotation} about its origin (Template.transformedBlockPos).
 */
public final class StructureTemplate {
    /** Rotation (1.9): clockwise quarter turns seen from above. */
    public enum Rotation {
        NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90;

        public Rotation add(Rotation other) { return values()[(ordinal() + other.ordinal()) % 4]; }

        /** Rotation.rotate on horizontal facings, with vanilla's facing meta (2 north, 3 south, 4 west, 5 east). */
        public int rotateFacing(int facing) {
            int[] clockwise = {0, 1, 5, 4, 2, 3}; // north->east, south->west, west->north, east->south
            for (int i = 0; i < ordinal(); i++) if (facing >= 2) facing = clockwise[facing];
            return facing;
        }
    }

    public record Block(int x, int y, int z, int id, int meta, NbtTag.Compound nbt) {}

    public final int sizeX, sizeY, sizeZ;
    public final List<Block> blocks;

    private StructureTemplate(int sizeX, int sizeY, int sizeZ, List<Block> blocks) {
        this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
        this.blocks = blocks;
    }

    private static final Map<String, StructureTemplate> CACHE = new ConcurrentHashMap<>();

    /** The template at assets/minecraft/structures/{name}.nbt, read once. */
    public static StructureTemplate get(String name) {
        return CACHE.computeIfAbsent(name, StructureTemplate::load);
    }

    private static StructureTemplate load(String name) {
        String path = "/assets/minecraft/structures/" + name + ".nbt";
        try (InputStream raw = StructureTemplate.class.getResourceAsStream(path)) {
            if (raw == null) throw new IOException("missing " + path);
            NbtTag.Compound root = NbtIo.read(new GZIPInputStream(new BufferedInputStream(raw)));
            int[] size = ints(root.getList("size"));
            List<Block> blocks = new ArrayList<>();
            for (NbtTag element : root.getList("blocks").values()) {
                NbtTag.Compound block = (NbtTag.Compound) element;
                int[] pos = ints(block.getList("pos"));
                int state = block.getInt("state");
                NbtTag.Compound nbt = block.get("nbt") instanceof NbtTag.Compound c ? c : null;
                blocks.add(new Block(pos[0], pos[1], pos[2], state & 4095, state >> 12 & 15, nbt));
            }
            return new StructureTemplate(size[0], size[1], size[2], List.copyOf(blocks));
        } catch (IOException error) {
            throw new UncheckedIOException("Couldn't read structure " + name, error);
        }
    }

    private static int[] ints(NbtTag.NbtList list) {
        int[] out = new int[list.values().size()];
        for (int i = 0; i < out.length; i++) out[i] = ((NbtTag.Int) list.values().get(i)).value();
        return out;
    }

    /** Template.transformedBlockPos: (x, z) turned about the origin; y is unchanged. */
    public static int[] transform(Rotation rotation, int x, int y, int z) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new int[]{-z, y, x};
            case COUNTERCLOCKWISE_90 -> new int[]{z, y, -x};
            case CLOCKWISE_180 -> new int[]{-x, y, -z};
            default -> new int[]{x, y, z};
        };
    }

    /**
     * IBlockState.withRotation for the blocks end cities use: facing metas
     * (chests, ladders, banners, wall skulls, end rods), stairs and pillar axes.
     */
    public static int rotateMeta(String block, int meta, Rotation rotation) {
        if (rotation == Rotation.NONE) return meta;
        switch (block) {
            case "chest", "ender_chest", "trapped_chest", "ladder", "wall_banner", "wall_sign", "furnace", "dispenser", "dropper" -> {
                return rotation.rotateFacing(meta);
            }
            case "skull", "end_rod" -> { return rotation.rotateFacing(meta & 7) | meta & 8; }
            case "purpur_pillar", "quartz_block" -> {
                if (block.equals("quartz_block") && meta < 2) return meta;
                int axis = meta & 12;
                boolean swap = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
                if (swap && axis == 4) axis = 8;
                else if (swap && axis == 8) axis = 4;
                return meta & ~12 | axis;
            }
            default -> {
                if (block.endsWith("_stairs")) {
                    // Stairs meta: 0 east, 1 west, 2 south, 3 north (+4 upside down).
                    int[] toFacing = {5, 4, 3, 2};
                    int facing = rotation.rotateFacing(toFacing[meta & 3]);
                    int stairs = switch (facing) { case 5 -> 0; case 4 -> 1; case 3 -> 2; default -> 3; };
                    return meta & 4 | stairs;
                }
                return meta;
            }
        }
    }
}

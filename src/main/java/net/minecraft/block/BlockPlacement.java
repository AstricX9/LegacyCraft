package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;
import java.util.Set;

/**
 * The shared placement flow (master context §16): a matching block may absorb
 * the click ({@link Block#placeOnto}, slabs), otherwise the target is the
 * clicked block when it is replaceable or the block beside the clicked face;
 * the block class then orients the placed state(s) ({@link Block#placements})
 * and every result must be able to stay ({@link Block#canStay}).
 */
public final class BlockPlacement {
    /** Where and how the player clicked. Hit coordinates are relative to the clicked block (0-1). */
    public record Context(int clickedX, int clickedY, int clickedZ, Direction face,
                          float hitX, float hitY, float hitZ,
                          Direction playerFacing, double playerX, double playerEyeY, double playerZ) {}

    public record Placement(int x, int y, int z, BlockState state) {}

    /** Blocks a placement replaces instead of being placed beside. */
    private static final Set<String> REPLACEABLE = Set.of("minecraft:air", "minecraft:water", "minecraft:flowing_water",
        "minecraft:lava", "minecraft:flowing_lava", "minecraft:tallgrass", "minecraft:deadbush", "minecraft:vine",
        "minecraft:fire", "minecraft:snow_layer");

    private BlockPlacement() {}

    /** The positions and states to write, or an empty list when the block cannot be placed there. */
    public static List<Placement> place(IBlockAccess world, BlockRegistry registry, Block block, int itemMetadata, Context context) {
        List<Placement> placements = orient(world, block, itemMetadata, context);
        IBlockAccess after = new IBlockAccess() {
            @Override public BlockState getState(int x, int y, int z) {
                for (Placement placement : placements)
                    if (placement.x() == x && placement.y() == y && placement.z() == z) return placement.state();
                return world.getState(x, y, z);
            }
            @Override public BlockRegistry registry() { return world.registry(); }
        };
        for (Placement placement : placements) {
            BlockState state = placement.state();
            if (!state.block().canStay(after, placement.x(), placement.y(), placement.z(), state)) return List.of();
        }
        return placements;
    }

    private static List<Placement> orient(IBlockAccess world, Block block, int itemMetadata, Context context) {
        int x = context.clickedX(), y = context.clickedY(), z = context.clickedZ();
        BlockState clicked = world.getState(x, y, z);
        Placement merged = block.placeOnto(world, clicked, itemMetadata, context);
        if (merged != null) return List.of(merged);

        Direction face = context.face();
        if (isReplaceable(clicked) && !clicked.block().isAir()) {
            face = Direction.UP;
        } else {
            x += face.xOffset; y += face.yOffset; z += face.zOffset;
        }
        if (y < 0 || y >= 256 || !isReplaceable(world.getState(x, y, z))) return List.of();
        return block.placements(world, x, y, z, itemMetadata, face, context);
    }

    static boolean isReplaceable(BlockState state) {
        String name = state.block().name().toString();
        if (!REPLACEABLE.contains(name)) return false;
        return !name.equals("minecraft:snow_layer") || state.get("layers").equals("1");
    }
}

package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Stairs: corner shapes from neighbouring stairs, stepped collision, facing/half from the click. */
public class BlockStairs extends Block {
    public BlockStairs(Properties properties) { super(properties); }

    /**
     * 1.8 stair corners. The block behind a stair (in its facing direction)
     * turns it into an outer corner when that stair faces across it; the block
     * in front turns it into an inner corner. A corner is not formed when the
     * side it would open onto already holds a stair continuing this one's row.
     *
     * <p>1.8 quirk (VERIFIED_FORMAT against the supplied blockstates, see
     * StairShapeGeometryTest): for upside-down stairs "left" and "right" are
     * swapped, because the top-half variants are the bottom models flipped by
     * x=180 without re-mirroring the shape name.
     */
    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        values.put("shape", shape(world, x, y, z, facing(state), state.get("half")));
        return new ActualState(state, values);
    }

    private static String shape(IBlockAccess world, int x, int y, int z, Direction facing, String half) {
        boolean top = half.equals("top");
        BlockState back = neighbour(world, x, y, z, facing);
        if (back.block() instanceof BlockStairs && back.get("half").equals(half)) {
            Direction backFacing = facing(back);
            if (backFacing.axis() != facing.axis()
                && !sameStairs(neighbour(world, x, y, z, backFacing.opposite()), facing, half))
                return (backFacing == facing.rotateYCCW()) != top ? "outer_left" : "outer_right";
        }
        BlockState front = neighbour(world, x, y, z, facing.opposite());
        if (front.block() instanceof BlockStairs && front.get("half").equals(half)) {
            Direction frontFacing = facing(front);
            if (frontFacing.axis() != facing.axis() && !sameStairs(neighbour(world, x, y, z, frontFacing), facing, half))
                return (frontFacing == facing.rotateYCCW()) != top ? "inner_left" : "inner_right";
        }
        return "straight";
    }

    private static boolean sameStairs(BlockState state, Direction facing, String half) {
        return state.block() instanceof BlockStairs && state.get("facing").equals(facing.getName())
            && state.get("half").equals(half);
    }

    /**
     * The half slab plus the full-height quarter cells. Which cells are tall
     * follows the shape name the same way the supplied models do, including
     * the mirrored names on top halves.
     */
    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        Direction facing = facing(state);
        boolean top = state.get("half").equals("top");
        String shape = state.get("shape");
        List<AxisAlignedBB> boxes = new ArrayList<>();
        boxes.add(top ? new AxisAlignedBB(0, .5, 0, 1, 1, 1) : new AxisAlignedBB(0, 0, 0, 1, .5, 1));
        double minY = top ? 0 : .5, maxY = top ? .5 : 1;
        boolean left = shape.endsWith("_left") != top;
        Direction across = left ? facing.rotateYCCW() : facing.rotateY();
        for (int cellX = 0; cellX < 2; cellX++) {
            for (int cellZ = 0; cellZ < 2; cellZ++) {
                boolean inFacing = inHalf(facing, cellX, cellZ);
                boolean tall = switch (shape) {
                    case "straight" -> inFacing;
                    case "outer_left", "outer_right" -> inFacing && inHalf(across, cellX, cellZ);
                    default -> !(inHalf(facing.opposite(), cellX, cellZ) && inHalf(across.opposite(), cellX, cellZ));
                };
                if (tall) boxes.add(new AxisAlignedBB(cellX * .5, minY, cellZ * .5, cellX * .5 + .5, maxY, cellZ * .5 + .5));
            }
        }
        return boxes;
    }

    private static boolean inHalf(Direction side, int cellX, int cellZ) {
        return switch (side) {
            case NORTH -> cellZ == 0; case SOUTH -> cellZ == 1;
            case WEST -> cellX == 0; case EAST -> cellX == 1;
            default -> true;
        };
    }

    /** Stairs rise away from the player; the upper half is used when the underside or top half of a side is clicked. */
    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        BlockState state = BlockState.of(this, itemMetadata).with("facing", context.playerFacing().getName())
            .with("half", BlockSlab.bottomHalf(face, context.hitY()) ? "bottom" : "top");
        return List.of(new BlockPlacement.Placement(x, y, z, state));
    }
}

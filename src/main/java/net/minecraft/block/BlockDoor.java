package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Doors: two blocks whose halves store different properties (the lower half
 * facing/open, the upper half hinge/powered) and share them as actual state.
 */
public class BlockDoor extends Block {
    private static final double THICKNESS = 3 / 16.0;

    public BlockDoor(Properties properties) { super(properties); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        boolean lower = state.get("half").equals("lower");
        BlockState other = world.getState(x, lower ? y + 1 : y - 1, z);
        if (other.block() == this) {
            if (lower) {
                values.put("hinge", other.get("hinge"));
                values.put("powered", other.get("powered"));
            } else {
                values.put("facing", other.get("facing"));
                values.put("open", other.get("open"));
            }
        }
        return new ActualState(state, values);
    }

    /** Closed doors sit on the side opposite their facing; open doors swing to the hinge side. Model-checked. */
    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        Direction facing = facing(state);
        if (!isTrue(state, "open")) return closed(facing);
        return closed(state.get("hinge").equals("right") ? facing.rotateYCCW() : facing.rotateY());
    }

    private static AxisAlignedBB closed(Direction facing) {
        return switch (facing) {
            case EAST -> new AxisAlignedBB(0, 0, 0, THICKNESS, 1, 1);
            case SOUTH -> new AxisAlignedBB(0, 0, 0, 1, 1, THICKNESS);
            case WEST -> new AxisAlignedBB(1 - THICKNESS, 0, 0, 1, 1, 1);
            default -> new AxisAlignedBB(0, 0, 1 - THICKNESS, 1, 1, 1);
        };
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(selectionBox(state, world, x, y, z));
    }

    /** Doors take two blocks; the hinge goes against walls and away from a neighbouring door. */
    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        if (y + 1 >= 256 || !BlockPlacement.isReplaceable(world.getState(x, y + 1, z))) return List.of();
        Direction facing = context.playerFacing();
        Direction left = facing.rotateYCCW(), right = facing.rotateY();
        int leftSolid = solidCount(world, x + left.xOffset, y, z + left.zOffset);
        int rightSolid = solidCount(world, x + right.xOffset, y, z + right.zOffset);
        boolean doorLeft = isDoor(world, x + left.xOffset, y, z + left.zOffset);
        boolean doorRight = isDoor(world, x + right.xOffset, y, z + right.zOffset);
        boolean hingeRight = doorLeft && !doorRight || rightSolid > leftSolid;
        BlockState lower = BlockState.of(this, 0).with("half", "lower").with("facing", facing.getName()).with("open", "false");
        BlockState upper = BlockState.of(this, 0).with("half", "upper").with("hinge", hingeRight ? "right" : "left");
        return List.of(new BlockPlacement.Placement(x, y, z, lower), new BlockPlacement.Placement(x, y + 1, z, upper));
    }

    private static int solidCount(IBlockAccess world, int x, int y, int z) {
        return (world.getState(x, y, z).block().isNormalCube() ? 1 : 0) + (world.getState(x, y + 1, z).block().isNormalCube() ? 1 : 0);
    }

    private boolean isDoor(IBlockAccess world, int x, int y, int z) {
        return world.getState(x, y, z).block() == this || world.getState(x, y + 1, z).block() == this;
    }

    /** The lower half needs the upper half and a solid top below; the upper half needs the lower half. */
    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        if (state.get("half").equals("lower")) {
            BlockState above = world.getState(x, y + 1, z);
            return above.block() == this && above.get("half").equals("upper") && hasSolidTop(world.getState(x, y - 1, z));
        }
        BlockState below = world.getState(x, y - 1, z);
        return below.block() == this && below.get("half").equals("lower");
    }
}

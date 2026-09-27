package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Pistons and sticky pistons: face the player; an extended base gives up the quarter its head occupies. */
public class BlockPistonBase extends Block {
    public BlockPistonBase(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        if (!isTrue(state, "extended")) return AxisAlignedBB.FULL;
        return switch (facing(state)) {
            case DOWN -> new AxisAlignedBB(0, .25, 0, 1, 1, 1);
            case UP -> new AxisAlignedBB(0, 0, 0, 1, .75, 1);
            case NORTH -> new AxisAlignedBB(0, 0, .25, 1, 1, 1);
            case SOUTH -> new AxisAlignedBB(0, 0, 0, 1, 1, .75);
            case WEST -> new AxisAlignedBB(.25, 0, 0, 1, 1, 1);
            case EAST -> new AxisAlignedBB(0, 0, 0, .75, 1, 1);
        };
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(selectionBox(state, world, x, y, z));
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        return List.of(new BlockPlacement.Placement(x, y, z,
            BlockState.of(this, itemMetadata).with("facing", BlockDispenser.towardPlayer(context, x, y, z).getName())));
    }
}

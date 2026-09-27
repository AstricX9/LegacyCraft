package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Skulls: half-block heads on the floor or against a wall. */
public class BlockSkull extends Block {
    public BlockSkull(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return switch (facing(state)) {
            case NORTH -> new AxisAlignedBB(.25, .25, .5, .75, .75, 1);
            case SOUTH -> new AxisAlignedBB(.25, .25, 0, .75, .75, .5);
            case WEST -> new AxisAlignedBB(.5, .25, .25, 1, .75, .75);
            case EAST -> new AxisAlignedBB(0, .25, .25, .5, .75, .75);
            default -> new AxisAlignedBB(.25, 0, .25, .75, .5, .75);
        };
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(selectionBox(state, world, x, y, z));
    }
}

package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Buttons: on any face of a normal cube; pressed buttons are half as deep. */
public class BlockButton extends Block {
    public BlockButton(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        double depth = isTrue(state, "powered") ? 1 / 16.0 : 2 / 16.0;
        return switch (facing(state)) {
            case UP -> new AxisAlignedBB(.3125, 0, .375, .6875, depth, .625);
            case DOWN -> new AxisAlignedBB(.3125, 1 - depth, .375, .6875, 1, .625);
            default -> against(facing(state), depth, .375, .625, .3125, .6875);
        };
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        return List.of(new BlockPlacement.Placement(x, y, z, BlockState.of(this, itemMetadata).with("facing", face.getName())));
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return attachedTo(world, x, y, z, facing(state));
    }
}

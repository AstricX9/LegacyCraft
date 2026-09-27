package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Ladders: a 2/16 plate against the block behind them. */
public class BlockLadder extends Block {
    public BlockLadder(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return against(facing(state), .125, 0, 1, 0, 1);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(selectionBox(state, world, x, y, z));
    }

    /** On the clicked wall if it can hold a ladder, otherwise the first supported side. */
    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        Direction attach = face.isHorizontal() && attachedTo(world, x, y, z, face) ? face : firstSupportedSide(world, x, y, z);
        if (attach == null) return List.of();
        return List.of(new BlockPlacement.Placement(x, y, z, BlockState.of(this, itemMetadata).with("facing", attach.getName())));
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return attachedTo(world, x, y, z, facing(state));
    }
}

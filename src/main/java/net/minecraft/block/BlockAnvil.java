package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Anvils: narrower across their length; placed turned a quarter clockwise from the player's facing. */
public class BlockAnvil extends Block {
    public BlockAnvil(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return facing(state).axis() == 'x' ? new AxisAlignedBB(0, 0, .125, 1, 1, .875) : new AxisAlignedBB(.125, 0, 0, .875, 1, 1);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(selectionBox(state, world, x, y, z));
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        return List.of(new BlockPlacement.Placement(x, y, z,
            BlockState.of(this, itemMetadata).with("facing", context.playerFacing().rotateY().getName())));
    }
}

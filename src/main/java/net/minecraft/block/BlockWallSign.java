package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

/** Wall signs and wall banners: a thin plate on the block behind them. */
public class BlockWallSign extends Block {
    public BlockWallSign(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return against(facing(state), .125, .28125, .78125, 0, 1);
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        var facing = facing(state);
        return world.getState(x - facing.xOffset, y, z - facing.zOffset).block().isSolid();
    }
}

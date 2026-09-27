package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

/** Rails (all four kinds): flat or ascending, on a solid top. Auto-shaping is not implemented (DEV-007). */
public class BlockRail extends Block {
    public BlockRail(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return new AxisAlignedBB(0, 0, 0, 1, state.get("shape").startsWith("ascending") ? .625 : .125, 1);
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return hasSolidTop(world.getState(x, y - 1, z));
    }
}

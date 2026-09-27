package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

/** Nether portal blocks: a thin sheet along their axis. */
public class BlockPortal extends Block {
    public BlockPortal(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return state.get("axis").equals("x") ? new AxisAlignedBB(0, 0, .375, 1, 1, .625) : new AxisAlignedBB(.375, 0, 0, .625, 1, 1);
    }
}

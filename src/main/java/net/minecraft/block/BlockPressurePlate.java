package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

/** Pressure plates (plain and weighted): inset 1/16, half as tall when pressed; on solid tops or fences. */
public class BlockPressurePlate extends Block {
    public BlockPressurePlate(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        boolean pressed = state.values().containsKey("powered") ? isTrue(state, "powered") : !state.get("power").equals("0");
        return new AxisAlignedBB(1 / 16.0, 0, 1 / 16.0, 15 / 16.0, pressed ? 1 / 32.0 : 1 / 16.0, 15 / 16.0);
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        BlockState below = world.getState(x, y - 1, z);
        return hasSolidTop(below) || below.block() instanceof BlockFence;
    }
}

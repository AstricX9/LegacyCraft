package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Cake: shrinks from the west with each bite; needs a solid block below. */
public class BlockCake extends Block {
    public BlockCake(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return new AxisAlignedBB((1 + 2 * Integer.parseInt(state.get("bites"))) / 16.0, 0, 1 / 16.0, 15 / 16.0, .5, 15 / 16.0);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(selectionBox(state, world, x, y, z));
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return world.getState(x, y - 1, z).block().isSolid();
    }
}

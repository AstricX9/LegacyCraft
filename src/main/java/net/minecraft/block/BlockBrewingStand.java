package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Brewing stands: targeted by the base plate only; collide with the base and the central rod. */
public class BlockBrewingStand extends Block {
    public BlockBrewingStand(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return new AxisAlignedBB(0, 0, 0, 1, .125, 1);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(new AxisAlignedBB(.4375, 0, .4375, .5625, .875, .5625), new AxisAlignedBB(0, 0, 0, 1, .125, 1));
    }
}

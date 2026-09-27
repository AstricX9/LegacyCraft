package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Cauldrons: a basin with a low floor. */
public class BlockCauldron extends Block {
    public BlockCauldron(Properties properties) { super(properties); }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return BlockHopper.basin(5 / 16.0);
    }
}

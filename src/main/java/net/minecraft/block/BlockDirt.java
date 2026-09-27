package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.world.IBlockAccess;

/** Dirt, coarse dirt and podzol: podzol shows a snowy side under snow. */
public class BlockDirt extends Block {
    public BlockDirt(Properties properties) { super(properties); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        return BlockGrass.snowy(world, x, y, z, state);
    }
}

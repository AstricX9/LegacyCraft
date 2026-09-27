package net.minecraft.block;

import net.minecraft.world.IBlockAccess;

/** Carpet: on anything but air. */
public class BlockCarpet extends Block {
    public BlockCarpet(Properties properties) { super(properties); }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return !world.getState(x, y - 1, z).block().isAir();
    }
}

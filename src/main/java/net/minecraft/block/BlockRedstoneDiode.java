package net.minecraft.block;

import net.minecraft.world.IBlockAccess;

/** Repeaters and comparators: 2/16 slabs (static bounds from data) on a solid top. */
public class BlockRedstoneDiode extends Block {
    public BlockRedstoneDiode(Properties properties) { super(properties); }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return hasSolidTop(world.getState(x, y - 1, z));
    }
}

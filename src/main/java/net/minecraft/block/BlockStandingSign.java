package net.minecraft.block;

import net.minecraft.world.IBlockAccess;

/** Standing signs and banners: need a solid block below. */
public class BlockStandingSign extends Block {
    public BlockStandingSign(Properties properties) { super(properties); }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return world.getState(x, y - 1, z).block().isSolid();
    }
}

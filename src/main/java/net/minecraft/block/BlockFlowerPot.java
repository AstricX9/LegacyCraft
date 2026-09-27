package net.minecraft.block;

import net.minecraft.world.IBlockAccess;

/** Flower pots: on a solid top. Contents come from a tile entity, not implemented yet (DEV-006). */
public class BlockFlowerPot extends Block {
    public BlockFlowerPot(Properties properties) { super(properties); }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return hasSolidTop(world.getState(x, y - 1, z));
    }
}

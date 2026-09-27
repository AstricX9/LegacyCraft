package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

/** Cactus: on sand or more cactus, with no solid block beside it. */
public class BlockCactus extends Block {
    public BlockCactus(Properties properties) { super(properties); }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        Block below = world.getState(x, y - 1, z).block();
        if (below != this && !below.name().path().equals("sand")) return false;
        for (Direction side : Direction.HORIZONTALS)
            if (neighbour(world, x, y, z, side).block().isSolid()) return false;
        return true;
    }
}

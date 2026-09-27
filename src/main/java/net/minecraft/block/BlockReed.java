package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.Set;

/** Sugar cane: on more sugar cane, or on grass, dirt or sand next to water. */
public class BlockReed extends Block {
    private static final Set<String> SOIL = Set.of("grass", "dirt", "sand");

    public BlockReed(Properties properties) { super(properties); }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        Block below = world.getState(x, y - 1, z).block();
        return below == this || SOIL.contains(below.name().path()) && waterBeside(world, x, y - 1, z);
    }

    private static boolean waterBeside(IBlockAccess world, int x, int y, int z) {
        for (Direction side : Direction.HORIZONTALS)
            if (neighbour(world, x, y, z, side).block().material().equals("water")) return true;
        return false;
    }
}

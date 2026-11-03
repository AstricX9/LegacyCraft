package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

/**
 * 1.9 chorus flowers (BlockChorusFlower): the tip of a chorus plant, on end
 * stone or plant, or hanging off the side of exactly one plant over air.
 * shortcut: no growth, since LegacyCraft has no random ticks yet; add it with them.
 */
public class BlockChorusFlower extends Block {
    public BlockChorusFlower(Properties properties) { super(properties); }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        Block below = world.getState(x, y - 1, z).block();
        if (below instanceof BlockChorusPlant || BlockChorusPlant.isEndStone(below)) return true;
        if (!below.isAir()) return false;
        int plants = 0;
        for (Direction side : Direction.HORIZONTALS) {
            Block other = neighbour(world, x, y, z, side).block();
            if (other instanceof BlockChorusPlant) plants++;
            else if (!other.isAir()) return false;
        }
        return plants == 1;
    }
}

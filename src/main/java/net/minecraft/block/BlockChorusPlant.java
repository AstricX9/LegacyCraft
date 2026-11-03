package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 1.9 chorus plants (BlockChorusPlant): a stem joined to neighbouring plants
 * and flowers (and end stone below), growing out of end stone or more plant.
 */
public class BlockChorusPlant extends Block {
    public BlockChorusPlant(Properties properties) { super(properties); }

    static boolean isChorus(Block block) { return block instanceof BlockChorusPlant || block instanceof BlockChorusFlower; }
    static boolean isEndStone(Block block) { return block.name().path().equals("end_stone"); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        for (Direction side : Direction.values()) {
            Block other = neighbour(world, x, y, z, side).block();
            values.put(side.getName(), flag(isChorus(other) || side == Direction.DOWN && isEndStone(other)));
        }
        return new ActualState(state, values);
    }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        double lo = 0.1875, hi = 0.8125;
        return new AxisAlignedBB(isTrue(state, "west") ? 0 : lo, isTrue(state, "down") ? 0 : lo, isTrue(state, "north") ? 0 : lo,
            isTrue(state, "east") ? 1 : hi, isTrue(state, "up") ? 1 : hi, isTrue(state, "south") ? 1 : hi);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(selectionBox(state, world, x, y, z));
    }

    /** canSurviveAt: on end stone or plant, or beside a plant that is itself rooted (with air above or below). */
    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        boolean upEmpty = world.getState(x, y + 1, z).block().isAir();
        boolean downEmpty = world.getState(x, y - 1, z).block().isAir();
        for (Direction side : Direction.HORIZONTALS) {
            if (!(neighbour(world, x, y, z, side).block() instanceof BlockChorusPlant)) continue;
            if (!upEmpty && !downEmpty) return false;
            Block under = world.getState(x + side.xOffset, y - 1, z + side.zOffset).block();
            if (under instanceof BlockChorusPlant || isEndStone(under)) return true;
        }
        Block below = world.getState(x, y - 1, z).block();
        return below instanceof BlockChorusPlant || isEndStone(below);
    }
}

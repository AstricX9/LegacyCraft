package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.SortedMap;
import java.util.TreeMap;

/** Tripwire: connects to tripwire and to hooks facing it. */
public class BlockTripWire extends Block {
    public BlockTripWire(Properties properties) { super(properties); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        for (Direction side : Direction.HORIZONTALS) {
            BlockState other = neighbour(world, x, y, z, side);
            boolean connected = other.block() == this
                || other.block() instanceof BlockTripWireHook && other.get("facing").equals(side.opposite().getName());
            values.put(side.getName(), flag(connected));
        }
        return new ActualState(state, values);
    }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        if (!isTrue(state, "suspended")) return new AxisAlignedBB(0, 0, 0, 1, .09375, 1);
        return isTrue(state, "attached") ? new AxisAlignedBB(0, .0625, 0, 1, .15625, 1) : new AxisAlignedBB(0, 0, 0, 1, .5, 1);
    }
}

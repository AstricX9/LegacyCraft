package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Tripwire hooks: on the side of a normal cube; "suspended" when there is no opaque block below. */
public class BlockTripWireHook extends Block {
    public BlockTripWireHook(Properties properties) { super(properties); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        values.put("suspended", flag(!world.getState(x, y - 1, z).block().isOpaque()));
        return new ActualState(state, values);
    }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return against(facing(state), .375, .2, .8, .3125, .6875);
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        if (!face.isHorizontal()) return List.of();
        return List.of(new BlockPlacement.Placement(x, y, z, BlockState.of(this, itemMetadata).with("facing", face.getName())));
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return attachedTo(world, x, y, z, facing(state));
    }
}

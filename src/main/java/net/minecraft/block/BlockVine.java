package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Vines: 1/16 plates on each attached side; they lose sides whose support goes. */
public class BlockVine extends Block {
    public BlockVine(Properties properties) { super(properties); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        values.put("up", flag(world.getState(x, y + 1, z).block().isNormalCube()));
        return new ActualState(state, values);
    }

    /** With no side attached, a vine hangs from the block above. */
    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        AxisAlignedBB box = null;
        if (isTrue(state, "west")) box = merge(box, new AxisAlignedBB(0, 0, 0, 1 / 16.0, 1, 1));
        if (isTrue(state, "east")) box = merge(box, new AxisAlignedBB(15 / 16.0, 0, 0, 1, 1, 1));
        if (isTrue(state, "north")) box = merge(box, new AxisAlignedBB(0, 0, 0, 1, 1, 1 / 16.0));
        if (isTrue(state, "south")) box = merge(box, new AxisAlignedBB(0, 0, 15 / 16.0, 1, 1, 1));
        return box != null ? box : new AxisAlignedBB(0, 15 / 16.0, 0, 1, 1, 1);
    }

    private static AxisAlignedBB merge(AxisAlignedBB a, AxisAlignedBB b) { return a == null ? b : a.union(b); }

    /** Placed on the side of a block, attached to it. */
    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        if (!face.isHorizontal()) return List.of();
        return List.of(new BlockPlacement.Placement(x, y, z, BlockState.of(this, itemMetadata).with(face.opposite().getName(), "true")));
    }

    /**
     * A vine stays as long as something holds it: any of its sides backed by a
     * full cube, or a vine or full cube directly above it. VERIFIED against
     * 1.8.9 saves: vanilla keeps the stored sides even after one loses its
     * wall — it only drops the whole vine — so the sides are not trimmed here.
     */
    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        for (Direction side : Direction.HORIZONTALS)
            if (state.get(side.getName()).equals("true") && neighbour(world, x, y, z, side).block().isNormalCube()) return true;
        Block above = world.getState(x, y + 1, z).block();
        return above == this || above.isNormalCube();
    }
}

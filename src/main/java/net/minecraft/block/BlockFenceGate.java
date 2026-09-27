package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Fence gates: lowered between walls, no collision when open, facing the player when placed. */
public class BlockFenceGate extends Block {
    public BlockFenceGate(Properties properties) { super(properties); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        Direction facing = facing(state);
        values.put("in_wall", flag(neighbour(world, x, y, z, facing.rotateYCCW()).block() instanceof BlockWall
            || neighbour(world, x, y, z, facing.rotateY()).block() instanceof BlockWall));
        return new ActualState(state, values);
    }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return facing(state).axis() == 'z' ? new AxisAlignedBB(0, 0, .375, 1, 1, .625) : new AxisAlignedBB(.375, 0, 0, .625, 1, 1);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        if (isTrue(state, "open")) return List.of();
        return List.of(facing(state).axis() == 'z'
            ? new AxisAlignedBB(0, 0, .375, 1, 1.5, .625) : new AxisAlignedBB(.375, 0, 0, .625, 1.5, 1));
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        return List.of(new BlockPlacement.Placement(x, y, z,
            BlockState.of(this, itemMetadata).with("facing", context.playerFacing().getName())));
    }
}

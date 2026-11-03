package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** 1.9 end rods: a thin rod pointing out of the clicked face (BlockEndRod). */
public class BlockEndRod extends Block {
    public BlockEndRod(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return switch (facing(state).axis()) {
            case 'x' -> new AxisAlignedBB(0, .375, .375, 1, .625, .625);
            case 'z' -> new AxisAlignedBB(.375, .375, 0, .625, .625, 1);
            default -> new AxisAlignedBB(.375, 0, .375, .625, 1, .625);
        };
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(selectionBox(state, world, x, y, z));
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        // Placed against another rod facing the same way, it points back into it (BlockEndRod.onBlockPlaced).
        BlockState against = world.getState(x - face.xOffset, y - face.yOffset, z - face.zOffset);
        Direction facing = face;
        if (against.block() == this && facing(against) == face) facing = face.opposite();
        return List.of(new BlockPlacement.Placement(x, y, z, BlockState.of(this, itemMetadata).with("facing", facing.getName())));
    }
}

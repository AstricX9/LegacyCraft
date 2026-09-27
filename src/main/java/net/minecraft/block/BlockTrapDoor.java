package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Trapdoors: 1.8 trapdoors hang on the side of a block, in its upper or lower half. */
public class BlockTrapDoor extends Block {
    private static final double THICKNESS = 3 / 16.0;

    public BlockTrapDoor(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        if (isTrue(state, "open")) return against(facing(state), THICKNESS, 0, 1, 0, 1);
        return state.get("half").equals("top") ? new AxisAlignedBB(0, 1 - THICKNESS, 0, 1, 1, 1)
            : new AxisAlignedBB(0, 0, 0, 1, THICKNESS, 1);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(selectionBox(state, world, x, y, z));
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        if (!face.isHorizontal()) return List.of();
        BlockState state = BlockState.of(this, itemMetadata).with("facing", face.getName()).with("open", "false")
            .with("half", context.hitY() > .5f ? "top" : "bottom");
        return List.of(new BlockPlacement.Placement(x, y, z, state));
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return attachedTo(world, x, y, z, facing(state));
    }
}

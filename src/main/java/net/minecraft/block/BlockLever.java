package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Levers: on walls, floors or ceilings; floor/ceiling levers align with the player's axis. */
public class BlockLever extends Block {
    public BlockLever(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return switch (state.get("facing")) {
            case "up_x", "up_z" -> new AxisAlignedBB(.25, 0, .25, .75, .6, .75);
            case "down_x", "down_z" -> new AxisAlignedBB(.25, .4, .25, .75, 1, .75);
            default -> against(facing(state), .375, .2, .8, .3125, .6875);
        };
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        char axis = context.playerFacing().axis();
        String orientation = switch (face) {
            case UP -> axis == 'x' ? "up_x" : "up_z";
            case DOWN -> axis == 'x' ? "down_x" : "down_z";
            default -> face.getName();
        };
        return List.of(new BlockPlacement.Placement(x, y, z, BlockState.of(this, itemMetadata).with("facing", orientation)));
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return switch (state.get("facing")) {
            case "up_x", "up_z" -> hasSolidTop(world.getState(x, y - 1, z));
            case "down_x", "down_z" -> world.getState(x, y + 1, z).block().isNormalCube();
            default -> attachedTo(world, x, y, z, facing(state));
        };
    }
}

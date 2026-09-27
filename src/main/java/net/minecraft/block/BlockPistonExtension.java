package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** The piston head (minecraft:piston_head): a plate at the front plus the arm reaching back into the base. */
public class BlockPistonExtension extends Block {
    public BlockPistonExtension(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return collisionBoxes(state, world, x, y, z).get(0);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return switch (facing(state)) {
            case DOWN -> List.of(new AxisAlignedBB(0, 0, 0, 1, .25, 1), new AxisAlignedBB(.375, .25, .375, .625, 1.25, .625));
            case UP -> List.of(new AxisAlignedBB(0, .75, 0, 1, 1, 1), new AxisAlignedBB(.375, -.25, .375, .625, .75, .625));
            case NORTH -> List.of(new AxisAlignedBB(0, 0, 0, 1, 1, .25), new AxisAlignedBB(.375, .375, .25, .625, .625, 1.25));
            case SOUTH -> List.of(new AxisAlignedBB(0, 0, .75, 1, 1, 1), new AxisAlignedBB(.375, .375, -.25, .625, .625, .75));
            case WEST -> List.of(new AxisAlignedBB(0, 0, 0, .25, 1, 1), new AxisAlignedBB(.25, .375, .375, 1.25, .625, .625));
            case EAST -> List.of(new AxisAlignedBB(.75, 0, 0, 1, 1, 1), new AxisAlignedBB(-.25, .375, .375, .75, .625, .625));
        };
    }
}

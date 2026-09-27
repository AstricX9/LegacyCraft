package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Cocoa pods: grow with age and hang on the side of a jungle log their facing points to. */
public class BlockCocoa extends Block {
    public BlockCocoa(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        int age = Integer.parseInt(state.get("age"));
        double width = (4 + age * 2) / 16.0, height = (5 + age * 2) / 16.0, half = width / 2;
        double minY = .75 - height;
        return switch (facing(state)) {
            case SOUTH -> new AxisAlignedBB(.5 - half, minY, 15 / 16.0 - width, .5 + half, .75, 15 / 16.0);
            case NORTH -> new AxisAlignedBB(.5 - half, minY, 1 / 16.0, .5 + half, .75, 1 / 16.0 + width);
            case WEST -> new AxisAlignedBB(1 / 16.0, minY, .5 - half, 1 / 16.0 + width, .75, .5 + half);
            default -> new AxisAlignedBB(15 / 16.0 - width, minY, .5 - half, 15 / 16.0, .75, .5 + half);
        };
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(selectionBox(state, world, x, y, z));
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        var facing = facing(state);
        BlockState log = world.getState(x + facing.xOffset, y, z + facing.zOffset);
        return log.block().name().path().equals("log") && log.get("variant").equals("jungle");
    }
}

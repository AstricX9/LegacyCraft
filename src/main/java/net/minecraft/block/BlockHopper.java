package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Hoppers: a basin you can stand in; the spout points into the clicked block (down when clicking a ceiling). */
public class BlockHopper extends Block {
    public BlockHopper(Properties properties) { super(properties); }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return basin(10 / 16.0);
    }

    /** A floor at the given height plus four 2/16-thick walls (hoppers and cauldrons). */
    static List<AxisAlignedBB> basin(double floor) {
        double wall = .125;
        return List.of(new AxisAlignedBB(0, 0, 0, 1, floor, 1),
            new AxisAlignedBB(0, 0, 0, wall, 1, 1), new AxisAlignedBB(0, 0, 0, 1, 1, wall),
            new AxisAlignedBB(1 - wall, 0, 0, 1, 1, 1), new AxisAlignedBB(0, 0, 1 - wall, 1, 1, 1));
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        Direction spout = face == Direction.DOWN ? Direction.DOWN : face.opposite();
        return List.of(new BlockPlacement.Placement(x, y, z, BlockState.of(this, itemMetadata).with("facing", spout.getName())));
    }
}

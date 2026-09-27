package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Glass panes and iron bars: connect to opaque cubes, glass and other panes; thin arm collision. */
public class BlockPane extends Block {
    public BlockPane(Properties properties) { super(properties); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        for (Direction side : Direction.HORIZONTALS) {
            Block other = neighbour(world, x, y, z, side).block();
            String name = other.name().toString();
            values.put(side.getName(), flag(other.isOpaque() || other instanceof BlockPane
                || name.equals("minecraft:glass") || name.equals("minecraft:stained_glass")));
        }
        return new ActualState(state, values);
    }

    /** Arms along each connected axis; an unconnected pane is a full cross. */
    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        boolean north = isTrue(state, "north"), south = isTrue(state, "south");
        boolean west = isTrue(state, "west"), east = isTrue(state, "east");
        boolean any = north || south || west || east;
        List<AxisAlignedBB> boxes = new ArrayList<>();
        if ((!west || !east) && any) {
            if (west) boxes.add(new AxisAlignedBB(0, 0, .4375, .5, 1, .5625));
            else if (east) boxes.add(new AxisAlignedBB(.5, 0, .4375, 1, 1, .5625));
        } else {
            boxes.add(new AxisAlignedBB(0, 0, .4375, 1, 1, .5625));
        }
        if ((!north || !south) && any) {
            if (north) boxes.add(new AxisAlignedBB(.4375, 0, 0, .5625, 1, .5));
            else if (south) boxes.add(new AxisAlignedBB(.4375, 0, .5, .5625, 1, 1));
        } else {
            boxes.add(new AxisAlignedBB(.4375, 0, 0, .5625, 1, 1));
        }
        return boxes;
    }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return BlockFence.unionAtHeight(collisionBoxes(state, world, x, y, z), 1);
    }
}

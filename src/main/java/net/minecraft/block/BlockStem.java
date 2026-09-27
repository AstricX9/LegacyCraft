package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.SortedMap;
import java.util.TreeMap;

/** Pumpkin and melon stems: on farmland, growing taller with age, bending towards an adjacent fruit. */
public class BlockStem extends BlockBush {
    private final String fruit;

    public BlockStem(Properties properties, String fruit) {
        super(properties);
        this.fruit = fruit;
    }

    /** The first fruit found scanning north, east, south, west. */
    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        values.put("facing", "up");
        for (Direction side : Direction.HORIZONTALS) {
            if (neighbour(world, x, y, z, side).block().name().toString().equals(fruit)) {
                values.put("facing", side.getName());
                break;
            }
        }
        return new ActualState(state, values);
    }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return new AxisAlignedBB(.375, 0, .375, .625, (Integer.parseInt(state.get("age")) * 2 + 2) / 16.0, .625);
    }

    @Override
    protected boolean canGrowOn(BlockState below) { return below.block().name().path().equals("farmland"); }
}

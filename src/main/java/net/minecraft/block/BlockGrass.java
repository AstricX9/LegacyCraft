package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.world.IBlockAccess;

import java.util.SortedMap;
import java.util.TreeMap;

/** Grass and mycelium (and podzol through {@link BlockDirt}): show a snowy side under snow. */
public class BlockGrass extends Block {
    public BlockGrass(Properties properties) { super(properties); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        return snowy(world, x, y, z, state);
    }

    static ActualState snowy(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        String above = world.getState(x, y + 1, z).block().name().path();
        values.put("snowy", flag(above.equals("snow") || above.equals("snow_layer")));
        return new ActualState(state, values);
    }
}

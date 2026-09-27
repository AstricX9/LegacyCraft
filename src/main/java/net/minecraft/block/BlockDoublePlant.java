package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Two-block-tall plants: the upper half stores nothing but "upper" and shows the variant of the half below. */
public class BlockDoublePlant extends BlockBush {
    public BlockDoublePlant(Properties properties) { super(properties); }

    @Override
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        SortedMap<String, String> values = new TreeMap<>(state.values());
        if (state.get("half").equals("upper")) {
            BlockState below = world.getState(x, y - 1, z);
            if (below.block() == this) values.put("variant", below.get("variant"));
        }
        return new ActualState(state, values);
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        if (y + 1 >= 256 || !BlockPlacement.isReplaceable(world.getState(x, y + 1, z))) return List.of();
        return List.of(new BlockPlacement.Placement(x, y, z, BlockState.of(this, itemMetadata).with("half", "lower")),
            new BlockPlacement.Placement(x, y + 1, z, BlockState.of(this, 0).with("half", "upper")));
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        if (state.get("half").equals("upper")) {
            BlockState below = world.getState(x, y - 1, z);
            return below.block() == this && below.get("half").equals("lower");
        }
        BlockState above = world.getState(x, y + 1, z);
        return above.block() == this && above.get("half").equals("upper") && super.canStay(world, x, y, z, state);
    }
}

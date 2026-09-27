package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Snow layers: 1-8 eighths high; entities sink one layer in. Not on ice. */
public class BlockSnowLayer extends Block {
    public BlockSnowLayer(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return new AxisAlignedBB(0, 0, 0, 1, Integer.parseInt(state.get("layers")) / 8.0, 1);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(new AxisAlignedBB(0, 0, 0, 1, (Integer.parseInt(state.get("layers")) - 1) / 8.0, 1));
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        BlockState below = world.getState(x, y - 1, z);
        String name = below.block().name().path();
        if (name.equals("ice") || name.equals("packed_ice")) return false;
        if (below.block().material().equals("leaves")) return true;
        if (below.block() == this) return below.get("layers").equals("8");
        return below.block().isOpaque() && below.block().isSolid();
    }
}

package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Quartz blocks: pillar quartz takes the axis of the clicked face. */
public class BlockQuartz extends Block {
    public BlockQuartz(Properties properties) { super(properties); }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        BlockState state = BlockState.of(this, itemMetadata);
        if (state.get("variant").startsWith("lines_")) state = state.with("variant", "lines_" + face.axis());
        return List.of(new BlockPlacement.Placement(x, y, z, state));
    }
}

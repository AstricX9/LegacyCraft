package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Logs and hay bales: the axis follows the clicked face. */
public class BlockRotatedPillar extends Block {
    public BlockRotatedPillar(Properties properties) { super(properties); }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        return List.of(new BlockPlacement.Placement(x, y, z,
            BlockState.of(this, itemMetadata).with("axis", String.valueOf(face.axis()))));
    }
}

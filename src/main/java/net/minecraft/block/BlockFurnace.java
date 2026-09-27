package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Furnaces (lit and unlit): front faces the player. */
public class BlockFurnace extends Block {
    public BlockFurnace(Properties properties) { super(properties); }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        return facingAwayFromPlayer(this, x, y, z, itemMetadata, context);
    }

    /** The block's facing points back at the player (shared by furnaces, chests, pumpkins, portal frames). */
    static List<BlockPlacement.Placement> facingAwayFromPlayer(Block block, int x, int y, int z, int itemMetadata,
                                                               BlockPlacement.Context context) {
        return List.of(new BlockPlacement.Placement(x, y, z,
            BlockState.of(block, itemMetadata).with("facing", context.playerFacing().opposite().getName())));
    }
}

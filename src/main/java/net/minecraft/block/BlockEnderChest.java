package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Ender chests: never double; front faces the player. */
public class BlockEnderChest extends Block {
    public BlockEnderChest(Properties properties) { super(properties); }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        return BlockFurnace.facingAwayFromPlayer(this, x, y, z, itemMetadata, context);
    }
}

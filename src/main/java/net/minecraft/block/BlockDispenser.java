package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Dispensers and droppers: face the player, vertically only when the player is close above or below. */
public class BlockDispenser extends Block {
    public BlockDispenser(Properties properties) { super(properties); }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        return List.of(new BlockPlacement.Placement(x, y, z,
            BlockState.of(this, itemMetadata).with("facing", towardPlayer(context, x, y, z).getName())));
    }

    /** Shared with pistons. */
    static Direction towardPlayer(BlockPlacement.Context context, int x, int y, int z) {
        if (Math.abs(context.playerX() - (x + .5)) < 2 && Math.abs(context.playerZ() - (z + .5)) < 2) {
            if (context.playerEyeY() - y > 2) return Direction.UP;
            if (y - context.playerEyeY() > 0) return Direction.DOWN;
        }
        return context.playerFacing().opposite();
    }
}

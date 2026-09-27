package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** End portal frames: 13/16 high, plus the eye when inserted; face the player. */
public class BlockEndPortalFrame extends Block {
    public BlockEndPortalFrame(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return new AxisAlignedBB(0, 0, 0, 1, 13 / 16.0, 1);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        AxisAlignedBB base = selectionBox(state, world, x, y, z);
        return isTrue(state, "eye") ? List.of(base, new AxisAlignedBB(.3125, 13 / 16.0, .3125, .6875, 1, .6875)) : List.of(base);
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        return BlockFurnace.facingAwayFromPlayer(this, x, y, z, itemMetadata, context);
    }
}

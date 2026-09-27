package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Chests and trapped chests: inset 1/16, extended towards a neighbouring chest of the same kind; front faces the player. */
public class BlockChest extends Block {
    public BlockChest(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        double minX = 1 / 16.0, maxX = 15 / 16.0, minZ = 1 / 16.0, maxZ = 15 / 16.0;
        if (world.getState(x, y, z - 1).block() == this) minZ = 0;
        else if (world.getState(x, y, z + 1).block() == this) maxZ = 1;
        else if (world.getState(x - 1, y, z).block() == this) minX = 0;
        else if (world.getState(x + 1, y, z).block() == this) maxX = 1;
        return new AxisAlignedBB(minX, 0, minZ, maxX, .875, maxZ);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(selectionBox(state, world, x, y, z));
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        return BlockFurnace.facingAwayFromPlayer(this, x, y, z, itemMetadata, context);
    }
}

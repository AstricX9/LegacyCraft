package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Beds: two halves that need each other; the head lies one block along the facing from the foot. */
public class BlockBed extends Block {
    public BlockBed(Properties properties) { super(properties); }

    /**
     * ItemBed.onItemUse: only on the top of a block; the foot goes in the
     * block above (or in place of a replaceable one), the head one block
     * further along the player's facing, both into air or replaceable blocks
     * with a solid top under each.
     */
    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        if (context.face() != Direction.UP) return List.of();
        Direction facing = context.playerFacing();
        int headX = x + facing.xOffset, headZ = z + facing.zOffset;
        if (!BlockPlacement.isReplaceable(world.getState(headX, y, headZ))) return List.of();
        if (!hasSolidTop(world.getState(x, y - 1, z)) || !hasSolidTop(world.getState(headX, y - 1, headZ))) return List.of();
        BlockState foot = BlockState.of(this, 0).with("facing", facing.getName()).with("occupied", "false").with("part", "foot");
        return List.of(new BlockPlacement.Placement(x, y, z, foot),
            new BlockPlacement.Placement(headX, y, headZ, foot.with("part", "head")));
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        Direction facing = facing(state);
        boolean head = state.get("part").equals("head");
        int sign = head ? -1 : 1;
        BlockState other = world.getState(x + facing.xOffset * sign, y, z + facing.zOffset * sign);
        return other.block() == this && other.get("part").equals(head ? "foot" : "head");
    }
}

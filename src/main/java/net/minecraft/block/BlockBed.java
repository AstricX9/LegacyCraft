package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

/** Beds: two halves that need each other; the head lies one block along the facing from the foot. */
public class BlockBed extends Block {
    public BlockBed(Properties properties) { super(properties); }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        Direction facing = facing(state);
        boolean head = state.get("part").equals("head");
        int sign = head ? -1 : 1;
        BlockState other = world.getState(x + facing.xOffset * sign, y, z + facing.zOffset * sign);
        return other.block() == this && other.get("part").equals(head ? "foot" : "head");
    }
}

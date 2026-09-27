package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Torches (and redstone torches): stand on a supporting top or lean on a wall. */
public class BlockTorch extends Block {
    public BlockTorch(Properties properties) { super(properties); }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        String facing = state.get("facing");
        return facing.equals("up") ? new AxisAlignedBB(.4, 0, .4, .6, .6, .6)
            : against(Direction.fromName(facing), .3, .2, .8, .35, .65);
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        Direction attach = attachment(world, x, y, z, face);
        if (attach == null) return List.of();
        return List.of(new BlockPlacement.Placement(x, y, z, BlockState.of(this, itemMetadata).with("facing", attach.getName())));
    }

    /** The clicked face if it can hold the torch, else the first supported wall, else the floor. */
    private static Direction attachment(IBlockAccess world, int x, int y, int z, Direction face) {
        if (face == Direction.UP && holdsTorch(world.getState(x, y - 1, z))) return Direction.UP;
        if (face.isHorizontal() && attachedTo(world, x, y, z, face)) return face;
        Direction side = firstSupportedSide(world, x, y, z);
        if (side != null) return side;
        return holdsTorch(world.getState(x, y - 1, z)) ? Direction.UP : null;
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        String facing = state.get("facing");
        return facing.equals("up") ? holdsTorch(world.getState(x, y - 1, z)) : attachedTo(world, x, y, z, Direction.fromName(facing));
    }

    /** Torches stand on solid tops, fences, walls and glass. */
    private static boolean holdsTorch(BlockState below) {
        Block block = below.block();
        String name = block.name().path();
        return hasSolidTop(below) || block instanceof BlockFence || block instanceof BlockWall
            || name.equals("glass") || name.equals("stained_glass");
    }
}

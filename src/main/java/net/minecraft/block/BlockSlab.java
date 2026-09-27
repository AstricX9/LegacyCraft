package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Single slabs: half-height, and a matching slab clicked on its open half becomes the double slab block. */
public class BlockSlab extends Block {
    private final String doubleSlab;

    public BlockSlab(Properties properties, String doubleSlab) {
        super(properties);
        this.doubleSlab = doubleSlab;
    }

    @Override
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return state.get("half").equals("top") ? new AxisAlignedBB(0, .5, 0, 1, 1, 1) : new AxisAlignedBB(0, 0, 0, 1, .5, 1);
    }

    @Override
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        return List.of(selectionBox(state, world, x, y, z));
    }

    @Override
    public BlockPlacement.Placement placeOnto(IBlockAccess world, BlockState clicked, int itemMetadata, BlockPlacement.Context context) {
        if (clicked.block() != this) return null;
        BlockState held = BlockState.of(this, itemMetadata);
        if (!clicked.get("variant").equals(held.get("variant"))) return null;
        String half = clicked.get("half");
        Direction face = context.face();
        if (!(face == Direction.UP && half.equals("bottom") || face == Direction.DOWN && half.equals("top"))) return null;
        Block doubleBlock = world.registry().get(doubleSlab);
        BlockState result = BlockState.of(doubleBlock, 0);
        if (doubleBlock.stateDefinition().isStored("variant")) result = result.with("variant", held.get("variant"));
        return new BlockPlacement.Placement(context.clickedX(), context.clickedY(), context.clickedZ(), result);
    }

    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        BlockState state = BlockState.of(this, itemMetadata).with("half", bottomHalf(face, context.hitY()) ? "bottom" : "top");
        return List.of(new BlockPlacement.Placement(x, y, z, state));
    }

    /** Stairs and slabs go in the lower half unless the underside or the upper half of a side was clicked. */
    static boolean bottomHalf(Direction face, float hitY) {
        return face != Direction.DOWN && (face == Direction.UP || hitY <= .5f);
    }
}

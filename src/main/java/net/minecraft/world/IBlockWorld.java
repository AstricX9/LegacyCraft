package net.minecraft.world;

import net.minecraft.block.BlockState;

/** Block access that can also be written, for block-update logic shared by the world and tests. */
public interface IBlockWorld extends IBlockAccess {
    void setState(int x, int y, int z, BlockState state);

    /**
     * A neighbour of this position changed and the block here stayed
     * (onNeighborBlockChange for blocks that react through the world, such as
     * still liquid starting to flow). Nothing by default.
     */
    default void neighbourChanged(int x, int y, int z, BlockState state) {}
}

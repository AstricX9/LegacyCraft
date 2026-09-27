package net.minecraft.world;

import net.minecraft.block.BlockState;

/** Block access that can also be written, for block-update logic shared by the world and tests. */
public interface IBlockWorld extends IBlockAccess {
    void setState(int x, int y, int z, BlockState state);
}

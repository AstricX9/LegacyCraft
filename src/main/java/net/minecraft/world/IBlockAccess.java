package net.minecraft.world;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;

/** Read-only stored block-state access, shared by the world and by focused tests. */
public interface IBlockAccess {
    BlockState getState(int x, int y, int z);
    BlockRegistry registry();
}

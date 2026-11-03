package net.minecraft.block;

import net.minecraft.world.IBlockAccess;

/** 1.9 grass paths: a shovel-flattened grass block that turns to dirt under a solid block (BlockGrassPath). */
public class BlockGrassPath extends Block {
    public BlockGrassPath(Properties properties) { super(properties); }

    @Override
    public BlockState afterNeighborChange(IBlockAccess world, int x, int y, int z, BlockState state) {
        Block above = world.getState(x, y + 1, z).block();
        if (above.isAir() || !above.isSolid()) return state;
        return BlockState.of(world.registry().get("minecraft:dirt"), 0);
    }
}

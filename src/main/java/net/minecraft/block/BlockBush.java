package net.minecraft.block;

import net.minecraft.world.IBlockAccess;

import java.util.Set;

/** Flowers, grass, ferns and saplings: need grass, dirt or farmland below. Subclasses change the soil. */
public class BlockBush extends Block {
    private static final Set<String> SOIL = Set.of("grass", "dirt", "farmland");

    public BlockBush(Properties properties) { super(properties); }

    /** Whether this plant can grow on the given block. */
    protected boolean canGrowOn(BlockState below) {
        return SOIL.contains(below.block().name().path());
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return canGrowOn(world.getState(x, y - 1, z));
    }
}

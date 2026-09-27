package net.minecraft.block;

/** Small mushrooms: on any opaque block (light levels are not checked yet). */
public class BlockMushroom extends BlockBush {
    public BlockMushroom(Properties properties) { super(properties); }

    @Override
    protected boolean canGrowOn(BlockState below) { return below.block().isOpaque(); }
}

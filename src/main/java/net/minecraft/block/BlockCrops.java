package net.minecraft.block;

/** Wheat, carrots and potatoes: on farmland only. */
public class BlockCrops extends BlockBush {
    public BlockCrops(Properties properties) { super(properties); }

    @Override
    protected boolean canGrowOn(BlockState below) { return below.block().name().path().equals("farmland"); }
}

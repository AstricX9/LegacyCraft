package net.minecraft.block;

/** Nether wart: on soul sand only. */
public class BlockNetherWart extends BlockBush {
    public BlockNetherWart(Properties properties) { super(properties); }

    @Override
    protected boolean canGrowOn(BlockState below) { return below.block().name().path().equals("soul_sand"); }
}

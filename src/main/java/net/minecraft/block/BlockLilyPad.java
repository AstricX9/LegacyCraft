package net.minecraft.block;

/** Lily pads: on still water or ice. */
public class BlockLilyPad extends BlockBush {
    public BlockLilyPad(Properties properties) { super(properties); }

    @Override
    protected boolean canGrowOn(BlockState below) {
        String name = below.block().name().path();
        return name.equals("water") && below.get("level").equals("0") || name.equals("ice");
    }
}

package net.minecraft.block;

import java.util.Set;

/** Dead bushes: grow on sand, (stained) hardened clay and dirt. */
public class BlockDeadBush extends BlockBush {
    private static final Set<String> SOIL = Set.of("sand", "hardened_clay", "stained_hardened_clay", "dirt");

    public BlockDeadBush(Properties properties) { super(properties); }

    @Override
    protected boolean canGrowOn(BlockState below) { return SOIL.contains(below.block().name().path()); }
}

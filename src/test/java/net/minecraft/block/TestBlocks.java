package net.minecraft.block;

import net.minecraft.block.state.BlockStateDefinition;
import net.minecraft.util.ResourceLocation;

import java.util.List;

/** Blocks made without the resource pack, for tests that only need a block's name, material or class. */
public final class TestBlocks {
    private TestBlocks() {}

    /** A plain full block. */
    public static Block block(String name, String material) { return new Block(properties(name, material)) {}; }

    /** A door block (its item is not a block item in vanilla). */
    public static Block door(String name) { return new BlockDoor(properties(name, "wood")); }

    private static Block.Properties properties(String name, String material) {
        return new Block.Properties(0, new ResourceLocation("minecraft", name), material, true, true, true, false, 1f,
            Block.RenderType.MODEL, Block.CullSame.NONE, 0xFFFFFF, 0, 255, false, null, BlockStateDefinition.EMPTY,
            null, List.of(), true);
    }
}

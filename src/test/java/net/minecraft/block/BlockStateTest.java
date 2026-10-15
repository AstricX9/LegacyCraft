package net.minecraft.block;

import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Block states are shared values: one object per (block, metadata), still compared by value. */
final class BlockStateTest {
    private final BlockRegistry blocks = GameData.blocks();

    @Test
    void theSameStateIsTheSameObjectHoweverItIsReached() {
        Block wool = blocks.get("minecraft:wool");
        BlockState red = BlockState.of(wool, 14);
        assertSame(red, BlockState.of(wool, 14));
        assertSame(red, BlockState.unpack(blocks, red.packed()));
        assertSame(BlockState.defaultOf(wool), BlockState.of(wool, 0));
        assertNotSame(red, BlockState.of(wool, 13));
        assertEquals(red, BlockState.of(wool, 14));
    }

    @Test
    void metadataIsStillCheckedBeforeAnythingIsShared() {
        Block stone = blocks.get("minecraft:stone");
        assertThrows(IllegalArgumentException.class, () -> BlockState.of(stone, 16));
        assertThrows(IllegalArgumentException.class, () -> BlockState.of(stone, -1));
        assertThrows(NullPointerException.class, () -> BlockState.of(null, 0));
    }
}

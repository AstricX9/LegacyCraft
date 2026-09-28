package net.minecraft.block.state;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Spot checks of the legacy metadata layouts (data/mc189/block_states/, tagged HYPOTHESIS). */
final class BlockStateDefinitionTest {
    private final BlockRegistry blocks = GameData.blocks();

    private BlockState state(String block, int meta) { return BlockState.of(blocks.get(block), meta); }

    @Test
    void decodesDocumentedMetadataLayouts() {
        assertEquals("spruce", state("minecraft:log", 5).get("variant"));
        assertEquals("x", state("minecraft:log", 5).get("axis"));
        assertEquals("none", state("minecraft:log", 12).get("axis"));
        assertEquals("dark_oak", state("minecraft:log2", 1).get("variant"));

        assertEquals("false", state("minecraft:leaves", 4).get("decayable"));
        assertEquals("true", state("minecraft:leaves", 8).get("check_decay"));

        assertEquals("south", state("minecraft:oak_stairs", 6).get("facing"));
        assertEquals("top", state("minecraft:oak_stairs", 6).get("half"));

        assertEquals("up", state("minecraft:torch", 5).get("facing"));
        assertEquals("east", state("minecraft:torch", 1).get("facing"));
        assertEquals("up", state("minecraft:torch", 0).get("facing"), "invalid torch metadata falls back to up");

        assertEquals("red", state("minecraft:wool", 14).get("color"));
        assertEquals("smooth_andesite", state("minecraft:stone", 6).get("variant"));
        assertEquals("stone", state("minecraft:stone", 9).get("variant"), "out-of-range stone metadata is plain stone");

        assertEquals("north", state("minecraft:furnace", 0).get("facing"), "vertical furnace metadata falls back to north");
        assertEquals("east", state("minecraft:furnace", 5).get("facing"));

        assertEquals("true", state("minecraft:double_stone_slab", 8).get("seamless"));
        assertEquals("top", state("minecraft:wooden_slab", 10).get("half"));
        assertEquals("birch", state("minecraft:wooden_slab", 10).get("variant"));
    }

    @Test
    void doorHalvesStoreDifferentProperties() {
        BlockState lower = state("minecraft:wooden_door", 0b0111);
        assertEquals("lower", lower.get("half"));
        assertEquals("north", lower.get("facing"));
        assertEquals("true", lower.get("open"));

        BlockState upper = state("minecraft:wooden_door", 0b1001);
        assertEquals("upper", upper.get("half"));
        assertEquals("right", upper.get("hinge"));
        assertEquals("false", upper.get("powered"));
    }

    @Test
    void defaultStatesAreNotAlwaysMetadataZero() {
        assertEquals(5, blocks.get("minecraft:torch").defaultState().metadata());
        assertEquals(2, blocks.get("minecraft:furnace").defaultState().metadata());
        assertEquals(3, blocks.get("minecraft:oak_stairs").defaultState().metadata());
        assertEquals(14, blocks.get("minecraft:brown_mushroom_block").defaultState().metadata());
        assertEquals(0, blocks.get("minecraft:stone").defaultState().metadata());
    }

    @Test
    void withReencodesStoredPropertiesAndRejectsDerivedOnes() {
        BlockState log = state("minecraft:log", 2).with("axis", "z");
        assertEquals(10, log.metadata());
        assertEquals("birch", log.get("variant"));

        BlockState stairs = blocks.get("minecraft:oak_stairs").defaultState();
        assertThrows(IllegalArgumentException.class, () -> stairs.with("shape", "outer_left"));
        assertThrows(IllegalArgumentException.class, () -> stairs.with("facing", "up"));
    }

    @Test
    void decodingIsStableUnderReencoding() {
        for (Block block : blocks.values()) {
            BlockStateDefinition definition = block.stateDefinition();
            for (int meta = 0; meta < 16; meta++) {
                var decoded = definition.decode(meta);
                assertEquals(decoded, definition.decode(definition.encode(decoded)), block + " meta " + meta);
            }
        }
    }
}

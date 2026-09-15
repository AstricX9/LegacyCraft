package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.resources.ClasspathResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the (block, metadata) storage added on top of the existing
 * Chunk/ChunkSection short-array encoding (see BlockState and the
 * World.getState/setState pair). Nothing yet computes a *meaningful*
 * metadata for any block — this only proves the plumbing is sound: values
 * round-trip, unrelated positions are unaffected, and the legacy-vanilla
 * pack/unpack formula ((id << 4) | metadata) is exactly what's stored.
 */
class WorldBlockStateTest {
    private final BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());

    @Test
    void setStateAndGetStateRoundTripMetadata() {
        World world = new World(blocks, 4242L);
        Block wool = blocks.get("minecraft:wool");

        world.setState(10, 90, 10, BlockState.of(wool, 14));

        BlockState read = world.getState(10, 90, 10);
        assertEquals(wool, read.block());
        assertEquals(14, read.metadata());
    }

    @Test
    void plainSetDefaultsToMetadataZero() {
        World world = new World(blocks, 4242L);
        Block cobblestone = blocks.get("minecraft:cobblestone");

        world.set(11, 90, 10, cobblestone);

        assertEquals(BlockState.of(cobblestone, 0), world.getState(11, 90, 10));
    }

    @Test
    void getIgnoresMetadataForBackwardCompatibility() {
        World world = new World(blocks, 4242L);
        Block wool = blocks.get("minecraft:wool");

        world.setState(12, 90, 10, BlockState.of(wool, 7));

        assertEquals(wool, world.get(12, 90, 10), "world.get must still work for callers that don't know about metadata");
    }

    @Test
    void settingOneStateDoesNotDisturbNeighbors() {
        World world = new World(blocks, 4242L);
        Block wool = blocks.get("minecraft:wool");
        Block stone = blocks.get("minecraft:stone");
        world.set(13, 90, 10, stone);

        world.setState(14, 90, 10, BlockState.of(wool, 3));

        assertEquals(stone, world.get(13, 90, 10));
        assertEquals(BlockState.of(stone, 0), world.getState(13, 90, 10));
    }

    @Test
    void unsetPositionDefaultsToAirAtMetadataZero() {
        World world = new World(blocks, 4242L);
        assertEquals(BlockState.of(blocks.air(), 0), world.getState(200, 250, 200));
    }

    @Test
    void packedMatchesLegacyExtendedStorageFormula() {
        Block diamondBlock = blocks.get("minecraft:diamond_block");
        BlockState state = BlockState.of(diamondBlock, 5);

        assertEquals((diamondBlock.registryId() << BlockState.METADATA_BITS) | 5, state.packed());
    }

    @Test
    void unpackReversesPacked() {
        Block glass = blocks.get("minecraft:glass");
        BlockState original = BlockState.of(glass, 0);

        BlockState roundTripped = BlockState.unpack(blocks, original.packed());

        assertEquals(original, roundTripped);
    }

    @Test
    void metadataMustFitInAVanillaNibble() {
        Block stone = blocks.get("minecraft:stone");
        assertThrows(IllegalArgumentException.class, () -> BlockState.of(stone, 16));
        assertThrows(IllegalArgumentException.class, () -> BlockState.of(stone, -1));
        assertTrue(BlockState.of(stone, BlockState.MAX_METADATA).metadata() == 15);
    }
}

package net.minecraft.world;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.util.StateHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Milestone 0 exit gate (master context §94): "deterministic empty-world
 * simulation reproduces itself bit-for-bit."
 *
 * <p>This is a self-consistency / golden-hash test, not a vanilla fidelity
 * test — it proves two independently constructed simulations agree with
 * each other, not that either agrees with real Minecraft 1.8.9. See
 * {@code docs/compat/unknowns.yml} for the vanilla-comparison work still
 * needed before that claim can be made (worldgen algorithm, legacy block
 * IDs, NBT/Anvil format).
 */
class WorldGenerationGoldenTest {
    private final BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());
    private final BiomeRegistry biomes = BiomeRegistry.load(new ClasspathResourceManager(), blocks);
    private static final int CHUNK_RADIUS = 3;

    @Test
    void sameSeedReproducesIdenticalRegionStateAcrossIndependentWorlds() {
        World first = new World(blocks, biomes, 20260915L);
        World second = new World(blocks, biomes, 20260915L);

        long firstHash = StateHash.regionHash(first, CHUNK_RADIUS);
        long secondHash = StateHash.regionHash(second, CHUNK_RADIUS);

        assertEquals(firstHash, secondHash,
            "Two independently generated worlds with the same seed must produce bit-for-bit "
                + "identical block state for the same region.");
    }

    @Test
    void populatedRegionReproducesItselfForTheSameRequestOrder() {
        World first = new World(blocks, biomes, 20260915L);
        World second = new World(blocks, biomes, 20260915L);
        for (int cz = -CHUNK_RADIUS - 1; cz <= CHUNK_RADIUS + 1; cz++) {
            for (int cx = -CHUNK_RADIUS - 1; cx <= CHUNK_RADIUS + 1; cx++) {
                first.getChunk(cx, cz);
                second.getChunk(cx, cz);
            }
        }
        assertEquals(StateHash.regionHash(first, CHUNK_RADIUS), StateHash.regionHash(second, CHUNK_RADIUS),
            "Population (lakes, ores, trees...) must be deterministic for a given seed and request order.");
    }

    /**
     * The terrain stage (shape, surface, caves, ravines) depends only on the
     * seed and chunk position. Population is left out: in vanilla 1.8.9 it is
     * order-dependent (neighbouring chunks' features compete for the same
     * space, and a chunk is populated when its neighbours happen to exist).
     */
    @Test
    void requestingChunksInADifferentOrderDoesNotChangeTheResultingState() {
        World inOrder = new World(blocks, biomes, 20260915L);
        World reversed = new World(blocks, biomes, 20260915L);
        inOrder.disablePopulation();
        reversed.disablePopulation();

        // Force generation in the opposite (cz outer, cx inner, descending) order
        // before hashing, to catch any accidental cross-chunk state leak (see
        // master context §13.4 "population-order tests").
        for (int cz = CHUNK_RADIUS; cz >= -CHUNK_RADIUS; cz--) {
            for (int cx = CHUNK_RADIUS; cx >= -CHUNK_RADIUS; cx--) {
                reversed.getChunk(cx, cz);
            }
        }

        long inOrderHash = StateHash.regionHash(inOrder, CHUNK_RADIUS);
        long reversedHash = StateHash.regionHash(reversed, CHUNK_RADIUS);

        assertEquals(inOrderHash, reversedHash,
            "Chunk generation order must not be observable in the resulting block state.");
    }

    @Test
    void differentSeedsProduceDifferentRegionState() {
        World first = new World(blocks, biomes, 20260915L);
        World second = new World(blocks, biomes, 20260916L);

        assertNotEquals(StateHash.regionHash(first, CHUNK_RADIUS), StateHash.regionHash(second, CHUNK_RADIUS),
            "Sanity check: the hash must actually be sensitive to world state, "
                + "not a constant that would make the equality assertions above vacuous.");
    }
}

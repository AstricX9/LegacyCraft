package net.minecraft.client.renderer;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mesher's copy of the world must read exactly like the world across the section and its border. */
class SectionSnapshotTest {
    private final BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());
    private final BiomeRegistry biomes = BiomeRegistry.load(new ClasspathResourceManager(), blocks);

    @Test
    void snapshotMatchesTheWorldInsideItsBorder() {
        World world = new World(blocks, biomes, 20260927L);
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) world.getChunk(cx, cz);
        world.setState(15, 70, 0, BlockState.of(blocks.get("minecraft:glowstone"), 0));
        for (int sectionY : new int[]{0, 4, 15}) {
            SectionSnapshot snapshot = new SectionSnapshot(world, 0, sectionY, 0);
            int min = -SectionSnapshot.MARGIN, max = 16 + SectionSnapshot.MARGIN;
            for (int x = min; x < max; x++) {
                for (int z = min; z < max; z++) {
                    assertEquals(world.biomeAt(x, z), snapshot.biomeAt(x, z));
                    for (int y = sectionY * 16 + min; y < sectionY * 16 + max; y++) {
                        String where = x + "," + y + "," + z;
                        assertEquals(world.getState(x, y, z), snapshot.getState(x, y, z), where);
                        if (y >= 0 && y < 256) {
                            assertEquals(world.getSkyLight(x, y, z), snapshot.skyLight(x, y, z), "sky " + where);
                            assertEquals(world.getBlockLight(x, y, z), snapshot.blockLight(x, y, z), "block " + where);
                        }
                    }
                }
            }
        }
        assertTrue(new SectionSnapshot(world, 0, 4, 0).blockLight(15, 70, 0) > 0, "the glowstone lights its cell");
    }

    @Test
    void translucentQuadsSortFarthestFirst() {
        SectionMesher.Buffer buffer = new SectionMesher.Buffer();
        for (int quad = 0; quad < 3; quad++) {
            buffer.ensure(SectionMesher.INTS_PER_QUAD);
            for (int vertex = 0; vertex < 4; vertex++) {
                int at = buffer.size + vertex * SectionMesher.INTS_PER_VERTEX;
                buffer.data[at] = Float.floatToRawIntBits(quad * 5f);
                buffer.data[at + 3] = quad;
            }
            buffer.size += SectionMesher.INTS_PER_QUAD;
        }
        SectionMesher.sortBackToFront(buffer, 0, 0, 0);
        assertEquals(2, buffer.data[3]);
        assertEquals(1, buffer.data[SectionMesher.INTS_PER_QUAD + 3]);
        assertEquals(0, buffer.data[2 * SectionMesher.INTS_PER_QUAD + 3]);
    }
}

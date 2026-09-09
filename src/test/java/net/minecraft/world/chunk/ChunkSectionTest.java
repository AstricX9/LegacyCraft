package net.minecraft.world.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkSectionTest {
    @Test
    void storesUnsignedRegistryIdsAndTracksOccupancy() {
        ChunkSection section = new ChunkSection();
        assertTrue(section.isEmpty());
        section.set(15, 15, 15, 400);
        assertEquals(400, section.get(15,15,15));
        assertEquals(1, section.nonAirCount());
        section.set(15,15,15,0);
        assertTrue(section.isEmpty());
    }
}

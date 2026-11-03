package net.minecraft.client.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SectionMesherOffsetTest {
    @Test
    void plantsShiftByPositionAndOthersStay() {
        assertArrayEquals(new float[3], SectionMesher.offset("stone", 5, 9));
        float[] grass = SectionMesher.offset("tallgrass", 5, 9);
        assertTrue(Math.abs(grass[0]) <= .25f && Math.abs(grass[2]) <= .25f && grass[1] <= 0 && grass[1] >= -.2f);
        assertArrayEquals(grass, SectionMesher.offset("tallgrass", 5, 9));
        assertEquals(0f, SectionMesher.offset("red_flower", 5, 9)[1]);
        boolean varies = false;
        for (int x = 0; x < 8; x++) varies |= SectionMesher.offset("tallgrass", x, 0)[0] != grass[0];
        assertTrue(varies);
    }
}

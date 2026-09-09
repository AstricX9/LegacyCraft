package net.minecraft.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScaledResolutionTest {
    @Test
    void automaticScaleHonorsMinimumLogicalResolution() {
        ScaledResolution resolution = new ScaledResolution(1280, 720, 0, false);
        assertEquals(3, resolution.getScaleFactor());
        assertEquals(427, resolution.getScaledWidth());
        assertEquals(240, resolution.getScaledHeight());
    }

    @Test
    void configuredScaleIsRespectedWhenItFits() {
        ScaledResolution resolution = new ScaledResolution(1280, 720, 2, false);
        assertEquals(2, resolution.getScaleFactor());
        assertEquals(640, resolution.getScaledWidth());
        assertEquals(360, resolution.getScaledHeight());
    }

    @Test
    void unicodeModeUsesAnEvenScale() {
        ScaledResolution resolution = new ScaledResolution(1280, 720, 0, true);
        assertEquals(2, resolution.getScaleFactor());
    }
}

package net.minecraft.client.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameSettingsTest {
    @Test
    void renderDistanceCyclesAcrossLegacyPresets() {
        GameSettings settings = new GameSettings();
        assertEquals(48, settings.getRenderDistanceBlocks());
        settings.cycleRenderDistance();
        assertEquals(64, settings.getRenderDistanceBlocks());
        settings.cycleRenderDistance();
        assertEquals(32, settings.getRenderDistanceBlocks());
    }

    @Test
    void clampsFovAndGuiScale() {
        GameSettings settings = new GameSettings();
        settings.setFov(500);
        settings.setGuiScale(99);
        assertEquals(110f, settings.getFov());
        assertEquals(4, settings.getGuiScale());
    }
}

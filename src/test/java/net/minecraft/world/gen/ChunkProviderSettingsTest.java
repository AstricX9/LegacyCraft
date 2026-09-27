package net.minecraft.world.gen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkProviderSettingsTest {
    @Test
    void defaultsMatchTheDefaultGenerator() {
        ChunkProviderSettings settings = new ChunkProviderSettings();
        assertEquals(63, settings.get("seaLevel"));
        assertEquals(684.412f, settings.get("coordinateScale"));
        assertTrue(settings.getBoolean("useCaves"));
        assertFalse(settings.getBoolean("useLavaOceans"));
        assertEquals(-1, settings.get("fixedBiome"));
        assertEquals(16, settings.get("lapisSpread"));
    }

    @Test
    void jsonRoundTripClampsAndIgnoresJunk() {
        ChunkProviderSettings settings = ChunkProviderSettings.fromJson(
            "{\"seaLevel\":80,\"useCaves\":false,\"dungeonChance\":500,\"stretchY\":3.25,\"bogus\":1}");
        assertEquals(80, settings.get("seaLevel"));
        assertFalse(settings.getBoolean("useCaves"));
        assertEquals(100, settings.get("dungeonChance"), "clamped to the slider range");
        String json = settings.toJson();
        assertTrue(json.startsWith("{\"seaLevel\":80,\"useCaves\":false,"));
        assertTrue(json.contains("\"stretchY\":3.25"));
        assertEquals(json, ChunkProviderSettings.fromJson(json).toJson());
        assertEquals(new ChunkProviderSettings().toJson(), ChunkProviderSettings.fromJson("not json").toJson());
    }
}

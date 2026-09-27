package net.minecraft.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldSettingsTest {
    @Test
    void seedTextFollowsTheCreateWorldRule() {
        assertEquals(99L, WorldSettings.parseSeed("", 99L));
        assertEquals(-123456789L, WorldSettings.parseSeed("-123456789", 99L));
        assertEquals(99L, WorldSettings.parseSeed("0", 99L), "zero means random");
        assertEquals("gargamel".hashCode(), WorldSettings.parseSeed("gargamel", 99L));
        assertEquals("9223372036854775808".hashCode(), WorldSettings.parseSeed("9223372036854775808", 99L), "overflow hashes");
    }

    @Test
    void worldTypeButtonCyclesCreatableTypesAndHidesDebugWithoutShift() {
        assertEquals(WorldType.FLAT, WorldType.DEFAULT.next(false));
        assertEquals(WorldType.DEFAULT, WorldType.CUSTOMIZED.next(false));
        assertEquals(WorldType.DEBUG_WORLD, WorldType.CUSTOMIZED.next(true));
        assertEquals(WorldType.DEFAULT, WorldType.DEBUG_WORLD.next(true), "default_1_1 can't be created");
        assertEquals(WorldType.LARGE_BIOMES, WorldType.parse("LARGEBIOMES"));
        assertEquals("generator.largeBiomes", WorldType.LARGE_BIOMES.getTranslateName());
        assertNull(WorldType.parse("nonsense"));
    }

    @Test
    void gameTypesUseLevelDatIds() {
        assertEquals(WorldSettings.GameType.SPECTATOR, WorldSettings.GameType.getByID(3));
        assertEquals(WorldSettings.GameType.SURVIVAL, WorldSettings.GameType.getByName("hardcore"),
            "hardcore is survival plus the hardcore flag");
        assertEquals("gameMode.creative", WorldSettings.GameType.CREATIVE.translationKey());
    }
}

package net.minecraft.client.renderer;

import net.minecraft.client.additions.Additions;
import net.minecraft.world.Dimension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** HYPOTHESIS console profile: bounded fog, atmosphere continuity, and persisted controls. */
final class LegacyConsoleGraphicsTest {
    @Test
    void theGraphicsEntryStartsOffAndItsControlsSurviveReload(@TempDir Path dir) {
        Additions additions = Additions.load(dir.resolve("additions.txt"));
        String id = Additions.LEGACY_CONSOLE_GRAPHICS;
        assertFalse(LegacyConsoleGraphics.profile(additions).enabled());
        assertTrue(additions.game(net.minecraft.client.additions.Addition.Category.GRAPHICS).contains(additions.find(id)));
        additions.setEnabled(true);
        additions.set(id, true);
        additions.setSetting(id, "fogStart", "55");
        additions.setSetting(id, "fogEnd", "80");
        additions.setSetting(id, "waterVisibility", "150");
        additions.save();
        LegacyConsoleGraphics.Profile profile = LegacyConsoleGraphics.profile(Additions.load(dir.resolve("additions.txt")));
        assertTrue(profile.enabled());
        assertEquals(.55f, profile.fogStart(), 1e-6f);
        assertEquals(.8f, profile.fogEnd(), 1e-6f);
        assertEquals(1.5f, profile.waterVisibility(), 1e-6f);
        additions.setEnabled(false);
        assertFalse(LegacyConsoleGraphics.profile(additions).enabled());
        additions.resetSettings(id);
        additions.setEnabled(true);
        assertEquals(defaultProfile(), LegacyConsoleGraphics.profile(additions));
    }

    @Test
    void evenCrossedFogSlidersKeepTheFadeInsideLoadedTerrain() {
        for (int chunks : new int[]{2, 4, 8, 16, 32}) {
            for (float start : new float[]{0f, .35f, .85f}) {
                for (float end : new float[]{.25f, .9f, 1f}) {
                    var profile = new LegacyConsoleGraphics.Profile(true, start, end, .25f, .35f, .2f, 1f);
                    var fog = LegacyConsoleGraphics.fog(profile, chunks * 16, Dimension.OVERWORLD, null, 0f);
                    assertTrue(fog.start() >= 0f);
                    assertTrue(fog.end() - fog.start() >= 8f);
                    assertTrue(fog.end() <= chunks * 16 - 16f, "fog conceals the final chunk boundary");
                    assertEquals(0f, fog.density());
                }
            }
        }
    }

    @Test
    void eyePlaneFogConcealsTheChunkBoundaryAtWideFieldsOfView() {
        for (float fov : new float[]{30f, 70f, 110f}) {
            for (float aspect : new float[]{1f, 16f / 9f, 32f / 9f}) {
                float distance = LegacyConsoleGraphics.fogViewDistance(256f, fov, aspect, false);
                var fog = LegacyConsoleGraphics.fog(defaultProfile(), distance, Dimension.OVERWORLD, null, 0f);
                double edgeScale = Math.sqrt(1 + Math.pow(Math.tan(Math.toRadians(fov) / 2) * aspect, 2));
                assertTrue(fog.end() * edgeScale < 256f, "screen sides fade before the circular terrain edge");
                assertTrue(fog.start() < fog.end());
                assertEquals(256f, LegacyConsoleGraphics.fogViewDistance(256f, fov, aspect, true), 0f);
            }
        }
    }

    @Test
    void waterRemainsFoggyAndLavaAndBlindnessTakePriority() {
        var profile = defaultProfile();
        float water = LegacyConsoleGraphics.fog(profile, 256, Dimension.OVERWORLD, "water", 0f).density();
        var clearer = new LegacyConsoleGraphics.Profile(true, .35f, .9f, .25f, .35f, .2f, 1.5f);
        assertTrue(water > 0f);
        assertTrue(LegacyConsoleGraphics.fog(clearer, 256, Dimension.OVERWORLD, "water", 0f).density() < water);
        assertEquals(2f, LegacyConsoleGraphics.fog(clearer, 256, Dimension.NETHER, "lava", 0f).density());
        var blind = LegacyConsoleGraphics.fog(profile, 256, Dimension.OVERWORLD, "water", 1f);
        assertEquals(5f, blind.end());
        assertEquals(0f, blind.density());
        var nether = LegacyConsoleGraphics.fog(profile, 512, Dimension.NETHER, null, 0f);
        assertTrue(nether.end() <= 96f);
    }

    @Test
    void theHorizonExactlyMatchesFogAndBlendsContinuouslyToTheSky() {
        float[] sky = {.4f, .6f, .9f}, fog = {.7f, .75f, .8f};
        assertArrayEquals(fog, LegacyConsoleGraphics.horizon(sky, fog, 0f), 0f);
        assertArrayEquals(fog, LegacyConsoleGraphics.horizon(sky, fog, -1f), 0f);
        assertArrayEquals(sky, LegacyConsoleGraphics.horizon(sky, fog, 1f), 1e-6f);
        assertArrayEquals(fog, LegacyConsoleGraphics.horizon(sky, fog, .0001f), 1e-6f);
        float[] middle = LegacyConsoleGraphics.horizon(sky, fog, .225f);
        for (int c = 0; c < 3; c++) assertEquals((sky[c] + fog[c]) * .5f, middle[c], 1e-6f);
    }

    @Test
    void rainAndThunderDarkenWithoutAffectingLiquidPalettes() {
        var profile = defaultProfile();
        SkyRenderer clear = weather(false, false), rainy = weather(true, false), storm = weather(true, true);
        float[] day = prepare(clear, 6000, null, profile);
        float[] rain = prepare(rainy, 6000, null, profile);
        float[] thunder = prepare(storm, 6000, null, profile);
        for (int c = 0; c < 3; c++) {
            assertTrue(rain[c] < day[c]);
            assertTrue(thunder[c] < rain[c]);
        }
        assertTrue(storm.lightmapSunBrightness() < rainy.lightmapSunBrightness());
        for (String liquid : new String[]{"water", "lava"})
            assertArrayEquals(prepare(clear, 6000, liquid, profile), prepare(storm, 6000, liquid, profile), 0f);
        float[] blind = clear.prepare(6000, 1f, .8f, 0, 0, 1, 12, 70, false, null, profile, 1f);
        assertArrayEquals(new float[3], blind, 0f);
        assertArrayEquals(new float[3], clear.skyColor(), 0f);
    }

    @Test
    void weatherChangesGraduallyAndOffRestoresTheOriginalSky() {
        SkyRenderer sky = weather(false, false);
        float[] before = prepare(sky, 6000, null, defaultProfile());
        sky.tick(6000, 15, 0, 12, true, true);
        float[] next = prepare(sky, 6000, null, defaultProfile());
        for (int c = 0; c < 3; c++) assertTrue(Math.abs(before[c] - next[c]) < .02f);
        for (int i = 0; i < 110; i++) sky.tick(6000, 15, 0, 12, true, true);
        assertTrue(prepare(sky, 6000, null, defaultProfile())[2] < next[2] * .7f);
        assertArrayEquals(prepare(weather(false, false), 6000, null, LegacyConsoleGraphics.OFF),
            prepare(sky, 6000, null, LegacyConsoleGraphics.OFF), 0f);
    }

    @Test
    void timeOfDayFogIsContinuousAcrossDawnDuskAndTheDayBoundary() {
        SkyRenderer sky = weather(false, false);
        var profile = defaultProfile();
        for (long tick = 0; tick <= 24000; tick += 20) {
            float[] color = prepare(sky, tick, null, profile);
            float[] next = prepare(sky, tick + 1, null, profile);
            for (int c = 0; c < 3; c++) {
                assertTrue(Float.isFinite(color[c]) && color[c] >= 0f && color[c] <= 1f);
                assertTrue(Math.abs(color[c] - next[c]) < .01f, "no time-of-day colour jumps");
            }
        }
        assertArrayEquals(prepare(sky, 0, null, defaultProfile()), prepare(sky, 24000, null, defaultProfile()), 0f);
        assertTrue(prepare(sky, 18000, null, defaultProfile())[2] < prepare(sky, 6000, null, defaultProfile())[2] * .2f);
    }

    static LegacyConsoleGraphics.Profile defaultProfile() {
        Additions additions = Additions.defaults();
        additions.setEnabled(true);
        additions.set(Additions.LEGACY_CONSOLE_GRAPHICS, true);
        return LegacyConsoleGraphics.profile(additions);
    }

    private static SkyRenderer weather(boolean rain, boolean thunder) {
        SkyRenderer sky = new SkyRenderer(null);
        sky.tick(6000, 15, 0, 12, rain, thunder);
        return sky;
    }

    private static float[] prepare(SkyRenderer sky, long time, String liquid, LegacyConsoleGraphics.Profile profile) {
        return sky.prepare(time, 1f, .8f, 0, 0, 1, 12, 70, false, liquid, profile);
    }
}

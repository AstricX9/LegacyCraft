package net.minecraft.client.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The time-of-day maths behind the sky (HYPOTHESIS formulas; these pin their
 * shape, not vanilla's pixels).
 */
final class SkyRendererTest {
    @Test
    void noonMidnightAndTheEasedDay() {
        assertEquals(0f, SkyRenderer.celestialAngle(6000, 0f), 1e-6f);
        assertEquals(0.5f, SkyRenderer.celestialAngle(18000, 0f), 1e-6f);
        assertEquals(0.5f, SkyRenderer.celestialAngle(18000 + 24000 * 5, 0f), 1e-6f);
        // Time 0 (the morning a new world starts in) is past sunrise: the sun is already up.
        float morning = SkyRenderer.celestialAngle(0, 0f);
        assertTrue(morning > 0.75f && morning < 0.8f, "angle " + morning);
    }

    @Test
    void sunBrightnessAndDarknessFollowTheDay() {
        assertEquals(1f, SkyRenderer.sunBrightness(SkyRenderer.celestialAngle(6000, 0f)), 1e-6f);
        assertEquals(0.2f, SkyRenderer.sunBrightness(SkyRenderer.celestialAngle(18000, 0f)), 1e-6f);
        assertEquals(0, SkyRenderer.skylightSubtracted(0f));
        assertEquals(11, SkyRenderer.skylightSubtracted(0.5f));
        assertEquals(0f, SkyRenderer.starBrightness(0f), 0f);
        assertEquals(0.5f, SkyRenderer.starBrightness(0.5f), 1e-6f);
    }

    @Test
    void theGlowOnlyShowsAroundSunriseAndSunset() {
        assertNull(SkyRenderer.sunriseColor(0f));
        assertNull(SkyRenderer.sunriseColor(0.5f));
        assertNotNull(SkyRenderer.sunriseColor(0.25f));
        assertNotNull(SkyRenderer.sunriseColor(0.75f));
    }

    @Test
    void plainsNoonSkyIsTheFamiliarBlue() {
        float[] sky = SkyRenderer.skyColor(0f, 0.8f);
        // The commonly quoted plains sky is #78A7FF; the recalled formula gives #79A7FF (UNK: which is exact).
        assertEquals(0x78, Math.round(sky[0] * 255), 1);
        assertEquals(0xA7, Math.round(sky[1] * 255));
        assertEquals(0xFF, Math.round(sky[2] * 255));
        float[] night = SkyRenderer.skyColor(0.5f, 0.8f);
        assertEquals(0f, night[0] + night[1] + night[2], 0f);
    }

    @Test
    void moonPhasesCycleEveryEightDays() {
        assertEquals(0, SkyRenderer.moonPhase(0));
        assertEquals(1, SkyRenderer.moonPhase(24000));
        assertEquals(0, SkyRenderer.moonPhase(24000 * 8));
    }

    @Test
    void starsSitOnASphereOfRadius100() {
        float[] stars = SkyRenderer.starVertices();
        assertEquals(0, stars.length % 12);
        assertTrue(stars.length / 12 > 500, "stars " + stars.length / 12);
        for (int i = 0; i < stars.length; i += 3) {
            double r = Math.sqrt(stars[i] * stars[i] + stars[i + 1] * stars[i + 1] + stars[i + 2] * stars[i + 2]);
            assertEquals(100.0, r, 0.3);
        }
    }

    @Test
    void fogFadesToBlackTowardTheVoid() {
        float[] sky = SkyRenderer.skyColor(0f, 0.8f);
        float[] high = SkyRenderer.fogColor(0f, sky, 0, 0, 1, 12, 1f, 70, false);
        float[] low = SkyRenderer.fogColor(0f, sky, 0, 0, 1, 12, 1f, 8, false);
        assertTrue(low[2] < high[2] * 0.1f);
        assertTrue(high[2] > 0.9f);
    }

    @Test
    void liquidsReplaceTheFogColour() {
        float[] sky = SkyRenderer.skyColor(0f, 0.8f);
        assertArrayEquals(new float[]{0.02f, 0.02f, 0.2f}, SkyRenderer.fogColor(0f, sky, 0, 0, 1, 12, 1f, 70, false, "water"), 1e-6f);
        // The camera's darkness still applies: half light halves the red lava fog.
        assertArrayEquals(new float[]{0.3f, 0.05f, 0f}, SkyRenderer.fogColor(0f, sky, 0, 0, 1, 12, 0.5f, 70, false, "lava"), 1e-6f);
    }
}

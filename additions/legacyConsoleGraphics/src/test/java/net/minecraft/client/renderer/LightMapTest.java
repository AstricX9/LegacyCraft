package net.minecraft.client.renderer;

import net.minecraft.world.Dimension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** HYPOTHESIS console lightmap: live invalidation, vanilla restoration, and usable dark/light ranges. */
final class LightMapTest {
    @AfterEach
    void restoreVanilla() { LightMap.update(1f, 0f, 0f, Dimension.OVERWORLD); }

    @Test
    void togglingTheProfileRestoresEveryVanillaTexelExactly() {
        for (Dimension dimension : Dimension.values()) {
            LightMap.update(.6f, .4f, 0f, dimension);
            float[][] original = snapshot();
            int oldVersion = LightMap.version();
            var profile = LegacyConsoleGraphicsTest.defaultProfile();
            LightMap.update(.6f, .4f, 0f, dimension, profile);
            assertTrue(LightMap.version() > oldVersion);
            assertNotEquals(original[8 * 16 + 4][2], LightMap.texel(8, 4)[2]);
            int consoleVersion = LightMap.version();
            LightMap.update(.6f, .4f, 0f, dimension, profile);
            assertEquals(consoleVersion, LightMap.version(), "unchanged tables do not need GPU uploads");
            LightMap.update(.6f, .4f, 0f, dimension);
            assertTrue(LightMap.version() > consoleVersion);
            float[][] restored = snapshot();
            for (int i = 0; i < original.length; i++) assertArrayEquals(original[i], restored[i], 0f);
        }
    }

    @Test
    void colourControlsLeaveTheNativeLightmapStable() {
        var profile = LegacyConsoleGraphicsTest.defaultProfile();
        LightMap.update(1f, 0f, 0f, Dimension.OVERWORLD, profile);
        float[] before = LightMap.texel(10, 0);
        int version = LightMap.version();
        var brighter = new LegacyConsoleGraphics.Profile(true, profile.fogStart(), profile.fogEnd(), 1f,
            profile.warmth(), profile.contrast(), profile.waterVisibility());
        LightMap.update(1f, 0f, 0f, Dimension.OVERWORLD, brighter);
        assertTrue(LightMap.version() > version);
        assertArrayEquals(before, LightMap.texel(10, 0), 0f, "gamma acts on actual scene colours after texture and face shading");
    }

    @Test
    void cavesStayDarkAndTorchLightAndNightVisionRemainUseful() {
        var profile = LegacyConsoleGraphicsTest.defaultProfile();
        for (float sun : new float[]{.2f, .6f, 1f}) {
            for (float gamma : new float[]{0f, .5f, 1f}) {
                LightMap.update(sun, gamma, 0f, Dimension.OVERWORLD, profile);
                assertTrue(LightMap.texel(0, 0)[0] < .2f, "console gamma does not create full bright");
                for (int sky = 0; sky <= 15; sky++) {
                    for (int block = 0; block <= 15; block++) {
                        float[] color = LightMap.texel(sky, block);
                        for (int c = 0; c < 3; c++) {
                            assertTrue(Float.isFinite(color[c]) && color[c] >= 0f && color[c] <= 1f);
                            if (block < 15) assertTrue(LightMap.texel(sky, block + 1)[c] >= color[c] - 1e-6f);
                        }
                    }
                }
                float[] torch = LightMap.texel(0, 10);
                assertTrue(torch[0] > torch[2], "torchlight keeps its warm spectrum");
                LightMap.update(sun, gamma, 1f, Dimension.OVERWORLD, profile);
                assertTrue(LightMap.texel(0, 0)[0] > .9f, "Night Vision and Full Bright still illuminate caves");
            }
        }
    }

    @Test
    void daylightIsSubtlyWarmerAndNightKeepsItsCoolTint() {
        var profile = LegacyConsoleGraphicsTest.defaultProfile();
        LightMap.update(1f, 0f, 0f, Dimension.OVERWORLD, profile);
        float[] day = LightMap.texel(12, 0);
        assertTrue(day[0] > day[2]);
        assertTrue(day[0] - day[2] < .04f);
        LightMap.update(.2f, 0f, 0f, Dimension.OVERWORLD, profile);
        float[] night = LightMap.texel(15, 0);
        assertTrue(night[2] > night[0]);
    }

    private static float[][] snapshot() {
        float[][] colors = new float[256][];
        for (int sky = 0; sky <= 15; sky++)
            for (int block = 0; block <= 15; block++) colors[sky * 16 + block] = LightMap.texel(sky, block);
        return colors;
    }
}

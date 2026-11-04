package net.minecraft.client.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Console controls must visibly affect texture colours even under a fully bright lightmap. */
final class ConsoleColorGradingTest {
    @Test
    void gammaChangesSunlitTextureMidtonesEvenWhenLightingIsAlreadyWhite() {
        var low = profile(0f, 0f);
        var high = profile(1f, 0f);
        // A fully sunlit texel still contains texture detail and directional face shade.
        float texturedBlock = .3f * .99f * .8f;
        assertTrue(LegacyConsoleGraphics.gradeChannel(texturedBlock, high)
            - LegacyConsoleGraphics.gradeChannel(texturedBlock, low) > .15f);
        // Unlike the old implementation, normal Brightness cannot saturate this control away.
        assertTrue(LegacyConsoleGraphics.gradeChannel(.3f, high) - .3f > .15f);
    }

    @Test
    void contrastWidensTheDifferenceBetweenShadowAndHighlightTextures() {
        var low = profile(0f, 0f);
        var high = profile(0f, 1f);
        float dark = LegacyConsoleGraphics.gradeChannel(.25f, high);
        float light = LegacyConsoleGraphics.gradeChannel(.75f, high);
        assertTrue(dark < .19f, "shadows visibly darken");
        assertTrue(light > .81f, "highlights visibly brighten");
        assertTrue(light - dark > LegacyConsoleGraphics.gradeChannel(.75f, low)
            - LegacyConsoleGraphics.gradeChannel(.25f, low) + .12f);
    }

    @Test
    void transferKeepsEndpointsAndColourOrderAndOffIsAnExactBypass() {
        for (float gamma : new float[]{0f, .25f, 1f}) {
            for (float contrast : new float[]{0f, .2f, 1f}) {
                var profile = profile(gamma, contrast);
                assertEquals(0f, LegacyConsoleGraphics.gradeChannel(0f, profile), 0f);
                assertEquals(1f, LegacyConsoleGraphics.gradeChannel(1f, profile), 0f);
                float previous = 0f;
                for (int i = 0; i <= 255; i++) {
                    float value = i / 255f;
                    float graded = LegacyConsoleGraphics.gradeChannel(value, profile);
                    assertTrue(Float.isFinite(graded) && graded >= previous && graded <= 1f);
                    assertEquals(value, LegacyConsoleGraphics.gradeChannel(value, LegacyConsoleGraphics.OFF), 0f);
                    previous = graded;
                }
            }
        }
    }

    private static LegacyConsoleGraphics.Profile profile(float gamma, float contrast) {
        return new LegacyConsoleGraphics.Profile(true, .35f, .9f, gamma, .35f, contrast, 1f);
    }
}

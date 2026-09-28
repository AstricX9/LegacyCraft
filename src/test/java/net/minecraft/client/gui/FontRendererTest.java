package net.minecraft.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Font rendering text handling: stripping formatting codes and carrying colour
 * and style across wrapped lines.
 */
final class FontRendererTest {
    @Test
    void stripsLegacyFormattingCodes() {
        assertEquals("Gold Bold", FontRenderer.stripFormattingCodes("§6Gold §lBold§r"));
    }

    @Test
    void carriesActiveColorAndStyleAcrossWrappedLines() {
        assertEquals("§a§l", FontRenderer.getFormatFromString("plain §aGreen §lBold"));
        assertEquals("", FontRenderer.getFormatFromString("§cRed§r reset"));
    }
}

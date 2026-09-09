package net.minecraft.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FontRendererTest {
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

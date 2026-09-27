package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * What a 1.8.9 client without a working Twitch SDK shows for "Broadcast
 * Settings...": "Twitch Broadcasting Unavailable" and the initialization
 * failure reason. LegacyCraft never ships the SDK, so this is always the
 * outcome — which is also what vanilla does today (DEV-010).
 */
public class GuiStreamUnavailable extends GuiScreen {
    private final GuiScreen parent;
    private final List<String> lines = new ArrayList<>();

    public GuiStreamUnavailable(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        lines.clear();
        lines.addAll(fontRendererObj.listFormattedStringToWidth(I18n.format("stream.unavailable.initialization_failure"), width - 50));
        lines.addAll(fontRendererObj.listFormattedStringToWidth(
            I18n.format("stream.unavailable.initialization_failure.extra", "Twitch SDK not bundled"), width - 50));
        buttonList.add(new GuiButton(1, width / 2 - 100, Math.min(height / 2 + lines.size() * 9 / 2 + 32, height - 30), I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) { mc.displayGuiScreen(parent); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int y = Math.max(height / 2 - lines.size() * 9 / 2 - 20, 20);
        drawCenteredString(fontRendererObj, I18n.format("stream.unavailable.title"), width / 2, y - 20, 0xFFFFFF);
        for (String line : lines) {
            drawCenteredString(fontRendererObj, line, width / 2, y, 0xA0A0A0);
            y += FontRenderer.FONT_HEIGHT;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

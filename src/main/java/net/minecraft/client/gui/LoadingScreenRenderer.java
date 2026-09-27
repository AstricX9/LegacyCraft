package net.minecraft.client.gui;

/**
 * The dirt "Loading world / Building terrain" and "Saving world" screens
 * (master context §78): a title, a message under it and, when progress is
 * known, a 100px progress bar (grey track, green fill) below the text.
 * Drawn directly by the client while it works, not as an interactive screen.
 */
public final class LoadingScreenRenderer extends GuiScreen {
    private String title = "";
    private String message = "";
    private int progress = -1;

    public void setTitle(String title) { this.title = title; }
    public void setMessage(String message) { this.message = message; }
    /** 0-100, or -1 to hide the bar. */
    public void setProgress(int progress) { this.progress = progress; }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void keyPressed(int key, int modifiers) {}

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackground(0);
        if (progress >= 0) {
            int barLeft = width / 2 - 50, barTop = height / 2 + 16;
            drawRect(barLeft, barTop, barLeft + 100, barTop + 2, 0xFF808080);
            drawRect(barLeft, barTop, barLeft + progress, barTop + 2, 0xFF80FF80);
        }
        fontRendererObj.drawStringWithShadow(title, (width - fontRendererObj.getStringWidth(title)) / 2f, height / 2 - 4 - 16, 0xFFFFFF);
        fontRendererObj.drawStringWithShadow(message, (width - fontRendererObj.getStringWidth(message)) / 2f, height / 2 - 4 + 8, 0xFFFFFF);
    }
}

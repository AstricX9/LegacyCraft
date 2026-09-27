package net.minecraft.client.gui;

import java.util.List;

/**
 * Two-button confirmation (delete world, delete server, quit from the death
 * screen...). The answer goes back to the parent's
 * {@link GuiScreen#confirmClicked(boolean, int)}; the parent decides which
 * screen to show next. Layout: first line at y=70, wrapped second line from
 * y=90, buttons at height/6+96 (HYPOTHESIS until screenshot-compared).
 */
public class GuiYesNo extends GuiScreen {
    protected final GuiScreen parent;
    protected final String messageLine1;
    private final String messageLine2;
    protected final String confirmButtonText, cancelButtonText;
    protected final int parentButtonClickedId;
    private List<String> wrappedLine2 = List.of();
    private int ticksUntilEnable;

    public GuiYesNo(GuiScreen parent, String line1, String line2, int id) {
        this(parent, line1, line2, I18n.format("gui.yes"), I18n.format("gui.no"), id);
    }

    public GuiYesNo(GuiScreen parent, String line1, String line2, String confirm, String cancel, int id) {
        this.parent = parent;
        this.messageLine1 = line1;
        this.messageLine2 = line2;
        this.confirmButtonText = confirm;
        this.cancelButtonText = cancel;
        this.parentButtonClickedId = id;
    }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(0, width / 2 - 155, height / 6 + 96, 150, 20, confirmButtonText));
        buttonList.add(new GuiButton(1, width / 2 - 155 + 160, height / 6 + 96, 150, 20, cancelButtonText));
        wrappedLine2 = fontRendererObj.listFormattedStringToWidth(messageLine2, width - 50);
        for (GuiButton button : buttonList) button.enabled = ticksUntilEnable <= 0;
    }

    @Override
    protected void actionPerformed(GuiButton button) { parent.confirmClicked(button.id == 0, parentButtonClickedId); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, messageLine1, width / 2, 70, 0xFFFFFF);
        int y = 90;
        for (String line : wrappedLine2) {
            drawCenteredString(fontRendererObj, line, width / 2, y, 0xFFFFFF);
            y += FontRenderer.FONT_HEIGHT;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Keeps the buttons disabled for a number of ticks (vanilla uses this on the death-screen quit prompt). */
    public void setButtonDelay(int ticks) {
        ticksUntilEnable = ticks;
        for (GuiButton button : buttonList) button.enabled = false;
    }

    @Override
    public void updateScreen() {
        if (--ticksUntilEnable == 0) for (GuiButton button : buttonList) button.enabled = true;
    }
}

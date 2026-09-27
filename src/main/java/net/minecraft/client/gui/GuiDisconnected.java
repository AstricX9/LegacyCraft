package net.minecraft.client.gui;

import java.util.List;

/**
 * Connection failure / kick screen: grey title, wrapped white reason, and a
 * "Back to server list" button under the text (HYPOTHESIS layout). Also used
 * for LegacyCraft's own "not implemented yet" notices so no menu path ends in
 * silence.
 */
public class GuiDisconnected extends GuiScreen {
    private final GuiScreen parent;
    private final String title;
    private final String message;
    private final String buttonText;
    private List<String> lines = List.of();
    private int textHeight;

    public GuiDisconnected(GuiScreen parent, String titleKey, String message) {
        this(parent, titleKey, message, "gui.toMenu");
    }

    public GuiDisconnected(GuiScreen parent, String titleKey, String message, String buttonKey) {
        this.parent = parent;
        this.title = I18n.format(titleKey);
        this.message = message;
        this.buttonText = I18n.format(buttonKey);
    }

    @Override
    public void keyPressed(int key, int modifiers) {}

    @Override
    public void initGui() {
        lines = fontRendererObj.listFormattedStringToWidth(message, width - 50);
        textHeight = lines.size() * FontRenderer.FONT_HEIGHT;
        buttonList.add(new GuiButton(0, width / 2 - 100, height / 2 + textHeight / 2 + FontRenderer.FONT_HEIGHT, buttonText));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, title, width / 2, height / 2 - textHeight / 2 - FontRenderer.FONT_HEIGHT * 2, 0xAAAAAA);
        int y = height / 2 - textHeight / 2;
        for (String line : lines) {
            drawCenteredString(fontRendererObj, line, width / 2, y, 0xFFFFFF);
            y += FontRenderer.FONT_HEIGHT;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

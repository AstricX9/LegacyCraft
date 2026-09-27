package net.minecraft.client.gui;

import net.minecraft.client.settings.GameSettings;

/** "Chat Settings": the chat options in a two-column grid, then Done. */
public class ScreenChatOptions extends GuiScreen {
    private static final GameSettings.Options[] OPTIONS = {
        GameSettings.Options.CHAT_VISIBILITY, GameSettings.Options.CHAT_COLOR, GameSettings.Options.CHAT_LINKS,
        GameSettings.Options.CHAT_OPACITY, GameSettings.Options.CHAT_LINKS_PROMPT, GameSettings.Options.CHAT_SCALE,
        GameSettings.Options.CHAT_HEIGHT_FOCUSED, GameSettings.Options.CHAT_HEIGHT_UNFOCUSED, GameSettings.Options.CHAT_WIDTH,
        GameSettings.Options.REDUCED_DEBUG_INFO};

    private final GuiScreen parent;

    public ScreenChatOptions(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        int i = 0;
        for (GameSettings.Options option : OPTIONS) {
            buttonList.add(GuiOptionButton.create(option.ordinal(), width / 2 - 155 + i % 2 * 160, height / 6 + 24 * (i >> 1),
                150, mc.settings(), option));
            i++;
        }
        buttonList.add(new GuiButton(200, width / 2 - 100, height / 6 + 120, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button instanceof GuiOptionButton option) { option.cycle(mc.settings()); mc.saveOptions(); }
        if (button.id == 200) { mc.saveOptions(); mc.displayGuiScreen(parent); }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) { super.mouseReleased(mouseX, mouseY, button); mc.saveOptions(); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("options.chat.title"), width / 2, 20, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

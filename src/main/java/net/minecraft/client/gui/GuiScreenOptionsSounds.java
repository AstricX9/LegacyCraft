package net.minecraft.client.gui;

import net.minecraft.client.settings.GameSettings;

/**
 * "Music & Sound Options": Master Volume full-width, then one slider per
 * sound category. Levels are saved and applied to playing sounds at once
 * (the master slider scales everything).
 */
public class GuiScreenOptionsSounds extends GuiScreen {
    private final GuiScreen parent;

    public GuiScreenOptionsSounds(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        int i = 0;
        buttonList.add(slider(0, width / 2 - 155, height / 6 - 12, 310, "master"));
        i += 2;
        for (String category : GameSettings.SOUND_CATEGORIES) {
            if (category.equals("master")) continue;
            buttonList.add(slider(i, width / 2 - 155 + i % 2 * 160, height / 6 - 12 + 24 * (i >> 1), 150, category));
            i++;
        }
        buttonList.add(new GuiButton(200, width / 2 - 100, height / 6 + 168, I18n.format("gui.done")));
    }

    private GuiSlider slider(int id, int x, int y, int width, String category) {
        GameSettings settings = mc.settings();
        return new GuiSlider(id, x, y, width, 20, settings.getSoundLevel(category), position -> position,
            position -> settings.setSoundLevel(category, (float) position),
            position -> I18n.format("soundCategory." + category) + ": "
                + (position == 0 ? I18n.format("options.off") : (int) (position * 100) + "%"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 200) { mc.saveOptions(); mc.displayGuiScreen(parent); }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) { super.mouseReleased(mouseX, mouseY, button); mc.saveOptions(); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("options.sounds.title"), width / 2, 15, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

package net.minecraft.client.gui;

import net.minecraft.client.settings.GameSettings;

/** "Skin Customization": one ON/OFF toggle per model part, then Done. */
public class GuiCustomizeSkin extends GuiScreen {
    private final GuiScreen parent;

    public GuiCustomizeSkin(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        int i = 0;
        for (String part : GameSettings.MODEL_PARTS) {
            buttonList.add(new GuiButton(i, width / 2 - 155 + i % 2 * 160, height / 6 + 24 * (i >> 1), 150, 20, caption(part)));
            i++;
        }
        if (i % 2 == 1) i++;
        buttonList.add(new GuiButton(200, width / 2 - 100, height / 6 + 24 * (i >> 1), I18n.format("gui.done")));
    }

    private String caption(String part) {
        return I18n.format("options.modelPart." + part) + ": "
            + I18n.format(mc.settings().isModelPartEnabled(part) ? "options.on" : "options.off");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 200) { mc.saveOptions(); mc.displayGuiScreen(parent); return; }
        String part = GameSettings.MODEL_PARTS[button.id];
        mc.settings().toggleModelPart(part);
        button.displayString = caption(part);
        mc.saveOptions();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("options.skinCustomisation.title"), width / 2, 20, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

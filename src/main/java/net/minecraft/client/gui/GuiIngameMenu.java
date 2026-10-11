package net.minecraft.client.gui;

/**
 * The Escape menu ("Game menu"). In singleplayer it pauses the integrated
 * server (master context §69). Achievements opens the achievement map;
 * Statistics and Open to LAN are shown but disabled until those systems
 * exist (DEV-010, DEV-013). While Additions are on for the instance, an
 * Additions button fills the row 1.8.9 leaves empty above Options (DEV-041).
 */
public class GuiIngameMenu extends GuiScreen {
    @Override
    public void initGui() {
        int offset = -16;
        buttonList.add(new GuiButton(1, width / 2 - 100, height / 4 + 120 + offset, I18n.format("menu.returnToMenu")));
        buttonList.add(new GuiButton(4, width / 2 - 100, height / 4 + 24 + offset, I18n.format("menu.returnToGame")));
        buttonList.add(new GuiButton(0, width / 2 - 100, height / 4 + 96 + offset, 98, 20, I18n.format("menu.options")));
        GuiButton lan = new GuiButton(7, width / 2 + 2, height / 4 + 96 + offset, 98, 20, I18n.format("menu.shareToLan"));
        lan.enabled = false;
        buttonList.add(lan);
        GuiButton achievements = new GuiButton(5, width / 2 - 100, height / 4 + 48 + offset, 98, 20, I18n.format("gui.achievements"));
        GuiButton stats = new GuiButton(6, width / 2 + 2, height / 4 + 48 + offset, 98, 20, I18n.format("gui.stats"));
        stats.enabled = false;
        buttonList.add(achievements);
        buttonList.add(stats);
        if (mc.additions().enabled())
            buttonList.add(new GuiButton(ADDITIONS, width / 2 - 100, height / 4 + 72 + offset, GuiAdditions.TITLE));
    }

    static final int ADDITIONS = 20;

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0 -> mc.displayGuiScreen(new GuiOptions(this));
            case 1 -> {
                button.enabled = false;
                mc.saveAndQuitToTitle();
            }
            case 4 -> mc.displayGuiScreen(null);
            case 5 -> mc.displayGuiScreen(new GuiAchievements(this));
            case ADDITIONS -> mc.displayGuiScreen(new GuiAdditions(this));
            default -> {}
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("menu.game"), width / 2, 40, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

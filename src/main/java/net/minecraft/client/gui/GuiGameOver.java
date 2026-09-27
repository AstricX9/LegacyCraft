package net.minecraft.client.gui;

import static org.lwjgl.opengl.GL11.*;

/**
 * The death screen: red gradient, "You died!" (or "Game over!" in hardcore)
 * at double size, the score, and — after a 20-tick delay — Respawn and
 * Title screen (hardcore: Delete world). Leaving for the title screen asks
 * for confirmation first, as 1.8.9 does. It does not pause the game.
 */
public class GuiGameOver extends GuiScreen {
    private int enableButtonsTimer;
    private final boolean hardcore;
    private final int score;

    public GuiGameOver(boolean hardcore, int score) {
        this.hardcore = hardcore;
        this.score = score;
    }

    @Override
    public void initGui() {
        if (hardcore) {
            buttonList.add(new GuiButton(1, width / 2 - 100, height / 4 + 96, I18n.format("deathScreen.deleteWorld")));
        } else {
            buttonList.add(new GuiButton(0, width / 2 - 100, height / 4 + 72, I18n.format("deathScreen.respawn")));
            buttonList.add(new GuiButton(1, width / 2 - 100, height / 4 + 96, I18n.format("deathScreen.titleScreen")));
        }
        for (GuiButton button : buttonList) button.enabled = enableButtonsTimer >= 20;
    }

    @Override
    public void keyPressed(int key, int modifiers) {}

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button.id == 0) {
            mc.respawnPlayer();
            mc.displayGuiScreen(null);
        } else if (button.id == 1) {
            if (hardcore) {
                mc.deleteWorldAndQuit();
            } else {
                GuiYesNo confirm = new GuiYesNo(this, I18n.format("deathScreen.quit.confirm"), "",
                    I18n.format("deathScreen.titleScreen"), I18n.format("deathScreen.respawn"), 0);
                mc.displayGuiScreen(confirm);
                confirm.setButtonDelay(20);
            }
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (result) mc.saveAndQuitToTitle();
        else {
            mc.respawnPlayer();
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void updateScreen() {
        if (++enableButtonsTimer == 20) for (GuiButton button : buttonList) button.enabled = true;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, width, height, 0x60500000, 0xA0803030);
        glPushMatrix();
        glScalef(2, 2, 2);
        drawCenteredString(fontRendererObj, I18n.format(hardcore ? "deathScreen.title.hardcore" : "deathScreen.title"), width / 2 / 2, 30, 0xFFFFFF);
        glPopMatrix();
        if (hardcore) drawCenteredString(fontRendererObj, I18n.format("deathScreen.hardcoreInfo"), width / 2, 144, 0xFFFFFF);
        drawCenteredString(fontRendererObj, I18n.format("deathScreen.score") + ": §e" + score, width / 2, 100, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

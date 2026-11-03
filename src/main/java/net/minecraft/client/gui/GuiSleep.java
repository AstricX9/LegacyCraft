package net.minecraft.client.gui;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * GuiSleepMP: shown while the player lies in bed, the screen darkening
 * as they fall asleep (GuiIngame's 220 * sleepTimer / 100 shade of 0x101020),
 * with Leave Bed; Escape leaves too. It does not pause the game.
 * shortcut: no chat box under it (vanilla's is a chat screen).
 */
public class GuiSleep extends GuiScreen {
    @Override
    public void initGui() {
        buttonList.add(new GuiButton(1, width / 2 - 100, height - 40, I18n.format("multiplayer.stopSleeping")));
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (key == GLFW_KEY_ESCAPE) mc.leaveBed();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) mc.leaveBed();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        float f = Math.min(1f, mc.sleepTimer() / 100f);
        drawRect(0, 0, width, height, (int) (220f * f) << 24 | 0x101020);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

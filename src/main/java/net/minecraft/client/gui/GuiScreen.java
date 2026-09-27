package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Base class for every menu (master context §63, §66). Coordinates are in
 * scaled GUI units; {@link #initGui()} runs on open and on every resize, so
 * layouts are always recomputed from {@link #width}/{@link #height} the way
 * vanilla screens are.
 */
public abstract class GuiScreen extends Gui {
    protected GuiContext mc;
    protected FontRenderer fontRendererObj;
    public int width, height;
    protected final List<GuiButton> buttonList = new ArrayList<>();
    private GuiButton selectedButton;
    private int eventButton = -1;
    private long lastMouseEvent;

    /** Called when the screen is shown or the window is resized. */
    public final void setWorldAndResolution(GuiContext context, int width, int height) {
        this.mc = context;
        this.fontRendererObj = context.font();
        this.width = width;
        this.height = height;
        buttonList.clear();
        initGui();
    }

    public void initGui() {}

    /** One call per client tick (20 per second). */
    public void updateScreen() {}

    public void onGuiClosed() {}

    /** Singleplayer pauses the integrated server while a pausing screen is open (§69). */
    public boolean doesGuiPauseGame() { return true; }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        for (GuiButton button : List.copyOf(buttonList)) button.drawButton(mc, mouseX, mouseY);
    }

    /** A key went down (GLFW code). Escape closes the screen by default. */
    public void keyPressed(int key, int modifiers) {
        if (key == GLFW_KEY_ESCAPE) mc.displayGuiScreen(null);
    }

    /** A printable character was typed. */
    public void charTyped(char character) {}

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        eventButton = mouseButton;
        lastMouseEvent = System.currentTimeMillis();
        if (mouseButton != 0) return;
        for (GuiButton button : List.copyOf(buttonList)) {
            if (button.mousePressed(mc, mouseX, mouseY)) {
                selectedButton = button;
                button.playPressSound(mc);
                actionPerformed(button);
                return;
            }
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (selectedButton != null && mouseButton == 0) {
            selectedButton.mouseReleased(mouseX, mouseY);
            selectedButton = null;
        }
        eventButton = -1;
    }

    /** Mouse moved with a button held (vanilla's mouseClickMove). */
    public void mouseDragged(int mouseX, int mouseY, int mouseButton, long timeSinceClick) {}

    /** Called for mouse movement; routes drags. */
    public final void mouseMoved(int mouseX, int mouseY) {
        if (eventButton != -1 && lastMouseEvent > 0)
            mouseDragged(mouseX, mouseY, eventButton, System.currentTimeMillis() - lastMouseEvent);
    }

    /** Wheel notches: positive is away from the user. */
    public void mouseScrolled(int mouseX, int mouseY, int direction) {}

    protected void actionPerformed(GuiButton button) {}

    /** Answer from a {@link GuiYesNo} this screen opened. */
    public void confirmClicked(boolean result, int id) {}

    /** The in-world translucent dim, or the dirt background on the title-side menus. */
    public void drawDefaultBackground() { drawWorldBackground(0); }

    public void drawWorldBackground(int tint) {
        if (mc.isInGame()) drawGradientRect(0, 0, width, height, 0xC0101010, 0xD0101010);
        else drawBackground(tint);
    }

    /** Tiled {@code options_background.png} at 1/4 brightness, 32 GUI px per tile. */
    public void drawBackground(int tint) {
        glDisable(GL_FOG);
        glDisable(GL_LIGHTING);
        glEnable(GL_TEXTURE_2D);
        mc.textures().bind(GuiTextures.OPTIONS_BACKGROUND);
        float tile = 32f;
        glColor4f(64 / 255f, 64 / 255f, 64 / 255f, 1f);
        glBegin(GL_QUADS);
        glTexCoord2f(0, height / tile + tint); glVertex2f(0, height);
        glTexCoord2f(width / tile, height / tile + tint); glVertex2f(width, height);
        glTexCoord2f(width / tile, tint); glVertex2f(width, 0);
        glTexCoord2f(0, tint); glVertex2f(0, 0);
        glEnd();
        glColor4f(1, 1, 1, 1);
    }

    protected boolean isShiftKeyDown() { return mc.isKeyDown(GLFW_KEY_LEFT_SHIFT) || mc.isKeyDown(GLFW_KEY_RIGHT_SHIFT); }
    protected boolean isCtrlKeyDown() { return mc.isKeyDown(GLFW_KEY_LEFT_CONTROL) || mc.isKeyDown(GLFW_KEY_RIGHT_CONTROL); }

    /** Draws a hovering text box with vanilla's purple-bordered tooltip style. */
    protected void drawHoveringText(List<String> lines, int x, int y) {
        if (lines.isEmpty()) return;
        int textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, fontRendererObj.getStringWidth(line));
        int left = x + 12, top = y - 12;
        int boxHeight = 8 + (lines.size() > 1 ? 2 + (lines.size() - 1) * 10 : 0);
        if (left + textWidth > width) left -= 28 + textWidth;
        if (top + boxHeight + 6 > height) top = height - boxHeight - 6;
        int background = 0xF0100010, borderTop = 0x505000FF, borderBottom = (borderTop & 0xFEFEFE) >> 1 | borderTop & 0xFF000000;
        drawGradientRect(left - 3, top - 4, left + textWidth + 3, top - 3, background, background);
        drawGradientRect(left - 3, top + boxHeight + 3, left + textWidth + 3, top + boxHeight + 4, background, background);
        drawGradientRect(left - 3, top - 3, left + textWidth + 3, top + boxHeight + 3, background, background);
        drawGradientRect(left - 4, top - 3, left - 3, top + boxHeight + 3, background, background);
        drawGradientRect(left + textWidth + 3, top - 3, left + textWidth + 4, top + boxHeight + 3, background, background);
        drawGradientRect(left - 3, top - 3 + 1, left - 3 + 1, top + boxHeight + 3 - 1, borderTop, borderBottom);
        drawGradientRect(left + textWidth + 2, top - 3 + 1, left + textWidth + 3, top + boxHeight + 3 - 1, borderTop, borderBottom);
        drawGradientRect(left - 3, top - 3, left + textWidth + 3, top - 3 + 1, borderTop, borderTop);
        drawGradientRect(left - 3, top + boxHeight + 2, left + textWidth + 3, top + boxHeight + 3, borderBottom, borderBottom);
        for (int i = 0; i < lines.size(); i++) {
            fontRendererObj.drawStringWithShadow(lines.get(i), left, top, -1);
            top += i == 0 ? 12 : 10;
        }
    }
}

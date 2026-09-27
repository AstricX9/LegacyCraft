package net.minecraft.client.gui;

import static org.lwjgl.opengl.GL11.*;

/**
 * The standard 1.8.9 button (master context §63): drawn from
 * {@code widgets.png} as a left and right half so any width up to 400 reuses
 * the same 200px strip; rows at v=46 (disabled), 66 (normal) and 86 (hovered).
 * Label colours: 0xE0E0E0 normal, 0xA0A0A0 disabled, 0xFFFFA0 hovered.
 * HYPOTHESIS until screenshot-compared (unknowns.yml "GUI pixel layout").
 */
public class GuiButton extends Gui {
    public static final int COLOR_NORMAL = 0xE0E0E0, COLOR_DISABLED = 0xA0A0A0, COLOR_HOVERED = 0xFFFFA0;

    public int id;
    public int xPosition, yPosition;
    public int width, height;
    public String displayString;
    public boolean enabled = true;
    public boolean visible = true;
    protected boolean hovered;
    /** Overrides the label colour when non-zero. */
    public int packedFGColour;

    public GuiButton(int id, int x, int y, String text) { this(id, x, y, 200, 20, text); }

    public GuiButton(int id, int x, int y, int width, int height, String text) {
        this.id = id; this.xPosition = x; this.yPosition = y;
        this.width = width; this.height = height; this.displayString = text;
    }

    /** 0 disabled, 1 normal, 2 hovered — the widgets.png row. */
    protected int getHoverState(boolean mouseOver) {
        if (!enabled) return 0;
        return mouseOver ? 2 : 1;
    }

    public boolean isMouseOver() { return hovered; }

    public void drawButton(GuiContext context, int mouseX, int mouseY) {
        if (!visible) return;
        hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
        context.textures().bind(GuiTextures.WIDGETS);
        glColor4f(1, 1, 1, 1);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        int state = getHoverState(hovered);
        drawTexturedModalRect(xPosition, yPosition, 0, 46 + state * 20, width / 2, height);
        drawTexturedModalRect(xPosition + width / 2, yPosition, 200 - width / 2, 46 + state * 20, width / 2, height);
        mouseDragged(context, mouseX, mouseY);
        int color = COLOR_NORMAL;
        if (packedFGColour != 0) color = packedFGColour;
        else if (!enabled) color = COLOR_DISABLED;
        else if (hovered) color = COLOR_HOVERED;
        drawCenteredString(context.font(), displayString, xPosition + width / 2, yPosition + (height - 8) / 2, color);
    }

    /** Called while drawing so sliders can follow a held mouse button. */
    protected void mouseDragged(GuiContext context, int mouseX, int mouseY) {}

    public void mouseReleased(int mouseX, int mouseY) {}

    /** Whether a click at this point presses the button (it must be enabled and visible). */
    public boolean mousePressed(GuiContext context, int mouseX, int mouseY) {
        return enabled && visible && mouseX >= xPosition && mouseY >= yPosition
            && mouseX < xPosition + width && mouseY < yPosition + height;
    }

    public void playPressSound(GuiContext context) { context.playClickSound(); }

    public int getButtonWidth() { return width; }
}

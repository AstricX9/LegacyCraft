package net.minecraft.client.gui;

import static org.lwjgl.opengl.GL11.*;

/**
 * Scrolling list used by the world, server, language, resource-pack and key
 * binding screens (1.8.9 GuiSlot behaviour, HYPOTHESIS for pixel details):
 * a darkened dirt well between {@code top} and {@code bottom}, dirt bands
 * redrawn over the rows above and below it, 4px shadows at both edges, a
 * 6px scrollbar at {@link #getScrollBarX()}, half-row wheel steps and a
 * 250ms double-click window.
 */
public abstract class GuiSlot extends Gui {
    protected final GuiContext mc;
    protected int width, height;
    protected int top, bottom;
    protected int left, right;
    protected final int slotHeight;
    protected float amountScrolled;
    protected int selectedElement = -1;
    private long lastClicked;
    private boolean draggingScrollbar;
    private int dragStartY;
    private float dragStartScroll;
    protected boolean showSelectionBox = true;
    protected boolean hasListHeader;
    protected int headerPadding;
    private boolean enabled = true;

    public GuiSlot(GuiContext mc, int width, int height, int top, int bottom, int slotHeight) {
        this.mc = mc;
        this.width = width; this.height = height;
        this.top = top; this.bottom = bottom;
        this.slotHeight = slotHeight;
        this.left = 0; this.right = width;
    }

    public void setDimensions(int width, int height, int top, int bottom) {
        this.width = width; this.height = height; this.top = top; this.bottom = bottom;
        this.left = 0; this.right = width;
    }

    protected void setHasListHeader(boolean hasHeader, int padding) {
        hasListHeader = hasHeader;
        headerPadding = hasHeader ? padding : 0;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    protected abstract int getSize();
    protected abstract void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY);
    protected abstract boolean isSelected(int index);
    protected abstract void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY);

    protected void drawListHeader(int x, int y) {}
    protected void func_148132_a(int x, int y) {}

    protected int getContentHeight() { return getSize() * slotHeight + headerPadding; }
    public int getListWidth() { return 220; }
    protected int getScrollBarX() { return width / 2 + 124; }

    public int getMaxScroll() { return Math.max(0, getContentHeight() - (bottom - top - 4)); }
    public float getAmountScrolled() { return amountScrolled; }
    public void scrollBy(float amount) { amountScrolled += amount; bindAmountScrolled(); }

    protected void bindAmountScrolled() {
        amountScrolled = Math.max(0, Math.min(getMaxScroll(), amountScrolled));
    }

    /** Index of the row under a point, or -1. */
    public int getSlotIndexFromScreenCoords(int mouseX, int mouseY) {
        int listLeft = left + width / 2 - getListWidth() / 2;
        int listRight = left + width / 2 + getListWidth() / 2;
        int relative = mouseY - top - headerPadding + (int) amountScrolled - 4;
        int index = relative / slotHeight;
        return isMouseYWithinSlotBounds(mouseY) && mouseX < getScrollBarX() && mouseX >= listLeft && mouseX <= listRight && relative >= 0
            && index >= 0 && index < getSize() ? index : -1;
    }

    /** Left x a row's content is drawn at. */
    protected int rowLeft() { return left + width / 2 - getListWidth() / 2 + 2; }

    /** Top y a row is drawn at with the current scroll. */
    protected int rowTop(int index) { return top + 4 - (int) amountScrolled + index * slotHeight + headerPadding; }

    public boolean isMouseYWithinSlotBounds(int mouseY) { return mouseY >= top && mouseY <= bottom; }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        bindAmountScrolled();
        glDisable(GL_LIGHTING);
        glDisable(GL_FOG);
        glEnable(GL_TEXTURE_2D);
        mc.textures().bind(GuiTextures.OPTIONS_BACKGROUND);
        glColor4f(32 / 255f, 32 / 255f, 32 / 255f, 1);
        float tile = 32f;
        glBegin(GL_QUADS);
        glTexCoord2f(left / tile, (bottom + (int) amountScrolled) / tile); glVertex2f(left, bottom);
        glTexCoord2f(right / tile, (bottom + (int) amountScrolled) / tile); glVertex2f(right, bottom);
        glTexCoord2f(right / tile, (top + (int) amountScrolled) / tile); glVertex2f(right, top);
        glTexCoord2f(left / tile, (top + (int) amountScrolled) / tile); glVertex2f(left, top);
        glEnd();
        glColor4f(1, 1, 1, 1);

        int rowX = left + width / 2 - getListWidth() / 2 + 2;
        int rowY = top + 4 - (int) amountScrolled;
        if (hasListHeader) drawListHeader(rowX, rowY);
        drawSelectionBox(rowX, rowY, mouseX, mouseY);

        overlayBackground(0, top);
        overlayBackground(bottom, height);
        drawGradientRect(left, top, right, top + 4, 0xFF000000, 0x00000000);
        drawGradientRect(left, bottom - 4, right, bottom, 0x00000000, 0xFF000000);

        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int barLeft = getScrollBarX(), barRight = barLeft + 6;
            int viewHeight = bottom - top;
            int thumb = viewHeight * viewHeight / getContentHeight();
            thumb = Math.max(32, Math.min(viewHeight - 8, thumb));
            int thumbTop = (int) amountScrolled * (viewHeight - thumb) / maxScroll + top;
            if (thumbTop < top) thumbTop = top;
            drawRect(barLeft, top, barRight, bottom, 0xFF000000);
            drawRect(barLeft, thumbTop, barRight, thumbTop + thumb, 0xFF808080);
            drawRect(barLeft, thumbTop, barRight - 1, thumbTop + thumb - 1, 0xFFC0C0C0);
        }
    }

    protected void drawSelectionBox(int x, int y, int mouseX, int mouseY) {
        int size = getSize();
        for (int index = 0; index < size; index++) {
            int rowTop = y + index * slotHeight + headerPadding;
            int innerHeight = slotHeight - 4;
            if (rowTop > bottom || rowTop + innerHeight < top) continue;
            if (showSelectionBox && isSelected(index)) {
                int boxLeft = left + (width / 2 - getListWidth() / 2);
                int boxRight = left + width / 2 + getListWidth() / 2;
                drawRect(boxLeft, rowTop - 2, boxRight, rowTop + innerHeight + 2, 0xFF808080);
                drawRect(boxLeft + 1, rowTop - 1, boxRight - 1, rowTop + innerHeight + 1, 0xFF000000);
            }
            drawSlot(index, x, rowTop, innerHeight, mouseX, mouseY);
        }
    }

    /** Dirt band at full vanilla list brightness (64/255) between two y values. */
    protected void overlayBackground(int startY, int endY) {
        glEnable(GL_TEXTURE_2D);
        mc.textures().bind(GuiTextures.OPTIONS_BACKGROUND);
        glColor4f(64 / 255f, 64 / 255f, 64 / 255f, 1);
        float tile = 32f;
        glBegin(GL_QUADS);
        glTexCoord2f(0, endY / tile); glVertex2f(left, endY);
        glTexCoord2f(width / tile, endY / tile); glVertex2f(left + width, endY);
        glTexCoord2f(width / tile, startY / tile); glVertex2f(left + width, startY);
        glTexCoord2f(0, startY / tile); glVertex2f(left, startY);
        glEnd();
        glColor4f(1, 1, 1, 1);
    }

    /** Returns whether the list consumed the click. */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!enabled || button != 0 || !isMouseYWithinSlotBounds(mouseY)) return false;
        int barLeft = getScrollBarX();
        if (mouseX >= barLeft && mouseX <= barLeft + 6 && getMaxScroll() > 0) {
            draggingScrollbar = true;
            dragStartY = mouseY;
            dragStartScroll = amountScrolled;
            return true;
        }
        int index = getSlotIndexFromScreenCoords(mouseX, mouseY);
        if (index >= 0) {
            long now = System.currentTimeMillis();
            boolean doubleClick = index == selectedElement && now - lastClicked < 250L;
            elementClicked(index, doubleClick, mouseX, mouseY);
            selectedElement = index;
            lastClicked = now;
            return true;
        }
        int listLeft = left + width / 2 - getListWidth() / 2;
        if (mouseX >= listLeft && mouseX <= left + width / 2 + getListWidth() / 2) {
            func_148132_a(mouseX - listLeft, mouseY - top + (int) amountScrolled - 4);
        }
        return false;
    }

    public void mouseDragged(int mouseX, int mouseY) {
        if (!draggingScrollbar) return;
        int viewHeight = bottom - top;
        int thumb = Math.max(32, Math.min(viewHeight - 8, viewHeight * viewHeight / Math.max(1, getContentHeight())));
        float perPixel = getMaxScroll() / (float) Math.max(1, viewHeight - thumb);
        amountScrolled = dragStartScroll + (mouseY - dragStartY) * perPixel;
        bindAmountScrolled();
    }

    public void mouseReleased() { draggingScrollbar = false; }

    public void mouseScrolled(int direction) {
        if (direction == 0) return;
        amountScrolled -= Integer.signum(direction) * slotHeight / 2f;
        bindAmountScrolled();
    }
}

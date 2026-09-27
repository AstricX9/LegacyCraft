package net.minecraft.client.gui;

import net.minecraft.client.settings.GameSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrolling two-column list of option controls (the Video Settings list):
 * rows 25px high, controls 150px wide at width/2-155 and width/2+5.
 */
public class GuiOptionsRowList extends GuiSlot {
    private final List<GuiButton[]> rows = new ArrayList<>();
    private GuiButton pressed;
    private final Runnable onChange;

    public GuiOptionsRowList(GuiContext mc, int width, int height, int top, int bottom, int slotHeight,
                             Runnable onChange, GameSettings.Options... options) {
        super(mc, width, height, top, bottom, slotHeight);
        this.onChange = onChange;
        showSelectionBox = false;
        for (int i = 0; i < options.length; i += 2) {
            GuiButton left = create(options[i], width / 2 - 155);
            GuiButton right = i + 1 < options.length ? create(options[i + 1], width / 2 - 155 + 160) : null;
            rows.add(new GuiButton[]{left, right});
        }
    }

    private GuiButton create(GameSettings.Options option, int x) {
        if (option == null) return null;
        return GuiOptionButton.create(option.ordinal(), x, 0, 150, mc.settings(), option);
    }

    @Override protected int getSize() { return rows.size(); }
    @Override protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {}
    @Override protected boolean isSelected(int index) { return false; }
    @Override public int getListWidth() { return 400; }
    @Override protected int getScrollBarX() { return width / 2 + 160; }

    @Override
    protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
        for (GuiButton button : rows.get(index)) {
            if (button == null) continue;
            button.yPosition = y;
            button.drawButton(mc, mouseX, mouseY);
        }
    }

    /** Presses the control under the mouse; returns whether one was hit. */
    public boolean clickControls(int mouseX, int mouseY, int mouseButton) {
        int index = getSlotIndexFromScreenCoords(mouseX, mouseY);
        if (mouseButton != 0 || index < 0) return false;
        {
            for (GuiButton button : rows.get(index)) {
                if (button == null) continue;
                button.yPosition = rowTop(index);
                if (!button.mousePressed(mc, mouseX, mouseY)) continue;
                button.playPressSound(mc);
                if (button instanceof GuiOptionButton option) option.cycle(mc.settings());
                pressed = button;
                onChange.run();
                return true;
            }
        }
        return false;
    }

    public void releaseControls(int mouseX, int mouseY) {
        if (pressed != null) {
            pressed.mouseReleased(mouseX, mouseY);
            onChange.run();
        }
        pressed = null;
    }
}

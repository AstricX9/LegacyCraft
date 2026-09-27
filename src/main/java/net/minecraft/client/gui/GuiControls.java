package net.minecraft.client.gui;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.settings.KeyCodes;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * "Controls": Invert Mouse, Sensitivity and Touchscreen Mode at the top, then
 * every key binding grouped by category (sorted as {@link KeyBinding#compareTo}),
 * each with a change button ("> W <" while waiting for a key, red when it
 * clashes with another binding) and a Reset button; Done and Reset Keys below.
 * Escape while rebinding unbinds the key (sets it to NONE), as in vanilla.
 */
public class GuiControls extends GuiScreen {
    private static final GameSettings.Options[] OPTIONS = {
        GameSettings.Options.INVERT_MOUSE, GameSettings.Options.SENSITIVITY, GameSettings.Options.TOUCHSCREEN};

    private final GuiScreen parent;
    /** The binding waiting for a new key, or null. */
    private KeyBinding buttonId;
    private KeyList list;
    private GuiButton resetAll;

    public GuiControls(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        list = new KeyList();
        buttonList.add(new GuiButton(200, width / 2 - 155, height - 29, 150, 20, I18n.format("gui.done")));
        buttonList.add(resetAll = new GuiButton(201, width / 2 - 155 + 160, height - 29, 150, 20, I18n.format("controls.resetAll")));
        int i = 0;
        for (GameSettings.Options option : OPTIONS) {
            buttonList.add(GuiOptionButton.create(option.ordinal(), width / 2 - 155 + i % 2 * 160, 18 + 24 * (i >> 1),
                150, mc.settings(), option));
            i++;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 200) { mc.saveOptions(); mc.displayGuiScreen(parent); return; }
        if (button.id == 201) {
            for (KeyBinding binding : mc.settings().keyBindings) binding.setKeyCode(binding.getKeyCodeDefault());
            mc.saveOptions();
            return;
        }
        if (button instanceof GuiOptionButton option) { option.cycle(mc.settings()); mc.saveOptions(); }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (buttonId != null) {
            buttonId.setKeyCode(KeyCodes.fromMouseButton(button));
            buttonId = null;
            mc.saveOptions();
            return;
        }
        if (button == 0 && list.clickControls(mouseX, mouseY)) return;
        list.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        list.mouseReleased();
        super.mouseReleased(mouseX, mouseY, button);
        mc.saveOptions();
    }

    @Override public void mouseDragged(int mouseX, int mouseY, int button, long time) { list.mouseDragged(mouseX, mouseY); }
    @Override public void mouseScrolled(int mouseX, int mouseY, int direction) { list.mouseScrolled(direction); }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (buttonId != null) {
            buttonId.setKeyCode(key == GLFW_KEY_ESCAPE ? KeyCodes.NONE : KeyCodes.fromGlfwKey(key));
            buttonId = null;
            mc.saveOptions();
            return;
        }
        super.keyPressed(key, modifiers);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, I18n.format("controls.title"), width / 2, 8, 0xFFFFFF);
        boolean anyChanged = false;
        for (KeyBinding binding : mc.settings().keyBindings) if (!binding.isDefault()) { anyChanged = true; break; }
        resetAll.enabled = anyChanged;
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Whether another binding uses the same (non-NONE) code. */
    static boolean conflicts(KeyBinding binding, List<KeyBinding> all) {
        if (binding.getKeyCode() == KeyCodes.NONE) return false;
        for (KeyBinding other : all) if (other != binding && other.getKeyCode() == binding.getKeyCode()) return true;
        return false;
    }

    private final class KeyList extends GuiSlot {
        private final List<Object> entries = new ArrayList<>();
        private final List<GuiButton[]> buttons = new ArrayList<>();
        private int maxLabelWidth;

        KeyList() {
            super(GuiControls.this.mc, GuiControls.this.width, GuiControls.this.height, 63, GuiControls.this.height - 32, 20);
            showSelectionBox = false;
            List<KeyBinding> sorted = new ArrayList<>(mc.settings().keyBindings);
            sorted.sort(null);
            String category = null;
            for (KeyBinding binding : sorted) {
                if (!binding.getKeyCategory().equals(category)) {
                    category = binding.getKeyCategory();
                    entries.add(category);
                    buttons.add(null);
                }
                maxLabelWidth = Math.max(maxLabelWidth, fontRendererObj.getStringWidth(I18n.format(binding.getKeyDescription())));
                entries.add(binding);
                buttons.add(new GuiButton[]{new GuiButton(0, 0, 0, 75, 20, ""), new GuiButton(0, 0, 0, 50, 20, I18n.format("controls.reset"))});
            }
        }

        @Override protected int getSize() { return entries.size(); }
        @Override protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {}
        @Override protected boolean isSelected(int index) { return false; }
        @Override protected int getScrollBarX() { return super.getScrollBarX() + 15; }
        @Override public int getListWidth() { return super.getListWidth() + 32; }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            Object entry = entries.get(index);
            if (entry instanceof String category) {
                String text = I18n.format(category);
                fontRendererObj.drawString(text, width / 2 - fontRendererObj.getStringWidth(text) / 2,
                    y + slotHeight - FontRenderer.FONT_HEIGHT - 1, 0xFFFFFF);
                return;
            }
            KeyBinding binding = (KeyBinding) entry;
            GuiButton change = buttons.get(index)[0], reset = buttons.get(index)[1];
            fontRendererObj.drawString(I18n.format(binding.getKeyDescription()), x + 90 - maxLabelWidth,
                y + slotHeight / 2 - FontRenderer.FONT_HEIGHT / 2, 0xFFFFFF);
            reset.xPosition = x + 190;
            reset.yPosition = y;
            reset.enabled = !binding.isDefault();
            reset.drawButton(mc, mouseX, mouseY);
            change.xPosition = x + 105;
            change.yPosition = y;
            String name = KeyBinding.displayName(binding.getKeyCode());
            if (buttonId == binding) change.displayString = "§f> §e" + name + "§f <";
            else if (conflicts(binding, mc.settings().keyBindings)) change.displayString = "§c" + name;
            else change.displayString = name;
            change.drawButton(mc, mouseX, mouseY);
        }

        boolean clickControls(int mouseX, int mouseY) {
            int i = getSlotIndexFromScreenCoords(mouseX, mouseY);
            if (i >= 0 && entries.get(i) instanceof KeyBinding binding) {
                GuiButton[] pair = buttons.get(i);
                pair[0].xPosition = rowLeft() + 105;
                pair[1].xPosition = rowLeft() + 190;
                pair[0].yPosition = pair[1].yPosition = rowTop(i);
                pair[1].enabled = !binding.isDefault();
                if (pair[0].mousePressed(mc, mouseX, mouseY)) {
                    pair[0].playPressSound(mc);
                    buttonId = binding;
                    return true;
                }
                if (pair[1].mousePressed(mc, mouseX, mouseY)) {
                    pair[1].playPressSound(mc);
                    binding.setKeyCode(binding.getKeyCodeDefault());
                    mc.saveOptions();
                    return true;
                }
            }
            return false;
        }
    }
}

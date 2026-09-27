package net.minecraft.client.gui;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.world.gen.FlatGeneratorInfo;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Superflat "Select a Preset": a shareable preset text box (up to 1230
 * characters, like vanilla) above the built-in preset list. Picking a list
 * entry fills the box; "Use Preset" applies whatever the box holds.
 */
public class GuiFlatPresets extends GuiScreen {
    private final GuiCreateFlatWorld parent;
    private GuiTextField export;
    private PresetList list;
    private int selected = -1;
    private GuiButton usePreset;

    public GuiFlatPresets(GuiCreateFlatWorld parent) { this.parent = parent; }

    @Override
    public void initGui() {
        String text = export == null ? parent.getPreset() : export.getText();
        export = new GuiTextField(2, fontRendererObj, 50, 40, width - 100, 20);
        export.setClipboard(GuiRenameWorld.clipboard(mc));
        export.setMaxStringLength(1230);
        export.setText(text);
        export.setOnChange(this::updateButtons);
        list = new PresetList();
        buttonList.add(usePreset = new GuiButton(0, width / 2 - 155, height - 28, 150, 20, I18n.format("createWorld.customize.presets.select")));
        buttonList.add(new GuiButton(1, width / 2 + 5, height - 28, 150, 20, I18n.format("gui.cancel")));
        updateButtons();
    }

    private void updateButtons() {
        usePreset.enabled = export.getText().length() > 1 || selected > -1;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0 && usePreset.enabled) {
            parent.setPreset(export.getText());
            mc.displayGuiScreen(parent);
        } else if (button.id == 1) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (export.keyPressed(key, modifiers)) { updateButtons(); return; }
        if (key == GLFW_KEY_ESCAPE) mc.displayGuiScreen(parent);
    }

    @Override
    public void charTyped(char character) { if (export.charTyped(character)) updateButtons(); }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        export.mouseClicked(mouseX, mouseY, button);
        if (list.mouseClicked(mouseX, mouseY, button)) return;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) { list.mouseReleased(); super.mouseReleased(mouseX, mouseY, button); }

    @Override
    public void mouseDragged(int mouseX, int mouseY, int button, long time) { list.mouseDragged(mouseX, mouseY); }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) { list.mouseScrolled(direction); }

    @Override
    public void updateScreen() { export.updateCursorCounter(); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, I18n.format("createWorld.customize.presets.title"), width / 2, 8, 0xFFFFFF);
        drawString(fontRendererObj, I18n.format("createWorld.customize.presets.share"), 50, 30, 0xA0A0A0);
        drawString(fontRendererObj, I18n.format("createWorld.customize.presets.list"), 50, 70, 0xA0A0A0);
        export.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private final class PresetList extends GuiSlot {
        PresetList() { super(GuiFlatPresets.this.mc, GuiFlatPresets.this.width, GuiFlatPresets.this.height, 80, GuiFlatPresets.this.height - 37, 24); }

        @Override protected int getSize() { return FlatGeneratorInfo.PRESETS.size(); }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            selected = index;
            export.setText(FlatGeneratorInfo.PRESETS.get(index).value());
            updateButtons();
        }

        @Override protected boolean isSelected(int index) { return index == selected; }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            FlatGeneratorInfo.Preset preset = FlatGeneratorInfo.PRESETS.get(index);
            mc.textures().bind(new net.minecraft.util.ResourceLocation("minecraft:textures/gui/container/stats_icons.png"));
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
            drawScaledCustomSizeModalRect(x + 1, y + 1, 0, 0, 18, 18, 18, 18, 128, 128);
            Block icon = mc.blocks().get(preset.iconBlock());
            if (icon != null) mc.renderBlockIcon(BlockState.of(icon, preset.iconMeta()), x + 2, y + 2);
            fontRendererObj.drawString(preset.name(), x + 18 + 5, y + 6, 0xFFFFFF);
        }
    }
}

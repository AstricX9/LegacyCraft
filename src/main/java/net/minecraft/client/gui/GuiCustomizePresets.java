package net.minecraft.client.gui;

import net.minecraft.util.ResourceLocation;

import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * "Customize World Presets": a share box holding the settings JSON, and the
 * seven named 1.8.9 presets with their icons. The named presets' exact values
 * have not been captured from a real client yet (unknowns.yml), so they are
 * listed but cannot be applied; pasting a JSON string into the box works.
 */
public class GuiCustomizePresets extends GuiScreen {
    private record Preset(String nameKey, String icon) {}

    private static final List<Preset> PRESETS = List.of(
        new Preset("createWorld.customize.custom.preset.waterWorld", "water"),
        new Preset("createWorld.customize.custom.preset.isleLand", "isles"),
        new Preset("createWorld.customize.custom.preset.caveDelight", "delight"),
        new Preset("createWorld.customize.custom.preset.mountains", "madness"),
        new Preset("createWorld.customize.custom.preset.drought", "drought"),
        new Preset("createWorld.customize.custom.preset.caveChaos", "chaos"),
        new Preset("createWorld.customize.custom.preset.goodLuck", "luck"));

    private final GuiCustomizeWorldScreen parent;
    private GuiTextField export;
    private PresetList list;
    private GuiButton select;

    public GuiCustomizePresets(GuiCustomizeWorldScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        String text = export == null ? parent.getSettingsJson() : export.getText();
        export = new GuiTextField(2, fontRendererObj, 50, 40, width - 100, 20);
        export.setClipboard(GuiRenameWorld.clipboard(mc));
        export.setMaxStringLength(2000);
        export.setText(text);
        list = new PresetList();
        buttonList.add(select = new GuiButton(0, width / 2 - 102, height - 27, 100, 20, I18n.format("createWorld.customize.presets.select")));
        buttonList.add(new GuiButton(1, width / 2 + 3, height - 27, 100, 20, I18n.format("gui.cancel")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) parent.loadSettings(export.getText());
        mc.displayGuiScreen(parent);
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (export.keyPressed(key, modifiers)) return;
        if (key == GLFW_KEY_ESCAPE) mc.displayGuiScreen(parent);
    }

    @Override public void charTyped(char character) { export.charTyped(character); }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        export.mouseClicked(mouseX, mouseY, button);
        if (list.mouseClicked(mouseX, mouseY, button)) return;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void mouseReleased(int mouseX, int mouseY, int button) { list.mouseReleased(); super.mouseReleased(mouseX, mouseY, button); }
    @Override public void mouseDragged(int mouseX, int mouseY, int button, long time) { list.mouseDragged(mouseX, mouseY); }
    @Override public void mouseScrolled(int mouseX, int mouseY, int direction) { list.mouseScrolled(direction); }
    @Override public void updateScreen() { export.updateCursorCounter(); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, I18n.format("createWorld.customize.custom.presets.title"), width / 2, 8, 0xFFFFFF);
        drawString(fontRendererObj, I18n.format("createWorld.customize.presets.share"), 50, 30, 0xA0A0A0);
        drawString(fontRendererObj, I18n.format("createWorld.customize.presets.list"), 50, 70, 0xA0A0A0);
        export.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private final class PresetList extends GuiSlot {
        PresetList() { super(GuiCustomizePresets.this.mc, GuiCustomizePresets.this.width, GuiCustomizePresets.this.height, 80, GuiCustomizePresets.this.height - 32, 38); }

        @Override protected int getSize() { return PRESETS.size(); }
        @Override protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {}
        @Override protected boolean isSelected(int index) { return false; }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            Preset preset = PRESETS.get(index);
            ResourceLocation icon = new ResourceLocation("minecraft:textures/gui/presets/" + preset.icon() + ".png");
            mc.textures().bind(icon);
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
            drawModalRectWithCustomSizedTexture(x + 5, y + 1, 0, 0, 32, 32, 32, 32);
            fontRendererObj.drawString(I18n.format(preset.nameKey()), x + 32 + 10, y + 14, 0x808080);
        }
    }
}

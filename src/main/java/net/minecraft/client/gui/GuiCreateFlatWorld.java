package net.minecraft.client.gui;

import net.minecraft.block.Block;
import net.minecraft.world.gen.FlatGeneratorInfo;

import java.util.Locale;

/**
 * "Superflat Customization": the layer list (top layer first) with each
 * layer's block and height, Remove Layer, Presets, Done and Cancel. In 1.8.9
 * the "Add Layer" and "Edit Layer" buttons exist but are never shown; they are
 * kept hidden here for the same reason.
 */
public class GuiCreateFlatWorld extends GuiScreen {
    private final GuiCreateWorld parent;
    private FlatGeneratorInfo info;
    private String preset;
    private int selectedLayer = -1;
    private LayerList list;
    private GuiButton addLayer, editLayer, removeLayer;

    public GuiCreateFlatWorld(GuiCreateWorld parent, String preset) {
        this.parent = parent;
        this.preset = preset == null || preset.isEmpty() ? FlatGeneratorInfo.DEFAULT_PRESET : preset;
    }

    public String getPreset() { return info.toString(); }

    public void setPreset(String preset) { info = FlatGeneratorInfo.parse(preset, mc.blocks()); }

    @Override
    public void initGui() {
        if (info == null) info = FlatGeneratorInfo.parse(preset, mc.blocks());
        list = new LayerList();
        buttonList.add(addLayer = new GuiButton(2, width / 2 - 154, height - 52, 100, 20,
            I18n.format("createWorld.customize.flat.addLayer")));
        buttonList.add(editLayer = new GuiButton(3, width / 2 - 50, height - 52, 100, 20,
            I18n.format("createWorld.customize.flat.editLayer")));
        buttonList.add(removeLayer = new GuiButton(4, width / 2 - 155, height - 52, 150, 20,
            I18n.format("createWorld.customize.flat.removeLayer")));
        buttonList.add(new GuiButton(0, width / 2 - 155, height - 28, 150, 20, I18n.format("gui.done")));
        buttonList.add(new GuiButton(5, width / 2 + 5, height - 52, 150, 20, I18n.format("createWorld.customize.presets")));
        buttonList.add(new GuiButton(1, width / 2 + 5, height - 28, 150, 20, I18n.format("gui.cancel")));
        addLayer.visible = editLayer.visible = false;
        updateButtons();
    }

    private void updateButtons() {
        removeLayer.enabled = selectedLayer > -1 && selectedLayer < info.layers().size();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int topIndex = info.layers().size() - selectedLayer - 1;
        switch (button.id) {
            case 1 -> mc.displayGuiScreen(parent);
            case 0 -> {
                parent.chunkProviderSettingsJson = getPreset();
                mc.displayGuiScreen(parent);
            }
            case 5 -> mc.displayGuiScreen(new GuiFlatPresets(this));
            case 4 -> {
                if (removeLayer.enabled && topIndex >= 0) {
                    info.layers().remove(topIndex);
                    selectedLayer = Math.min(selectedLayer, info.layers().size() - 1);
                }
            }
            default -> {}
        }
        updateButtons();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, I18n.format("createWorld.customize.flat.title"), width / 2, 8, 0xFFFFFF);
        int left = width / 2 - 92 - 16;
        drawString(fontRendererObj, I18n.format("createWorld.customize.flat.tile"), left, 32, 0xFFFFFF);
        String heightLabel = I18n.format("createWorld.customize.flat.height");
        drawString(fontRendererObj, heightLabel, left + 2 + 213 - fontRendererObj.getStringWidth(heightLabel), 32, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** A block's display name from the language file ("tile.<name>.name"), else a readable form of its id. */
    static String blockName(Block block) {
        String path = block.name().path();
        String key = "tile." + path + ".name";
        if (I18n.hasKey(key)) return I18n.format(key);
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }

    private final class LayerList extends GuiSlot {
        LayerList() { super(GuiCreateFlatWorld.this.mc, GuiCreateFlatWorld.this.width, GuiCreateFlatWorld.this.height, 43, GuiCreateFlatWorld.this.height - 60, 24); }

        @Override protected int getSize() { return info.layers().size(); }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            selectedLayer = index;
            updateButtons();
        }

        @Override protected boolean isSelected(int index) { return index == selectedLayer; }

        @Override
        protected int getScrollBarX() { return width - 70; }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            int count = info.layers().size();
            FlatGeneratorInfo.Layer layer = info.layers().get(count - index - 1);
            // Item slot frame (stats_icons.png 0,0 18x18) then the block icon inside it.
            mc.textures().bind(new net.minecraft.util.ResourceLocation("minecraft:textures/gui/container/stats_icons.png"));
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
            drawScaledCustomSizeModalRect(x + 1, y + 1, 0, 0, 18, 18, 18, 18, 128, 128);
            mc.renderBlockIcon(layer.state(), x + 2, y + 2);
            fontRendererObj.drawString(blockName(layer.state().block()), x + 18 + 5, y + 3, 0xFFFFFF);
            String height;
            if (index == 0) height = I18n.format("createWorld.customize.flat.layer.top", layer.count());
            else if (index == count - 1) height = I18n.format("createWorld.customize.flat.layer.bottom", layer.count());
            else height = I18n.format("createWorld.customize.flat.layer", layer.count());
            fontRendererObj.drawString(height, x + 2 + 213 - fontRendererObj.getStringWidth(height), y + 3, 0xFFFFFF);
        }
    }
}

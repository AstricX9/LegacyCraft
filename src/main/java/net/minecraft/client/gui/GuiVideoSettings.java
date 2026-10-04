package net.minecraft.client.gui;

import net.minecraft.client.settings.GameSettings;

/** "Video Settings": the 1.8.9 option list in its original pairing, then Done. */
public class GuiVideoSettings extends GuiScreen {
    private static final GameSettings.Options[] OPTIONS = {
        GameSettings.Options.GRAPHICS, GameSettings.Options.RENDER_DISTANCE,
        GameSettings.Options.AMBIENT_OCCLUSION, GameSettings.Options.FRAMERATE_LIMIT,
        GameSettings.Options.ANAGLYPH, GameSettings.Options.VIEW_BOBBING,
        GameSettings.Options.GUI_SCALE, GameSettings.Options.GAMMA,
        GameSettings.Options.RENDER_CLOUDS, GameSettings.Options.PARTICLES,
        GameSettings.Options.USE_FULLSCREEN, GameSettings.Options.ENABLE_VSYNC,
        GameSettings.Options.MIPMAP_LEVELS, GameSettings.Options.BLOCK_ALTERNATIVES,
        GameSettings.Options.USE_VBO, GameSettings.Options.ENTITY_SHADOWS};

    private final GuiScreen parent;
    private GuiOptionsRowList list;
    private int mipmapLevels = -1;

    public GuiVideoSettings(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        if (mipmapLevels < 0) mipmapLevels = mc.settings().mipmapLevels;
        buttonList.add(new GuiButton(200, width / 2 - 100, height - 27, I18n.format("gui.done")));
        list = new GuiOptionsRowList(mc, width, height, 32, height - 32, 25, this::optionChanged, OPTIONS);
    }

    private void optionChanged() {
        mc.applyDisplaySettings();
        mc.saveOptions();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 200) { mc.saveOptions(); mc.displayGuiScreen(parent); }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (list.clickControls(mouseX, mouseY, button)) return;
        list.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        list.releaseControls(mouseX, mouseY);
        list.mouseReleased();
        super.mouseReleased(mouseX, mouseY, button);
        // A new mipmap level rebuilds the block atlas (GameSettings.setOptionFloatValue → scheduleResourcesRefresh);
        // done once the slider is let go rather than on every step of the drag.
        if (mc.settings().mipmapLevels != mipmapLevels) {
            mipmapLevels = mc.settings().mipmapLevels;
            mc.refreshResources();
        }
    }

    @Override public void mouseDragged(int mouseX, int mouseY, int button, long time) { list.mouseDragged(mouseX, mouseY); }
    @Override public void mouseScrolled(int mouseX, int mouseY, int direction) { list.mouseScrolled(direction); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, I18n.format("options.videoTitle"), width / 2, 5, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

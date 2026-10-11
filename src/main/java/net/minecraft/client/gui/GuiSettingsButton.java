package net.minecraft.client.gui;

import net.minecraft.util.ResourceLocation;

import static org.lwjgl.opengl.GL11.*;

/**
 * A square button showing {@code textures/gui/settings.png} (from the
 * resource packs, so a pack can change it), used for an addition's settings.
 * Without that image it draws a small gear instead.
 */
public class GuiSettingsButton extends GuiButton {
    public static final ResourceLocation ICON = new ResourceLocation("minecraft:textures/gui/settings.png");
    /** The gear drawn when there's no settings.png: 8x8, '#' is a pixel. */
    private static final String[] GEAR = {
        "...##...",
        ".######.",
        ".##..##.",
        "###..###",
        "###..###",
        ".##..##.",
        ".######.",
        "...##..."};

    public GuiSettingsButton(int id, int x, int y) { super(id, x, y, 20, 20, ""); }

    @Override
    public void drawButton(GuiContext context, int mouseX, int mouseY) {
        super.drawButton(context, mouseX, mouseY);
        if (!visible) return;
        float shade = enabled ? 1f : 0.6f;
        if (context.textures().exists(ICON)) {
            context.textures().bind(ICON);
            int[] size = context.textures().size(ICON);
            glColor4f(shade, shade, shade, 1);
            glEnable(GL_BLEND);
            drawScaledCustomSizeModalRect(xPosition + 2, yPosition + 2, 0, 0, size[0], size[1], 16, 16, size[0], size[1]);
            glColor4f(1, 1, 1, 1);
        } else {
            int colour = !enabled ? COLOR_DISABLED : hovered ? COLOR_HOVERED : COLOR_NORMAL;
            int left = xPosition + 6, top = yPosition + 6;
            for (int y = 0; y < GEAR.length; y++)
                for (int x = 0; x < GEAR[y].length(); x++)
                    if (GEAR[y].charAt(x) == '#') drawRect(left + x, top + y, left + x + 1, top + y + 1, 0xFF000000 | colour);
            glColor4f(1, 1, 1, 1);
        }
    }
}

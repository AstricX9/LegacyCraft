package net.minecraft.client.gui;

import net.minecraft.client.settings.GameSettings;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleUnaryOperator;

import static org.lwjgl.opengl.GL11.*;

/**
 * A 1.8.9 slider: the track is the disabled button strip, the handle an
 * 8px-wide notch (two 4px halves of the normal button strip) at
 * {@code x + value * (width - 8)}. Drag and click both set the value from
 * {@code (mouseX - (x + 4)) / (width - 8)}.
 */
public class GuiSlider extends GuiButton {
    private float sliderValue;
    private boolean dragging;
    private final DoubleUnaryOperator snap;
    private final DoubleConsumer onChange;
    private final DoubleFunction<String> label;

    /**
     * @param normalized initial handle position, 0-1
     * @param snap maps a raw 0-1 position onto the nearest allowed position
     * @param onChange receives the snapped 0-1 position
     * @param label caption for a 0-1 position
     */
    public GuiSlider(int id, int x, int y, int width, int height, float normalized,
                     DoubleUnaryOperator snap, DoubleConsumer onChange, DoubleFunction<String> label) {
        super(id, x, y, width, height, "");
        this.sliderValue = normalized;
        this.snap = snap;
        this.onChange = onChange;
        this.label = label;
        displayString = label.apply(normalized);
    }

    /** A slider bound to a float {@link GameSettings.Options} value (150x20, like GuiOptionSlider). */
    public static GuiSlider forOption(int id, int x, int y, GameSettings settings, GameSettings.Options option) {
        return forOption(id, x, y, 150, settings, option);
    }

    public static GuiSlider forOption(int id, int x, int y, int width, GameSettings settings, GameSettings.Options option) {
        return new GuiSlider(id, x, y, width, 20, option.normalizeValue(settings.getOptionFloatValue(option)),
            position -> option.normalizeValue(option.denormalizeValue((float) position)),
            position -> settings.setOptionFloatValue(option, option.denormalizeValue((float) position)),
            position -> settings.getKeyBinding(option));
    }

    public float getValue() { return sliderValue; }

    public void setValue(float normalized) {
        sliderValue = (float) snap.applyAsDouble(Math.max(0, Math.min(1, normalized)));
        onChange.accept(sliderValue);
        displayString = label.apply(sliderValue);
    }

    @Override
    protected int getHoverState(boolean mouseOver) { return 0; }

    @Override
    protected void mouseDragged(GuiContext context, int mouseX, int mouseY) {
        if (!visible) return;
        if (dragging) setValue((mouseX - (xPosition + 4)) / (float) (width - 8));
        context.textures().bind(GuiTextures.WIDGETS);
        glColor4f(1, 1, 1, 1);
        int handle = xPosition + (int) (sliderValue * (width - 8));
        drawTexturedModalRect(handle, yPosition, 0, 66, 4, 20);
        drawTexturedModalRect(handle + 4, yPosition, 196, 66, 4, 20);
    }

    @Override
    public boolean mousePressed(GuiContext context, int mouseX, int mouseY) {
        if (!super.mousePressed(context, mouseX, mouseY)) return false;
        setValue((mouseX - (xPosition + 4)) / (float) (width - 8));
        dragging = true;
        return true;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) { dragging = false; }
}

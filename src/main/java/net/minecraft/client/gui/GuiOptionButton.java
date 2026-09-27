package net.minecraft.client.gui;

import net.minecraft.client.settings.GameSettings;

/** A 150x20 button bound to a boolean or cycling {@link GameSettings.Options} value. */
public class GuiOptionButton extends GuiButton {
    private final GameSettings.Options option;

    public GuiOptionButton(int id, int x, int y, GameSettings.Options option, String text) {
        this(id, x, y, 150, 20, option, text);
    }

    public GuiOptionButton(int id, int x, int y, int width, int height, GameSettings.Options option, String text) {
        super(id, x, y, width, height, text);
        this.option = option;
    }

    public GameSettings.Options returnEnumOptions() { return option; }

    /** Steps the option and refreshes the caption; screens call this from actionPerformed. */
    public void cycle(GameSettings settings) {
        settings.setOptionValue(option, 1);
        displayString = settings.getKeyBinding(option);
    }

    /** Builds the right control for any option: a slider for floats, a button otherwise. */
    public static GuiButton create(int id, int x, int y, int width, GameSettings settings, GameSettings.Options option) {
        if (option.isFloat()) return GuiSlider.forOption(id, x, y, width, settings, option);
        return new GuiOptionButton(id, x, y, width, 20, option, settings.getKeyBinding(option));
    }
}

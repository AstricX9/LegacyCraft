package net.minecraft.client.settings;

import net.minecraft.client.gui.I18n;

import java.util.List;

/**
 * One rebindable control. Codes are LWJGL 2 codes ({@link KeyCodes}) so the
 * value written to {@code options.txt} is the same number vanilla 1.8.9 writes
 * (key {@code key_<description>}).
 *
 * <p>{@link #isKeyDown()} is the held state; {@link #isPressed()} consumes one
 * queued press, which is how vanilla handles hotbar/inventory/drop keys.
 */
public final class KeyBinding implements Comparable<KeyBinding> {
    private final String description;
    private final String category;
    private final int defaultCode;
    private int code;
    private boolean down;
    private int pressTime;

    public KeyBinding(String description, int defaultCode, String category) {
        this.description = description;
        this.defaultCode = defaultCode;
        this.code = defaultCode;
        this.category = category;
    }

    /** Called by the input layer for every key/mouse transition. */
    public static void onTransition(List<KeyBinding> bindings, int legacyCode, boolean pressed) {
        if (legacyCode == KeyCodes.NONE) return;
        for (KeyBinding binding : bindings) {
            if (binding.code != legacyCode) continue;
            binding.down = pressed;
            if (pressed) binding.pressTime++;
        }
    }

    /** Releases every key (vanilla does this when a GUI opens or focus is lost). */
    public static void unpressAll(List<KeyBinding> bindings) {
        for (KeyBinding binding : bindings) { binding.down = false; binding.pressTime = 0; }
    }

    public boolean isKeyDown() { return down; }

    public boolean isPressed() {
        if (pressTime == 0) return false;
        pressTime--;
        return true;
    }

    public String getKeyDescription() { return description; }
    public String getKeyCategory() { return category; }
    public int getKeyCodeDefault() { return defaultCode; }
    public int getKeyCode() { return code; }
    public void setKeyCode(int code) { this.code = code; }
    public boolean isDefault() { return code == defaultCode; }

    /** "W", "LSHIFT", "Button 1"... as the Controls screen shows it. */
    public static String displayName(int code) {
        if (KeyCodes.isMouse(code)) return I18n.format("key.mouseButton", code + 101);
        String name = KeyCodes.legacyName(code);
        return name != null ? name : "NONE";
    }

    /**
     * VERIFIED_HISTORY-shaped ordering (HYPOTHESIS until screenshot-compared):
     * category first, then description, both compared by their translated text.
     */
    @Override
    public int compareTo(KeyBinding other) {
        int byCategory = I18n.format(category).compareTo(I18n.format(other.category));
        return byCategory != 0 ? byCategory : I18n.format(description).compareTo(I18n.format(other.description));
    }
}

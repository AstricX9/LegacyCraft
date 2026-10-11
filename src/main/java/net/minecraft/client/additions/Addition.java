package net.minecraft.client.additions;

import java.util.List;
import java.util.Locale;

/**
 * One optional change to the 1.8.9 experience, defined by its own file in
 * {@code assets/legacycraft/additions/} ({@code _template.json} there shows
 * every field). Game additions are switched per instance (in the game and in
 * the launcher); launcher additions only change the launcher and are listed
 * here so the definitions stay in one place. {@code defaultOn} is whether it
 * starts switched on; {@code settings} are its own options, shown behind the
 * settings button on its row.
 */
public record Addition(String id, Category category, Scope scope, boolean defaultOn, String name, String description,
                       List<Setting> settings) {
    public Addition {
        settings = List.copyOf(settings);
    }

    public Setting setting(String key) {
        for (Setting s : settings) if (s.id().equals(key)) return s;
        return null;
    }

    /** The groups the Additions screens show, in order. */
    public enum Category {
        GAMEPLAY("Gameplay"), GRAPHICS("Graphics"), INTERFACE("Interface"), ACCESSIBILITY("Accessibility"), LAUNCHER("Launcher");

        public final String title;

        Category(String title) { this.title = title; }

        static Category byId(String id) { return valueOf(id.toUpperCase(Locale.ROOT)); }
    }

    public enum Scope { GAME, LAUNCHER }

    /**
     * One option of an addition, saved in additions.txt as
     * {@code <addition>.<setting>:<value>}. A toggle is true/false; a slider a
     * number from min to max in steps; a choice one of its options; a file
     * the name of a file in {@code folder} under the game folder (empty for
     * none) with one of the extensions.
     */
    public record Setting(String id, Type type, String name, String description, String defaultValue, List<String> options,
                          double min, double max, double step, String unit, String folder, List<String> extensions) {
        public enum Type { TOGGLE, SLIDER, CHOICE, FILE }

        public Setting {
            options = List.copyOf(options);
            extensions = List.copyOf(extensions);
        }

        /** {@code value} made valid for this setting; anything it can't use becomes the default. */
        public String clean(String value) {
            if (value == null) return defaultValue;
            switch (type) {
                case TOGGLE: return value.equals("true") || value.equals("false") ? value : defaultValue;
                case SLIDER:
                    try { return number(snap(Double.parseDouble(value))); }
                    catch (NumberFormatException e) { return defaultValue; }
                case CHOICE: return options.contains(value) ? value : defaultValue;
                default: return value.contains("/") || value.contains("\\") || value.contains("..") ? defaultValue : value;
            }
        }

        /** Whether {@code value} can be used (a slider takes any number and snaps it). */
        public boolean accepts(String value) {
            if (value == null) return false;
            if (type != Type.SLIDER) return clean(value).equals(value);
            try { return !Double.isNaN(Double.parseDouble(value)); }
            catch (NumberFormatException e) { return false; }
        }

        /** The nearest allowed slider value. */
        public double snap(double value) {
            double v = Math.max(min, Math.min(max, value));
            if (step > 0) v = Math.min(max, min + Math.round((v - min) / step) * step);
            return v;
        }

        /** A slider value as additions.txt stores it: no ".0" on whole numbers. */
        public String number(double value) {
            if (value == Math.rint(value)) return Long.toString((long) value);
            return Double.toString(Math.round(value * 1e6) / 1e6);
        }

        /** How a value reads on screen: On/Off, a number with its unit, the option, or the file name. */
        public String display(String value) {
            switch (type) {
                case TOGGLE: return Boolean.parseBoolean(value) ? "ON" : "OFF";
                case SLIDER: return value + unit;
                case FILE: return value.isEmpty() ? "None" : value;
                default: return value;
            }
        }
    }
}

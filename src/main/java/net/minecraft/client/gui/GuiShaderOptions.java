package net.minecraft.client.gui;

import net.minecraft.client.shaders.ShaderPack;
import net.minecraft.client.shaders.Shaders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * A shader pack's own settings, laid out as the pack asks in shaders.properties
 * (OptiFine's Shader Options): {@code screen} lists the first page's options,
 * {@code [NAME]} opens the page {@code screen.NAME}, {@code <profile>} cycles
 * the pack's quality profiles and {@code <empty>} leaves a gap. Names and
 * hover help come from the pack's lang/en_US.lang. Click to go forward
 * through an option's values, right-click to go back. Changes are saved to
 * the pack's .txt file and the pack reloads when the screens close.
 */
public class GuiShaderOptions extends GuiScreen {
    private static final int DONE = 200, RESET = 201, CONTROL = 300;

    private final GuiScreen parent;
    private final ShaderPack pack;
    private final String screen;
    /** Shared by a pack's pages: option name to its current value. */
    private final Map<String, String> values;
    private final Map<String, ShaderPack.Option> options;
    private final Map<String, String> lang;
    private final List<String> entries = new ArrayList<>();
    private final List<GuiButton> controls = new ArrayList<>();
    private final List<String> controlEntries = new ArrayList<>();
    private int scroll, rows, totalRows;

    /** The first page of the pack's options; null if the pack can't be read (reported to the parent's chat). */
    public static GuiShaderOptions open(GuiScreen parent, java.nio.file.Path packFile) {
        try {
            ShaderPack pack = Shaders.readForOptions(packFile);
            Map<String, ShaderPack.Option> options = pack.options();
            Map<String, String> values = new HashMap<>();
            for (ShaderPack.Option o : options.values()) values.put(o.name(), o.defaultValue());
            values.putAll(pack.optionValues());
            return new GuiShaderOptions(parent, pack, null, values, options, pack.lang());
        } catch (IOException | RuntimeException e) {
            System.err.println("Couldn't read shader pack " + packFile + ": " + e.getMessage());
            return null;
        }
    }

    private GuiShaderOptions(GuiScreen parent, ShaderPack pack, String screen, Map<String, String> values,
                             Map<String, ShaderPack.Option> options, Map<String, String> lang) {
        this.parent = parent;
        this.pack = pack;
        this.screen = screen;
        this.values = values;
        this.options = options;
        this.lang = lang;
        String layout = pack.property(screen == null ? "screen" : "screen." + screen);
        if (layout != null) for (String token : layout.trim().split("\\s+")) entries.add(token);
        else if (screen == null) entries.addAll(options.keySet());
    }

    private int columns() {
        String value = pack.property(screen == null ? "screen.columns" : "screen." + screen + ".columns");
        try { return value == null ? 2 : Math.max(1, Math.min(4, Integer.parseInt(value.trim()))); }
        catch (NumberFormatException e) { return 2; }
    }

    @Override
    public void initGui() {
        controls.clear();
        controlEntries.clear();
        int columns = columns(), gap = 4, buttonWidth = Math.min(150, (width - 20) / columns - gap);
        int left = width / 2 - (columns * (buttonWidth + gap) - gap) / 2, top = 28;
        rows = Math.max(1, (height - 46 - top) / 22);
        totalRows = (entries.size() + columns - 1) / columns;
        scroll = Math.max(0, Math.min(scroll, totalRows - rows));
        for (int i = 0; i < entries.size(); i++) {
            int row = i / columns - scroll;
            if (row < 0 || row >= rows) continue;
            String entry = entries.get(i);
            if (entry.equals("<empty>") || entry.equals("*")) continue;
            ShaderPack.Option option = options.get(entry);
            if (!entry.equals("<profile>") && !entry.startsWith("[") && option == null) continue;
            int x = left + (i % columns) * (buttonWidth + gap), y = top + row * 22;
            GuiButton button = option != null && isSlider(entry) && option.values().size() > 1
                ? slider(CONTROL + controls.size(), x, y, buttonWidth, option)
                : new GuiButton(CONTROL + controls.size(), x, y, buttonWidth, 20, "");
            controls.add(button);
            controlEntries.add(entry);
            buttonList.add(button);
        }
        buttonList.add(new GuiButton(RESET, width / 2 - 155, height - 28, 150, 20, "Reset to Defaults"));
        buttonList.add(new GuiButton(DONE, width / 2 + 5, height - 28, 150, 20, I18n.format("gui.done")));
        refresh();
    }

    private boolean isSlider(String name) {
        String sliders = pack.property("sliders");
        return sliders != null && List.of(sliders.trim().split("\\s+")).contains(name);
    }

    private GuiSlider slider(int id, int x, int y, int width, ShaderPack.Option option) {
        List<String> choices = option.values();
        int steps = choices.size() - 1;
        int at = Math.max(0, choices.indexOf(values.get(option.name())));
        return new GuiSlider(id, x, y, width, 20, at / (float) steps,
            p -> Math.round(p * steps) / (double) steps,
            p -> values.put(option.name(), choices.get((int) Math.round(p * steps))),
            p -> label(option.name()) + ": " + valueLabel(option, choices.get((int) Math.round(p * steps))));
    }

    /** Captions for the buttons (sliders keep their own). */
    private void refresh() {
        for (int i = 0; i < controls.size(); i++) {
            GuiButton b = controls.get(i);
            if (b instanceof GuiSlider) continue;
            String entry = controlEntries.get(i);
            String text;
            if (entry.equals("<profile>")) text = "Profile: " + profileLabel(currentProfile());
            else if (entry.startsWith("[")) text = screenLabel(entry.substring(1, entry.length() - 1)) + "...";
            else text = label(entry) + ": " + valueLabel(options.get(entry), values.get(entry));
            b.displayString = fit(text, b.width - 10);
        }
    }

    private String label(String option) { return lang.getOrDefault("option." + option, option); }

    private String screenLabel(String name) { return lang.getOrDefault("screen." + name, name); }

    private String valueLabel(ShaderPack.Option option, String value) {
        String named = lang.get("value." + option.name() + "." + value);
        if (named != null) return named;
        if (option.toggle()) return value.equals("true") ? "§aON" : "§cOFF";
        return value;
    }

    private String profileLabel(String profile) {
        return profile == null ? "Custom" : lang.getOrDefault("profile." + profile, profile);
    }

    private List<String> profiles() {
        List<String> names = new ArrayList<>();
        for (String key : pack.properties().keySet()) if (key.startsWith("profile.")) names.add(key.substring(8));
        return names;
    }

    /** The profile every one of whose settings matches the current values, or null. */
    private String currentProfile() {
        for (String profile : profiles()) {
            Map<String, String> settings = new HashMap<>();
            profileSettings(profile, settings, 0);
            boolean match = !settings.isEmpty();
            for (Map.Entry<String, String> e : settings.entrySet()) match &= e.getValue().equals(values.get(e.getKey()));
            if (match) return profile;
        }
        return null;
    }

    /** OptiFine profile lines: {@code A=1 B !C profile.OTHER} (B on, C off, OTHER's settings too). */
    private void profileSettings(String profile, Map<String, String> out, int depth) {
        String line = pack.property("profile." + profile);
        if (line == null || depth > 8) return;
        for (String token : line.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            if (token.startsWith("profile.")) profileSettings(token.substring(8), out, depth + 1);
            else if (token.startsWith("!")) out.put(token.substring(1), "false");
            else if (token.contains("=")) out.put(token.substring(0, token.indexOf('=')), token.substring(token.indexOf('=') + 1));
            else out.put(token, "true");
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 1) {
            for (int i = 0; i < controls.size(); i++) {
                GuiButton b = controls.get(i);
                if (!(b instanceof GuiSlider) && b.mousePressed(mc, mouseX, mouseY)) {
                    b.playPressSound(mc);
                    step(controlEntries.get(i), -1);
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        scroll -= Integer.signum(direction);
        buttonList.clear();
        initGui();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE) {
            close();
        } else if (button.id == RESET) {
            for (ShaderPack.Option o : options.values()) values.put(o.name(), o.defaultValue());
            buttonList.clear();
            initGui();
        } else if (button.id >= CONTROL && !(button instanceof GuiSlider)) {
            String entry = controlEntries.get(button.id - CONTROL);
            if (entry.startsWith("[")) {
                mc.displayGuiScreen(new GuiShaderOptions(this, pack, entry.substring(1, entry.length() - 1), values, options, lang));
            } else {
                step(entry, 1);
            }
        }
    }

    /** Moves an option (or the profile) one value forward or back, wrapping round. */
    private void step(String entry, int direction) {
        if (entry.startsWith("[")) return;
        if (entry.equals("<profile>")) {
            List<String> profiles = profiles();
            if (profiles.isEmpty()) return;
            int at = profiles.indexOf(currentProfile());
            int next = at < 0 ? (direction > 0 ? 0 : profiles.size() - 1) : Math.floorMod(at + direction, profiles.size());
            Map<String, String> settings = new HashMap<>();
            profileSettings(profiles.get(next), settings, 0);
            settings.keySet().retainAll(options.keySet());
            values.putAll(settings);
        } else {
            List<String> choices = options.get(entry).values();
            int at = choices.indexOf(values.get(entry));
            values.put(entry, choices.get(Math.floorMod(at + direction, choices.size())));
        }
        refresh();
    }

    /** {@code text}, cut short with "..." if it is wider than {@code width}. */
    private String fit(String text, int width) {
        if (fontRendererObj.getStringWidth(text) <= width) return text;
        while (!text.isEmpty() && fontRendererObj.getStringWidth(text + "...") > width) text = text.substring(0, text.length() - 1);
        return text + "...";
    }

    private void close() {
        if (screen == null) {
            try {
                pack.saveOptions(values);
                Shaders.reload();
            } catch (IOException e) {
                System.err.println("Couldn't save the shader pack's options: " + e.getMessage());
            }
        }
        mc.displayGuiScreen(parent);
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (key == GLFW_KEY_ESCAPE) close();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, screen == null ? pack.name : screenLabel(screen), width / 2, 15, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (totalRows > rows)
            drawCenteredString(fontRendererObj, "Scroll for more (" + (scroll + 1) + "-" + Math.min(totalRows, scroll + rows) + " of " + totalRows + " rows)",
                width / 2, height - 42, 0xA0A0A0);
        for (int i = 0; i < controls.size(); i++) {
            if (!controls.get(i).isMouseOver()) continue;
            String entry = controlEntries.get(i);
            String key = entry.equals("<profile>") ? "profile.comment"
                : entry.startsWith("[") ? "screen." + entry.substring(1, entry.length() - 1) + ".comment" : "option." + entry + ".comment";
            String help = lang.get(key);
            if (help != null) drawHoveringText(fontRendererObj.listFormattedStringToWidth(help, 250), mouseX, mouseY);
        }
    }
}

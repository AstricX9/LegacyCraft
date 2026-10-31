package net.minecraft.client.gui;

import net.minecraft.client.additions.Addition;
import net.minecraft.client.additions.Additions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * One addition's settings (DEV-041), opened with the settings button on its
 * row in {@link GuiAdditions}. Laid out like 1.8.9's options screens: two
 * columns of 150-wide buttons, a slider for each number, a button that
 * cycles for each switch, choice and file. Hovering a setting shows what it
 * does. Changes apply at once and are saved to additions.txt when the screen
 * closes, where the launcher shows them too.
 */
public class GuiAdditionSettings extends GuiScreen {
    private static final int DONE = 200, RESET = 201, FOLDER = 202, SHADER_OPTIONS = 203, SETTING = 300;

    private final GuiScreen parent;
    private final Addition addition;
    private Additions additions;
    private final List<GuiButton> controls = new ArrayList<>();
    private List<String> description = List.of();

    public GuiAdditionSettings(GuiScreen parent, Addition addition) {
        this.parent = parent;
        this.addition = addition;
    }

    @Override
    public void initGui() {
        additions = mc.additions();
        description = fontRendererObj.listFormattedStringToWidth(addition.description(), 300);
        controls.clear();
        int top = 34 + description.size() * 10 + 8;
        List<Addition.Setting> settings = addition.settings();
        for (int i = 0; i < settings.size(); i++) {
            Addition.Setting s = settings.get(i);
            int x = width / 2 - 155 + (i % 2) * 160, y = top + (i / 2) * 24;
            GuiButton b = s.type() == Addition.Setting.Type.SLIDER ? slider(SETTING + i, x, y, s)
                : new GuiButton(SETTING + i, x, y, 150, 20, "");
            controls.add(b);
            buttonList.add(b);
        }
        if (addition.id().equals(Additions.SHADER_PACKS)) {
            int i = settings.size();
            GuiButton options = new GuiButton(SHADER_OPTIONS, width / 2 - 155 + (i % 2) * 160, top + (i / 2) * 24, 150, 20, "Shader Options...");
            options.enabled = shaderPack() != null;
            buttonList.add(options);
        }
        boolean files = settings.stream().anyMatch(s -> s.type() == Addition.Setting.Type.FILE);
        if (files) {
            buttonList.add(new GuiButton(FOLDER, width / 2 - 155, height - 52, 150, 20, "Open Folder"));
            buttonList.add(new GuiButton(RESET, width / 2 + 5, height - 52, 150, 20, "Reset to Defaults"));
        } else {
            buttonList.add(new GuiButton(RESET, width / 2 - 100, height - 52, "Reset to Defaults"));
        }
        buttonList.add(new GuiButton(DONE, width / 2 - 100, height - 28, I18n.format("gui.done")));
        refresh();
    }

    private GuiSlider slider(int id, int x, int y, Addition.Setting s) {
        double range = s.max() - s.min();
        float position = range <= 0 ? 0 : (float) ((additions.number(addition.id(), s.id()) - s.min()) / range);
        return new GuiSlider(id, x, y, 150, 20, position,
            p -> range <= 0 ? 0 : (s.snap(s.min() + p * range) - s.min()) / range,
            p -> additions.setSetting(addition.id(), s.id(), s.number(s.snap(s.min() + p * range))),
            p -> s.name() + ": " + s.display(s.number(s.snap(s.min() + p * range))));
    }

    /** Captions for the buttons (sliders keep their own). */
    private void refresh() {
        for (int i = 0; i < controls.size(); i++) {
            Addition.Setting s = addition.settings().get(i);
            if (s.type() != Addition.Setting.Type.SLIDER)
                controls.get(i).displayString = fit(s.name() + ": " + s.display(additions.setting(addition.id(), s.id())), 140);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE) {
            close();
        } else if (button.id == RESET) {
            additions.resetSettings(addition.id());
            buttonList.clear();
            initGui();
        } else if (button.id == FOLDER) {
            openFolder();
        } else if (button.id == SHADER_OPTIONS) {
            GuiShaderOptions options = shaderPack() == null ? null : GuiShaderOptions.open(this, shaderPack());
            if (options != null) mc.displayGuiScreen(options);
        } else if (button.id >= SETTING) {
            Addition.Setting s = addition.settings().get(button.id - SETTING);
            String value = additions.setting(addition.id(), s.id());
            switch (s.type()) {
                case TOGGLE -> additions.setSetting(addition.id(), s.id(), Boolean.toString(!Boolean.parseBoolean(value)));
                case CHOICE -> additions.setSetting(addition.id(), s.id(), next(s.options(), value));
                case FILE -> {
                    List<String> choices = new ArrayList<>();
                    choices.add("");
                    choices.addAll(additions.files(s));
                    additions.setSetting(addition.id(), s.id(), next(choices, value));
                }
                default -> {}
            }
            refresh();
            for (GuiButton b : buttonList) if (b.id == SHADER_OPTIONS) b.enabled = shaderPack() != null;
        }
    }

    /** The Shader Packs addition's picked pack file, or null. */
    private java.nio.file.Path shaderPack() {
        Addition.Setting s = addition.setting("pack");
        String name = s == null ? "" : additions.setting(addition.id(), s.id());
        return name == null || name.isEmpty() ? null : additions.folder(s).resolve(name);
    }

    /** {@code text}, cut short with "..." if it is wider than {@code width}. */
    private String fit(String text, int width) {
        if (fontRendererObj.getStringWidth(text) <= width) return text;
        while (!text.isEmpty() && fontRendererObj.getStringWidth(text + "...") > width) text = text.substring(0, text.length() - 1);
        return text + "...";
    }

    /** The option after {@code value}, wrapping round. */
    static String next(List<String> options, String value) {
        if (options.isEmpty()) return value;
        int i = options.indexOf(value);
        return options.get((i + 1) % options.size());
    }

    /** Opens the first file setting's folder, making it if needed. */
    private void openFolder() {
        for (Addition.Setting s : addition.settings()) {
            if (s.type() != Addition.Setting.Type.FILE) continue;
            Path folder = additions.folder(s);
            try {
                Files.createDirectories(folder);
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(folder.toFile());
            } catch (Exception e) {
                System.err.println("Couldn't open " + folder + ": " + e.getMessage());
            }
            return;
        }
    }

    private void close() {
        additions.save();
        mc.displayGuiScreen(parent);
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (key == GLFW_KEY_ESCAPE) close();
    }

    @Override
    public void onGuiClosed() { additions.save(); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, addition.name() + " Settings", width / 2, 15, 0xFFFFFF);
        for (int i = 0; i < description.size(); i++)
            drawCenteredString(fontRendererObj, description.get(i), width / 2, 32 + i * 10, 0xB0B0B0);
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (int i = 0; i < controls.size(); i++) {
            GuiButton b = controls.get(i);
            String text = addition.settings().get(i).description();
            if (b.isMouseOver() && !text.isEmpty())
                drawHoveringText(fontRendererObj.listFormattedStringToWidth(text, 200), mouseX, mouseY);
        }
    }
}

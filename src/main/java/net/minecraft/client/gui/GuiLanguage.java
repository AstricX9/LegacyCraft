package net.minecraft.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * "Language...": every language the resource pack declares in its
 * {@code pack.mcmeta} "language" section, shown as "Name (Region)", plus the
 * Force Unicode Font toggle and the translation-accuracy warning. Choosing a
 * language reloads the lang files immediately, as in vanilla.
 *
 * <p>LegacyCraft's font renderer has no Unicode glyph pages yet (DEV-010), so
 * languages outside Latin-1 display '?' for unsupported characters.
 */
public class GuiLanguage extends GuiScreen {
    public record Language(String code, String name, String region) {
        @Override public String toString() { return name + " (" + region + ")"; }
    }

    private final GuiScreen parent;
    private final List<Language> languages = new ArrayList<>();
    private LanguageList list;

    public GuiLanguage(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        if (languages.isEmpty()) loadLanguages();
        buttonList.add(new GuiOptionButton(100, width / 2 - 155, height - 38, GameSettings.Options.FORCE_UNICODE_FONT,
            mc.settings().getKeyBinding(GameSettings.Options.FORCE_UNICODE_FONT)));
        buttonList.add(new GuiButton(6, width / 2 - 155 + 160, height - 38, 150, 20, I18n.format("gui.done")));
        list = new LanguageList();
    }

    private void loadLanguages() {
        try (InputStream stream = GuiLanguage.class.getResourceAsStream("/assets/pack.mcmeta")) {
            if (stream != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject declared = root.getAsJsonObject("language");
                if (declared != null) {
                    for (var entry : declared.entrySet()) {
                        JsonObject value = entry.getValue().getAsJsonObject();
                        languages.add(new Language(entry.getKey(), value.get("name").getAsString(), value.get("region").getAsString()));
                    }
                }
            }
        } catch (Exception unreadable) {
            System.err.println("Couldn't read the language list: " + unreadable.getMessage());
        }
        if (languages.isEmpty()) {
            try {
                for (ResourceLocation file : mc.resources().list(new ResourceLocation("minecraft", "lang"), ".lang")) {
                    String path = file.path();
                    String code = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
                    languages.add(new Language(code, code, ""));
                }
            } catch (Exception ignored) {
                languages.add(new Language("en_US", "English", "US"));
            }
        }
        languages.sort((a, b) -> a.code().compareTo(b.code()));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 6) { mc.saveOptions(); mc.displayGuiScreen(parent); return; }
        if (button instanceof GuiOptionButton option) {
            option.cycle(mc.settings());
            mc.saveOptions();
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (list.mouseClicked(mouseX, mouseY, button)) return;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void mouseReleased(int mouseX, int mouseY, int button) { list.mouseReleased(); super.mouseReleased(mouseX, mouseY, button); }
    @Override public void mouseDragged(int mouseX, int mouseY, int button, long time) { list.mouseDragged(mouseX, mouseY); }
    @Override public void mouseScrolled(int mouseX, int mouseY, int direction) { list.mouseScrolled(direction); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, I18n.format("options.language"), width / 2, 16, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "(" + I18n.format("options.languageWarning") + ")", width / 2, height - 56, 0x808080);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private final class LanguageList extends GuiSlot {
        LanguageList() { super(GuiLanguage.this.mc, GuiLanguage.this.width, GuiLanguage.this.height, 32, GuiLanguage.this.height - 65 + 4, 18); }

        @Override protected int getSize() { return languages.size(); }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            Language language = languages.get(index);
            if (language.code().equals(mc.settings().language)) return;
            mc.settings().language = language.code();
            mc.refreshLanguage();
            mc.saveOptions();
            // Re-lay the screen so every caption is in the new language.
            mc.displayGuiScreen(GuiLanguage.this);
        }

        @Override protected boolean isSelected(int index) { return languages.get(index).code().equals(mc.settings().language); }

        @Override protected int getContentHeight() { return getSize() * 18; }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            drawCenteredString(fontRendererObj, languages.get(index).toString(), width / 2, y + 1, 0xFFFFFF);
        }
    }
}

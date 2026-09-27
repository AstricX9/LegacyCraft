package net.minecraft.client.gui;

import net.minecraft.client.settings.GameSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Snooper Settings": the vanilla explanation, the Allow Snooper toggle and a
 * list of the machine facts the snooper reports. LegacyCraft never sends
 * anything anywhere — the toggle is stored for options.txt compatibility and
 * the list only shows what vanilla would have collected (DEV-010).
 */
public class GuiSnooper extends GuiScreen {
    private final GuiScreen parent;
    private final List<String> description = new ArrayList<>();
    private final List<String[]> facts = new ArrayList<>();
    private GuiButton toggle;
    private FactList list;

    public GuiSnooper(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        description.clear();
        description.addAll(fontRendererObj.listFormattedStringToWidth(I18n.format("options.snooper.desc"), width - 30));
        facts.clear();
        for (var fact : machineFacts().entrySet()) facts.add(new String[]{fact.getKey(), fact.getValue()});
        buttonList.add(toggle = new GuiButton(1, width / 2 - 152, height - 30, 150, 20,
            mc.settings().getKeyBinding(GameSettings.Options.SNOOPER_ENABLED)));
        buttonList.add(new GuiButton(2, width / 2 + 2, height - 30, 150, 20, I18n.format("gui.done")));
        list = new FactList();
    }

    private static Map<String, String> machineFacts() {
        Map<String, String> facts = new LinkedHashMap<>();
        Runtime runtime = Runtime.getRuntime();
        facts.put("os_name", System.getProperty("os.name"));
        facts.put("os_version", System.getProperty("os.version"));
        facts.put("os_architecture", System.getProperty("os.arch"));
        facts.put("java_version", System.getProperty("java.version"));
        facts.put("memory_total", String.valueOf(runtime.totalMemory()));
        facts.put("memory_max", String.valueOf(runtime.maxMemory()));
        facts.put("memory_free", String.valueOf(runtime.freeMemory()));
        facts.put("cpu_cores", String.valueOf(runtime.availableProcessors()));
        return facts;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) { mc.saveOptions(); mc.displayGuiScreen(parent); }
        if (button.id == 1) {
            mc.settings().setOptionValue(GameSettings.Options.SNOOPER_ENABLED, 1);
            toggle.displayString = mc.settings().getKeyBinding(GameSettings.Options.SNOOPER_ENABLED);
            mc.saveOptions();
        }
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) { list.mouseScrolled(direction); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, I18n.format("options.snooper.title"), width / 2, 8, 0xFFFFFF);
        int y = 22;
        for (String line : description) {
            drawCenteredString(fontRendererObj, line, width / 2, y, 0x808080);
            y += fontRendererObj == null ? 9 : FontRenderer.FONT_HEIGHT;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private final class FactList extends GuiSlot {
        FactList() { super(GuiSnooper.this.mc, GuiSnooper.this.width, GuiSnooper.this.height, 80, GuiSnooper.this.height - 40,
            FontRenderer.FONT_HEIGHT + 1); }

        @Override protected int getSize() { return facts.size(); }
        @Override protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {}
        @Override protected boolean isSelected(int index) { return false; }
        @Override protected int getScrollBarX() { return width - 10; }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            fontRendererObj.drawString(facts.get(index)[0], 10, y, 0xFFFFFF);
            fontRendererObj.drawString(facts.get(index)[1], 230, y, 0xFFFFFF);
        }
    }
}

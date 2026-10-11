package net.minecraft.client.gui;

import net.minecraft.client.additions.Addition;
import net.minecraft.client.additions.Additions;

import java.util.ArrayList;
import java.util.List;

/**
 * LegacyCraft's Additions screen (DEV-041), opened from the title screen:
 * the look (Faithful, Recommended or Custom), then a tab per category with a
 * switch for each of its additions; hovering one explains it. Saved to the
 * instance's additions.txt on Done, the same file the launcher edits.
 */
public class GuiAdditions extends GuiScreen {
    public static final String TITLE = "Additions";
    private static final Addition.Category[] TABS = {
        Addition.Category.GAMEPLAY, Addition.Category.GRAPHICS, Addition.Category.INTERFACE, Addition.Category.ACCESSIBILITY};
    private static final int LOOK = 100, DONE = 200, TAB = 300, SWITCH = 400;

    private final GuiScreen parent;
    private Addition.Category tab = Addition.Category.GAMEPLAY;
    private Additions additions;
    private GuiButton look;
    private final List<GuiButton> switches = new ArrayList<>();
    private List<Addition> shown = List.of();

    public GuiAdditions(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        additions = mc.additions();
        buttonList.add(look = new GuiButton(LOOK, width / 2 - 100, 42, ""));
        int tabWidth = 74, left = width / 2 - (TABS.length * tabWidth + (TABS.length - 1) * 4) / 2;
        for (int i = 0; i < TABS.length; i++) {
            GuiButton b = new GuiButton(TAB + i, left + i * (tabWidth + 4), 70, tabWidth, 20, TABS[i].title);
            b.enabled = TABS[i] != tab;
            buttonList.add(b);
        }
        switches.clear();
        shown = additions.game(tab);
        for (int i = 0; i < shown.size(); i++) {
            GuiButton b = new GuiButton(SWITCH + i, width / 2 - 100, 98 + i * 24, "");
            switches.add(b);
            buttonList.add(b);
        }
        buttonList.add(new GuiButton(DONE, width / 2 - 100, height - 27, I18n.format("gui.done")));
        refresh();
    }

    private void refresh() {
        look.displayString = "Look: " + additions.look().title;
        for (int i = 0; i < switches.size(); i++)
            switches.get(i).displayString = shown.get(i).name() + ": " + I18n.format(additions.isOn(shown.get(i).id()) ? "options.on" : "options.off");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE) {
            additions.save();
            mc.displayGuiScreen(parent);
        } else if (button.id == LOOK) {
            additions.apply(additions.look() == Additions.Look.FAITHFUL ? Additions.Look.RECOMMENDED : Additions.Look.FAITHFUL);
            refresh();
        } else if (button.id >= SWITCH) {
            Addition a = shown.get(button.id - SWITCH);
            additions.set(a.id(), !additions.isOn(a.id()));
            refresh();
        } else if (button.id >= TAB) {
            tab = TABS[button.id - TAB];
            buttonList.clear();
            initGui();
        }
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) actionPerformed(buttonList.get(buttonList.size() - 1));
    }

    @Override
    public void onGuiClosed() { additions.save(); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, TITLE, width / 2, 15, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Choose how faithful LegacyCraft looks. Faithful is plain 1.8.9.", width / 2, 28, 0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
        String help = null;
        if (look.isMouseOver()) help = "Faithful turns every addition off; Recommended turns on a few that don't change how the game looks. Changing any switch makes it Custom.";
        for (int i = 0; i < switches.size(); i++) if (switches.get(i).isMouseOver()) help = shown.get(i).description();
        if (help == null && shown.isEmpty()) help = "Nothing here yet.";
        if (help != null) fontRendererObj.drawSplitString(help, width / 2 - 150, height - 64, 300, 0xA0A0A0);
    }
}

package net.minecraft.client.gui;

import net.minecraft.client.additions.Addition;
import net.minecraft.client.additions.Additions;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * LegacyCraft's Additions screen (DEV-041), opened from the title screen.
 * Top to bottom, in one 308-wide column: the three looks (Faithful,
 * Recommended, Custom) with a line saying what the chosen one means, a tab
 * per category, then a panel listing that category's additions, each with
 * its name, its description and an ON/OFF switch on the right. Saved to the
 * instance's additions.txt on Done, the same file the launcher edits.
 */
public class GuiAdditions extends GuiScreen {
    public static final String TITLE = "Additions";
    private static final Addition.Category[] TABS = {
        Addition.Category.GAMEPLAY, Addition.Category.GRAPHICS, Addition.Category.INTERFACE, Addition.Category.ACCESSIBILITY};
    private static final Additions.Look[] LOOKS = Additions.Look.values();
    private static final String[] LOOK_HELP = {
        "Plain 1.8.9: every addition is off.",
        "A few additions that make 1.8.9 easier without changing its look.",
        "Your own mix. Switch additions below to make one."};
    private static final int LOOK = 100, DONE = 200, TAB = 300, SWITCH = 400;
    private static final int COLUMN = 308, SWITCH_WIDTH = 44, PAD = 6;
    private static final int ON_COLOUR = 0x80FF80, NAME_COLOUR = 0xFFFFFF, TEXT_COLOUR = 0xB0B0B0, TAG_COLOUR = 0xE0C060;

    private final GuiScreen parent;
    private Addition.Category tab = Addition.Category.GAMEPLAY;
    private Additions additions;
    private final List<GuiButton> looks = new ArrayList<>(), tabs = new ArrayList<>(), switches = new ArrayList<>();
    private List<Addition> shown = List.of();
    /** Each shown addition's description, wrapped to the space beside its switch. */
    private final List<List<String>> descriptions = new ArrayList<>();
    private int left, panelTop, panelBottom, firstRow;

    public GuiAdditions(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        additions = mc.additions();
        left = width / 2 - COLUMN / 2;
        int lookWidth = (COLUMN - 2 * 4) / 3;
        looks.clear();
        for (int i = 0; i < LOOKS.length; i++) {
            GuiButton b = new GuiButton(LOOK + i, left + i * (lookWidth + 4), 22, lookWidth, 20, LOOKS[i].title);
            looks.add(b);
            buttonList.add(b);
        }
        int tabWidth = (COLUMN - (TABS.length - 1) * 4) / TABS.length;
        tabs.clear();
        for (int i = 0; i < TABS.length; i++) {
            GuiButton b = new GuiButton(TAB + i, left + i * (tabWidth + 4), 60, tabWidth, 20, TABS[i].title);
            tabs.add(b);
            buttonList.add(b);
        }
        panelTop = 84;
        panelBottom = height - 32;
        switches.clear();
        descriptions.clear();
        shown = additions.game(tab);
        for (int i = 0; i < shown.size(); i++) {
            GuiButton b = new GuiButton(SWITCH + i, left + COLUMN - SWITCH_WIDTH - PAD, 0, SWITCH_WIDTH, 20, "");
            switches.add(b);
            buttonList.add(b);
            descriptions.add(fontRendererObj.listFormattedStringToWidth(shown.get(i).description(), textWidth()));
        }
        firstRow = Math.min(firstRow, Math.max(0, shown.size() - 1));
        buttonList.add(new GuiButton(DONE, width / 2 - 100, height - 26, I18n.format("gui.done")));
        refresh();
    }

    private int textWidth() { return COLUMN - SWITCH_WIDTH - 3 * PAD; }

    /** Where rows must end: the panel's bottom, less a line for "Scroll for more" when they don't all fit. */
    private int listBottom() {
        int total = 0;
        for (int i = 0; i < shown.size(); i++) total += rowHeight(i);
        return total > panelBottom - panelTop ? panelBottom - 12 : panelBottom;
    }

    private int rowHeight(int i) { return Math.max(20, 10 + descriptions.get(i).size() * 9) + 2 * PAD; }

    /** Captions, which look and tab are picked (shown as pressed), and where each switch sits after scrolling. */
    private void refresh() {
        Additions.Look current = additions.look();
        for (int i = 0; i < looks.size(); i++) looks.get(i).enabled = LOOKS[i] != current && LOOKS[i] != Additions.Look.CUSTOM;
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).enabled = TABS[i] != tab;
            int on = 0;
            for (Addition a : additions.game(TABS[i])) if (additions.isOn(a.id())) on++;
            tabs.get(i).displayString = TABS[i].title + (on > 0 ? " (" + on + ")" : "");
        }
        int y = panelTop, bottom = listBottom();
        for (int i = 0; i < switches.size(); i++) {
            GuiButton b = switches.get(i);
            boolean on = additions.isOn(shown.get(i).id());
            b.displayString = on ? "ON" : "OFF";
            b.packedFGColour = on ? ON_COLOUR : 0;
            int h = i < firstRow ? 0 : rowHeight(i);
            b.visible = i >= firstRow && y + h <= bottom;
            b.yPosition = y + (h - 20) / 2;
            y += h;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE) {
            additions.save();
            mc.displayGuiScreen(parent);
        } else if (button.id >= SWITCH) {
            Addition a = shown.get(button.id - SWITCH);
            additions.set(a.id(), !additions.isOn(a.id()));
            refresh();
        } else if (button.id >= TAB) {
            tab = TABS[button.id - TAB];
            firstRow = 0;
            buttonList.clear();
            initGui();
        } else if (button.id >= LOOK) {
            additions.apply(LOOKS[button.id - LOOK]);
            refresh();
        }
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        firstRow = Math.max(0, Math.min(shown.size() - 1, firstRow - Integer.signum(direction)));
        refresh();
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (key == GLFW_KEY_ESCAPE) { additions.save(); mc.displayGuiScreen(parent); }
    }

    @Override
    public void onGuiClosed() { additions.save(); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, TITLE, width / 2, 8, NAME_COLOUR);
        drawCenteredString(fontRendererObj, LOOK_HELP[additions.look().ordinal()], width / 2, 47, TEXT_COLOUR);

        // The list panel: darker than the background, a line between rows.
        drawRect(left, panelTop, left + COLUMN, panelBottom, 0x90000000);
        int y = panelTop, bottom = listBottom();
        for (int i = firstRow; i < shown.size(); i++) {
            int h = rowHeight(i);
            if (y + h > bottom) break;
            Addition a = shown.get(i);
            int textTop = y + (h - (10 + descriptions.get(i).size() * 9)) / 2;
            drawString(fontRendererObj, a.name(), left + PAD, textTop, NAME_COLOUR);
            if (a.recommended())
                drawString(fontRendererObj, "Recommended", left + PAD + fontRendererObj.getStringWidth(a.name()) + 6, textTop, TAG_COLOUR);
            for (int line = 0; line < descriptions.get(i).size(); line++)
                drawString(fontRendererObj, descriptions.get(i).get(line), left + PAD, textTop + 11 + line * 9, TEXT_COLOUR);
            y += h;
            if (i + 1 < shown.size() && y + rowHeight(i + 1) <= bottom) drawRect(left + PAD, y, left + COLUMN - PAD, y + 1, 0x40FFFFFF);
        }
        if (shown.isEmpty()) drawCenteredString(fontRendererObj, "Nothing here yet.", width / 2, panelTop + 12, TEXT_COLOUR);
        if (bottom < panelBottom) drawCenteredString(fontRendererObj, "Scroll for more", width / 2, panelBottom - 10, TEXT_COLOUR);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

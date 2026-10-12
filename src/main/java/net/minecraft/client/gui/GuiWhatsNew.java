package net.minecraft.client.gui;

import net.minecraft.client.additions.Addition;
import net.minecraft.client.additions.Additions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Shows game additions that have not yet been announced for this game folder. */
final class GuiWhatsNew extends GuiScreen {
    private static final String SEEN_FILE = ".legacycraft-seen-additions";
    private static final int COLUMN = 308, PAD = 6;
    private final GuiScreen parent;
    private final List<Addition> fresh;
    private List<List<String>> descriptions = List.of();
    private int left, panelTop, panelBottom, firstRow;

    private GuiWhatsNew(GuiScreen parent, List<Addition> fresh) {
        this.parent = parent;
        this.fresh = fresh;
    }

    static GuiScreen forNewAdditions(GuiScreen parent, GuiContext context) {
        Set<String> seen = readSeen(context.gameDirectory());
        List<Addition> fresh = new ArrayList<>();
        for (Addition addition : context.additions().game()) {
            if (!seen.contains(addition.id())) fresh.add(addition);
        }
        if (fresh.isEmpty()) return null;
        seen.addAll(fresh.stream().map(Addition::id).toList());
        writeSeen(context.gameDirectory(), seen);
        return new GuiWhatsNew(parent, fresh);
    }

    private static Set<String> readSeen(Path gameDirectory) {
        Path file = gameDirectory.resolve(SEEN_FILE);
        try {
            return new LinkedHashSet<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new LinkedHashSet<>();
        }
    }

    private static void writeSeen(Path gameDirectory, Set<String> seen) {
        try {
            Files.write(gameDirectory.resolve(SEEN_FILE), seen, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Couldn't save " + SEEN_FILE + ": " + e.getMessage());
        }
    }

    @Override
    public void initGui() {
        left = width / 2 - COLUMN / 2;
        panelTop = 34;
        panelBottom = height - 40;
        descriptions = new ArrayList<>();
        for (Addition addition : fresh)
            descriptions.add(fontRendererObj.listFormattedStringToWidth(addition.description(), COLUMN - 2 * PAD));
        firstRow = Math.min(firstRow, Math.max(0, fresh.size() - 1));
        buttonList.add(new GuiButton(0, width / 2 - 100, height - 30, 200, 20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) mc.displayGuiScreen(parent);
    }

    private int rowHeight(int index) {
        return 10 + (1 + descriptions.get(index).size()) * 9 + 2 * PAD;
    }

    private int listBottom() {
        int total = 0;
        for (int i = 0; i < fresh.size(); i++) total += rowHeight(i);
        return total > panelBottom - panelTop ? panelBottom - 12 : panelBottom;
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        firstRow = Math.max(0, Math.min(fresh.size() - 1, firstRow - Integer.signum(direction)));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "What's New", width / 2, 12, 0xFFFFFF);
        drawRect(left, panelTop, left + COLUMN, panelBottom, 0x90000000);
        int y = panelTop, bottom = listBottom();
        for (int i = firstRow; i < fresh.size(); i++) {
            int h = rowHeight(i);
            if (y + h > bottom) break;
            Addition addition = fresh.get(i);
            int textTop = y + PAD;
            drawString(fontRendererObj, addition.name(), left + PAD, textTop, 0xFFFFFF);
            textTop += 11;
            for (String line : descriptions.get(i)) {
                drawString(fontRendererObj, line, left + PAD, textTop, 0xB0B0B0);
                textTop += 9;
            }
            y += h;
            if (i + 1 < fresh.size() && y + rowHeight(i + 1) <= bottom)
                drawRect(left + PAD, y, left + COLUMN - PAD, y + 1, 0x40FFFFFF);
        }
        if (bottom < panelBottom) drawCenteredString(fontRendererObj, "Scroll for more", width / 2, panelBottom - 10, 0xB0B0B0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

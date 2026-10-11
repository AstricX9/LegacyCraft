package net.minecraft.client.gui;

import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.SaveFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * "Select World" (master context §66): the saves list with display name,
 * "folder (last played)" and game-mode lines, and Play / Create / Rename /
 * Delete / Re-Create / Cancel. Double-clicking a world plays it. Buttons
 * that need a selection stay disabled until one is made.
 */
public class GuiSelectWorld extends GuiScreen {
    private final GuiScreen parent;
    private String screenTitle = "Select World";
    private List<SaveFormat.SaveSummary> saves = new ArrayList<>();
    private int selectedIndex = -1;
    private WorldList list;
    private boolean confirmingDelete;
    private boolean launching;
    private GuiButton selectButton, renameButton, deleteButton, recreateButton;

    public GuiSelectWorld(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        screenTitle = I18n.format("selectWorld.title");
        loadSaves();
        list = new WorldList();
        buttonList.add(selectButton = new GuiButton(1, width / 2 - 154, height - 52, 150, 20, I18n.format("selectWorld.select")));
        buttonList.add(new GuiButton(3, width / 2 + 4, height - 52, 150, 20, I18n.format("selectWorld.create")));
        buttonList.add(renameButton = new GuiButton(6, width / 2 - 154, height - 28, 72, 20, I18n.format("selectWorld.rename")));
        buttonList.add(deleteButton = new GuiButton(2, width / 2 - 76, height - 28, 72, 20, I18n.format("selectWorld.delete")));
        buttonList.add(recreateButton = new GuiButton(7, width / 2 + 4, height - 28, 72, 20, I18n.format("selectWorld.recreate")));
        buttonList.add(new GuiButton(0, width / 2 + 82, height - 28, 72, 20, I18n.format("gui.cancel")));
        updateButtons();
    }

    private void loadSaves() {
        try {
            saves = mc.saves().getSaveList();
        } catch (IOException error) {
            saves = new ArrayList<>();
            mc.displayGuiScreen(new GuiDisconnected(parent, "selectWorld.title", "Couldn't read saves: " + error.getMessage(), "gui.back"));
        }
        if (selectedIndex >= saves.size()) selectedIndex = -1;
    }

    private void updateButtons() {
        boolean selected = selectedIndex >= 0 && selectedIndex < saves.size();
        selectButton.enabled = selected;
        renameButton.enabled = selected;
        deleteButton.enabled = selected;
        recreateButton.enabled = selected;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        switch (button.id) {
            case 0 -> mc.displayGuiScreen(parent);
            case 1 -> selectWorld(selectedIndex);
            case 2 -> {
                String name = saves.get(selectedIndex).displayName();
                confirmingDelete = true;
                mc.displayGuiScreen(new GuiYesNo(this, I18n.format("selectWorld.deleteQuestion"),
                    "'" + name + "' " + I18n.format("selectWorld.deleteWarning"),
                    I18n.format("selectWorld.deleteButton"), I18n.format("gui.cancel"), selectedIndex));
            }
            case 3 -> mc.displayGuiScreen(new GuiCreateWorld(this));
            case 6 -> mc.displayGuiScreen(new GuiRenameWorld(this, saves.get(selectedIndex).fileName()));
            case 7 -> {
                SaveFormat.SaveSummary summary = saves.get(selectedIndex);
                GuiCreateWorld create = new GuiCreateWorld(this);
                create.recreateFrom(summary.displayName(), summary.info().toSettings());
                mc.displayGuiScreen(create);
            }
            default -> {}
        }
    }

    /** Opens a world; vanilla guards against a double launch from a double-click. */
    public void selectWorld(int index) {
        if (launching || index < 0 || index >= saves.size()) return;
        SaveFormat.SaveSummary summary = saves.get(index);
        if (summary.requiresConversion()) {
            launching = true;
            mc.convertAndLaunch(summary.fileName(), summary.displayName());
            return;
        }
        launching = true;
        mc.launchIntegratedServer(summary.fileName(), summary.displayName(), null);
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (confirmingDelete) {
            confirmingDelete = false;
            if (result && id >= 0 && id < saves.size()) {
                mc.saves().deleteWorldDirectory(saves.get(id).fileName());
                selectedIndex = -1;
            }
            mc.displayGuiScreen(this);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, screenTitle, width / 2, 20, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (list.mouseClicked(mouseX, mouseY, button)) return;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        list.mouseReleased();
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseDragged(int mouseX, int mouseY, int button, long time) { list.mouseDragged(mouseX, mouseY); }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) { list.mouseScrolled(direction); }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (key == GLFW_KEY_ENTER && selectedIndex >= 0) { selectWorld(selectedIndex); return; }
        super.keyPressed(key, modifiers);
    }

    private final class WorldList extends GuiSlot {
        WorldList() { super(GuiSelectWorld.this.mc, GuiSelectWorld.this.width, GuiSelectWorld.this.height, 32, GuiSelectWorld.this.height - 64, 36); }

        @Override protected int getSize() { return saves.size(); }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            selectedIndex = index;
            updateButtons();
            if (doubleClick) selectWorld(index);
        }

        @Override protected boolean isSelected(int index) { return index == selectedIndex; }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            SaveFormat.SaveSummary summary = saves.get(index);
            String name = summary.displayName();
            if (name.isEmpty()) name = I18n.format("selectWorld.world") + " " + (index + 1);
            String folder = summary.fileName() + " (" + SaveFormat.formatDate(summary.lastTimePlayed()) + ")";
            String mode;
            if (summary.requiresConversion()) {
                mode = I18n.format("selectWorld.conversion");
            } else {
                WorldSettings.GameType type = summary.info().gameType;
                mode = I18n.format(type.translationKey());
                if (summary.info().hardcore) mode = "§4" + I18n.format("gameMode.hardcore") + "§r";
                if (summary.info().allowCommands) mode += ", " + I18n.format("selectWorld.cheats");
            }
            drawString(fontRendererObj, name, x + 2, y + 1, 0xFFFFFF);
            drawString(fontRendererObj, folder, x + 2, y + 12, 0x808080);
            drawString(fontRendererObj, mode, x + 2, y + 12 + 10, 0x808080);
        }
    }
}

package net.minecraft.client.gui;

import net.minecraft.world.storage.LevelInfo;

import java.io.IOException;

import static org.lwjgl.glfw.GLFW.*;

/** "Rename World": edits level.dat's LevelName only; the folder keeps its name. */
public class GuiRenameWorld extends GuiScreen {
    private final GuiScreen parent;
    private final String folder;
    private GuiTextField nameField;

    public GuiRenameWorld(GuiScreen parent, String folder) {
        this.parent = parent;
        this.folder = folder;
    }

    @Override
    public void updateScreen() { nameField.updateCursorCounter(); }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(0, width / 2 - 100, height / 4 + 96 + 12, I18n.format("selectWorld.renameButton")));
        buttonList.add(new GuiButton(1, width / 2 - 100, height / 4 + 120 + 12, I18n.format("gui.cancel")));
        LevelInfo info = mc.saves().getWorldInfo(folder);
        String name = info == null ? "" : info.levelName;
        String previous = nameField == null ? name : nameField.getText();
        nameField = new GuiTextField(2, fontRendererObj, width / 2 - 100, 60, 200, 20);
        nameField.setClipboard(clipboard(mc));
        nameField.setFocused(true);
        nameField.setText(previous);
        nameField.setOnChange(this::updateButtons);
        updateButtons();
    }

    private void updateButtons() { buttonList.get(0).enabled = !nameField.getText().trim().isEmpty(); }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button.id == 1) { mc.displayGuiScreen(parent); return; }
        if (button.id == 0) {
            try {
                mc.saves().renameWorld(folder, nameField.getText().trim());
            } catch (IOException error) {
                System.err.println("Couldn't rename world: " + error.getMessage());
            }
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (nameField.keyPressed(key, modifiers)) { updateButtons(); return; }
        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) { actionPerformed(buttonList.get(0)); return; }
        if (key == GLFW_KEY_ESCAPE) mc.displayGuiScreen(parent);
    }

    @Override
    public void charTyped(char character) {
        if (nameField.charTyped(character)) updateButtons();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        nameField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("selectWorld.renameTitle"), width / 2, 20, 0xFFFFFF);
        drawString(fontRendererObj, I18n.format("selectWorld.enterName"), width / 2 - 100, 47, 0xA0A0A0);
        nameField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Routes a text field's ctrl+C/V to the system clipboard. */
    static GuiTextField.Clipboard clipboard(GuiContext context) {
        return new GuiTextField.Clipboard() {
            public String get() { return context.getClipboard(); }
            public void set(String value) { context.setClipboard(value); }
        };
    }
}

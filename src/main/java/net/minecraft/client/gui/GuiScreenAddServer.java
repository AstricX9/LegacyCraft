package net.minecraft.client.gui;

import net.minecraft.client.multiplayer.ServerData;

import static org.lwjgl.glfw.GLFW.*;

/** "Edit Server Info": name, address and the server resource pack policy. */
public class GuiScreenAddServer extends GuiScreen {
    private final GuiScreen parent;
    private final ServerData server;
    private GuiTextField nameField, ipField;
    private GuiButton resourcePacks;

    public GuiScreenAddServer(GuiScreen parent, ServerData server) {
        this.parent = parent;
        this.server = server;
    }

    @Override
    public void updateScreen() { nameField.updateCursorCounter(); ipField.updateCursorCounter(); }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(0, width / 2 - 100, height / 4 + 96 + 18, I18n.format("addServer.add")));
        buttonList.add(new GuiButton(1, width / 2 - 100, height / 4 + 120 + 18, I18n.format("gui.cancel")));
        buttonList.add(resourcePacks = new GuiButton(2, width / 2 - 100, height / 4 + 72, packCaption()));
        nameField = new GuiTextField(0, fontRendererObj, width / 2 - 100, 66, 200, 20);
        nameField.setClipboard(GuiRenameWorld.clipboard(mc));
        nameField.setFocused(true);
        nameField.setText(server.serverName);
        ipField = new GuiTextField(1, fontRendererObj, width / 2 - 100, 106, 200, 20);
        ipField.setClipboard(GuiRenameWorld.clipboard(mc));
        ipField.setMaxStringLength(128);
        ipField.setText(server.serverIP);
        updateButtons();
    }

    private String packCaption() {
        String mode = switch (server.resourceMode) {
            case ENABLED -> "addServer.resourcePack.enabled";
            case DISABLED -> "addServer.resourcePack.disabled";
            case PROMPT -> "addServer.resourcePack.prompt";
        };
        return I18n.format("addServer.resourcePack") + ": " + I18n.format(mode);
    }

    private void updateButtons() {
        String ip = ipField.getText();
        buttonList.get(0).enabled = !ip.isEmpty() && ip.split(":").length > 0 && !nameField.getText().isEmpty();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        switch (button.id) {
            case 2 -> {
                ServerData.ResourceMode[] modes = ServerData.ResourceMode.values();
                server.resourceMode = modes[(server.resourceMode.ordinal() + 1) % modes.length];
                resourcePacks.displayString = packCaption();
            }
            case 1 -> parent.confirmClicked(false, 0);
            case 0 -> {
                server.serverName = nameField.getText();
                server.serverIP = ipField.getText();
                parent.confirmClicked(true, 0);
            }
            default -> {}
        }
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (nameField.keyPressed(key, modifiers) || ipField.keyPressed(key, modifiers)) { updateButtons(); return; }
        if (key == GLFW_KEY_TAB) {
            boolean nameFocused = nameField.isFocused();
            nameField.setFocused(!nameFocused);
            ipField.setFocused(nameFocused);
            return;
        }
        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) { actionPerformed(buttonList.get(0)); return; }
        if (key == GLFW_KEY_ESCAPE) parent.confirmClicked(false, 0);
    }

    @Override
    public void charTyped(char character) {
        nameField.charTyped(character);
        ipField.charTyped(character);
        updateButtons();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        ipField.mouseClicked(mouseX, mouseY, button);
        nameField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("addServer.title"), width / 2, 17, 0xFFFFFF);
        drawString(fontRendererObj, I18n.format("addServer.enterName"), width / 2 - 100, 53, 0xA0A0A0);
        drawString(fontRendererObj, I18n.format("addServer.enterIp"), width / 2 - 100, 94, 0xA0A0A0);
        nameField.drawTextBox();
        ipField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

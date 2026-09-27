package net.minecraft.client.gui;

import net.minecraft.client.multiplayer.ServerData;

import static org.lwjgl.glfw.GLFW.*;

/** "Direct Connect": one address box, prefilled with options.txt's lastServer. */
public class GuiScreenServerList extends GuiScreen {
    private final GuiScreen parent;
    private final ServerData server;
    private GuiTextField ipField;

    public GuiScreenServerList(GuiScreen parent, ServerData server) {
        this.parent = parent;
        this.server = server;
    }

    @Override
    public void updateScreen() { ipField.updateCursorCounter(); }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(0, width / 2 - 100, height / 4 + 96 + 12, I18n.format("selectServer.select")));
        buttonList.add(new GuiButton(1, width / 2 - 100, height / 4 + 120 + 12, I18n.format("gui.cancel")));
        ipField = new GuiTextField(2, fontRendererObj, width / 2 - 100, 116, 200, 20);
        ipField.setClipboard(GuiRenameWorld.clipboard(mc));
        ipField.setMaxStringLength(128);
        ipField.setFocused(true);
        ipField.setText(mc.settings().lastServer);
        updateButtons();
    }

    private void updateButtons() {
        String ip = ipField.getText();
        buttonList.get(0).enabled = !ip.isEmpty() && ip.split(":").length > 0;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button.id == 1) parent.confirmClicked(false, 0);
        else if (button.id == 0) {
            server.serverIP = ipField.getText();
            parent.confirmClicked(true, 0);
        }
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (ipField.keyPressed(key, modifiers)) { updateButtons(); return; }
        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) { actionPerformed(buttonList.get(0)); return; }
        if (key == GLFW_KEY_ESCAPE) parent.confirmClicked(false, 0);
    }

    @Override
    public void charTyped(char character) { if (ipField.charTyped(character)) updateButtons(); }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        ipField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("selectServer.direct"), width / 2, 20, 0xFFFFFF);
        drawString(fontRendererObj, I18n.format("addServer.enterIp"), width / 2 - 100, 100, 0xA0A0A0);
        ipField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

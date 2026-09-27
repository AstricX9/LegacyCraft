package net.minecraft.client.gui;

import net.minecraft.client.multiplayer.ServerData;

/**
 * "Connecting to the server..." with Cancel. LegacyCraft has no protocol-47
 * client yet, so after one tick this always resolves to the vanilla
 * connection-failed screen with an honest reason (DEV-013) rather than
 * pretending to connect.
 */
public class GuiConnecting extends GuiScreen {
    private final GuiScreen parent;
    private final ServerData server;
    private int ticks;
    private boolean cancelled;

    public GuiConnecting(GuiScreen parent, ServerData server) {
        this.parent = parent;
        this.server = server;
    }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(0, width / 2 - 100, height / 4 + 120 + 12, I18n.format("gui.cancel")));
    }

    @Override
    public void keyPressed(int key, int modifiers) {}

    @Override
    public void updateScreen() {
        if (cancelled || ++ticks < 10) return;
        mc.displayGuiScreen(new GuiDisconnected(parent, "connect.failed",
            "LegacyCraft cannot join " + server.serverIP + " yet: the Minecraft protocol 47 client is not implemented."));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            cancelled = true;
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("connect.connecting"), width / 2, height / 2 - 50, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

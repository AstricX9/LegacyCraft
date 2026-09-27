package net.minecraft.client.gui;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.util.ResourceLocation;

import static org.lwjgl.glfw.GLFW.*;

/**
 * "Play Multiplayer" (master context §77): the saved servers from
 * servers.dat, a LAN scanning row, and Join Server / Direct Connect /
 * Add server / Edit / Delete / Refresh / Cancel. Shift+Up/Down reorders
 * the selected server as in vanilla.
 *
 * <p>LegacyCraft does not speak protocol 47 yet (unknowns.yml), so pings
 * report "Can't connect to server" and joining ends on a connection-failed
 * screen instead of a world (DEV-013).
 */
public class GuiMultiplayer extends GuiScreen {
    private static final ResourceLocation UNKNOWN_SERVER = new ResourceLocation("minecraft:textures/misc/unknown_server.png");
    private final GuiScreen parent;
    private ServerList servers;
    private int selected = -1;
    private ServerSelectionList list;
    private GuiButton btnEdit, btnSelect, btnDelete;
    private int pendingAction;
    private ServerData editing;
    private int ticks;

    public GuiMultiplayer(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        if (servers == null) servers = new ServerList(mc.gameDirectory().resolve("servers.dat"));
        list = new ServerSelectionList();
        buttonList.add(btnEdit = new GuiButton(7, width / 2 - 154, height - 28, 70, 20, I18n.format("selectServer.edit")));
        buttonList.add(btnDelete = new GuiButton(2, width / 2 - 74, height - 28, 70, 20, I18n.format("selectServer.delete")));
        buttonList.add(btnSelect = new GuiButton(1, width / 2 - 154, height - 52, 100, 20, I18n.format("selectServer.select")));
        buttonList.add(new GuiButton(4, width / 2 - 50, height - 52, 100, 20, I18n.format("selectServer.direct")));
        buttonList.add(new GuiButton(3, width / 2 + 4 + 50, height - 52, 100, 20, I18n.format("selectServer.add")));
        buttonList.add(new GuiButton(8, width / 2 + 4, height - 28, 70, 20, I18n.format("selectServer.refresh")));
        buttonList.add(new GuiButton(0, width / 2 + 4 + 76, height - 28, 75, 20, I18n.format("gui.cancel")));
        selectServer(selected);
    }

    private void selectServer(int index) {
        selected = index >= 0 && index < servers.countServers() ? index : -1;
        btnSelect.enabled = btnEdit.enabled = btnDelete.enabled = selected >= 0;
    }

    @Override
    public void updateScreen() { ticks++; }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        switch (button.id) {
            case 0 -> mc.displayGuiScreen(parent);
            case 1 -> connectToSelected();
            case 2 -> {
                String name = servers.getServerData(selected).serverName;
                pendingAction = 2;
                mc.displayGuiScreen(new GuiYesNo(this, I18n.format("selectServer.deleteQuestion"),
                    "'" + name + "' " + I18n.format("selectServer.deleteWarning"),
                    I18n.format("selectServer.deleteButton"), I18n.format("gui.cancel"), selected));
            }
            case 3 -> {
                pendingAction = 3;
                editing = new ServerData(I18n.format("selectServer.defaultName"), "");
                mc.displayGuiScreen(new GuiScreenAddServer(this, editing));
            }
            case 4 -> {
                pendingAction = 4;
                editing = new ServerData(I18n.format("selectServer.defaultName"), "");
                mc.displayGuiScreen(new GuiScreenServerList(this, editing));
            }
            case 7 -> {
                pendingAction = 7;
                ServerData original = servers.getServerData(selected);
                editing = new ServerData(original.serverName, original.serverIP);
                editing.copyFrom(original);
                mc.displayGuiScreen(new GuiScreenAddServer(this, editing));
            }
            case 8 -> mc.displayGuiScreen(new GuiMultiplayer(parent));
            default -> {}
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        switch (pendingAction) {
            case 2 -> {
                if (result) { servers.removeServerData(id); servers.save(); selected = -1; }
            }
            case 3 -> {
                if (result) { servers.addServerData(editing); servers.save(); selected = servers.countServers() - 1; }
            }
            case 4 -> {
                if (result) {
                    mc.settings().lastServer = editing.serverIP;
                    mc.saveOptions();
                    connect(editing);
                    pendingAction = 0;
                    return;
                }
            }
            case 7 -> {
                if (result) { servers.getServerData(selected).copyFrom(editing); servers.save(); }
            }
            default -> {}
        }
        pendingAction = 0;
        mc.displayGuiScreen(this);
    }

    private void connectToSelected() {
        if (selected >= 0) connect(servers.getServerData(selected));
    }

    private void connect(ServerData server) { mc.displayGuiScreen(new GuiConnecting(this, server)); }

    @Override
    public void keyPressed(int key, int modifiers) {
        boolean shift = (modifiers & GLFW_MOD_SHIFT) != 0;
        if (key == GLFW_KEY_UP && selected > 0) {
            if (shift) { servers.swapServers(selected, selected - 1); }
            selectServer(selected - 1);
            return;
        }
        if (key == GLFW_KEY_DOWN && selected >= 0 && selected < servers.countServers() - 1) {
            if (shift) { servers.swapServers(selected, selected + 1); }
            selectServer(selected + 1);
            return;
        }
        if (key == GLFW_KEY_F5) { actionPerformed(buttonList.get(5)); return; }
        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) { actionPerformed(btnSelect); return; }
        super.keyPressed(key, modifiers);
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
        drawDefaultBackground();
        list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, I18n.format("multiplayer.title"), width / 2, 20, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private final class ServerSelectionList extends GuiSlot {
        ServerSelectionList() { super(GuiMultiplayer.this.mc, GuiMultiplayer.this.width, GuiMultiplayer.this.height, 32, GuiMultiplayer.this.height - 64, 36); }

        /** The saved servers plus the LAN scanning row. */
        @Override protected int getSize() { return servers.countServers() + 1; }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            if (index >= servers.countServers()) return;
            selectServer(index);
            if (doubleClick) connectToSelected();
        }

        @Override protected boolean isSelected(int index) { return index == selected; }
        @Override public int getListWidth() { return super.getListWidth() + 85; }
        @Override protected int getScrollBarX() { return super.getScrollBarX() + 30; }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            if (index >= servers.countServers()) {
                String scanning = I18n.format("lanServer.scanning");
                fontRendererObj.drawString(scanning, width / 2 - fontRendererObj.getStringWidth(scanning) / 2, y + slotHeight / 2 - 9 / 2 + 1 - 9, 0xFFFFFF);
                String dots = switch ((int) (System.currentTimeMillis() / 300L % 4L)) {
                    case 1, 3 -> "o O o";
                    case 2 -> "o o O";
                    default -> "O o o";
                };
                fontRendererObj.drawString(dots, width / 2 - fontRendererObj.getStringWidth(dots) / 2, y + slotHeight / 2 - 9 / 2 + 1, 0x808080);
                return;
            }
            ServerData server = servers.getServerData(index);
            fontRendererObj.drawString(server.serverName, x + 32 + 3, y + 1, 0xFFFFFF);
            fontRendererObj.drawString("§4Can't connect to server", x + 32 + 3, y + 12, 0x808080);
            // Ping bars: icons.png column 0, row 5 is "no connection".
            mc.textures().bind(GuiTextures.ICONS);
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
            drawModalRectWithCustomSizedTexture(x + getListWidth() - 15, y, 0, 176 + 5 * 8, 10, 8, 256, 256);
            mc.textures().bind(UNKNOWN_SERVER);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            drawModalRectWithCustomSizedTexture(x, y, 0, 0, 32, 32, 32, 32);
        }
    }
}

package net.minecraft.client.gui;

import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * "Select Resource Packs": Available packs (from the {@code resourcepacks}
 * folder) on the left, Selected on the right with the built-in Default pack,
 * and "Open resource pack folder" / Done.
 *
 * <p>LegacyCraft's resource manager only reads the base classpath pack so
 * far, so extra packs are listed but cannot be moved to Selected (DEV-013).
 */
public class GuiScreenResourcePacks extends GuiScreen {
    private static final ResourceLocation PACK_ICON = new ResourceLocation("minecraft:textures/misc/unknown_pack.png");
    private final GuiScreen parent;
    private final List<String> available = new ArrayList<>();
    private PackList availableList, selectedList;

    public GuiScreenResourcePacks(GuiScreen parent) { this.parent = parent; }

    private Path packDirectory() { return mc.gameDirectory().resolve("resourcepacks"); }

    @Override
    public void initGui() {
        available.clear();
        try {
            Files.createDirectories(packDirectory());
            try (DirectoryStream<Path> packs = Files.newDirectoryStream(packDirectory())) {
                for (Path pack : packs) {
                    String name = pack.getFileName().toString();
                    if (Files.isDirectory(pack) ? Files.isRegularFile(pack.resolve("pack.mcmeta")) : name.endsWith(".zip"))
                        available.add(name);
                }
            }
        } catch (IOException error) {
            System.err.println("Couldn't list resource packs: " + error.getMessage());
        }
        available.sort(String::compareTo);
        buttonList.add(new GuiButton(2, width / 2 - 154, height - 48, 150, 20, I18n.format("resourcePack.openFolder")));
        buttonList.add(new GuiButton(1, width / 2 + 4, height - 48, 150, 20, I18n.format("gui.done")));
        availableList = new PackList(I18n.format("resourcePack.available.title"), available, width / 2 - 4 - 200, false);
        selectedList = new PackList(I18n.format("resourcePack.selected.title"), List.of("Default"), width / 2 + 4, true);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) { mc.saveOptions(); mc.displayGuiScreen(parent); }
        if (button.id == 2) {
            try {
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(packDirectory().toFile());
            } catch (Exception unsupported) {
                System.out.println("Resource pack folder: " + packDirectory().toAbsolutePath());
            }
        }
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        (mouseX < width / 2 ? availableList : selectedList).mouseScrolled(direction);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackground(0);
        availableList.drawScreen(mouseX, mouseY, partialTicks);
        selectedList.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, I18n.format("resourcePack.title"), width / 2, 16, 0xFFFFFF);
        drawCenteredString(fontRendererObj, I18n.format("resourcePack.folderInfo"), width / 2 - 77, height - 26, 0x808080);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** The base pack's own description from assets/pack.mcmeta ("The default look of Minecraft" in 1.8.9). */
    private static String defaultDescription() {
        try (var stream = GuiScreenResourcePacks.class.getResourceAsStream("/assets/pack.mcmeta")) {
            if (stream != null) {
                var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                return root.getAsJsonObject("pack").get("description").getAsString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "";
    }

    private final class PackList extends GuiSlot {
        private final String title;
        private final List<String> packs;
        private final boolean selected;

        PackList(String title, List<String> packs, int left, boolean selected) {
            super(GuiScreenResourcePacks.this.mc, 200, GuiScreenResourcePacks.this.height, 32, GuiScreenResourcePacks.this.height - 55 + 4, 36);
            this.title = title;
            this.packs = packs;
            this.selected = selected;
            this.left = left;
            this.right = left + 200;
            setHasListHeader(true, (int) (FontRenderer.FONT_HEIGHT * 1.5f));
        }

        @Override protected int getSize() { return packs.size(); }
        @Override protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {}
        @Override protected boolean isSelected(int index) { return false; }
        @Override public int getListWidth() { return width; }
        @Override protected int getScrollBarX() { return right - 6; }

        @Override
        protected void drawListHeader(int x, int y) {
            String header = "§n§l" + title;
            fontRendererObj.drawString(header, x + width / 2 - fontRendererObj.getStringWidth(header) / 2,
                Math.min(top + 3, y), 0xFFFFFF);
        }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            mc.textures().bind(selected ? new ResourceLocation("minecraft:pack.png") : PACK_ICON);
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
            drawModalRectWithCustomSizedTexture(x, y, 0, 0, 32, 32, 32, 32);
            String name = packs.get(index);
            String description = selected ? defaultDescription() : "§7Not loadable yet in LegacyCraft";
            fontRendererObj.drawStringWithShadow(name, x + 32 + 2, y + 1, 0xFFFFFF);
            List<String> lines = fontRendererObj.listFormattedStringToWidth(description, 157);
            for (int i = 0; i < 2 && i < lines.size(); i++)
                fontRendererObj.drawStringWithShadow(lines.get(i), x + 32 + 2, y + 12 + 10 * i, 0x808080);
        }
    }
}

package net.minecraft.client.gui;

import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;

/**
 * "Select Resource Packs" (1.8.9 GuiScreenResourcePacks): Available packs from
 * the {@code resourcepacks} folder on the left, Selected packs on the right
 * with the highest priority at the top and the built-in Default pack at the
 * bottom. Hovering a pack's icon shows the arrows from
 * {@code textures/gui/resource_packs.png}: right adds it, left removes it,
 * up and down reorder it. Packs whose pack_format isn't 1 get a red
 * background and ask for confirmation. Done applies the change and reloads
 * the resources (HYPOTHESIS: recalled layout constants).
 */
public class GuiScreenResourcePacks extends GuiScreen {
    private static final ResourceLocation PACK_ICON = new ResourceLocation("minecraft:textures/misc/unknown_pack.png");
    private static final ResourceLocation DEFAULT_ICON = new ResourceLocation("minecraft:pack.png");
    private static final ResourceLocation ARROWS = new ResourceLocation("minecraft:textures/gui/resource_packs.png");
    private static final int CONFIRM_INCOMPATIBLE = 0;

    private final GuiScreen parent;
    /** null stands for the Default pack. */
    private List<ResourcePackRepository.Entry> available, selected;
    private PackList availableList, selectedList;
    private boolean changed;
    private ResourcePackRepository.Entry pendingIncompatible;
    private final Map<String, Integer> icons = new HashMap<>();

    public GuiScreenResourcePacks(GuiScreen parent) { this.parent = parent; }

    private ResourcePackRepository repository() { return mc.resourcePacks(); }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(2, width / 2 - 154, height - 48, 150, 20, I18n.format("resourcePack.openFolder")));
        buttonList.add(new GuiButton(1, width / 2 + 4, height - 48, 150, 20, I18n.format("gui.done")));
        if (available == null) {
            available = new ArrayList<>();
            selected = new ArrayList<>();
            List<ResourcePackRepository.Entry> found = repository() == null ? List.of() : repository().scan();
            Map<String, ResourcePackRepository.Entry> byName = new HashMap<>();
            for (ResourcePackRepository.Entry entry : found) byName.put(entry.name, entry);
            List<String> active = repository() == null ? List.of() : repository().activeNames();
            for (int i = active.size() - 1; i >= 0; i--) {
                ResourcePackRepository.Entry entry = byName.get(active.get(i));
                if (entry != null) selected.add(entry);
            }
            for (ResourcePackRepository.Entry entry : found) if (!selected.contains(entry)) available.add(entry);
            selected.add(null);
        }
        availableList = new PackList(I18n.format("resourcePack.available.title"), available, width / 2 - 4 - 200);
        selectedList = new PackList(I18n.format("resourcePack.selected.title"), selected, width / 2 + 4);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) {
            java.nio.file.Path folder = repository() != null ? repository().directory() : mc.gameDirectory().resolve("resourcepacks");
            try {
                java.nio.file.Files.createDirectories(folder);
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(folder.toFile());
            } catch (Exception unsupported) {
                System.out.println("Resource pack folder: " + folder.toAbsolutePath());
            }
        }
        if (button.id == 1) {
            if (changed) {
                List<String> names = new ArrayList<>();
                List<String> incompatible = mc.settings().incompatibleResourcePacks;
                for (int i = selected.size() - 1; i >= 0; i--) {
                    ResourcePackRepository.Entry entry = selected.get(i);
                    if (entry == null) continue;
                    names.add(entry.name);
                    if (!entry.compatible() && !incompatible.contains(entry.name)) incompatible.add(entry.name);
                }
                incompatible.removeIf(name -> !names.contains(name));
                mc.settings().resourcePacks = names;
                mc.saveOptions();
                mc.displayGuiScreen(parent);
                mc.refreshResources();
            } else {
                mc.displayGuiScreen(parent);
            }
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (id == CONFIRM_INCOMPATIBLE && pendingIncompatible != null) {
            if (result) {
                available.remove(pendingIncompatible);
                selected.add(0, pendingIncompatible);
                changed = true;
            }
            pendingIncompatible = null;
            mc.displayGuiScreen(this);
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (!availableList.mouseClicked(mouseX, mouseY, button)) selectedList.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        availableList.mouseReleased();
        selectedList.mouseReleased();
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseDragged(int mouseX, int mouseY, int button, long time) {
        availableList.mouseDragged(mouseX, mouseY);
        selectedList.mouseDragged(mouseX, mouseY);
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

    @Override
    public void onGuiClosed() {
        for (int id : icons.values()) glDeleteTextures(id);
        icons.clear();
    }

    private void bindIcon(ResourcePackRepository.Entry entry) {
        if (entry == null) {
            // The Default pack shows LegacyCraft's own pack.png, or the unknown-pack icon if it is missing.
            mc.textures().bind(mc.resources().exists(DEFAULT_ICON) ? DEFAULT_ICON : PACK_ICON);
            return;
        }
        if (entry.icon == null) { mc.textures().bind(PACK_ICON); return; }
        Integer id = icons.get(entry.name);
        if (id == null) {
            id = GuiTextures.upload(entry.icon, false);
            icons.put(entry.name, id);
        }
        glBindTexture(GL_TEXTURE_2D, id);
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
        private final List<ResourcePackRepository.Entry> packs;

        PackList(String title, List<ResourcePackRepository.Entry> packs, int left) {
            super(GuiScreenResourcePacks.this.mc, 200, GuiScreenResourcePacks.this.height, 32, GuiScreenResourcePacks.this.height - 55 + 4, 36);
            this.title = title;
            this.packs = packs;
            this.left = left;
            this.right = left + 200;
            setHasListHeader(true, (int) (FontRenderer.FONT_HEIGHT * 1.5f));
        }

        private boolean isSelectedList() { return packs == selected; }

        // ResourcePackListEntry's move rules.
        private boolean canMoveRight(int index) { return !isSelectedList(); }
        private boolean canMoveLeft(int index) { return isSelectedList() && packs.get(index) != null; }
        private boolean canMoveUp(int index) { return isSelectedList() && packs.get(index) != null && index > 0; }
        private boolean canMoveDown(int index) {
            return isSelectedList() && packs.get(index) != null && index < packs.size() - 1 && packs.get(index + 1) != null;
        }

        @Override protected int getSize() { return packs.size(); }
        @Override protected boolean isSelected(int index) { return false; }
        @Override public int getListWidth() { return width; }
        @Override protected int getScrollBarX() { return right - 6; }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            ResourcePackRepository.Entry entry = packs.get(index);
            if (entry == null) return;
            int x = mouseX - rowLeft(), y = mouseY - rowTop(index);
            if (x > 32) return;
            if (canMoveRight(index)) {
                if (!entry.compatible()) {
                    pendingIncompatible = entry;
                    mc.displayGuiScreen(new GuiYesNo(GuiScreenResourcePacks.this,
                        I18n.format("resourcePack.incompatible.confirm.title"),
                        I18n.format("resourcePack.incompatible.confirm." + (entry.format > ResourcePackRepository.PACK_FORMAT ? "new" : "old")),
                        CONFIRM_INCOMPATIBLE));
                } else {
                    available.remove(entry);
                    selected.add(0, entry);
                    changed = true;
                }
                return;
            }
            if (x < 16 && canMoveLeft(index)) {
                selected.remove(entry);
                available.add(0, entry);
                changed = true;
            } else if (x > 16 && y < 16 && canMoveUp(index)) {
                packs.set(index, packs.get(index - 1));
                packs.set(index - 1, entry);
                changed = true;
            } else if (x > 16 && y > 16 && canMoveDown(index)) {
                packs.set(index, packs.get(index + 1));
                packs.set(index + 1, entry);
                changed = true;
            }
        }

        @Override
        protected void drawListHeader(int x, int y) {
            String header = "§n§l" + title;
            fontRendererObj.drawString(header, x + width / 2 - fontRendererObj.getStringWidth(header) / 2,
                Math.min(top + 3, y), 0xFFFFFF);
        }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            ResourcePackRepository.Entry entry = packs.get(index);
            int format = entry == null ? ResourcePackRepository.PACK_FORMAT : entry.format;
            if (format != ResourcePackRepository.PACK_FORMAT) drawRect(x - 1, y - 1, x + width - 9, y + slotHeight + 1, 0xFF770000);
            bindIcon(entry);
            glColor4f(1, 1, 1, 1);
            drawModalRectWithCustomSizedTexture(x, y, 0, 0, 32, 32, 32, 32);
            String name = entry == null ? "Default" : entry.name.replaceFirst("(?i)\\.zip$", "");
            String description = entry == null ? defaultDescription() : entry.description;
            boolean hovered = mouseX >= x && mouseX < x + width - 6 && mouseY >= y && mouseY < y + slotHeight
                && isMouseYWithinSlotBounds(mouseY);
            if (hovered && entry != null) {
                drawRect(x, y, x + 32, y + 32, 0xA0909090);
                mc.textures().bind(ARROWS);
                glColor4f(1, 1, 1, 1);
                int relX = mouseX - x, relY = mouseY - y;
                if (format < ResourcePackRepository.PACK_FORMAT) {
                    name = "§c" + I18n.format("resourcePack.incompatible");
                    description = "§c" + I18n.format("resourcePack.incompatible.old");
                } else if (format > ResourcePackRepository.PACK_FORMAT) {
                    name = "§c" + I18n.format("resourcePack.incompatible");
                    description = "§c" + I18n.format("resourcePack.incompatible.new");
                }
                if (canMoveRight(index)) {
                    drawModalRectWithCustomSizedTexture(x, y, 0, relX < 32 ? 32 : 0, 32, 32, 256, 256);
                } else {
                    if (canMoveLeft(index))
                        drawModalRectWithCustomSizedTexture(x, y, 32, relX < 16 ? 32 : 0, 32, 32, 256, 256);
                    if (canMoveUp(index))
                        drawModalRectWithCustomSizedTexture(x, y, 96, relX < 32 && relX > 16 && relY < 16 ? 32 : 0, 32, 32, 256, 256);
                    if (canMoveDown(index))
                        drawModalRectWithCustomSizedTexture(x, y, 64, relX < 32 && relX > 16 && relY > 16 ? 32 : 0, 32, 32, 256, 256);
                }
            }
            if (fontRendererObj.getStringWidth(name) > 157)
                name = fontRendererObj.trimStringToWidth(name, 157 - fontRendererObj.getStringWidth("...")) + "...";
            fontRendererObj.drawStringWithShadow(name, x + 32 + 2, y + 1, 0xFFFFFF);
            List<String> lines = fontRendererObj.listFormattedStringToWidth(description, 157);
            for (int i = 0; i < 2 && i < lines.size(); i++)
                fontRendererObj.drawStringWithShadow(lines.get(i), x + 32 + 2, y + 12 + 10 * i, 0x808080);
        }
    }
}

package net.minecraft.client.gui;

import net.minecraft.client.settings.KeyCodes;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * 1.8.9's creative inventory (master context §21), opened with the inventory
 * key in Creative mode. A 195x136 window with twelve tabs (six above, six
 * below), a 9x5 item grid with a scrollbar, the hotbar row, a search field on
 * the Search tab and the player's own inventory (with the destroy slot) on
 * the Survival Inventory tab.
 *
 * <p>Layout constants and click rules are HYPOTHESIS, reconstructed from the
 * vanilla client's behaviour; see docs/compat/unknowns.yml.
 */
public class GuiContainerCreative extends GuiContainer {
    static final ResourceLocation TABS = new ResourceLocation("minecraft:textures/gui/container/creative_inventory/tabs.png");
    /** The tab shown when the screen opens; vanilla remembers it for the whole session. */
    static int selectedTabIndex = 0;

    private final List<CreativeTabs.Entry> itemList = new ArrayList<>();
    private float currentScroll;
    private boolean isScrolling;
    private GuiTextField searchField;
    private SlotPos destroySlot;
    private final List<SlotPos> listSlots = new ArrayList<>();
    /** A number key over a slot has been handled; drop the character it also produces. */
    private boolean swallowNextChar;

    public GuiContainerCreative() {
        xSize = 195;
        ySize = 136;
    }

    public static int selectedTab() { return selectedTabIndex; }

    private CreativeTabs tabs() { return mc.creativeTabs(); }
    private CreativeTabs.Tab selected() { return tabs().tab(selectedTabIndex); }

    @Override
    public void initGui() {
        buttonList.clear();
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;
        String previousSearch = searchField == null ? "" : searchField.getText();
        searchField = new GuiTextField(0, fontRendererObj, guiLeft + 82, guiTop + 6, 89, FontRenderer.FONT_HEIGHT);
        searchField.setMaxStringLength(15);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setVisible(false);
        searchField.setTextColor(0xFFFFFF);
        searchField.setClipboard(new GuiTextField.Clipboard() {
            public String get() { return mc.getClipboard(); }
            public void set(String value) { mc.setClipboard(value); }
        });
        searchField.setText(previousSearch);
        int tab = selectedTabIndex;
        selectedTabIndex = -1;
        setCurrentCreativeTab(tab);
    }

    /** Switches tab: rebuilds the slots, resets the scroll and (on Search) focuses the empty search field. */
    public void setCurrentCreativeTab(int index) {
        int previous = selectedTabIndex;
        selectedTabIndex = index;
        slots.clear();
        listSlots.clear();
        destroySlot = null;
        buildSlots();
        itemList.clear();
        CreativeTabs.Tab tab = selected();
        if (index == CreativeTabs.SEARCH) {
            searchField.setVisible(true);
            searchField.setCanLoseFocus(false);
            searchField.setFocused(true);
            if (previous != CreativeTabs.SEARCH) searchField.setText("");
            updateCreativeSearch();
        } else {
            searchField.setVisible(false);
            searchField.setCanLoseFocus(true);
            searchField.setFocused(false);
            if (index != CreativeTabs.INVENTORY) itemList.addAll(tab.entries());
        }
        currentScroll = 0;
    }

    /** Refilters the Search tab by the field's text (case-insensitive substring of the display name). */
    void updateCreativeSearch() {
        itemList.clear();
        itemList.addAll(CreativeTabs.filter(tabs().tab(CreativeTabs.SEARCH).entries(), searchField.getText(),
            entry -> mc.itemName(entry.item())));
        currentScroll = 0;
    }

    @Override
    protected void buildSlots() {
        InventoryPlayer inventory = inventory();
        if (selectedTabIndex == CreativeTabs.INVENTORY) {
            // Armor in two columns: helmet (9,6), chestplate (9,33), leggings (63,6), boots (63,33).
            for (int i = 0; i < 4; i++) {
                int armorSlot = 3 - i;
                slots.add(new SlotPos(Slot.armor(inventory, armorSlot), 9 + (i / 2) * 54, 6 + (i % 2) * 27, Kind.ARMOR));
            }
            for (int k = 9; k < InventoryPlayer.MAIN_SIZE; k++)
                slots.add(new SlotPos(Slot.of(inventory.mainInventory, k), 9 + (k % 9) * 18, 54 + (k / 9 - 1) * 18, Kind.MAIN));
            for (int k = 0; k < InventoryPlayer.HOTBAR_SIZE; k++)
                slots.add(new SlotPos(Slot.of(inventory.mainInventory, k), 9 + k * 18, 112, Kind.HOTBAR));
            destroySlot = new SlotPos(DESTROY, 173, 112, Kind.OTHER);
            slots.add(destroySlot);
        } else {
            for (int row = 0; row < 5; row++)
                for (int column = 0; column < 9; column++) {
                    SlotPos pos = new SlotPos(new ListSlot(row * 9 + column), 9 + column * 18, 18 + row * 18, Kind.OTHER);
                    listSlots.add(pos);
                    slots.add(pos);
                }
            for (int k = 0; k < InventoryPlayer.HOTBAR_SIZE; k++)
                slots.add(new SlotPos(Slot.of(inventory.mainInventory, k), 9 + k * 18, 112, Kind.HOTBAR));
        }
    }

    // ---- item list and scrolling -----------------------------------------------------------

    /** Rows scrolled past: the scroll fraction times the rows beyond the first five, rounded. */
    int rowOffset() {
        int extraRows = (itemList.size() + 9 - 1) / 9 - 5;
        int offset = (int) (currentScroll * extraRows + 0.5);
        return Math.max(0, offset);
    }

    boolean needsScrollBars() {
        return selectedTabIndex != CreativeTabs.INVENTORY && selected().hasScrollbar() && itemList.size() > 45;
    }

    public List<CreativeTabs.Entry> itemList() { return itemList; }
    public float currentScroll() { return currentScroll; }
    public String searchText() { return searchField.getText(); }

    /** The entry shown in grid cell {@code index} (0-44) at the current scroll, or null. */
    CreativeTabs.Entry entryAt(int index) {
        int i = rowOffset() * 9 + index;
        return i >= 0 && i < itemList.size() ? itemList.get(i) : null;
    }

    /** A cell of the 9x5 item grid: shows one of the listed item, never holds anything. */
    private final class ListSlot implements Slot {
        final int index;
        ListSlot(int index) { this.index = index; }
        @Override public ItemStack get() {
            CreativeTabs.Entry entry = entryAt(index);
            return entry == null ? null : entry.newStack();
        }
        @Override public void set(ItemStack stack) {}
        @Override public boolean accepts(ItemStack stack) { return false; }
        @Override public int limit() { return 64; }
    }

    /** The Survival Inventory tab's bin: whatever is put in disappears. */
    private static final Slot DESTROY = new Slot() {
        @Override public ItemStack get() { return null; }
        @Override public void set(ItemStack stack) {}
        @Override public boolean accepts(ItemStack stack) { return true; }
        @Override public int limit() { return 64; }
    };

    private boolean isListSlot(SlotPos pos) { return pos.slot() instanceof ListSlot; }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        if (direction == 0 || !needsScrollBars()) return;
        int extraRows = (itemList.size() + 9 - 1) / 9 - 5;
        currentScroll -= (direction > 0 ? 1 : -1) / (float) extraRows;
        currentScroll = Math.max(0, Math.min(1, currentScroll));
    }

    private boolean overScrollbar(int mouseX, int mouseY) {
        int left = guiLeft + 175, top = guiTop + 18;
        return mouseX >= left && mouseY >= top && mouseX < left + 14 && mouseY < top + 112;
    }

    /**
     * Once a frame: while the button that pressed the scrollbar is held, the
     * thumb's middle follows the pointer (GuiContainerCreative.drawScreen), so
     * dragging works even when the pointer leaves the bar.
     */
    void followScrollbar(int mouseY) {
        if (isScrolling) dragScrollbar(mouseY);
    }

    private void dragScrollbar(int mouseY) {
        int top = guiTop + 18, bottom = top + 112;
        currentScroll = (mouseY - top - 7.5f) / ((bottom - top) - 15.0f);
        currentScroll = Math.max(0, Math.min(1, currentScroll));
    }

    // ---- tabs -------------------------------------------------------------------------------

    /** Whether (mouseX, mouseY), relative to the window, is on a tab (28x32, slightly larger than drawn). */
    boolean isOverTab(CreativeTabs.Tab tab, int mouseX, int mouseY) {
        int column = tab.column();
        int x = 28 * column;
        if (column == 5) x = xSize - 28 + 2;
        else if (column > 0) x += column;
        int y = tab.isTopRow() ? -32 : ySize;
        return mouseX >= x && mouseX <= x + 28 && mouseY >= y && mouseY <= y + 32;
    }

    private CreativeTabs.Tab tabAt(int mouseX, int mouseY) {
        for (CreativeTabs.Tab tab : tabs().tabs())
            if (isOverTab(tab, mouseX - guiLeft, mouseY - guiTop)) return tab;
        return null;
    }

    // ---- input ------------------------------------------------------------------------------

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        trackMouse(mouseX, mouseY);
        if (button == 0) {
            // Tabs switch on release; pressing one does nothing else.
            if (tabAt(mouseX, mouseY) != null) return;
            if (overScrollbar(mouseX, mouseY)) {
                isScrolling = needsScrollBars();
                if (isScrolling) { dragScrollbar(mouseY); return; }
            }
        }
        if (selectedTabIndex == CreativeTabs.SEARCH) searchField.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) {
            isScrolling = false;
            CreativeTabs.Tab tab = tabAt(mouseX, mouseY);
            if (tab != null) {
                if (tab.index() != selectedTabIndex) setCurrentCreativeTab(tab.index());
                return;
            }
        }
        super.mouseReleased(mouseX, mouseY, button);
    }

    /** ContainerCreative.canDragIntoSlot: only the player's own slots, never the item grid or the bin. */
    @Override
    protected boolean canDragIntoSlot(SlotPos pos) { return pos != destroySlot && !isListSlot(pos); }

    /** ContainerCreative.canMergeSlot: double-click gathers only from slots below y 90 (the hotbar row, mostly). */
    @Override
    protected boolean canMergeSlot(SlotPos pos) { return pos != destroySlot && !isListSlot(pos) && pos.y() > 90; }

    @Override
    protected boolean isOutside(int mouseX, int mouseY) {
        return super.isOutside(mouseX, mouseY) && tabAt(mouseX, mouseY) == null;
    }

    @Override
    protected void handleSlotClick(SlotPos pos, int button, boolean shift) {
        InventoryPlayer inventory = inventory();
        if (pos == destroySlot) {
            // Shift-clicking the bin empties the whole inventory; any other click deletes the held stack.
            if (shift) {
                Arrays.fill(inventory.mainInventory, null);
                Arrays.fill(inventory.armorInventory, null);
            } else {
                inventory.setItemStack(null);
            }
            return;
        }
        if (isListSlot(pos)) {
            clickListSlot(pos.slot().get(), button, shift);
            return;
        }
        if (selectedTabIndex != CreativeTabs.INVENTORY && shift && button != 2) {
            // Shift-click on the hotbar row of an item tab clears that slot.
            pos.slot().set(null);
            return;
        }
        super.handleSlotClick(pos, button, shift);
    }

    /**
     * An item-grid click: with an empty hand, take one (a full stack with
     * shift); holding the same item, left adds one (shift fills the stack) and
     * right removes one; holding anything else deletes it.
     */
    void clickListSlot(ItemStack shown, int button, boolean shift) {
        InventoryPlayer inventory = inventory();
        ItemStack cursor = inventory.getItemStack();
        if (button == 2) {
            if (cursor == null && shown != null)
                inventory.setItemStack(shown.withCount(shown.item().maxStackSize()));
            return;
        }
        if (cursor != null && shown != null && cursor.canMerge(shown)) {
            int max = cursor.item().maxStackSize();
            if (button == 0) {
                int count = shift ? max : Math.min(max, cursor.count() + 1);
                inventory.setItemStack(cursor.withCount(count));
            } else if (cursor.count() <= 1) {
                inventory.setItemStack(null);
            } else {
                inventory.setItemStack(cursor.withCount(cursor.count() - 1));
            }
        } else if (shown != null && cursor == null) {
            inventory.setItemStack(shown.withCount(shift ? shown.item().maxStackSize() : 1));
        } else {
            inventory.setItemStack(null);
        }
    }

    /** The drop key over the item grid throws one of the item, or a full stack with Ctrl. */
    @Override
    protected void dropFromSlot(SlotPos pos, boolean wholeStack) {
        if (pos == destroySlot) return;
        if (isListSlot(pos)) {
            ItemStack shown = pos.slot().get();
            if (shown != null) mc.dropPlayerItem(shown.withCount(wholeStack ? shown.item().maxStackSize() : 1));
            return;
        }
        super.dropFromSlot(pos, wholeStack);
    }

    @Override
    protected void numberKey(SlotPos pos, int hotbarIndex) {
        if (isListSlot(pos)) {
            ItemStack shown = pos.slot().get();
            if (shown != null)
                inventory().mainInventory[hotbarIndex] = shown.withCount(shown.item().maxStackSize());
            return;
        }
        if (pos == destroySlot) return;
        super.numberKey(pos, hotbarIndex);
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        int legacy = KeyCodes.fromGlfwKey(key);
        if (selectedTabIndex != CreativeTabs.SEARCH) {
            if (legacy != KeyCodes.NONE && legacy == mc.settings().keyBindChat.getKeyCode()) {
                setCurrentCreativeTab(CreativeTabs.SEARCH);
                swallowNextChar = true;
                return;
            }
            super.keyPressed(key, modifiers);
            return;
        }
        // On the Search tab every other key edits the text; only Escape closes.
        if (key == GLFW_KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        SlotPos hovered = slotAt(mouseXForKeys(), mouseYForKeys());
        if (hovered != null && inventory().getItemStack() == null) {
            for (int i = 0; i < 9; i++) {
                if (legacy != KeyCodes.NONE && legacy == mc.settings().keyBindsHotbar[i].getKeyCode()) {
                    numberKey(hovered, i);
                    swallowNextChar = true;
                    return;
                }
            }
        }
        String before = searchField.getText();
        searchField.keyPressed(key, modifiers);
        if (!before.equals(searchField.getText())) updateCreativeSearch();
    }

    @Override
    public void charTyped(char character) {
        if (swallowNextChar) { swallowNextChar = false; return; }
        if (selectedTabIndex != CreativeTabs.SEARCH) return;
        if (searchField.charTyped(character)) updateCreativeSearch();
    }

    @Override
    public void updateScreen() { if (searchField != null) searchField.updateCursorCounter(); }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    // ---- drawing ----------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        followScrollbar(mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (CreativeTabs.Tab tab : tabs().tabs()) {
            if (isOverTab(tab, mouseX - guiLeft, mouseY - guiTop)) {
                drawHoveringText(List.of(I18n.format(tab.translationKey())), mouseX, mouseY);
                break;
            }
        }
        if (destroySlot != null && inventory().getItemStack() == null && isMouseOverSlot(destroySlot, mouseX, mouseY))
            drawHoveringText(List.of(I18n.format("inventory.binSlot")), mouseX, mouseY);
    }

    /** On the Search tab the tooltip's second line names the item's tab, in bold blue. */
    @Override
    protected List<String> tooltip(ItemStack stack) {
        List<String> lines = super.tooltip(stack);
        if (selectedTabIndex == CreativeTabs.SEARCH) {
            CreativeTabs.Tab tab = tabs().tabOf(stack.item());
            if (tab != null) lines.add(1, FontRenderer.FORMAT_CHAR + "l" + FontRenderer.FORMAT_CHAR + "9" + I18n.format(tab.translationKey()));
        }
        return lines;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        CreativeTabs.Tab current = selected();
        for (CreativeTabs.Tab tab : tabs().tabs()) if (tab.index() != selectedTabIndex) drawTab(tab);
        mc.textures().bind(new ResourceLocation("minecraft:textures/gui/container/creative_inventory/tab_" + current.background() + ".png"));
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        searchField.drawTextBox();
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
        if (current.hasScrollbar()) {
            mc.textures().bind(TABS);
            int left = guiLeft + 175, top = guiTop + 18, bottom = top + 112;
            drawTexturedModalRect(left, top + (int) ((bottom - top - 17) * currentScroll), 232 + (needsScrollBars() ? 0 : 12), 0, 12, 15);
        }
        drawTab(current);
        if (selectedTabIndex == CreativeTabs.INVENTORY)
            mc.drawPlayerOnScreen(guiLeft + 43, guiTop + 45, 20, guiLeft + 43 - mouseX, guiTop + 45 - 30 - mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        CreativeTabs.Tab tab = selected();
        if (tab.drawsTitle()) fontRendererObj.drawString(I18n.format(tab.translationKey()), 8, 6, 0x404040);
    }

    /** One tab from tabs.png: 28 wide, 32 tall, raised when selected, with its icon item. */
    private void drawTab(CreativeTabs.Tab tab) {
        boolean isSelected = tab.index() == selectedTabIndex;
        boolean topRow = tab.isTopRow();
        int column = tab.column();
        int u = column * 28, v = isSelected ? 32 : 0;
        int x = guiLeft + 28 * column, y = guiTop;
        if (column == 5) x = guiLeft + xSize - 28;
        else if (column > 0) x += column;
        if (topRow) y -= 28;
        else { v += 64; y += ySize - 4; }
        mc.textures().bind(TABS);
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
        drawTexturedModalRect(x, y, u, v, 28, 32);
        if (tab.icon() != null && mc.itemRenderer() != null)
            mc.itemRenderer().renderItemIntoGUI(tab.icon(), x + 6, y + 8 + (topRow ? 1 : -1));
    }

    static void resetSelectedTabForTests() { selectedTabIndex = 0; }
}

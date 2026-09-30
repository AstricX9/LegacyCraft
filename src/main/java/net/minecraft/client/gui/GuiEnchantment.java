package net.minecraft.client.gui;

import net.minecraft.client.renderer.BookModel;
import net.minecraft.client.renderer.StandardLighting;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.EnchantingTable;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * The enchanting table (1.8.9 GuiEnchantment / ContainerEnchantment;
 * HYPOTHESIS: recalled positions, colours and animation constants): the item
 * and lapis slots under the open book, the three offers to their right, the
 * inventory below, from {@code container/enchanting_table.png}.
 *
 * <p>Each offer shows a line of Standard Galactic words (drawn from the
 * player's seed, so they change with it but mean nothing), the level it asks
 * for, and a 1-3 lapis icon; offers the player can't afford are greyed.
 * Hovering one shows its clue ("Protection III . . . ?") and the lapis and
 * levels it takes. The book turns pages whenever the item changes and opens
 * while there are offers. The item and lapis go back to the player on close.
 */
public class GuiEnchantment extends GuiContainer {
    static final ResourceLocation BACKGROUND = new ResourceLocation("minecraft:textures/gui/container/enchanting_table.png");
    /** EnchantmentNameParts' word list. */
    static final String[] NAME_PARTS = ("the elder scrolls klaatu berata niktu xyzzy bless curse light darkness fire air "
        + "earth water hot dry cold wet ignite snuff embiggen twist shorten stretch fiddle destroy imbue galvanize enchant "
        + "free limited range of towards inside sphere cube self other ball mental physical grow shrink demon elemental "
        + "spirit animal creature beast humanoid undead fresh stale").split(" ");

    private final int tableX, tableY, tableZ;
    private EnchantingTable table;
    private final Random bookRandom = new Random();
    private ItemStack lastItem;
    private int ticks;
    private float flip, flipPrev, flipTarget, flipSpeed, open, openPrev;

    public GuiEnchantment(int x, int y, int z) {
        tableX = x;
        tableY = y;
        tableZ = z;
        xSize = 176;
        ySize = 166;
    }

    public EnchantingTable table() { return table; }

    @Override
    protected void buildSlots() {
        InventoryPlayer inventory = inventory();
        if (table == null) {
            table = new EnchantingTable(mc::itemByName);
            table.open(mc.xpSeed(), mc.bookshelvesAround(tableX, tableY, tableZ));
        }
        slots.add(new SlotPos(table.itemSlot(), 15, 47, Kind.OTHER));
        slots.add(new SlotPos(table.lapisSlot(), 35, 47, Kind.OTHER));
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                slots.add(new SlotPos(Slot.of(inventory.mainInventory, column + (row + 1) * 9), 8 + column * 18, 84 + row * 18, Kind.MAIN));
        for (int column = 0; column < 9; column++)
            slots.add(new SlotPos(Slot.of(inventory.mainInventory, column), 8 + column * 18, 142, Kind.HOTBAR));
    }

    /** The table's view of the local player. */
    private EnchantingTable.Player player() {
        return new EnchantingTable.Player() {
            public int experienceLevel() { return mc.experienceLevel(); }
            public int xpSeed() { return mc.xpSeed(); }
            public boolean isCreative() { return mc.isCreative(); }
            public void removeExperienceLevels(int levels) { mc.removeExperienceLevels(levels); }
        };
    }

    /**
     * ContainerEnchantment.transferStackInSlot: the table's slots go to the
     * inventory from the end; lapis goes to the lapis slot; anything else
     * goes, one item, into an empty item slot.
     */
    @Override
    protected void shiftClick(SlotPos pos) {
        ItemStack stack = pos.slot().get();
        if (stack == null) return;
        InventoryPlayer inventory = inventory();
        if (pos.kind() == Kind.OTHER) {
            mergeBackwards(inventory.mainInventory, stack);
            pos.slot().set(stack.isEmpty() ? null : stack);
            return;
        }
        if (EnchantingTable.isLapis(stack)) {
            Slot lapis = slots.get(1).slot();
            ItemStack there = lapis.get();
            if (there == null) lapis.set(stack.split(stack.count()));
            else if (there.canMerge(stack)) {
                there.mergeFrom(stack);
                lapis.set(there);
            }
        } else {
            Slot item = slots.get(0).slot();
            if (item.get() != null) return;
            boolean tagged = stack.writeTag() != null;
            if (tagged && stack.count() == 1) item.set(stack.split(1));
            else item.set(new ItemStack(stack.split(1).item(), 1, stack.damage()));
        }
        pos.slot().set(stack.isEmpty() ? null : stack);
    }

    /** mergeItemStack(stack, 2, 38, true): hotbar from its last slot, then the main slots from the bottom right. */
    static void mergeBackwards(ItemStack[] slots, ItemStack stack) {
        int[] order = new int[InventoryPlayer.MAIN_SIZE];
        int n = 0;
        for (int i = InventoryPlayer.HOTBAR_SIZE - 1; i >= 0; i--) order[n++] = i;
        for (int i = InventoryPlayer.MAIN_SIZE - 1; i >= InventoryPlayer.HOTBAR_SIZE; i--) order[n++] = i;
        for (int i : order) {
            if (stack.isEmpty()) return;
            if (slots[i] != null && slots[i].canMerge(stack)) slots[i].mergeFrom(stack);
        }
        for (int i : order) {
            if (stack.isEmpty()) return;
            if (slots[i] == null) slots[i] = stack.split(stack.count());
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        for (int i = 0; i < 3; i++) {
            int dx = mouseX - (guiLeft + 60), dy = mouseY - (guiTop + 14 + 19 * i);
            if (dx >= 0 && dy >= 0 && dx < 108 && dy < 19) table.enchant(i, player());
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!mc.canInteractWith(tableX, tableY, tableZ, "enchanting_table")) {
            mc.displayGuiScreen(null);
            return;
        }
        tickBook();
    }

    /** GuiEnchantment.func_147068_g: a new item flips to another page; offers open the book. */
    void tickBook() {
        ItemStack item = table.item();
        if (!sameStack(item, lastItem)) {
            lastItem = item == null ? null : item.copy();
            do {
                flipTarget += bookRandom.nextInt(4) - bookRandom.nextInt(4);
            } while (flip <= flipTarget + 1f && flip >= flipTarget - 1f);
        }
        ticks++;
        flipPrev = flip;
        openPrev = open;
        boolean offers = false;
        for (int level : table.levels) if (level != 0) offers = true;
        open = Math.max(0f, Math.min(1f, open + (offers ? 0.2f : -0.2f)));
        float step = Math.max(-0.2f, Math.min(0.2f, (flipTarget - flip) * 0.4f));
        flipSpeed += (step - flipSpeed) * 0.9f;
        flip += flipSpeed;
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        return a.item() == b.item() && a.count() == b.count() && a.damage() == b.damage() && a.tagsEqual(b);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        glColor4f(1, 1, 1, 1);
        mc.textures().bind(BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        drawBook(partialTicks);
        Random names = new Random(mc.xpSeed());
        ItemStack lapis = table.lapis();
        int lapisCount = lapis == null ? 0 : lapis.count();
        boolean creative = mc.isCreative();
        for (int i = 0; i < 3; i++) {
            int left = guiLeft + 60, textLeft = left + 20, rowTop = guiTop + 14 + 19 * i;
            String words = randomName(names);
            glColor4f(1, 1, 1, 1);
            mc.textures().bind(BACKGROUND);
            int level = table.levels[i];
            if (level == 0) {
                drawTexturedModalRect(left, rowTop, 0, 185, 108, 19);
                continue;
            }
            int color = 6839882;
            FontRenderer galactic = mc.galacticFont();
            if ((lapisCount < i + 1 || mc.experienceLevel() < level) && !creative) {
                drawTexturedModalRect(left, rowTop, 0, 185, 108, 19);
                drawTexturedModalRect(left + 1, rowTop + 1, 16 * i, 239, 16, 16);
                galactic.drawSplitString(words, textLeft, rowTop + 2, 86, (color & 0xFEFEFE) >> 1);
                color = 4226832;
            } else {
                int dx = mouseX - left, dy = mouseY - rowTop;
                if (dx >= 0 && dy >= 0 && dx < 108 && dy < 19) {
                    drawTexturedModalRect(left, rowTop, 0, 204, 108, 19);
                    color = 16777088;
                } else {
                    drawTexturedModalRect(left, rowTop, 0, 166, 108, 19);
                }
                drawTexturedModalRect(left + 1, rowTop + 1, 16 * i, 223, 16, 16);
                galactic.drawSplitString(words, textLeft, rowTop + 2, 86, color);
                color = 8453920;
            }
            String number = Integer.toString(level);
            fontRendererObj.drawStringWithShadow(number, textLeft + 86 - fontRendererObj.getStringWidth(number), rowTop + 2 + 7, color);
        }
        glColor4f(1, 1, 1, 1);
    }

    /** EnchantmentNameParts.generateNewRandomName: three or four of the words. */
    static String randomName(Random random) {
        int count = random.nextInt(2) + 3;
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) name.append(' ');
            name.append(NAME_PARTS[random.nextInt(NAME_PARTS.length)]);
        }
        return name.toString();
    }

    /**
     * The open book above the slots, in its own 320x240 (GUI pixel) viewport
     * centred on the screen with a 90 degree perspective, pushed left and up.
     */
    private void drawBook(float partialTicks) {
        if (mc.displayWidth() <= 0 || width <= 0) return;
        int scale = Math.max(1, mc.displayWidth() / width);
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glViewport((width - 320) / 2 * scale, (height - 240) / 2 * scale, 320 * scale, 240 * scale);
        glTranslatef(-0.34f, 0.23f, 0f);
        float near = 9f, top = near * (float) Math.tan(Math.toRadians(45.0));
        glFrustum(-top * 1.3333334f, top * 1.3333334f, -top, top, near, 80f);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        boolean depth = glIsEnabled(GL_DEPTH_TEST);
        glClear(GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        StandardLighting.enable();
        glTranslatef(0f, 3.3f, -16f);
        glScalef(5f, 5f, 5f);
        glRotatef(180f, 0f, 0f, 1f);
        mc.textures().bind(BookModel.TEXTURE);
        glRotatef(20f, 1f, 0f, 0f);
        float spread = openPrev + (open - openPrev) * partialTicks;
        glTranslatef((1f - spread) * 0.2f, (1f - spread) * 0.1f, (1f - spread) * 0.25f);
        glRotatef(-(1f - spread) * 90f - 90f, 0f, 1f, 0f);
        glRotatef(180f, 1f, 0f, 0f);
        float page = flipPrev + (flip - flipPrev) * partialTicks;
        glColor4f(1, 1, 1, 1);
        BookModel.render(0f, BookModel.flipFraction(page, 0.25f), BookModel.flipFraction(page, 0.75f), spread);
        StandardLighting.disable();
        glClear(GL_DEPTH_BUFFER_BIT);
        if (!depth) glDisable(GL_DEPTH_TEST);
        glMatrixMode(GL_PROJECTION);
        glViewport(0, 0, mc.displayWidth(), mc.displayHeight());
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (inventory().getItemStack() != null) return;
        List<String> lines = offerTooltip(mouseX, mouseY);
        if (lines != null) drawHoveringText(lines, mouseX, mouseY);
    }

    /** The tooltip over an offer, or null. */
    List<String> offerTooltip(int mouseX, int mouseY) {
        boolean creative = mc.isCreative();
        ItemStack lapis = table.lapis();
        int lapisCount = lapis == null ? 0 : lapis.count();
        for (int i = 0; i < 3; i++) {
            int level = table.levels[i], clue = table.clues[i], cost = i + 1;
            int dx = mouseX - (guiLeft + 60), dy = mouseY - (guiTop + 14 + 19 * i);
            if (dx < 0 || dy < 0 || dx >= 108 || dy >= 17 || level <= 0 || clue < 0) continue;
            List<String> lines = new ArrayList<>();
            Enchantment enchantment = Enchantment.byId(clue & 255);
            if (enchantment != null) {
                String name = GuiContainer.enchantmentName(clue & 255, (clue & 0xFF00) >> 8);
                lines.add(FontRenderer.FORMAT_CHAR + "f" + FontRenderer.FORMAT_CHAR + "o" + I18n.format("container.enchant.clue", name));
            }
            if (!creative) {
                lines.add("");
                if (mc.experienceLevel() < level) {
                    lines.add(FontRenderer.FORMAT_CHAR + "c" + "Level Requirement: " + level);
                } else {
                    String lapisText = cost == 1 ? I18n.format("container.enchant.lapis.one")
                        : I18n.format("container.enchant.lapis.many", cost);
                    lines.add(FontRenderer.FORMAT_CHAR + (lapisCount >= cost ? "7" : "c") + lapisText);
                    String levelText = cost == 1 ? I18n.format("container.enchant.level.one")
                        : I18n.format("container.enchant.level.many", cost);
                    lines.add(FontRenderer.FORMAT_CHAR + "7" + levelText);
                }
            }
            return lines;
        }
        return null;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(I18n.format("container.enchant"), 12, 5, 0x404040);
        fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 96 + 2, 0x404040);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (table != null) table.returnAll(inventory(), mc::dropPlayerItem);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}

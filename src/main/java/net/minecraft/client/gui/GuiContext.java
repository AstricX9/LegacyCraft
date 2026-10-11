package net.minecraft.client.gui;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.item.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.storage.LevelInfo;
import net.minecraft.world.storage.SaveFormat;

import java.nio.file.Path;
import java.util.Locale;

/**
 * What a screen may ask of the running client. {@link net.minecraft.client.Minecraft}
 * implements it; tests can supply a fake. Keeping screens behind this seam
 * stops GUI code from becoming the source of truth for game state (master
 * context §7: "Do not let GUI code become the source of truth").
 */
public interface GuiContext {
    FontRenderer font();
    GuiTextures textures();
    GameSettings settings();
    IResourceManager resources();
    BlockRegistry blocks();
    BiomeRegistry biomes();
    Path gameDirectory();
    SaveFormat saves();

    void displayGuiScreen(GuiScreen screen);
    GuiScreen currentScreen();
    void playClickSound();
    boolean isKeyDown(int glfwKey);
    String getClipboard();
    void setClipboard(String text);
    int displayWidth();
    int displayHeight();

    /** Saves the options file (vanilla saves on every change made through a menu). */
    void saveOptions();
    /** Re-reads the language files after the language changed. */
    void refreshLanguage();

    /** Reloads every resource from the selected resource packs (no-op where there is no GL context). */
    default void refreshResources() {}

    /** The resource pack folder and stack; null in tests. */
    default net.minecraft.client.resources.ResourcePackRepository resourcePacks() { return null; }
    /** Applies display options that need the window (vsync, fullscreen). */
    void applyDisplaySettings();

    /** Starts the integrated server on a save folder; {@code settings} is null when loading an existing world. */
    void launchIntegratedServer(String folderName, String worldName, WorldSettings settings);
    /** Converts a vanilla Anvil world to LegacyCraft chunks, then opens it (vanilla's "Converting world" step). */
    default void convertAndLaunch(String folderName, String worldName) {}
    boolean isInGame();
    /** The loaded world's level data, or null on the title screen. */
    LevelInfo currentLevel();
    void saveAndQuitToTitle();
    void respawnPlayer();
    /** Hardcore death: delete the world and return to the title screen. */
    void deleteWorldAndQuit();
    void shutdown();

    /** Draws a block's inventory-style icon at 16x16 (flat world layer list, hotbar). */
    void renderBlockIcon(BlockState state, int x, int y);

    // ---- in-game interface ----------------------------------------------------------------

    /** Draws item stacks in slots; null where no GL context exists (tests). */
    ItemRenderer itemRenderer();
    CreativeTabs creativeTabs();
    /** The local player's inventory, or null on the title screen. */
    InventoryPlayer playerInventory();
    /** The local player's active effects (empty on the title screen and in tests). */
    default java.util.Collection<net.minecraft.potion.PotionEffect> activePotionEffects() { return java.util.List.of(); }
    /** The crafting recipes, or null when none are loaded (tests). */
    default net.minecraft.item.crafting.CraftingManager crafting() { return null; }
    /** An item by its LegacyCraft name ("bucket"), or null. */
    default Item itemByName(String name) { return null; }
    /**
     * Whether a block-backed screen may stay open: the block at the position is
     * still {@code block} and the player is within 8 blocks of its centre
     * (Container.canInteractWith).
     */
    default boolean canInteractWith(int x, int y, int z, String block) { return true; }

    /** The local player's achievements, or null outside a world. */
    default net.minecraft.stats.StatFile stats() { return null; }
    /** The local player's name. */
    default String playerName() { return "Player"; }
    /** Something was crafted and taken from a crafting result (achievement triggers). */
    default void onItemCrafted(ItemStack stack) {}
    /** The furnace recipes, or null when none are loaded (tests). */
    default net.minecraft.item.crafting.FurnaceRecipes smelting() { return null; }
    /** The furnace at a block position in the loaded world, or null. */
    default net.minecraft.inventory.Furnace furnaceAt(int x, int y, int z) { return null; }
    /** {@code count} smelted items were taken out of a furnace (SlotFurnaceOutput.onCrafting: experience, achievements). */
    default void onItemSmelted(Item item, int count) {}
    /** Draws a block texture ("stone" for textures/blocks/stone.png) from the block atlas, {@code size} GUI pixels square. */
    default void drawBlockTexture(String texture, int x, int y, int size) {}

    /** The enchanting table's Standard Galactic font ({@code font/ascii_sga.png}); the normal font when there is none. */
    default FontRenderer galacticFont() { return font(); }
    /** The local player's experience level. */
    default int experienceLevel() { return 0; }
    /** The local player's enchanting seed (EntityPlayer.xpSeed), which decides the table's offers. */
    default int xpSeed() { return 0; }
    /** Takes levels for an enchantment (EntityPlayer.removeExperienceLevel), which also picks a new seed. */
    default void removeExperienceLevels(int levels) {}
    /** How many bookshelves feed the enchanting table at a position. */
    default int bookshelvesAround(int x, int y, int z) { return 0; }
    /**
     * An anvil's result was taken: {@code levels} are charged and the anvil
     * may wear a step or break (SlotRepair.onPickupFromSlot), with its sound.
     */
    default void onAnvilUsed(int x, int y, int z, int levels) {}

    /** A stack's display name (ItemStack.getDisplayName): its anvil name, else the item's name. */
    default String stackName(ItemStack stack) { return stack.hasCustomName() ? stack.customName() : itemName(stack.item()); }

    /** Whether the local player is in Creative mode (instant break, flight, the creative inventory). */
    boolean isCreative();

    // ---- chat ------------------------------------------------------------------------------

    /** The chat lines above the hotbar, or null outside a world. */
    default GuiNewChat chatGui() { return null; }
    /** Sends what was typed in chat: a "/" command runs on the integrated server, anything else is said as the player. */
    default void sendChatMessage(String message) {}
    /** Tab completions for the chat text before the cursor. */
    default java.util.List<String> tabComplete(String beforeCursor) { return java.util.List.of(); }
    /** Shows a line in chat, replacing the earlier line with the same non-zero id. */
    default void printChatLine(String text, int id) {}

    /**
     * Draws the local player posed in a screen, feet at (x, y), {@code scale}
     * GUI pixels per block, turned toward the mouse offsets given.
     */
    default void drawPlayerOnScreen(int x, int y, int scale, float lookX, float lookY) {}

    /** Throws a stack from the player (clicking outside an inventory, the drop key); it lands as an item entity. */
    void dropPlayerItem(ItemStack stack);

    /**
     * An item's display name: its vanilla translation from the creative tab
     * data, else the LegacyCraft item name made readable ("oak_planks" to
     * "Oak Planks").
     */
    default String itemName(Item item) {
        String variant = net.minecraft.item.VanillaItems.displayName(item, I18n::format);
        if (variant != null) return variant;
        CreativeTabs tabs = creativeTabs();
        String key = tabs == null ? null : tabs.nameKey(item);
        if (key != null && I18n.hasKey(key)) return I18n.format(key);
        StringBuilder name = new StringBuilder();
        for (String word : item.name().path().split("_")) {
            if (word.isEmpty()) continue;
            if (name.length() > 0) name.append(' ');
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return name.toString();
    }
}

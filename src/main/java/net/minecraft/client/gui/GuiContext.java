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
    /** Applies display options that need the window (vsync, fullscreen). */
    void applyDisplaySettings();

    /** Starts the integrated server on a save folder; {@code settings} is null when loading an existing world. */
    void launchIntegratedServer(String folderName, String worldName, WorldSettings settings);
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

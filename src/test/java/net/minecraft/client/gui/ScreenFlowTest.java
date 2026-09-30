package net.minecraft.client.gui;

import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.item.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.storage.LevelInfo;
import net.minecraft.world.storage.SaveFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Drives the menus through a fake client (no window, no OpenGL): clicks
 * buttons by their captions and checks where each click leads. Captions are
 * the raw translation keys because the language table is empty here.
 */
final class ScreenFlowTest {
    private Fake mc;

    @BeforeEach
    void setUp() throws Exception {
        I18n.set(Map.of());
        mc = new Fake(Files.createTempDirectory("lc-gui"));
    }

    @Test
    void titleScreenReachesEveryTopLevelMenu() {
        GuiMainMenu title = new GuiMainMenu();
        mc.displayGuiScreen(title);
        click("menu.singleplayer");
        assertInstanceOf(GuiSelectWorld.class, mc.screen);
        click("gui.cancel");
        assertSame(title, mc.screen);
        click("menu.multiplayer");
        assertInstanceOf(GuiMultiplayer.class, mc.screen);
        click("gui.cancel");
        click("menu.options");
        assertInstanceOf(GuiOptions.class, mc.screen);
        click("gui.done");
        assertSame(title, mc.screen);
        click("menu.quit");
        assertTrue(mc.quit);
    }

    @Test
    void createWorldFlowPassesVanillaSettingsToTheServer() {
        GuiSelectWorld select = new GuiSelectWorld(new GuiMainMenu());
        mc.displayGuiScreen(select);
        click("selectWorld.create");
        GuiCreateWorld create = assertInstanceOf(GuiCreateWorld.class, mc.screen);
        // Name field is focused: type a name, then open More World Options.
        for (int i = 0; i < "selectWorld.newWorld".length(); i++) mc.screen.keyPressed(GLFW_KEY_BACKSPACE, 0);
        type("Test/World");
        clickPrefix("selectWorld.gameMode");  // survival -> hardcore
        clickPrefix("selectWorld.gameMode");  // hardcore -> creative
        click("selectWorld.moreWorldOptions");
        type("gargamel");
        clickPrefix("selectWorld.mapType");   // default -> flat
        clickPrefix("selectWorld.mapFeatures");
        click("selectWorld.create");

        assertEquals("Test_World", mc.launchedFolder, "'/' is not allowed in folder names");
        assertEquals("Test/World", mc.launchedName);
        WorldSettings settings = mc.launchedSettings;
        assertEquals("gargamel".hashCode(), settings.seed());
        assertEquals(WorldSettings.GameType.CREATIVE, settings.gameType());
        assertTrue(settings.commandsAllowed(), "creative turns cheats on unless the player chose");
        assertEquals(WorldType.FLAT, settings.worldType());
        assertFalse(settings.mapFeatures());
        assertFalse(settings.hardcore());
        assertSame(create, mc.screen);
    }

    @Test
    void hardcoreLocksCheatsAndBonusChest() {
        mc.displayGuiScreen(new GuiCreateWorld(new GuiMainMenu()));
        clickPrefix("selectWorld.gameMode");
        click("selectWorld.moreWorldOptions");
        GuiButton cheats = button("selectWorld.allowCommands", true);
        assertFalse(cheats.enabled);
        assertFalse(button("selectWorld.bonusItems", true).enabled);
        click("selectWorld.create");
        assertTrue(mc.launchedSettings.hardcore());
        assertEquals(WorldSettings.GameType.SURVIVAL, mc.launchedSettings.gameType());
        assertFalse(mc.launchedSettings.commandsAllowed());
    }

    @Test
    void worldListRenamesAndDeletesWithConfirmation() throws Exception {
        LevelInfo info = LevelInfo.create("Old Name", new WorldSettings(5, WorldSettings.GameType.SURVIVAL, true, false,
            WorldType.DEFAULT, true, false, ""));
        mc.saves.saveWorldInfo("w1", info);
        GuiSelectWorld select = new GuiSelectWorld(new GuiMainMenu());
        mc.displayGuiScreen(select);
        assertFalse(button("selectWorld.rename", false).enabled, "nothing selected yet");
        // Click the first row of the list (rows start at top 32 + 4).
        select.mouseClicked(select.width / 2, 40, 0);
        assertTrue(button("selectWorld.rename", false).enabled);
        click("selectWorld.rename");
        GuiRenameWorld rename = assertInstanceOf(GuiRenameWorld.class, mc.screen);
        rename.keyPressed(GLFW_KEY_A, GLFW_MOD_CONTROL);
        type("New Name");
        click("selectWorld.renameButton");
        assertEquals("New Name", mc.saves.getWorldInfo("w1").levelName);

        select.mouseClicked(select.width / 2, 40, 0);
        click("selectWorld.delete");
        assertInstanceOf(GuiYesNo.class, mc.screen);
        click("selectWorld.deleteButton");
        assertSame(select, mc.screen);
        assertNull(mc.saves.getWorldInfo("w1"));
    }

    @Test
    void reCreateCopiesTheWorldsSettings() throws Exception {
        LevelInfo info = LevelInfo.create("Base", new WorldSettings(777, WorldSettings.GameType.CREATIVE, false, false,
            WorldType.AMPLIFIED, true, false, ""));
        mc.saves.saveWorldInfo("base", info);
        GuiSelectWorld select = new GuiSelectWorld(new GuiMainMenu());
        mc.displayGuiScreen(select);
        select.mouseClicked(select.width / 2, 40, 0);
        click("selectWorld.recreate");
        assertInstanceOf(GuiCreateWorld.class, mc.screen);
        click("selectWorld.create");
        assertEquals(777, mc.launchedSettings.seed());
        assertEquals(WorldType.AMPLIFIED, mc.launchedSettings.worldType());
        assertEquals(WorldSettings.GameType.CREATIVE, mc.launchedSettings.gameType());
        assertFalse(mc.launchedSettings.mapFeatures());
    }

    @Test
    void everyOptionsSubScreenOpensAndReturns() {
        GuiOptions options = new GuiOptions(new GuiMainMenu());
        String[] subScreens = {"options.skinCustomisation", "options.sounds", "options.video", "options.controls",
            "options.language", "options.chat.title", "options.resourcepack", "options.snooper.view", "options.stream"};
        for (String caption : subScreens) {
            mc.displayGuiScreen(options);
            click(caption);
            assertNotSame(options, mc.screen, caption);
            GuiScreen opened = mc.screen;
            GuiButton done = button("gui.done", true);
            opened.mouseClicked(done.xPosition + 1, done.yPosition + 1, 0);
            assertSame(options, mc.screen, caption + " returns to Options");
        }
        assertTrue(mc.optionsSaved > 0);
    }

    @Test
    void controlsRebindsAKeyAndEscapeUnbindsIt() {
        GuiControls controls = new GuiControls(new GuiOptions(new GuiMainMenu()));
        mc.displayGuiScreen(controls);
        // The first binding row sits under the "Gameplay" header: header row at 67, first key row at 87.
        int changeX = controls.width / 2 - 126 + 105 + 5;
        controls.mouseClicked(changeX, 87 + 5, 0);
        controls.keyPressed(GLFW_KEY_K, 0);
        GameSettings settings = mc.settings;
        long rebound = settings.keyBindings.stream().filter(binding -> binding.getKeyCode() == 37).count();
        assertEquals(1, rebound, "one binding moved to K (LWJGL code 37)");
        controls.mouseClicked(changeX, 87 + 5, 0);
        controls.keyPressed(GLFW_KEY_ESCAPE, 0);
        assertEquals(0, settings.keyBindings.stream().filter(binding -> binding.getKeyCode() == 37).count());
        assertEquals(1, settings.keyBindings.stream().filter(binding -> binding.getKeyCode() == 0
            && binding.getKeyCodeDefault() != 0).count(), "Escape unbinds (NONE)");
        assertSame(controls, mc.screen, "Escape while rebinding does not close the screen");
    }

    @Test
    void directConnectRemembersTheAddressAndFailsHonestly() {
        GuiMultiplayer multiplayer = new GuiMultiplayer(new GuiMainMenu());
        mc.displayGuiScreen(multiplayer);
        click("selectServer.direct");
        type("play.example.org");
        click("selectServer.select");
        assertEquals("play.example.org", mc.settings.lastServer);
        GuiConnecting connecting = assertInstanceOf(GuiConnecting.class, mc.screen);
        for (int i = 0; i < 10; i++) connecting.updateScreen();
        assertInstanceOf(GuiDisconnected.class, mc.screen);
        click("gui.toMenu");
        assertSame(multiplayer, mc.screen);
    }

    @Test
    void pauseMenuOffersVanillaButtonsAndQuits() {
        mc.inGame = true;
        mc.displayGuiScreen(new GuiIngameMenu());
        assertFalse(button("menu.shareToLan", false).enabled);
        click("menu.returnToGame");
        assertNull(mc.screen);
        mc.displayGuiScreen(new GuiIngameMenu());
        click("menu.returnToMenu");
        assertTrue(mc.savedAndQuit);
    }

    @Test
    void deathScreenWaitsTwentyTicksAndConfirmsQuitting() {
        mc.inGame = true;
        GuiGameOver death = new GuiGameOver(false, 0);
        mc.displayGuiScreen(death);
        assertFalse(button("deathScreen.respawn", false).enabled);
        for (int i = 0; i < 20; i++) death.updateScreen();
        click("deathScreen.titleScreen");
        GuiYesNo confirm = assertInstanceOf(GuiYesNo.class, mc.screen);
        for (int i = 0; i < 20; i++) confirm.updateScreen();
        click("deathScreen.respawn");
        assertTrue(mc.respawned);
    }

    // ---- helpers --------------------------------------------------------------------------

    private void type(String text) { for (char c : text.toCharArray()) mc.screen.charTyped(c); }

    private void click(String caption) { press(button(caption, false)); }

    private void clickPrefix(String prefix) { press(button(prefix, true)); }

    private void press(GuiButton button) {
        assertTrue(button.enabled, button.displayString + " is enabled");
        mc.screen.mouseClicked(button.xPosition + 1, button.yPosition + 1, 0);
        if (mc.screen != null) mc.screen.mouseReleased(button.xPosition + 1, button.yPosition + 1, 0);
    }

    private GuiButton button(String caption, boolean prefix) {
        for (GuiButton button : mc.screen.buttonList)
            if (button.visible && (prefix ? button.displayString.startsWith(caption) : button.displayString.equals(caption))) return button;
        List<String> captions = new ArrayList<>();
        for (GuiButton button : mc.screen.buttonList) captions.add(button.displayString + (button.visible ? "" : " (hidden)"));
        throw new AssertionError("No button '" + caption + "' on " + mc.screen.getClass().getSimpleName() + ": " + captions);
    }

    /** A client with no window: records navigation and launches. */
    static final class Fake implements GuiContext {
        final Path directory;
        final SaveFormat saves;
        final GameSettings settings = new GameSettings();
        final FontRenderer font = new FontRenderer(null);
        GuiScreen screen;
        boolean quit, inGame, savedAndQuit, respawned;
        int optionsSaved;
        String launchedFolder, launchedName;
        WorldSettings launchedSettings;
        CreativeTabs creativeTabs;
        InventoryPlayer inventory = new InventoryPlayer();
        boolean creative = true, shiftDown;
        final List<ItemStack> dropped = new ArrayList<>();

        Fake(Path directory) {
            this.directory = directory;
            this.saves = new SaveFormat(directory.resolve("saves"));
        }

        @Override public FontRenderer font() { return font; }
        @Override public GuiTextures textures() { return null; }
        @Override public GameSettings settings() { return settings; }
        @Override public IResourceManager resources() {
            return new IResourceManager() {
                public InputStream open(ResourceLocation location) throws java.io.IOException { throw new FileNotFoundException(location.toString()); }
                public boolean exists(ResourceLocation location) { return false; }
                public List<ResourceLocation> list(ResourceLocation directory, String suffix) { return List.of(); }
            };
        }
        @Override public BlockRegistry blocks() { return null; }
        @Override public BiomeRegistry biomes() { return null; }
        @Override public Path gameDirectory() { return directory; }
        @Override public SaveFormat saves() { return saves; }
        @Override public void displayGuiScreen(GuiScreen next) {
            if (screen != null && screen != next) screen.onGuiClosed();
            screen = next;
            if (next != null) next.setWorldAndResolution(this, 427, 240);
        }
        @Override public GuiScreen currentScreen() { return screen; }
        @Override public void playClickSound() {}
        @Override public boolean isKeyDown(int glfwKey) {
            return shiftDown && (glfwKey == GLFW_KEY_LEFT_SHIFT || glfwKey == GLFW_KEY_RIGHT_SHIFT);
        }
        @Override public String getClipboard() { return ""; }
        @Override public void setClipboard(String text) {}
        @Override public int displayWidth() { return 854; }
        @Override public int displayHeight() { return 480; }
        @Override public void saveOptions() { optionsSaved++; }
        @Override public void refreshLanguage() {}
        @Override public void applyDisplaySettings() {}
        @Override public void launchIntegratedServer(String folderName, String worldName, WorldSettings worldSettings) {
            launchedFolder = folderName; launchedName = worldName; launchedSettings = worldSettings;
        }
        @Override public boolean isInGame() { return inGame; }
        @Override public LevelInfo currentLevel() { return null; }
        @Override public void saveAndQuitToTitle() { savedAndQuit = true; }
        @Override public void respawnPlayer() { respawned = true; }
        @Override public void deleteWorldAndQuit() {}
        @Override public void shutdown() { quit = true; }
        @Override public void renderBlockIcon(BlockState state, int x, int y) {}
        @Override public ItemRenderer itemRenderer() { return null; }
        @Override public CreativeTabs creativeTabs() { return creativeTabs; }
        @Override public InventoryPlayer playerInventory() { return inventory; }
        @Override public boolean isCreative() { return creative; }
        @Override public void dropPlayerItem(ItemStack stack) { dropped.add(stack); }

        /** The furnace a furnace screen opens on, and what was reported taken out of furnaces ("iron_ingot x3"). */
        net.minecraft.inventory.Furnace furnace;
        final List<String> smelted = new ArrayList<>();
        @Override public net.minecraft.inventory.Furnace furnaceAt(int x, int y, int z) { return furnace; }
        @Override public void onItemSmelted(net.minecraft.item.Item item, int count) { smelted.add(item.name().path() + " x" + count); }

        /** The player's levels and enchanting seed, the shelves around any table, and the levels anvils charged. */
        int level, seed, shelves;
        final List<Integer> anvilCharges = new ArrayList<>();
        @Override public int experienceLevel() { return level; }
        @Override public int xpSeed() { return seed; }
        @Override public void removeExperienceLevels(int levels) {
            level = Math.max(0, level - levels);
            seed++;
        }
        @Override public int bookshelvesAround(int x, int y, int z) { return shelves; }
        @Override public void onAnvilUsed(int x, int y, int z, int levels) {
            anvilCharges.add(levels);
            level -= levels;
        }
        @Override public net.minecraft.item.Item itemByName(String name) {
            return name.equals("enchanted_book") ? net.minecraft.item.TestItems.item(name, 1, 0) : null;
        }
    }
}

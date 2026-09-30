package net.minecraft.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDrops;
import net.minecraft.block.BlockPlacement;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockUpdates;
import net.minecraft.block.state.ActualState;
import net.minecraft.client.audio.SoundRegistry;
import net.minecraft.client.entity.Player;
import net.minecraft.client.entity.PlayerAnimation;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.CommandSender;
import net.minecraft.client.multiplayer.PlayerController;
import net.minecraft.client.gui.GuiContext;
import net.minecraft.client.gui.GuiContainerCreative;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiInventory;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiOverlayDebug;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextures;
import net.minecraft.client.gui.I18n;
import net.minecraft.client.gui.LoadingScreenRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.DebugRenderer;
import net.minecraft.client.renderer.FirstPersonRenderer;
import net.minecraft.client.renderer.PlayerModel;
import net.minecraft.client.renderer.StandardLighting;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.LightMap;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.block.model.BakedModel;
import net.minecraft.client.renderer.block.model.BlockModelManager;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.settings.KeyCodes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityItem;
import net.minecraft.inventory.InventoryPlayer;
import net.minecraft.item.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolValues;
import net.minecraft.server.IntegratedServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeNames;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.gen.FlatGeneratorInfo;
import net.minecraft.world.storage.LevelInfo;
import net.minecraft.world.storage.SaveFormat;
import net.minecraft.world.storage.WorldStorage;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * The client: window, input, menus, the integrated-server session and
 * rendering. Starts on the 1.8.9 title screen; singleplayer worlds live in
 * {@code saves/<folder>} with a vanilla-shaped {@code level.dat} plus
 * LegacyCraft's own chunk files (DEV-003). Options live in {@code options.txt}.
 *
 * <p>Developer entry points (none of these exist in vanilla):
 * <ul>
 *   <li>{@code --world=<folder>} opens (or creates) a save and skips the menus.</li>
 *   <li>{@code --seed=<n or text>}, {@code --spawn=<x>,<z>} start a throwaway, unsaved world at that seed/position
 *       (text seeds turn into numbers the way Create World does, so {@code --seed=amazing} is vanilla's "amazing").</li>
 *   <li>{@code --pos=<x>,<y>,<z>,<yaw>,<pitch>} puts the camera exactly where vanilla's F3 screen says (XYZ and the
 *       Facing angles), flying, for side-by-side screenshots with the real game.</li>
 *   <li>{@code --capture=<file.png>} renders one in-game frame to a PNG and quits (implies a throwaway world).</li>
 *   <li>{@code --debug} opens the F3 screen immediately.</li>
 *   <li>{@code --gamedir=<path>} moves saves/options/servers.dat (defaults to the working directory).</li>
 * </ul>
 */
public final class Minecraft implements GuiContext {
    private static final int INITIAL_WIDTH = 854, INITIAL_HEIGHT = 480;
    /** Vanilla prepares a 25x25-chunk spawn area; LegacyCraft shrinks it to the view distance (DEV-009). */
    private static final int MAX_SPAWN_RADIUS = 12;

    private long window;
    private int width = INITIAL_WIDTH, height = INITIAL_HEIGHT;
    private int windowWidth = INITIAL_WIDTH, windowHeight = INITIAL_HEIGHT;
    private int windowedX = 90, windowedY = 70, windowedWidth = INITIAL_WIDTH, windowedHeight = INITIAL_HEIGHT;
    private boolean fullscreenApplied;
    private Path gameDirectory = Path.of(".");
    private SaveFormat saveFormat;
    private final GameSettings gameSettings = new GameSettings();
    private final IResourceManager resourceManager = new ClasspathResourceManager();
    private BlockRegistry blockRegistry;
    private BiomeRegistry biomeRegistry;
    private BiomeColors biomeColors;
    private ItemRegistry itemRegistry;
    private SoundRegistry soundRegistry;
    private net.minecraft.client.audio.SoundSystem soundSystem;
    private net.minecraft.client.audio.SoundHandler soundHandler;
    private net.minecraft.client.audio.MusicTicker musicTicker;
    private boolean soundsPaused;
    /** Cave ambience (World.playMoodSoundAndCheckLight): countdown, the LCG picking spots, chunks already tried. */
    private int ambientCountdown = new Random().nextInt(12000), caveLcg = new Random().nextInt();
    private final java.util.Set<Long> moodChunksTried = new java.util.HashSet<>();
    private BlockModelManager modelManager;
    private TextureAtlas atlas;
    private FontRenderer fontRenderer;
    private GuiTextures guiTextures;
    private ItemRenderer itemRenderer;
    private FirstPersonRenderer firstPersonRenderer;
    private final PlayerModel playerModel = new PlayerModel();
    private PlayerAnimation playerAnimation = new PlayerAnimation();
    private CreativeTabs creativeTabs;
    private net.minecraft.item.crafting.CraftingManager crafting;
    /** Ticks the renderer has run (EntityRenderer.rendererUpdateCount): drives the portal wobble. */
    private int rendererUpdateCount;
    private net.minecraft.item.crafting.FurnaceRecipes smelting;
    private GuiIngame ingameGui;
    /** The local player's achievements, and the pop-up that announces them. */
    private net.minecraft.stats.StatFile stats;
    private final net.minecraft.client.gui.GuiAchievementToast achievementToast = new net.minecraft.client.gui.GuiAchievementToast();
    private BlockDrops blockDrops;
    private BlockDrops.Items blockItems;
    private final PlayerController playerController = new PlayerController();
    private final Random random = new Random();
    /** Ticks until holding the use button places again (vanilla rightClickDelayTimer). */
    private int rightClickDelayTimer;
    private boolean deathHandled;
    private final GuiOverlayDebug debugOverlay = new GuiOverlayDebug();
    private final LoadingScreenRenderer loadingScreen = new LoadingScreenRenderer();
    private String glVendor = "", glRenderer = "", glVersion = "";

    // The singleplayer session; all null on the title screen.
    private World world;
    private Player player;
    private IntegratedServer server;
    private WorldRenderer renderer;
    private SkyRenderer sky;
    private ParticleManager particles;
    private FontRenderer galacticFont;
    /** The books over enchanting tables. */
    private final net.minecraft.client.renderer.EnchantingTableBooks tableBooks = new net.minecraft.client.renderer.EnchantingTableBooks();
    /**
     * The local player's fire ticks (above 0 = burning; players start at
     * -20, their fire resistance).
     */
    private int playerFire = -20;
    private LevelInfo levelInfo;
    /** Save folder of the running world, or null for a throwaway command-line world. */
    private String worldFolder;
    private SpawnPreparation preparing;

    private GuiScreen currentScreen;
    private int lastScaledWidth, lastScaledHeight, lastScaleFactor;
    private InventoryPlayer inventory;
    private boolean mouseCaptured;
    private boolean firstMouse = true;
    private double cursorX, cursorY, lastMouseX, lastMouseY;
    private boolean f3Down, f3ComboUsed;
    private int fps, frames, chunkUpdates;
    private double fpsTimer;
    private String screenshotRequested;
    private boolean quitAfterScreenshot;
    private boolean running = true;

    // Command-line state.
    private long cliSeed = 889L;
    private Double cliSpawnX, cliSpawnZ;
    /** --pos: x, y, z, vanilla yaw, pitch. */
    private double[] cliPos;
    private boolean cliThrowawayWorld;
    /** The local player's name (offline; {@code --username=} sets it). */
    private String playerName = "Player";
    private final CommandHandler commands = new CommandHandler();
    private final ChatSender chatSender = new ChatSender();
    private String cliWorld;

    /** Chunks generated around spawn behind the "Building terrain" screen before play starts. */
    private static final class SpawnPreparation {
        final List<int[]> chunks = new ArrayList<>();
        int next;
        final Runnable onDone;
        SpawnPreparation(int centerChunkX, int centerChunkZ, int radius, Runnable onDone) {
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) chunks.add(new int[]{centerChunkX + dx, centerChunkZ + dz});
            this.onDone = onDone;
        }
        int percent() { return chunks.isEmpty() ? 100 : next * 100 / chunks.size(); }
    }

    public void run() {
        try {
            init();
            loop();
        } finally {
            if (world != null && worldFolder != null) saveWorld();
            closeSession();
            if (fontRenderer != null) fontRenderer.destroy();
            if (atlas != null) atlas.destroy();
            if (guiTextures != null) guiTextures.destroy();
            if (soundSystem != null) soundSystem.destroy();
            if (window != NULL) {
                glfwFreeCallbacks(window);
                glfwDestroyWindow(window);
            }
            glfwTerminate();
            GLFWErrorCallback callback = glfwSetErrorCallback(null);
            if (callback != null) callback.free();
        }
    }

    private void init() {
        saveFormat = new SaveFormat(gameDirectory.resolve("saves"));
        try {
            gameSettings.load(gameDirectory.resolve("options.txt"));
        } catch (IOException error) {
            System.err.println("Couldn't load options: " + error.getMessage());
        }
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DEPTH_BITS, 24);
        window = glfwCreateWindow(width, height, Branding.WINDOW_TITLE, NULL, NULL);
        if (window == NULL) throw new IllegalStateException("Failed to create the GLFW window");
        glfwSetWindowPos(window, windowedX, windowedY);
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glVendor = String.valueOf(glGetString(GL_VENDOR));
        glRenderer = String.valueOf(glGetString(GL_RENDERER));
        glVersion = String.valueOf(glGetString(GL_VERSION));
        installCallbacks();

        I18n.load(resourceManager, gameSettings.language);
        blockRegistry = BlockRegistry.load(resourceManager);
        itemRegistry = ItemRegistry.load(resourceManager, blockRegistry);
        soundRegistry = SoundRegistry.load(resourceManager);
        soundSystem = new net.minecraft.client.audio.SoundSystem(resourceManager);
        soundSystem.init();
        soundHandler = new net.minecraft.client.audio.SoundHandler(soundRegistry, gameSettings, soundSystem);
        musicTicker = new net.minecraft.client.audio.MusicTicker(soundHandler, new Random());
        modelManager = new BlockModelManager(resourceManager, blockRegistry);
        atlas = new TextureAtlas(resourceManager);
        atlas.load();
        fontRenderer = new FontRenderer(resourceManager);
        fontRenderer.load();
        guiTextures = new GuiTextures(resourceManager);
        biomeRegistry = BiomeRegistry.load(resourceManager, blockRegistry);
        biomeColors = BiomeColors.load(resourceManager);
        itemRenderer = new ItemRenderer(resourceManager, guiTextures, atlas, modelManager, biomeColors);
        firstPersonRenderer = new FirstPersonRenderer(guiTextures, itemRenderer);
        creativeTabs = CreativeTabs.load(resourceManager, itemRegistry);
        crafting = net.minecraft.item.crafting.CraftingManager.load(resourceManager, itemRegistry);
        smelting = net.minecraft.item.crafting.FurnaceRecipes.load(resourceManager, itemRegistry);
        blockItems = BlockDrops.itemsOf(itemRegistry.values());
        blockDrops = BlockDrops.load(resourceManager, blockItems);
        ingameGui = new GuiIngame(this);

        int[] framebuffer = new int[1], other = new int[1];
        glfwGetFramebufferSize(window, framebuffer, other);
        width = Math.max(1, framebuffer[0]); height = Math.max(1, other[0]);
        applyDisplaySettings();
        glfwShowWindow(window);

        if (cliWorld != null) {
            launchIntegratedServer(cliWorld, cliWorld, saveFormat.getWorldInfo(cliWorld) != null ? null
                : new WorldSettings(new Random().nextLong(), WorldSettings.GameType.SURVIVAL, true, false,
                    WorldType.DEFAULT, false, false, ""));
        } else if (cliThrowawayWorld) {
            startThrowawayWorld();
        } else {
            displayGuiScreen(new GuiMainMenu());
        }
    }

    // ---- input ------------------------------------------------------------------------------

    private void installCallbacks() {
        glfwSetFramebufferSizeCallback(window, (ignored, w, h) -> {
            width = Math.max(1, w); height = Math.max(1, h);
            glViewport(0, 0, width, height);
        });
        glfwSetWindowSizeCallback(window, (ignored, w, h) -> { windowWidth = Math.max(1, w); windowHeight = Math.max(1, h); });
        glfwSetWindowFocusCallback(window, (ignored, focused) -> {
            if (!focused && gameSettings.pauseOnLostFocus && world != null && currentScreen == null) displayInGameMenu();
        });
        glfwSetCursorPosCallback(window, (ignored, mx, my) -> {
            cursorX = mx; cursorY = my;
            if (currentScreen != null) {
                currentScreen.mouseMoved(guiMouseX(), guiMouseY());
                return;
            }
            if (!mouseCaptured || player == null) return;
            if (firstMouse) { lastMouseX = mx; lastMouseY = my; firstMouse = false; return; }
            player.turn(mx - lastMouseX, my - lastMouseY, gameSettings.mouseSensitivity, gameSettings.invertMouse);
            lastMouseX = mx; lastMouseY = my;
        });
        glfwSetMouseButtonCallback(window, (ignored, button, action, mods) -> {
            if (currentScreen != null) {
                if (action == GLFW_PRESS) currentScreen.mouseClicked(guiMouseX(), guiMouseY(), button);
                else if (action == GLFW_RELEASE) currentScreen.mouseReleased(guiMouseX(), guiMouseY(), button);
                return;
            }
            if (world == null) return;
            if (!mouseCaptured) { if (action == GLFW_PRESS) captureMouse(); return; }
            KeyBinding.onTransition(gameSettings.keyBindings, KeyCodes.fromMouseButton(button), action == GLFW_PRESS);
        });
        glfwSetScrollCallback(window, (ignored, dx, dy) -> {
            int direction = dy > 0 ? 1 : dy < 0 ? -1 : 0;
            if (currentScreen != null) { currentScreen.mouseScrolled(guiMouseX(), guiMouseY(), direction); return; }
            if (inventory != null && player != null && !isSpectator()) inventory.changeCurrentItem(direction);
        });
        glfwSetCharCallback(window, (ignored, codepoint) -> {
            if (currentScreen != null && Character.isBmpCodePoint(codepoint)) currentScreen.charTyped((char) codepoint);
        });
        glfwSetKeyCallback(window, (ignored, key, scancode, action, mods) -> onKey(key, action, mods));
    }

    private void onKey(int key, int action, int mods) {
        int legacy = KeyCodes.fromGlfwKey(key);
        if (action == GLFW_RELEASE) {
            KeyBinding.onTransition(gameSettings.keyBindings, legacy, false);
            if (key == GLFW_KEY_F3) {
                f3Down = false;
                if (!f3ComboUsed && currentScreen == null && world != null) {
                    gameSettings.showDebugInfo = !gameSettings.showDebugInfo;
                    gameSettings.showDebugProfilerChart = (mods & GLFW_MOD_SHIFT) != 0;
                    gameSettings.showLagometer = (mods & GLFW_MOD_ALT) != 0;
                }
            }
            return;
        }
        // Screenshot and fullscreen work everywhere, as in vanilla.
        if (action == GLFW_PRESS && legacy != KeyCodes.NONE && legacy == gameSettings.keyBindScreenshot.getKeyCode()) {
            screenshotRequested = "screenshots/" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new java.util.Date()) + ".png";
            return;
        }
        if (action == GLFW_PRESS && legacy != KeyCodes.NONE && legacy == gameSettings.keyBindFullscreen.getKeyCode()) {
            gameSettings.fullScreen = !gameSettings.fullScreen;
            applyDisplaySettings();
            saveOptions();
            return;
        }
        if (currentScreen != null) {
            currentScreen.keyPressed(key, mods);
            return;
        }
        if (world == null || action != GLFW_PRESS) return;
        if (key == GLFW_KEY_F3) { f3Down = true; f3ComboUsed = false; return; }
        if (f3Down && handleDebugCombo(key, mods)) { f3ComboUsed = true; return; }
        if (key == GLFW_KEY_ESCAPE) { displayInGameMenu(); return; }
        if (key == GLFW_KEY_F1) { gameSettings.hideGUI = !gameSettings.hideGUI; return; }
        KeyBinding.onTransition(gameSettings.keyBindings, legacy, true);
    }

    /**
     * 1.8.9's F3+key debug combinations (the overlay toggle is skipped when one is used):
     * A reload chunks, B hitboxes, D clear chat, F render distance (shift: nearer),
     * G chunk borders, H advanced tooltips, N creative/spectator, P pause on lost
     * focus, T reload resources. Vanilla prints nothing in chat for these in 1.8.9.
     */
    private boolean handleDebugCombo(int key, int mods) {
        switch (key) {
            case GLFW_KEY_A -> { if (renderer != null) renderer.markAllDirty(); }
            case GLFW_KEY_B -> gameSettings.showHitboxes = !gameSettings.showHitboxes;
            case GLFW_KEY_D -> { if (ingameGui != null) ingameGui.getChatGUI().clearChatMessages(); }
            case GLFW_KEY_F -> { gameSettings.changeRenderDistance((mods & GLFW_MOD_SHIFT) != 0 ? -1 : 1); saveOptions(); }
            case GLFW_KEY_G -> gameSettings.showChunkBorders = !gameSettings.showChunkBorders;
            case GLFW_KEY_H -> { gameSettings.advancedItemTooltips = !gameSettings.advancedItemTooltips; saveOptions(); }
            case GLFW_KEY_N -> {
                if (levelInfo != null && levelInfo.allowCommands) {
                    levelInfo.gameType = levelInfo.gameType == WorldSettings.GameType.SPECTATOR
                        ? WorldSettings.GameType.CREATIVE : WorldSettings.GameType.SPECTATOR;
                    applyGameMode();
                }
            }
            case GLFW_KEY_P -> { gameSettings.pauseOnLostFocus = !gameSettings.pauseOnLostFocus; saveOptions(); }
            case GLFW_KEY_T -> { guiTextures.reload(); refreshLanguage(); }
            default -> { return false; }
        }
        return true;
    }

    private int guiMouseX() { return (int) (cursorX * lastScaledWidth / Math.max(1, windowWidth)); }
    private int guiMouseY() { return (int) (cursorY * lastScaledHeight / Math.max(1, windowHeight)); }

    private void captureMouse() {
        mouseCaptured = true;
        firstMouse = true;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        if (glfwRawMouseMotionSupported()) glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
    }

    private void releaseMouse() {
        mouseCaptured = false;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        // Vanilla re-centres the cursor when a GUI opens.
        glfwSetCursorPos(window, windowWidth / 2.0, windowHeight / 2.0);
        cursorX = windowWidth / 2.0; cursorY = windowHeight / 2.0;
    }

    private void displayInGameMenu() {
        if (currentScreen == null) displayGuiScreen(new GuiIngameMenu());
    }

    // ---- main loop --------------------------------------------------------------------------

    private void loop() {
        double previous = glfwGetTime();
        double tickAccumulator = 0;
        final double tickStep = 1.0 / IntegratedServer.TICKS_PER_SECOND;
        while (running && !glfwWindowShouldClose(window)) {
            long frameStart = System.nanoTime();
            double now = glfwGetTime();
            double frame = Math.min(0.1, now - previous);
            previous = now;
            glfwPollEvents();

            if (preparing != null) stepSpawnPreparation();
            tickAccumulator += frame;
            while (tickAccumulator >= tickStep) {
                runTick();
                tickAccumulator -= tickStep;
            }
            float partialTicks = (float) (tickAccumulator / tickStep);
            render(partialTicks);
            updateSoundListener(partialTicks);
            if (screenshotRequested != null && (!quitAfterScreenshot || world != null && preparing == null)) {
                saveScreenshot(screenshotRequested);
                screenshotRequested = null;
                if (quitAfterScreenshot) running = false;
            }
            glfwSwapBuffers(window);
            limitFramerate(frameStart);
            debugOverlay.addFrameTime(System.nanoTime() - frameStart);

            frames++;
            fpsTimer += frame;
            if (fpsTimer >= 1.0) {
                fps = frames;
                frames = 0;
                fpsTimer -= 1.0;
                chunkUpdates = renderer == null ? 0 : renderer.takeRebuildCount();
            }
        }
    }

    /** One 20 Hz client tick: screen animation, the integrated server (unless paused), death, key presses. */
    private void runTick() {
        if (currentScreen != null) currentScreen.updateScreen();
        boolean pausedNow = world != null && preparing == null && currentScreen != null && currentScreen.doesGuiPauseGame();
        // Minecraft.runGameLoop: every sound pauses with the game and resumes with it.
        if (pausedNow != soundsPaused) {
            if (pausedNow) soundHandler.pauseAll(); else soundHandler.resumeAll();
            soundsPaused = pausedNow;
        }
        if (!pausedNow) musicTicker.tick(musicType());
        if (world == null || preparing != null) return;
        boolean paused = currentScreen != null && currentScreen.doesGuiPauseGame();
        if (!paused) {
            world.setDifficulty(levelInfo.difficulty);
            world.setDoFireTick(!"false".equals(levelInfo.gameRules.get("doFireTick")));
            world.setRaining(levelInfo.raining);
            world.tick();
            atlas.tickAnimations();
            server.tick();
            tickItemEntities();
            levelInfo.totalTime++;
            player.difficulty = levelInfo.difficulty;
            player.naturalRegeneration = Boolean.parseBoolean(levelInfo.gameRules.getOrDefault("naturalRegeneration", "true"));
            if (preparing == null) player.tick(Player.Input.of(gameSettings));
            updatePortalTime();
            rendererUpdateCount++;
            playerAnimation.tick(player);
            tickPlayerLiquids();
            tickParticles();
            tickCaveAmbience();
            updateBiomesExplored();
            if (inventory != null) firstPersonRenderer.tick(inventory.getCurrentItem(), inventory.currentItem);
            if (Boolean.parseBoolean(levelInfo.gameRules.getOrDefault("doDaylightCycle", "true"))) levelInfo.worldTime++;
            tickSky();
        }
        if (player.dead && !deathHandled) {
            deathHandled = true;
            dropInventoryOnDeath();
        }
        if (player.dead && !(currentScreen instanceof GuiGameOver)) {
            displayGuiScreen(new GuiGameOver(levelInfo.hardcore, 0));
            return;
        }
        ingameGui.updateTick(inventory);
        // EntityPlayer.onUpdate: using stops when the held stack is no longer the one in use.
        if (player.isUsingItem() && (inventory.getCurrentItem() != player.itemInUse() || isSpectator()))
            player.clearItemInUse();
        if (rightClickDelayTimer > 0) rightClickDelayTimer--;
        if (currentScreen != null) {
            playerController.resetBlockRemoving();
            return;
        }
        while (gameSettings.keyBindTogglePerspective.isPressed())
            gameSettings.thirdPersonView = (gameSettings.thirdPersonView + 1) % 3;
        for (int i = 0; i < 9; i++) if (gameSettings.keyBindsHotbar[i].isPressed() && !isSpectator()) inventory.currentItem = i;
        if (gameSettings.keyBindInventory.isPressed()) {
            // C16PacketClientStatus OPEN_INVENTORY_ACHIEVEMENT, sent in every mode.
            triggerAchievement("openInventory");
            displayGuiScreen(isCreative() ? new GuiContainerCreative() : new GuiInventory());
        } else {
            while (gameSettings.keyBindDrop.isPressed()) if (!isSpectator()) dropHeldItem(isKeyDown(GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW_KEY_RIGHT_CONTROL));
            // Opened from the tick, after the key's own character event has gone by, so "t" or "/" is not typed into the field.
            if (gameSettings.keyBindChat.isPressed()) displayGuiScreen(new GuiChat());
            else if (gameSettings.keyBindCommand.isPressed()) displayGuiScreen(new GuiChat("/"));
            if (currentScreen == null) interact();
        }
    }

    /** The music the game wants now (Minecraft.getAmbientMusicType). */
    private net.minecraft.client.audio.MusicTicker.Type musicType() {
        if (player == null || world == null) return net.minecraft.client.audio.MusicTicker.Type.MENU;
        return isCreative() && player.allowFlying ? net.minecraft.client.audio.MusicTicker.Type.CREATIVE
            : net.minecraft.client.audio.MusicTicker.Type.GAME;
    }

    /** The listener follows the player's eyes and view each frame; menus without a world only refresh volumes. */
    private void updateSoundListener(float partialTicks) {
        if (soundHandler == null) return;
        if (player == null || world == null) {
            soundHandler.update();
            return;
        }
        soundHandler.update(player.renderX(partialTicks), player.renderY(partialTicks) + player.eyeHeight(),
            player.renderZ(partialTicks), player.dirX(), player.dirY(), player.dirZ());
    }

    /** A sound in the world (World.playSound on the client). */
    private void playSound(String event, double x, double y, double z, float volume, float pitch) {
        if (soundHandler != null) soundHandler.play(event, x, y, z, volume, pitch);
    }

    /** A block breaking (RenderGlobal.playAuxSFX 2001): its break sound at (volume + 1) / 2, frequency x 0.8. */
    private void playBreakSound(int x, int y, int z, BlockState state) {
        if (state.block().isAir()) return;
        net.minecraft.block.BlockSoundType type = net.minecraft.block.BlockSoundType.of(state.block());
        playSound(type.breakSound(), x + 0.5, y + 0.5, z + 0.5, (type.volume() + 1f) / 2f, type.frequency() * 0.8f);
    }

    /**
     * Cave ambience (WorldClient.updateBlocks, World.playMoodSoundAndCheckLight;
     * HYPOTHESIS): up to 10 not-yet-tried chunks within 7 of the player a
     * tick; once the countdown has run out, each picks a spot with an LCG and,
     * if it is air with no sky light and block light no more than a random
     * 0-7, and the player is 2-8 blocks from it, plays the cave sound there
     * and waits 6000-18000 ticks.
     */
    private void tickCaveAmbience() {
        if (ambientCountdown > 0) ambientCountdown--;
        int centerX = (int) Math.floor(player.x) >> 4, centerZ = (int) Math.floor(player.z) >> 4;
        List<Long> active = new ArrayList<>();
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++) active.add(World.chunkKey(centerX + dx, centerZ + dz));
        moodChunksTried.retainAll(active);
        if (moodChunksTried.size() == active.size()) moodChunksTried.clear();
        int tried = 0;
        for (int i = 0; i < active.size(); i++) {
            long key = active.get(i);
            if (moodChunksTried.contains(key)) continue;
            moodSound((centerX + i / 15 - 7) * 16, (centerZ + i % 15 - 7) * 16);
            moodChunksTried.add(key);
            if (++tried >= 10) return;
        }
    }

    private void moodSound(int chunkBlockX, int chunkBlockZ) {
        if (ambientCountdown != 0) return;
        caveLcg = caveLcg * 3 + 1013904223;
        int bits = caveLcg >> 2;
        int x = chunkBlockX + (bits & 15), z = chunkBlockZ + (bits >> 8 & 15), y = bits >> 16 & 255;
        if (world.generatedChunk(x >> 4, z >> 4) == null) return;
        if (!world.get(x, y, z).isAir() || world.getSkyLight(x, y, z) > 0) return;
        if (world.getBlockLight(x, y, z) > random.nextInt(8)) return;
        double dx = player.x - (x + 0.5), dy = player.y - (y + 0.5), dz = player.z - (z + 0.5);
        double distance = dx * dx + dy * dy + dz * dz;
        if (distance > 64 || distance <= 4) return;
        playSound("ambient.cave.cave", x + 0.5, y + 0.5, z + 0.5, 0.7f, 0.8f + random.nextFloat() * 0.2f);
        ambientCountdown = random.nextInt(12000) + 6000;
    }

    /** Merges nearby dropped stacks and lets the player pick up what it touches (server side of 1.8.9's EntityItem). */
    private void tickItemEntities() {
        List<EntityItem> items = new ArrayList<>();
        for (Entity entity : server.entities().values()) if (entity instanceof EntityItem item && !item.isRemoved()) items.add(item);
        for (EntityItem item : items) {
            for (EntityItem.Sound sound : item.takeSounds())
                playSound(sound.event(), item.x(), item.y(), item.z(), sound.volume(), sound.pitch());
            if (item.takeSplash() && particles != null)
                particles.addWaterSplash(item.x(), item.y(), item.z(), (float) EntityItem.SIZE, item.motionX, item.motionY, item.motionZ);
        }
        for (EntityItem item : items) {
            if (item.isRemoved() || item.age % 25 != 1) continue;
            for (EntityItem other : items) if (other != item && item.isNear(other)) item.tryMerge(other);
        }
        if (player == null || player.dead || isSpectator()) return;
        AxisAlignedBB reach = player.boundingBox();
        reach = new AxisAlignedBB(reach.minX() - 1, reach.minY() - 0.5, reach.minZ() - 1, reach.maxX() + 1, reach.maxY() + 0.5, reach.maxZ() + 1);
        for (EntityItem item : items) {
            if (item.isRemoved() || item.pickupDelay > 0 || !item.boundingBox().intersects(reach)) continue;
            int before = item.stack().count();
            inventory.addItemStackToInventory(item.stack());
            // EntityItem.onCollideWithPlayer: a pop when anything was picked up.
            if (item.stack().count() < before) {
                String earned = net.minecraft.stats.AchievementList.forPickedUp(item.stack().item().name().path());
                if (earned != null) triggerAchievement(earned);
            }
            if (item.stack().count() < before)
                playSound("random.pop", player.x, player.y, player.z, 0.2f,
                    ((random.nextFloat() - random.nextFloat()) * 0.7f + 1f) * 2f);
            if (item.stack().isEmpty()) item.remove();
        }
    }

    /** Menus run at 30 fps like 1.8.9; in game the Max Framerate option applies unless Unlimited or vsync. */
    private void limitFramerate(long frameStart) {
        int limit = world == null && currentScreen != null ? 30 : gameSettings.limitFramerate;
        if (limit >= GameSettings.Options.FRAMERATE_LIMIT.getMax() || (gameSettings.enableVsync && limit >= 60)) return;
        long target = 1_000_000_000L / limit;
        long remaining = target - (System.nanoTime() - frameStart);
        if (remaining > 0) {
            try { Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L)); } catch (InterruptedException ignored) {}
        }
    }

    // ---- world session ----------------------------------------------------------------------

    @Override
    public void launchIntegratedServer(String folderName, String worldName, WorldSettings settings) {
        closeSession();
        worldFolder = folderName;
        LevelInfo info;
        if (settings != null) {
            info = LevelInfo.create(worldName, settings);
            if (settings.bonusChest()) System.out.println("Bonus chest requested; not generated yet (DEV-011).");
        } else {
            info = saveFormat.getWorldInfo(folderName);
            if (info == null) {
                displayGuiScreen(new net.minecraft.client.gui.GuiDisconnected(new GuiMainMenu(), "selectWorld.title",
                    "Couldn't read " + folderName + "/level.dat", "gui.toTitle"));
                return;
            }
        }
        levelInfo = info;
        Path directory = saveFormat.worldDirectory(folderName);
        try {
            if (settings == null && WorldStorage.exists(directory)) {
                world = WorldStorage.load(directory, blockRegistry, seed -> createWorld(info, seed));
            } else {
                world = createWorld(info, info.randomSeed);
            }
        } catch (IOException error) {
            world = null;
            levelInfo = null;
            displayGuiScreen(new net.minecraft.client.gui.GuiDisconnected(new GuiMainMenu(), "selectWorld.title",
                "Couldn't load world: " + error.getMessage(), "gui.toTitle"));
            return;
        }
        loadingScreen.setTitle(I18n.format("menu.loadingLevel"));
        loadingScreen.setMessage(I18n.format("menu.generatingTerrain"));
        loadingScreen.setProgress(0);
        displayGuiScreen(loadingScreen);
        chooseSpawn(info);
        int radius = Math.min(MAX_SPAWN_RADIUS, gameSettings.renderDistanceChunks + 1);
        preparing = new SpawnPreparation(info.spawnX >> 4, info.spawnZ >> 4, radius, this::finishLaunch);
    }

    /** A new world's spawn point: vanilla's search for grass in a spawn-friendly biome near the origin. */
    private void chooseSpawn(LevelInfo info) {
        if (info.initialized || world.isFlat()) return;
        int[] spawn = world.findSpawn();
        info.spawnX = spawn[0];
        info.spawnZ = spawn[1];
    }

    private World createWorld(LevelInfo info, long seed) {
        World created = info.terrainType == WorldType.FLAT
            ? new World(blockRegistry, biomeRegistry, seed, FlatGeneratorInfo.parse(info.generatorOptions, blockRegistry))
            : new World(blockRegistry, biomeRegistry, seed);
        // Before loading, so saved furnaces can resolve their items.
        created.setSmelting(smelting, this::itemByName);
        return created;
    }

    /** Generates spawn-area chunks for up to ~40ms per frame so the progress screen stays live. */
    private void stepSpawnPreparation() {
        long deadline = System.nanoTime() + 40_000_000L;
        while (preparing.next < preparing.chunks.size() && System.nanoTime() < deadline) {
            int[] chunk = preparing.chunks.get(preparing.next++);
            world.getChunk(chunk[0], chunk[1]);
        }
        loadingScreen.setProgress(preparing.percent());
        if (preparing.next >= preparing.chunks.size()) {
            Runnable done = preparing.onDone;
            preparing = null;
            done.run();
        }
    }

    private void finishLaunch() {
        if (!levelInfo.initialized) {
            levelInfo.spawnY = world.surfaceHeight(levelInfo.spawnX, levelInfo.spawnZ) + 1;
            levelInfo.initialized = true;
        }
        double spawnX = cliSpawnX != null ? cliSpawnX : levelInfo.spawnX + 0.5;
        double spawnZ = cliSpawnZ != null ? cliSpawnZ : levelInfo.spawnZ + 0.5;
        player = new Player(world, spawnX, spawnZ);
        playerAnimation = new PlayerAnimation();
        playerFire = -20;
        if (levelInfo.player != null && cliSpawnX == null) {
            LevelInfo.PlayerPosition saved = levelInfo.player;
            // level.dat stores vanilla yaw (0 = south); the player model uses 0 = north.
            player.setPositionAndRotation(saved.x(), saved.y(), saved.z(), saved.yaw() - 180f, saved.pitch());
        }
        inventory = new InventoryPlayer();
        deathHandled = false;
        playerController.resetBlockRemoving();
        loadInventory();
        loadVitals();
        player.armor = inventory.armorInventory;
        player.damageListener = new Player.DamageListener() {
            @Override public void died(net.minecraft.entity.DamageSource source) { announceDeath(source); }
            @Override public void armorBroke(int slot) {
                playSound("random.break", player.x, player.y, player.z, 0.8f, 0.8f + random.nextFloat() * 0.4f);
            }
        };
        loadStats();
        applyGameMode();
        if (levelInfo.abilities != null && levelInfo.abilities.flying() && player.allowFlying && cliSpawnX == null)
            player.isFlying = true;
        if (cliPos != null) {
            // F3's yaw is vanilla's (0 = south); the player model uses 0 = north.
            player.setPositionAndRotation(cliPos[0], cliPos[1], cliPos[2], (float) cliPos[3] - 180f, (float) cliPos[4]);
            if (player.allowFlying) player.isFlying = true;
        }
        player.sounds = (event, volume, pitch) -> playSound(event, player.x, player.y, player.z, volume, pitch);
        server = new IntegratedServer(world);
        renderer = new WorldRenderer(world, atlas, modelManager, biomeColors);
        sky = new SkyRenderer(guiTextures);
        particles = new ParticleManager(world, atlas, guiTextures, modelManager, biomeColors);
        particles.sounds = this::playSound;
        world.setEvents(new World.Events() {
            @Override public void washedAway(int x, int y, int z, BlockState previous) { dropBlock(x, y, z, previous); }
            @Override public void liquidsMixed(int x, int y, int z) {
                // BlockLiquid.triggerMixEffects.
                playSound("random.fizz", x + 0.5, y + 0.5, z + 0.5, 0.5f, 2.6f + (random.nextFloat() - random.nextFloat()) * 0.8f);
                if (particles != null)
                    for (int i = 0; i < 8; i++)
                        particles.spawn(ParticleManager.Kind.LARGE_SMOKE, x + Math.random(), y + 1.2, z + Math.random(), 0, 0, 0, null);
            }
            @Override public void popped(int x, int y, int z, BlockState previous) { blockPopped(x, y, z, previous); }
            @Override public void shattered(int x, int y, int z, BlockState previous) {
                if (particles != null) particles.addBlockDestroyEffects(x, y, z, previous);
                playBreakSound(x, y, z, previous);
            }
            @Override public void containerBroken(int x, int y, int z, List<ItemStack> contents) { spillContents(x, y, z, contents); }
        });
        gameSettings.difficulty = levelInfo.difficulty;
        if (worldFolder != null) saveWorld();
        displayGuiScreen(null);
    }

    private void startThrowawayWorld() {
        worldFolder = null;
        // Developer worlds start in Creative with a starter hotbar (vanilla worlds start empty).
        WorldSettings settings = new WorldSettings(cliSeed, WorldSettings.GameType.CREATIVE, true, false,
            WorldType.DEFAULT, true, false, "");
        levelInfo = LevelInfo.create("Throwaway", settings);
        List<LevelInfo.SavedStack> starter = new ArrayList<>();
        String[] initialItems = {"grass", "cobblestone", "oak_planks", "oak_stairs", "glass", "oak_log", "oak_fence",
            "torch", "stone_slab"};
        for (int i = 0; i < initialItems.length; i++) starter.add(new LevelInfo.SavedStack(i, "minecraft:" + initialItems[i], 64, 0));
        levelInfo.inventory = starter;
        world = createWorld(levelInfo, cliSeed);
        loadingScreen.setTitle(I18n.format("menu.loadingLevel"));
        loadingScreen.setMessage(I18n.format("menu.generatingTerrain"));
        displayGuiScreen(loadingScreen);
        if (cliSpawnX == null) chooseSpawn(levelInfo);
        int centerX = cliSpawnX == null ? levelInfo.spawnX >> 4 : (int) Math.floor(cliSpawnX) >> 4;
        int centerZ = cliSpawnZ == null ? levelInfo.spawnZ >> 4 : (int) Math.floor(cliSpawnZ) >> 4;
        preparing = new SpawnPreparation(centerX, centerZ, Math.min(MAX_SPAWN_RADIUS, gameSettings.renderDistanceChunks + 1),
            this::finishLaunch);
    }

    /** Writes level.dat (time, last played, player) and the chunks. */
    private void saveWorld() {
        if (world == null || worldFolder == null || levelInfo == null) return;
        levelInfo.lastPlayed = System.currentTimeMillis();
        if (player != null && !player.dead)
            levelInfo.player = new LevelInfo.PlayerPosition(player.x, player.y, player.z, player.yaw + 180f, player.pitch, player.onGround);
        if (player != null) {
            saveInventory();
            saveStats();
            levelInfo.abilities = LevelInfo.Abilities.of(levelInfo.gameType, player.isFlying);
        }
        try {
            Path directory = saveFormat.worldDirectory(worldFolder);
            Files.createDirectories(directory);
            WorldStorage.save(directory, world);
            levelInfo.sizeOnDisk = directorySize(directory);
            saveFormat.saveWorldInfo(worldFolder, levelInfo);
        } catch (IOException error) {
            System.err.println("Couldn't save world " + worldFolder + ": " + error.getMessage());
        }
    }

    private static long directorySize(Path directory) {
        try (var walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).mapToLong(file -> file.toFile().length()).sum();
        } catch (IOException error) {
            return 0;
        }
    }

    private void closeSession() {
        if (soundHandler != null) soundHandler.stopAll();
        if (server != null) server.close();
        if (renderer != null) renderer.destroy();
        if (sky != null) sky.destroy();
        if (particles != null) particles.clear();
        particles = null;
        server = null;
        renderer = null;
        sky = null;
        world = null;
        player = null;
        inventory = null;
        preparing = null;
    }

    private void saveStats() {
        Path file = statsFile();
        if (file == null || stats == null) return;
        try {
            stats.save(file);
        } catch (IOException error) {
            System.err.println("Couldn't save statistics: " + error.getMessage());
        }
    }

    /** Fills the player's inventory from level.dat (unknown item names are dropped with a warning). */
    /** The world's {@code stats/<player>.json}, or null for a throwaway world. */
    private Path statsFile() {
        return worldFolder == null ? null : saveFormat.worldDirectory(worldFolder).resolve("stats").resolve(playerName + ".json");
    }

    /**
     * Reads the player's achievements; a player without Taking Inventory is
     * shown the standing "press E" hint.
     */
    private void loadStats() {
        Path file = statsFile();
        stats = file == null ? new net.minecraft.stats.StatFile() : net.minecraft.stats.StatFile.load(file);
        achievementToast.clear();
        net.minecraft.stats.Achievement open = net.minecraft.stats.AchievementList.get("openInventory");
        if (!stats.has(open))
            achievementToast.displayHint(I18n.format(open.nameKey()),
                I18n.format(open.descriptionKey(), net.minecraft.client.gui.GuiAchievements.inventoryKeyName(this)),
                itemByName(open.icon()), System.currentTimeMillis());
    }

    /**
     * Awards an achievement (EntityPlayerMP.triggerAchievement): only once its
     * parent is earned and only once; the pop-up slides in and chat says
     * "<player> has just earned the achievement [name]" (green, purple for
     * special ones).
     */
    public void triggerAchievement(String id) {
        net.minecraft.stats.Achievement achievement = net.minecraft.stats.AchievementList.get(id);
        if (stats == null || achievement == null || !stats.unlock(achievement)) return;
        announceAchievement(achievement);
    }

    private void announceAchievement(net.minecraft.stats.Achievement achievement) {
        String name = I18n.format(achievement.nameKey());
        achievementToast.display(name, itemByName(achievement.icon()), System.currentTimeMillis());
        String colour = achievement.special() ? "\u00a75" : "\u00a7a";
        printChatLine(net.minecraft.client.gui.I18n.translateChat("chat.type.achievement", playerName,
            colour + "[" + name + "]\u00a7r"), 0);
    }

    @Override public void onItemCrafted(ItemStack stack) {
        String id = net.minecraft.stats.AchievementList.forCrafted(stack.item().name().path());
        if (id != null) triggerAchievement(id);
    }

    /** Every 100 ticks the biome underfoot counts toward Adventuring Time (EntityPlayerMP.updateBiomesExplored). */
    private void updateBiomesExplored() {
        net.minecraft.stats.Achievement all = net.minecraft.stats.AchievementList.get("exploreAllBiomes");
        if (stats == null || stats.has(all) || playerAnimation.ticksExisted % 100 != 0) return;
        String name = net.minecraft.stats.AchievementList.EXPLORATION_BIOMES.get(
            world.biomeAt((int) Math.floor(player.x), (int) Math.floor(player.z)).id());
        if (name == null) return;
        stats.exploreBiome(name);
        if (stats.biomesExplored().containsAll(net.minecraft.stats.AchievementList.EXPLORATION_BIOMES.values()))
            triggerAchievement("exploreAllBiomes");
    }

    @Override public net.minecraft.stats.StatFile stats() { return stats; }
    @Override public String playerName() { return playerName; }

    @Override public void drawBlockTexture(String texture, int x, int y, int size) {
        TextureAtlas.Tile tile = atlas.tile(new net.minecraft.util.ResourceLocation("minecraft:textures/blocks/" + texture + ".png"));
        if (tile == null) return;
        atlas.bind();
        glBegin(GL_QUADS);
        glTexCoord2f(tile.u0(), tile.v1()); glVertex2f(x, y + size);
        glTexCoord2f(tile.u1(), tile.v1()); glVertex2f(x + size, y + size);
        glTexCoord2f(tile.u1(), tile.v0()); glVertex2f(x + size, y);
        glTexCoord2f(tile.u0(), tile.v0()); glVertex2f(x, y);
        glEnd();
    }

    private void loadInventory() {
        inventory.clear();
        if (levelInfo.inventory == null) return;
        for (LevelInfo.SavedStack saved : levelInfo.inventory) {
            Item item = itemRegistry.get(new net.minecraft.util.ResourceLocation(saved.id()));
            if (item == null || saved.count() <= 0) {
                System.err.println("Dropping unknown saved item " + saved.id());
                continue;
            }
            ItemStack stack = new ItemStack(item, Math.min(saved.count(), item.maxStackSize()),
                Math.max(0, Math.min(item.maxDamage(), saved.damage())));
            stack.readTag(saved.tag());
            inventory.setBySaveSlot(saved.slot(), stack);
        }
        inventory.currentItem = Math.max(0, Math.min(InventoryPlayer.HOTBAR_SIZE - 1, levelInfo.selectedItemSlot));
    }

    /** The saved experience, health, air and enchanting seed (a new player keeps its fresh values). */
    private void loadVitals() {
        if (levelInfo.experience != null) {
            player.experienceLevel = levelInfo.experience.level();
            player.experience = levelInfo.experience.progress();
            player.experienceTotal = levelInfo.experience.total();
        }
        if (levelInfo.vitals != null) {
            player.health = Math.max(0f, Math.min(20f, levelInfo.vitals.health()));
            player.air = levelInfo.vitals.air();
            player.xpSeed = levelInfo.vitals.xpSeed();
            if (player.health <= 0) player.health = 20f;
        }
    }

    private void saveInventory() {
        List<LevelInfo.SavedStack> saved = new ArrayList<>();
        for (int slot = 0; slot < InventoryPlayer.MAIN_SIZE; slot++) addSaved(saved, slot);
        for (int slot = 100; slot < 100 + InventoryPlayer.ARMOR_SIZE; slot++) addSaved(saved, slot);
        levelInfo.inventory = saved;
        levelInfo.selectedItemSlot = inventory.currentItem;
        levelInfo.experience = new LevelInfo.Experience(player.experienceLevel, player.experience, player.experienceTotal);
        levelInfo.vitals = new LevelInfo.Vitals(player.health, player.air, player.xpSeed);
    }

    private void addSaved(List<LevelInfo.SavedStack> saved, int slot) {
        ItemStack stack = inventory.getBySaveSlot(slot);
        if (stack != null)
            saved.add(new LevelInfo.SavedStack(slot, stack.item().name().toString(), stack.count(), stack.damage(), stack.writeTag()));
    }

    /** Gives the player the current game mode's abilities (flight in Creative, noclip flight in Spectator). */
    private void applyGameMode() {
        if (player == null || levelInfo == null) return;
        player.setAbilities(levelInfo.gameType == WorldSettings.GameType.CREATIVE, isSpectator());
        player.disableDamage = levelInfo.gameType == WorldSettings.GameType.CREATIVE || isSpectator();
    }

    /** The death message in chat (CombatTracker.getDeathMessage for a death with no attacker). */
    private void announceDeath(net.minecraft.entity.DamageSource source) {
        printChatLine(net.minecraft.client.gui.I18n.translateChat(source.deathMessageKey(), playerName), 0);
    }

    private boolean isSpectator() { return levelInfo != null && levelInfo.gameType == WorldSettings.GameType.SPECTATOR; }

    /** Survival and Adventure show hearts, food, armor and experience. */
    private boolean survivalHud() {
        return levelInfo != null && (levelInfo.gameType == WorldSettings.GameType.SURVIVAL
            || levelInfo.gameType == WorldSettings.GameType.ADVENTURE);
    }

    /** Block reach: 5 blocks in Creative, 4.5 otherwise (vanilla PlayerControllerMP.getBlockReachDistance). */
    private double reach() { return isCreative() ? 5.0 : 4.5; }

    @Override
    public void saveAndQuitToTitle() {
        loadingScreen.setTitle("");
        loadingScreen.setMessage("");
        loadingScreen.setProgress(-1);
        displayGuiScreen(loadingScreen);
        render(0);
        glfwSwapBuffers(window);
        saveWorld();
        closeSession();
        levelInfo = null;
        worldFolder = null;
        displayGuiScreen(new GuiMainMenu());
    }

    @Override
    public void deleteWorldAndQuit() {
        String folder = worldFolder;
        closeSession();
        levelInfo = null;
        worldFolder = null;
        if (folder != null) saveFormat.deleteWorldDirectory(folder);
        displayGuiScreen(new GuiMainMenu());
    }

    @Override
    public void respawnPlayer() {
        if (player == null) return;
        // EntityPlayerMP.clonePlayer: experience is lost on death unless keepInventory (no orbs are dropped yet, DEV-029).
        if (!Boolean.parseBoolean(levelInfo.gameRules.getOrDefault("keepInventory", "false"))) {
            player.experienceLevel = 0;
            player.experience = 0;
            player.experienceTotal = 0;
        }
        player.respawn();
        deathHandled = false;
        applyGameMode();
    }

    @Override
    public void shutdown() { running = false; }

    // ---- rendering --------------------------------------------------------------------------

    private float partialTicks;

    private void render(float partialTicks) {
        this.partialTicks = partialTicks;
        ScaledResolution resolution = new ScaledResolution(width, height, gameSettings.getGuiScale(), gameSettings.isUnicodeFont());
        if (resolution.getScaledWidth() != lastScaledWidth || resolution.getScaledHeight() != lastScaledHeight
            || resolution.getScaleFactor() != lastScaleFactor) {
            lastScaledWidth = resolution.getScaledWidth();
            lastScaledHeight = resolution.getScaledHeight();
            lastScaleFactor = resolution.getScaleFactor();
            if (currentScreen != null) currentScreen.setWorldAndResolution(this, lastScaledWidth, lastScaledHeight);
        }
        glViewport(0, 0, width, height);
        boolean inWorld = world != null && player != null && renderer != null && sky != null;
        // The player moves 20 times a second; the world is drawn from where they are between the last two ticks.
        double tickX = inWorld ? player.x : 0, tickY = inWorld ? player.y : 0, tickZ = inWorld ? player.z : 0;
        if (inWorld) {
            player.x = player.renderX(partialTicks);
            player.y = player.renderY(partialTicks);
            player.z = player.renderZ(partialTicks);
        }
        try {
            float[] fog = inWorld ? prepareSky() : null;
            if (fog != null) glClearColor(fog[0], fog[1], fog[2], 0.0f);
            else glClearColor(0.50f, 0.69f, 1.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            if (inWorld) renderWorld(fog);
        } finally {
            if (inWorld) {
                player.x = tickX;
                player.y = tickY;
                player.z = tickZ;
            }
        }
        if (inWorld) {
            glClear(GL_DEPTH_BUFFER_BIT);
            if (!gameSettings.hideGUI) drawHud(resolution);
        }
        if (currentScreen != null) {
            glClear(GL_DEPTH_BUFFER_BIT);
            setupGuiProjection(resolution);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_FOG);
            glEnable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glEnable(GL_ALPHA_TEST);
            glAlphaFunc(GL_GREATER, 0.1f);
            currentScreen.drawScreen(guiMouseX(), guiMouseY(), partialTicks);
            glColor4f(1, 1, 1, 1);
        }
        // GuiAchievement.updateAchievementWindow: over everything, screens included.
        if (player != null && world != null) {
            glClear(GL_DEPTH_BUFFER_BIT);
            setupGuiProjection(resolution);
            achievementToast.draw(this, resolution.getScaledWidth(), System.currentTimeMillis());
        }
    }

    private void setupGuiProjection(ScaledResolution resolution) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, resolution.getScaledWidthDouble(), resolution.getScaledHeightDouble(), 0, -1000, 3000);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    /** Vanilla's far plane is the render distance times sqrt(2); the fog ends at the render distance. */
    private static final double SQRT_2 = Math.sqrt(2.0);

    /**
     * Once per unpaused tick: particles move and age, blocks around the
     * player get their random display ticks (torch flames, drips, ...), and a
     * sprinting player kicks up flecks of the block underfoot (1.8.9
     * Entity.createRunningParticles, HYPOTHESIS).
     */
    private void tickParticles() {
        if (particles == null || player == null) return;
        particles.setView(gameSettings.particleSetting, player.x, player.eyeY(), player.z);
        particles.tick();
        particles.randomDisplayTicks(player.x, player.y, player.z);
        tableBooks.tick(world, player.x, player.y, player.z);
        double motionX = player.motionX, motionZ = player.motionZ;
        if (player.sprinting && !player.inWater) {
            int bx = (int) Math.floor(player.x), by = (int) Math.floor(player.y - 0.2), bz = (int) Math.floor(player.z);
            if (by >= 0 && by < World.HEIGHT) {
                BlockState below = world.getState(bx, by, bz);
                if (!below.block().isAir())
                    particles.spawn(ParticleManager.Kind.BLOCK_CRACK, player.x + (random.nextFloat() - 0.5) * Player.WIDTH,
                        player.y + 0.1, player.z + (random.nextFloat() - 0.5) * Player.WIDTH, -motionX * 4.0, 1.5, -motionZ * 4.0, below);
            }
        }
    }

    /**
     * The player and liquids, once per unpaused tick (1.8.9
     * Entity.onEntityUpdate, HYPOTHESIS): entering water splashes (bubbles
     * and droplets) and puts out fire; lava sets the player alight for 15
     * seconds and hurts 4; fire and lava blocks hurt 1 a tick and set them
     * alight after 20 ticks inside; burning hurts 1 a second and counts
     * down. Fire Protection shortens how long a fire lasts
     * (EnchantmentProtection.getFireTimeForEntity).
     */
    private void tickPlayerLiquids() {
        if (player == null || isSpectator()) return;
        AxisAlignedBB box = player.boundingBox();
        if (player.enteredWater && particles != null)
            particles.addWaterSplash(player.x, player.y, player.z, (float) Player.WIDTH, player.motionX, player.motionY, player.motionZ);
        if (player.inWater) {
            // Entity.moveEntity: water puts the fire out with a fizz.
            if (playerFire > 0)
                playSound("random.fizz", player.x, player.y, player.z, 0.7f, 1.6f + (random.nextFloat() - random.nextFloat()) * 0.4f);
            playerFire = 0;
        }
        // Entity.onEntityUpdate: burning hurts once a second. A Creative player keeps burning until water puts
        // the fire out (reported from vanilla play).
        if (playerFire > 0 && playerFire % 20 == 0) player.attack(net.minecraft.entity.DamageSource.ON_FIRE, 1f);
        if (playerFire > 0 && !isCreative()) playerFire--;
        AxisAlignedBB lavaBox = new AxisAlignedBB(box.minX() + 0.1, box.minY() + 0.4, box.minZ() + 0.1,
            box.maxX() - 0.1, box.maxY() - 0.4, box.maxZ() - 0.1);
        if (world.isMaterialInBox(lavaBox, "lava")) {
            // Entity.setOnFireFromLava.
            player.attack(net.minecraft.entity.DamageSource.LAVA, 4f);
            setPlayerOnFire(15);
        }
        AxisAlignedBB inner = new AxisAlignedBB(box.minX() + 0.001, box.minY() + 0.001, box.minZ() + 0.001,
            box.maxX() - 0.001, box.maxY() - 0.001, box.maxZ() - 0.001);
        if (world.isMaterialInBox(inner, "fire") || world.isMaterialInBox(inner, "lava")) {
            // Entity.moveEntity: dealFireDamage(1), then the fire timer climbs until it catches.
            player.attack(net.minecraft.entity.DamageSource.IN_FIRE, 1f);
            if (!player.inWater) {
                playerFire++;
                if (playerFire == 0) setPlayerOnFire(8);
            }
        } else if (playerFire <= 0) {
            playerFire = -20;
        }
    }

    /** Entity.setFire: alight for the seconds given, less with Fire Protection, never shortening a longer fire. */
    private void setPlayerOnFire(int seconds) {
        int ticks = net.minecraft.enchantment.EnchantmentHelper.fireDuration(player.armor, seconds * 20);
        if (playerFire < ticks) playerFire = ticks;
    }

    /**
     * The liquid the camera is in, or null (ActiveRenderInfo.getBlockAtEntityViewpoint):
     * a liquid block counts only below its surface, which sits lower the
     * further the liquid has flowed; above it, the block above decides.
     */
    private String cameraLiquid() {
        double camX = player.x, camY = player.eyeY(), camZ = player.z;
        int view = gameSettings.thirdPersonView;
        if (view != 0) {
            double distance = thirdPersonDistance(view == 2 ? 1 : -1) * (view == 2 ? 1 : -1);
            camX += player.dirX() * distance;
            camY += player.dirY() * distance;
            camZ += player.dirZ() * distance;
        }
        int bx = (int) Math.floor(camX), by = (int) Math.floor(camY), bz = (int) Math.floor(camZ);
        if (by < 0 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null) return null;
        BlockState state = world.getState(bx, by, bz);
        String material = state.block().material();
        if (material.equals("water") || material.equals("lava")) {
            if (camY >= by + 1 - liquidSurfaceDrop(state)) {
                if (by + 1 >= World.HEIGHT) return null;
                material = world.get(bx, by + 1, bz).material();
            }
        }
        return material.equals("water") || material.equals("lava") ? material : null;
    }

    /** How far below the block's top a liquid's surface is: BlockLiquid.getLiquidHeightPercent minus a ninth. */
    private static float liquidSurfaceDrop(BlockState state) {
        String level = state.get("level");
        int meta = level == null ? 0 : Integer.parseInt(level);
        if (meta >= 8) meta = 0;
        return (meta + 1) / 9f - 1f / 9f;
    }

    /** Whether the player's eyes are under water (Entity.isInsideOfMaterial), which shows the underwater overlay. */
    private boolean eyesUnderWater() {
        double eye = player.eyeY();
        int bx = (int) Math.floor(player.x), by = (int) Math.floor(eye), bz = (int) Math.floor(player.z);
        if (by < 0 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null) return false;
        BlockState state = world.getState(bx, by, bz);
        if (!state.block().material().equals("water")) return false;
        return eye < by + 1 - liquidSurfaceDrop(state);
    }

    /** Advances the clouds and the fog's cave darkening once per unpaused tick. */
    private void tickSky() {
        if (sky == null || player == null) return;
        int x = (int) Math.floor(player.x), y = (int) Math.floor(player.y), z = (int) Math.floor(player.z);
        boolean inside = y >= 0 && y < World.HEIGHT;
        int skyLight = inside ? world.getSkyLight(x, y, z) : y >= World.HEIGHT ? 15 : 0;
        int blockLight = inside ? world.getBlockLight(x, y, z) : 0;
        sky.tick(levelInfo.worldTime, skyLight, blockLight, gameSettings.renderDistanceChunks);
    }

    /** This frame's sky, fog and lightmap for the time of day; returns the fog (and clear) colour. */
    private float[] prepareSky() {
        int x = (int) Math.floor(player.x), y = (int) Math.floor(player.y), z = (int) Math.floor(player.z);
        float temperature = net.minecraft.world.gen.ChunkPopulator.temperature(world.biomeAt(x, z), x, y, z);
        float[] fog = sky.prepare(levelInfo.worldTime, partialTicks, temperature, player.dirX(), player.dirY(), player.dirZ(),
            gameSettings.renderDistanceChunks, player.y, world.isFlat(), cameraLiquid());
        LightMap.update(SkyRenderer.sunBrightness(sky.angle()), gameSettings.gammaSetting);
        return fog;
    }

    private void renderWorld(float[] fog) {
        int renderDistance = gameSettings.getRenderDistanceBlocks();
        boolean skyVisible = gameSettings.renderDistanceChunks >= 4;
        double eyeY = player.eyeY();
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        String liquid = cameraLiquid();
        glHint(GL_FOG_HINT, GL_FASTEST);
        if (org.lwjgl.opengl.GL.getCapabilities().GL_NV_fog_distance) glFogi(0x855A, 0x855B); // eye-radial fog
        glFogfv(GL_FOG_COLOR, new float[]{fog[0], fog[1], fog[2], 1.0f});
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        loadCamera();
        if (skyVisible) {
            setupFog(liquid, 0f, renderDistance);
            perspective(worldFov(), width / (double) height, 0.05, renderDistance * 2.0);
            glMatrixMode(GL_MODELVIEW);
            sky.renderSky(levelInfo.worldTime, eyeY);
        }
        glEnable(GL_FOG);
        setupFog(liquid, renderDistance * 0.75f, renderDistance);
        if (eyeY < SkyRenderer.CLOUD_HEIGHT) renderClouds(renderDistance, skyVisible);
        perspective(worldFov(), width / (double) height, 0.05, renderDistance * SQRT_2);
        glMatrixMode(GL_MODELVIEW);
        glTranslated(-player.x, -eyeY, -player.z);
        glColor4f(1, 1, 1, 1);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        renderer.render(player, renderDistance);
        if (gameSettings.showChunkBorders) DebugRenderer.renderChunkBorders(player.x, player.y, player.z);
        renderItemEntities(renderDistance);
        tableBooks.render(world, guiTextures, partialTicks, player.x, player.z, Math.min(64.0, renderDistance));
        if (gameSettings.thirdPersonView != 0 && !player.dead) renderPlayerModel();
        WorldRenderer.Hit hit = currentScreen == null && !isSpectator() ? renderer.raycast(player, reach()) : null;
        drawDestroyProgress();
        if (hit != null && !gameSettings.hideGUI) drawSelection(hit);
        renderParticles();
        if (eyeY >= SkyRenderer.CLOUD_HEIGHT) {
            glPushMatrix();
            glTranslated(player.x, eyeY, player.z);
            renderClouds(renderDistance, skyVisible);
            glPopMatrix();
        }
        glClear(GL_DEPTH_BUFFER_BIT);
        renderHand(renderDistance);
    }

    /** Particles face the camera: the player's view, turned round in the front third-person view. */
    private void renderParticles() {
        if (particles == null) return;
        boolean front = gameSettings.thirdPersonView == 2;
        float yaw = PlayerAnimation.vanillaYaw(player) + (front ? 180f : 0f);
        float pitch = front ? -player.pitch : player.pitch;
        glEnable(GL_FOG);
        particles.render(partialTicks, yaw, pitch);
    }

    // ---- camera, hand and player model --------------------------------------------------------

    /**
     * Linear fog from {@code start} to {@code end}, or, with the camera in a
     * liquid, exponential fog: density 0.1 in water, 2.0 in lava (1.8.9
     * setupFog, HYPOTHESIS; no Respiration or Water Breathing yet).
     */
    private static void setupFog(String liquid, float start, float end) {
        if (liquid == null) {
            glFogi(GL_FOG_MODE, GL_LINEAR);
            glFogf(GL_FOG_START, start);
            glFogf(GL_FOG_END, end);
        } else {
            glFogi(GL_FOG_MODE, GL_EXP);
            glFogf(GL_FOG_DENSITY, liquid.equals("water") ? 0.1f : 2.0f);
        }
    }

    /** The field of view for the world: the setting times the sprint/flight modifier, narrower with the camera in water. */
    private float worldFov() {
        float fov = gameSettings.getFov() * playerAnimation.fov(partialTicks);
        if ("water".equals(cameraLiquid())) fov = fov * 60f / 70f;
        return fov;
    }

    /**
     * EntityRenderer.hurtCameraEffect: after a hit the view rolls up to 14
     * degrees and back over 10 ticks ({@code sin(t^4 x pi)}); a dead player's
     * view tips over sideways ({@code 40 - 8000 / (deathTime + 200)} degrees).
     */
    private void applyHurtEffect() {
        if (player == null) return;
        if (player.health <= 0) glRotatef(40f - 8000f / (player.deathTime + partialTicks + 200f), 0f, 0f, 1f);
        float time = player.hurtTime - partialTicks;
        if (time < 0f) return;
        time /= player.maxHurtTime;
        time = (float) Math.sin(time * time * time * time * Math.PI);
        glRotatef(-time * 14f, 0f, 0f, 1f);
    }

    /**
     * The view transform without the world position (1.8.9
     * setupCameraTransform/orientCamera, HYPOTHESIS): the view bob, then in
     * first person the eye pulled back 0.1; behind the player 4 blocks back,
     * in front 4 blocks ahead turned to face the player, both shortened
     * when a block is in the way; then pitch and yaw.
     */
    private void loadCamera() {
        applyHurtEffect();
        if (gameSettings.viewBobbing) applyViewBobbing();
        // The portal's wobble (EntityRenderer.setupCameraTransform): the view squeezed sideways about a turning axis.
        float portal = PlayerAnimation.lerp(player.prevTimeInPortal, player.timeInPortal, partialTicks);
        if (portal > 0f) {
            float squeeze = 5f / (portal * portal + 5f) - portal * 0.04f;
            squeeze *= squeeze;
            float turn = (rendererUpdateCount + partialTicks) * 20f;
            glRotatef(turn, 0f, 1f, 1f);
            glScalef(1f / squeeze, 1f, 1f);
            glRotatef(-turn, 0f, 1f, 1f);
        }
        int view = gameSettings.thirdPersonView;
        if (view == 0) {
            glTranslatef(0f, 0f, -0.1f);
        } else {
            double distance = thirdPersonDistance(view == 2 ? 1 : -1);
            if (view == 2) {
                glRotatef(180f, 0f, 1f, 0f);
                glTranslatef(0f, 0f, (float) distance);
            } else {
                glTranslatef(0f, 0f, (float) -distance);
            }
        }
        glRotatef(player.pitch, 1, 0, 0);
        glRotatef(player.yaw, 0, 1, 0);
    }

    /** How far the third-person camera can go (up to 4) along the view (+1) or against it (-1) before a block: eight rays 0.1 apart. */
    private double thirdPersonDistance(int direction) {
        double distance = 4.0;
        double dx = player.dirX() * direction, dy = player.dirY() * direction, dz = player.dirZ() * direction;
        double eyeX = player.x, eyeY = player.eyeY(), eyeZ = player.z;
        for (int i = 0; i < 8; i++) {
            double ox = ((i & 1) * 2 - 1) * 0.1, oy = ((i >> 1 & 1) * 2 - 1) * 0.1, oz = ((i >> 2 & 1) * 2 - 1) * 0.1;
            WorldRenderer.Hit hit = WorldRenderer.raycast(world, eyeX + ox, eyeY + oy, eyeZ + oz, dx, dy, dz, distance);
            if (hit == null) continue;
            double hx = hit.x() + hit.hitX() - eyeX, hy = hit.y() + hit.hitY() - eyeY, hz = hit.z() + hit.hitZ() - eyeZ;
            distance = Math.min(distance, Math.sqrt(hx * hx + hy * hy + hz * hz));
        }
        return distance;
    }

    /** Walking bob: side-to-side and up-down with each step, a roll and a nod, plus the fall/jump tilt. */
    private void applyViewBobbing() {
        PlayerAnimation animation = playerAnimation;
        float step = animation.distanceWalked - animation.prevDistanceWalked;
        float walked = -(animation.distanceWalked + step * partialTicks);
        float strength = PlayerAnimation.lerp(animation.prevCameraYaw, animation.cameraYaw, partialTicks);
        float tilt = PlayerAnimation.lerp(animation.prevCameraPitch, animation.cameraPitch, partialTicks);
        float phase = walked * (float) Math.PI;
        glTranslatef((float) Math.sin(phase) * strength * 0.5f, -Math.abs((float) Math.cos(phase) * strength), 0f);
        glRotatef((float) Math.sin(phase) * strength * 3f, 0f, 0f, 1f);
        glRotatef(Math.abs((float) Math.cos(phase - 0.2f) * strength) * 5f, 1f, 0f, 0f);
        glRotatef(tilt, 1f, 0f, 0f);
    }

    /** The lightmap colour at the player's eyes (what the hand and the player model are lit by). */
    private float[] eyeLight() {
        int x = (int) Math.floor(player.x), y = (int) Math.floor(player.eyeY()), z = (int) Math.floor(player.z);
        boolean inside = y >= 0 && y < World.HEIGHT;
        int sky = inside ? world.getSkyLight(x, y, z) : y >= World.HEIGHT ? 15 : 0;
        int block = inside ? world.getBlockLight(x, y, z) : 0;
        return LightMap.color(sky, block);
    }

    /**
     * The first-person hand: its own 70° projection (narrower in water), the
     * view bob, over a cleared depth buffer; then the screen overlays, which
     * F1 does not hide.
     */
    private void renderHand(int renderDistance) {
        if (gameSettings.thirdPersonView != 0 || player.dead) return;
        glDisable(GL_FOG);
        float fov = "water".equals(cameraLiquid()) ? 60f : 70f;
        perspective(fov, width / (double) height, 0.05, renderDistance * 2.0);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        if (!gameSettings.hideGUI && !isSpectator()) {
            glPushMatrix();
            applyHurtEffect();
            if (gameSettings.viewBobbing) applyViewBobbing();
            firstPersonRenderer.render(player, playerAnimation, partialTicks, eyeLight());
            glPopMatrix();
            glShadeModel(GL_SMOOTH);
        }
        renderOverlays();
    }

    /** The underwater tint and the flames of a burning player, over the view (ItemRenderer.renderOverlays). */
    private void renderOverlays() {
        if (isSpectator()) return;
        glDisable(GL_ALPHA_TEST);
        if (eyesUnderWater()) renderWaterOverlay();
        if (playerFire > 0) renderFireOverlay();
        glEnable(GL_ALPHA_TEST);
        glColor4f(1, 1, 1, 1);
    }

    /**
     * misc/underwater.png tiled four times across the screen at half
     * opacity, darkened like the light at the eyes and sliding as the player
     * turns (1/64 of a tile per degree), HYPOTHESIS.
     */
    private void renderWaterOverlay() {
        int x = (int) Math.floor(player.x), y = (int) Math.floor(player.eyeY()), z = (int) Math.floor(player.z);
        boolean inside = y >= 0 && y < World.HEIGHT;
        int skyLight = inside ? world.getSkyLight(x, y, z) : 15, blockLight = inside ? world.getBlockLight(x, y, z) : 0;
        int light = Math.max(skyLight - SkyRenderer.skylightSubtracted(sky.angle()), blockLight);
        float brightness = SkyRenderer.brightness(Math.max(0, light));
        guiTextures.bind(new net.minecraft.util.ResourceLocation("minecraft:textures/misc/underwater.png"));
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(brightness, brightness, brightness, 0.5f);
        float u = -PlayerAnimation.vanillaYaw(player) / 64f, v = player.pitch / 64f;
        glPushMatrix();
        glLoadIdentity();
        glBegin(GL_QUADS);
        glTexCoord2f(4f + u, 4f + v); glVertex3f(-1f, -1f, -0.5f);
        glTexCoord2f(0f + u, 4f + v); glVertex3f(1f, -1f, -0.5f);
        glTexCoord2f(0f + u, 0f + v); glVertex3f(1f, 1f, -0.5f);
        glTexCoord2f(4f + u, 0f + v); glVertex3f(-1f, 1f, -0.5f);
        glEnd();
        glPopMatrix();
        glColor4f(1, 1, 1, 1);
    }

    /** Two flame sprites rising from the bottom corners of the view, drawn over everything (renderFireInFirstPerson). */
    private void renderFireOverlay() {
        TextureAtlas.Tile tile = atlas.tile(new net.minecraft.util.ResourceLocation("minecraft:textures/blocks/fire_layer_1.png"));
        if (tile == null) return;
        glColor4f(1f, 1f, 1f, 0.9f);
        glDepthFunc(GL_ALWAYS);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        atlas.bind();
        for (int i = 0; i < 2; i++) {
            glPushMatrix();
            glLoadIdentity();
            glTranslatef(-(i * 2 - 1) * 0.24f, -0.3f, 0f);
            glRotatef((i * 2 - 1) * 10f, 0f, 1f, 0f);
            float x0 = -0.5f, x1 = 0.5f, y0 = -0.5f, y1 = 0.5f, z = -0.5f;
            glBegin(GL_QUADS);
            glTexCoord2f(tile.u1(), tile.v1()); glVertex3f(x0, y0, z);
            glTexCoord2f(tile.u0(), tile.v1()); glVertex3f(x1, y0, z);
            glTexCoord2f(tile.u0(), tile.v0()); glVertex3f(x1, y1, z);
            glTexCoord2f(tile.u1(), tile.v0()); glVertex3f(x0, y1, z);
            glEnd();
            glPopMatrix();
        }
        glColor4f(1, 1, 1, 1);
        glDepthMask(true);
        glDepthFunc(GL_LEQUAL);
    }

    /**
     * The player seen in third person (RendererLivingEntity/RenderPlayer,
     * HYPOTHESIS): turned to the body's yaw, flipped into model space,
     * 0.9375 scale, posed, lit by the standard lights and the light at the
     * eyes, with the held item in the right hand.
     */
    private void renderPlayerModel() {
        PlayerAnimation animation = playerAnimation;
        float body = animation.bodyYaw(partialTicks);
        float amount = Math.min(1f, PlayerAnimation.lerp(animation.prevLimbSwingAmount, animation.limbSwingAmount, partialTicks));
        float swing = animation.limbSwing - animation.limbSwingAmount * (1f - partialTicks);
        StandardLighting.enable();
        glPushMatrix();
        glTranslated(player.x, player.y, player.z);
        drawPlayer(body, PlayerAnimation.wrap(PlayerAnimation.vanillaYaw(player) - body), player.pitch, swing, amount,
            animation.ticksExisted + partialTicks, animation.swingProgress(partialTicks), eyeLight());
        glPopMatrix();
        StandardLighting.disable();
        glShadeModel(GL_SMOOTH);
        glColor4f(1, 1, 1, 1);
    }

    /**
     * The posed player with its held item at the current origin (feet), as
     * RendererLivingEntity draws it: turned to the body's yaw, flipped into
     * model space and scaled 0.9375. Lighting is the caller's.
     */
    private void drawPlayer(float bodyYaw, float headYaw, float pitch, float limbSwing, float limbAmount, float age,
                            float attackSwing, float[] light) {
        ItemStack held = inventory == null ? null : inventory.getCurrentItem();
        glPushMatrix();
        glRotatef(180f - bodyYaw, 0f, 1f, 0f);
        glScalef(-1f, -1f, 1f);
        glScalef(0.9375f, 0.9375f, 0.9375f);
        glTranslatef(0f, -1.5078125f, 0f);
        playerModel.setPose(new PlayerModel.Pose(limbSwing, limbAmount, age, headYaw, pitch, attackSwing,
            player.sneaking, held != null, held != null && player.isBlocking()));
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        guiTextures.bind(FirstPersonRenderer.STEVE);
        glColor4f(light[0], light[1], light[2], 1f);
        playerModel.render(player.sneaking);
        if (inventory != null)
            net.minecraft.client.renderer.ArmorLayer.render(playerModel, inventory.armorInventory, player.sneaking, guiTextures, light,
                player.ticksExisted() + partialTicks);
        if (player.hurtTime > 0 || player.deathTime > 0) {
            // RendererLivingEntity.setBrightness: a hurt entity is mixed 30% toward red.
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthFunc(GL_LEQUAL);
            glColor4f(1f, 0f, 0f, 0.3f);
            playerModel.render(player.sneaking);
            glEnable(GL_TEXTURE_2D);
            glColor4f(1f, 1f, 1f, 1f);
        }
        if (held != null) {
            glPushMatrix();
            playerModel.rightArm.transform();
            glTranslatef(-0.0625f, 0.4375f, 0.0625f);
            if (player.sneaking) glTranslatef(0f, 0.203125f, 0f);
            itemRenderer.renderHeld(held, ItemRenderer.Held.THIRD_PERSON, light[0], light[1], light[2]);
            glPopMatrix();
        }
        glPopMatrix();
    }

    /**
     * The player in an inventory screen (1.8.9 GuiInventory.drawEntityOnScreen,
     * HYPOTHESIS): feet at (x, y), {@code scale} GUI pixels per block, lit by
     * the standard lights, body and head turned toward the mouse
     * ({@code lookX}/{@code lookY} are the feet-relative offsets vanilla
     * passes: positive when the mouse is left of / above the reference point),
     * still walking and swinging as in the world, fully bright.
     */
    @Override public void drawPlayerOnScreen(int x, int y, int scale, float lookX, float lookY) {
        if (player == null) return;
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        glPushMatrix();
        glTranslatef(x, y, 50f);
        glScalef(-scale, scale, scale);
        glRotatef(180f, 0f, 0f, 1f);
        glRotatef(135f, 0f, 1f, 0f);
        StandardLighting.enable();
        glRotatef(-135f, 0f, 1f, 0f);
        float tilt = (float) Math.atan(lookY / 40f);
        float turn = (float) Math.atan(lookX / 40f);
        glRotatef(-tilt * 20f, 1f, 0f, 0f);
        float body = turn * 20f, head = turn * 40f, pitch = -tilt * 20f;
        PlayerAnimation animation = playerAnimation;
        drawPlayer(body, head - body, pitch, animation.limbSwing, Math.min(1f, animation.limbSwingAmount),
            animation.ticksExisted + 1f, animation.swingProgress(1f), new float[]{1f, 1f, 1f});
        glPopMatrix();
        StandardLighting.disable();
        glColor4f(1, 1, 1, 1);
        glClear(GL_DEPTH_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);
    }

    /** Clouds with the terrain fog and a far plane four times the render distance (the modelview holds only the camera's rotation). */
    private void renderClouds(int renderDistance, boolean skyVisible) {
        if (!skyVisible || !gameSettings.clouds) return;
        perspective(worldFov(), width / (double) height, 0.05, renderDistance * 4.0);
        glMatrixMode(GL_MODELVIEW);
        sky.renderClouds(player.x, player.eyeY(), player.z, partialTicks, gameSettings.fancyGraphics);
        perspective(worldFov(), width / (double) height, 0.05, renderDistance * SQRT_2);
        glMatrixMode(GL_MODELVIEW);
        glDisable(GL_CULL_FACE);
    }

    private void perspective(double fov, double aspect, double near, double far) {
        double top = near * Math.tan(Math.toRadians(fov) * 0.5);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glFrustum(-top * aspect, top * aspect, -top, top, near, far);
    }

    /**
     * Dropped items: bobbing 0-0.2 blocks and turning once every ~6 seconds
     * (1.8.9 RenderEntityItem), lit by the light where they are, with extra
     * copies for bigger stacks (2 above 1 item, 3 above 16, 4 above 32, 5 above 48).
     */
    private void renderItemEntities(int renderDistance) {
        double limit = (double) renderDistance * renderDistance;
        glEnable(GL_DEPTH_TEST);
        for (Entity entity : server.entities().values()) {
            if (!(entity instanceof EntityItem item) || item.isRemoved()) continue;
            double x = item.prevX + (item.x() - item.prevX) * partialTicks;
            double y = item.prevY + (item.y() - item.prevY) * partialTicks;
            double z = item.prevZ + (item.z() - item.prevZ) * partialTicks;
            double dx = x - player.x, dz = z - player.z;
            if (dx * dx + dz * dz > limit) continue;
            int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
            boolean inWorld = by >= 0 && by < World.HEIGHT;
            float[] light = net.minecraft.client.renderer.LightMap.color(inWorld ? world.getSkyLight(bx, by, bz) : 15,
                inWorld ? world.getBlockLight(bx, by, bz) : 0);
            float time = item.age + partialTicks;
            float bob = (float) Math.sin(time / 10.0f + item.hoverStart) * 0.1f + 0.1f;
            float spin = (float) Math.toDegrees(time / 20.0f + item.hoverStart);
            int count = item.stack().count();
            int copies = count > 48 ? 5 : count > 32 ? 4 : count > 16 ? 3 : count > 1 ? 2 : 1;
            boolean block = itemRenderer.isBlockModel(item.stack().item());
            Random spread = new Random(187L);
            glPushMatrix();
            glTranslated(x, y + bob + 0.25, z);
            glRotatef(spin, 0, 1, 0);
            for (int copy = 0; copy < copies; copy++) {
                glPushMatrix();
                if (copy > 0) {
                    if (block) glTranslatef((spread.nextFloat() * 2 - 1) * 0.15f * 0.5f, (spread.nextFloat() * 2 - 1) * 0.15f * 0.5f,
                        (spread.nextFloat() * 2 - 1) * 0.15f * 0.5f);
                    else glTranslatef(0, 0, copy * -0.046875f);
                }
                itemRenderer.renderItemInWorld(item.stack(), light[0], light[1], light[2]);
                glPopMatrix();
            }
            glPopMatrix();
            if (item.isBurning()) renderEntityFire(x, y, z, (float) EntityItem.SIZE, (float) EntityItem.SIZE);
        }
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
    }

    /**
     * Flames on a burning entity (Render.renderEntityOnFire, HYPOTHESIS):
     * alternating fire_layer_0/1 sprites stacked up its height, each 0.45
     * higher, 10% narrower and a little nearer the camera, turned to face it.
     */
    private void renderEntityFire(double x, double y, double z, float entityWidth, float entityHeight) {
        TextureAtlas.Tile[] layers = {
            atlas.tile(new net.minecraft.util.ResourceLocation("minecraft:textures/blocks/fire_layer_0.png")),
            atlas.tile(new net.minecraft.util.ResourceLocation("minecraft:textures/blocks/fire_layer_1.png"))};
        if (layers[0] == null || layers[1] == null) return;
        atlas.bind();
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        glPushMatrix();
        glTranslated(x, y, z);
        float scale = entityWidth * 1.4f;
        glScalef(scale, scale, scale);
        float half = 0.5f, remaining = entityHeight / scale, bottom = 0f, depth = 0f;
        float cameraYaw = PlayerAnimation.vanillaYaw(player) + (gameSettings.thirdPersonView == 2 ? 180f : 0f);
        glRotatef(-cameraYaw, 0f, 1f, 0f);
        glTranslatef(0f, 0f, -0.3f + (int) remaining * 0.02f);
        glColor4f(1f, 1f, 1f, 1f);
        glBegin(GL_QUADS);
        for (int i = 0; remaining > 0f; i++) {
            TextureAtlas.Tile tile = layers[i % 2];
            float u0 = tile.u0(), u1 = tile.u1();
            if (i / 2 % 2 == 0) { float swap = u0; u0 = u1; u1 = swap; }
            glTexCoord2f(u1, tile.v1()); glVertex3f(half, 0f - bottom, depth);
            glTexCoord2f(u0, tile.v1()); glVertex3f(-half, 0f - bottom, depth);
            glTexCoord2f(u0, tile.v0()); glVertex3f(-half, 1.4f - bottom, depth);
            glTexCoord2f(u1, tile.v0()); glVertex3f(half, 1.4f - bottom, depth);
            remaining -= 0.45f;
            bottom -= 0.45f;
            half *= 0.9f;
            depth += 0.03f;
        }
        glEnd();
        glPopMatrix();
        glColor4f(1, 1, 1, 1);
    }

    /** Cracks (destroy_stage_0-9) over the block being broken, multiplied onto it like vanilla's DST_COLOR/SRC_COLOR blend. */
    private void drawDestroyProgress() {
        int stage = playerController.destroyStage();
        int[] pos = playerController.currentBlock();
        if (stage < 0 || pos == null) return;
        BlockState state = world.getState(pos[0], pos[1], pos[2]);
        if (state.block().isAir()) return;
        AxisAlignedBB box = state.block().selectionBox(state.block().actualState(world, pos[0], pos[1], pos[2], state),
            world, pos[0], pos[1], pos[2]);
        if (box == null) box = AxisAlignedBB.FULL;
        double e = 0.002;
        double x0 = pos[0] + box.minX() - e, y0 = pos[1] + box.minY() - e, z0 = pos[2] + box.minZ() - e;
        double x1 = pos[0] + box.maxX() + e, y1 = pos[1] + box.maxY() + e, z1 = pos[2] + box.maxZ() + e;
        guiTextures.bind(new net.minecraft.util.ResourceLocation("minecraft:textures/blocks/destroy_stage_" + stage + ".png"));
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_DST_COLOR, GL_SRC_COLOR);
        // The crack textures are see-through outside the cracks: without the alpha test those texels
        // multiply the block by their (light) colour and leave a pale square over it.
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        glDisable(GL_CULL_FACE);
        glDepthMask(false);
        glColor4f(1, 1, 1, 0.5f);
        glBegin(GL_QUADS);
        // UVs follow the block-relative box so the cracks line up with the block's texture grid.
        float bx0 = (float) box.minX(), bx1 = (float) box.maxX(), by0 = (float) box.minY(), by1 = (float) box.maxY();
        float bz0 = (float) box.minZ(), bz1 = (float) box.maxZ();
        crackFace(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 1 - bx0, 1 - bx1, 1 - by0, 1 - by1);
        crackFace(x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, bx1, bx0, 1 - by0, 1 - by1);
        crackFace(x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, 1 - bz1, 1 - bz0, 1 - by0, 1 - by1);
        crackFace(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1 - bz0, 1 - bz1, 1 - by0, 1 - by1);
        crackFace(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, bx0, bx1, bz0, bz1);
        crackFace(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, bx0, bx1, bz1, bz0);
        glEnd();
        glDepthMask(true);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1, 1, 1, 1);
    }

    private static void crackFace(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz,
                                  double dx, double dy, double dz, float u0, float u1, float v0, float v1) {
        glTexCoord2f(u0, v0); glVertex3d(ax, ay, az);
        glTexCoord2f(u1, v0); glVertex3d(bx, by, bz);
        glTexCoord2f(u1, v1); glVertex3d(cx, cy, cz);
        glTexCoord2f(u0, v1); glVertex3d(dx, dy, dz);
    }

    private void drawSelection(WorldRenderer.Hit hit) {
        double e = 0.002;
        double x0 = hit.box().minX() - e, y0 = hit.box().minY() - e, z0 = hit.box().minZ() - e;
        double x1 = hit.box().maxX() + e, y1 = hit.box().maxY() + e, z1 = hit.box().maxZ() + e;
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_FOG);
        glColor4f(0, 0, 0, .4f);
        glLineWidth(2f);
        glBegin(GL_LINES);
        line(x0,y0,z0,x1,y0,z0); line(x1,y0,z0,x1,y0,z1); line(x1,y0,z1,x0,y0,z1); line(x0,y0,z1,x0,y0,z0);
        line(x0,y1,z0,x1,y1,z0); line(x1,y1,z0,x1,y1,z1); line(x1,y1,z1,x0,y1,z1); line(x0,y1,z1,x0,y1,z0);
        line(x0,y0,z0,x0,y1,z0); line(x1,y0,z0,x1,y1,z0); line(x1,y0,z1,x1,y1,z1); line(x0,y0,z1,x0,y1,z1);
        glEnd();
        glLineWidth(1f);
        glEnable(GL_FOG);
        glEnable(GL_TEXTURE_2D);
    }

    private static void line(double ax, double ay, double az, double bx, double by, double bz) {
        glVertex3d(ax, ay, az); glVertex3d(bx, by, bz);
    }

    private void drawHud(ScaledResolution resolution) {
        int guiWidth = resolution.getScaledWidth(), guiHeight = resolution.getScaledHeight();
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_FOG);
        setupGuiProjection(resolution);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1, 1, 1, 1);

        float portal = PlayerAnimation.lerp(player.prevTimeInPortal, player.timeInPortal, partialTicks);
        if (portal > 0f) renderPortalOverlay(portal, guiWidth, guiHeight);

        boolean showDebug = gameSettings.showDebugInfo;
        boolean axisCrosshair = showDebug && !gameSettings.reducedDebugInfo;
        GuiIngame.Status status = new GuiIngame.Status(survivalHud(), isSpectator(), levelInfo.hardcore, player.health,
            player.foodLevel, inventory.getTotalArmorValue(), player.experienceLevel, player.experience,
            player.headInWater(), player.air, player.hurtResistantTime);
        ingameGui.renderGameOverlay(guiWidth, guiHeight, inventory, status, !axisCrosshair);
        if (axisCrosshair) GuiOverlayDebug.renderAxisCrosshair(guiWidth, guiHeight, player.yaw + 180f, player.pitch);
        if (showDebug) debugOverlay.render(fontRenderer, guiWidth, guiHeight, debugSnapshot(), gameSettings.showLagometer);
        ingameGui.renderChat(guiHeight, currentScreen instanceof GuiChat);
        glEnable(GL_DEPTH_TEST);
        glColor4f(1, 1, 1, 1);
    }

    private GuiOverlayDebug.Snapshot debugSnapshot() {
        int bx = (int) Math.floor(player.x), by = (int) Math.floor(player.y), bz = (int) Math.floor(player.z);
        boolean inWorld = by >= 0 && by < World.HEIGHT;
        Biome biome = world.biomeAt(bx, bz);
        String biomeName = BiomeNames.get(biome.id());
        if (biomeName == null) biomeName = biome.name().path();
        GuiOverlayDebug.Target target = null;
        WorldRenderer.Hit hit = isSpectator() ? null : renderer.raycast(player, reach());
        if (hit != null) {
            BlockState state = world.getState(hit.x(), hit.y(), hit.z());
            ActualState actual = state.block().actualState(world, hit.x(), hit.y(), hit.z(), state);
            target = new GuiOverlayDebug.Target(hit.x(), hit.y(), hit.z(), state.block().name().toString(),
                new TreeMap<>(actual.values()));
        }
        int loaded = world.loadedChunkCount();
        return new GuiOverlayDebug.Snapshot(fps, chunkUpdates, renderer.renderedCount(), renderer.meshCount(), gameSettings.renderDistanceChunks,
            0, server.entities().values().size(), particles == null ? 0 : particles.count(), loaded,
            player.x, player.y, player.z, player.yaw + 180f, player.pitch,
            biomeName, inWorld, inWorld ? world.getSkyLight(bx, by, bz) : 15, inWorld ? world.getBlockLight(bx, by, bz) : 0,
            GuiOverlayDebug.localDifficulty(levelInfo.difficulty, levelInfo.worldTime, 0L), levelInfo.worldTime,
            target, gameSettings.reducedDebugInfo,
            width, height, glVendor, glRenderer, glVersion, GuiOverlayDebug.cpuDescription());
    }

    @Override
    public void renderBlockIcon(BlockState state, int x, int y) {
        BakedModel model = modelManager.getModel(state);
        if (model == null) return;
        TextureAtlas.Tile tile = atlas.tile(model.particleTexture());
        if (tile == null) return;
        atlas.bind();
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
        glBegin(GL_QUADS);
        glTexCoord2f(tile.u0(), tile.v0()); glVertex2f(x, y);
        glTexCoord2f(tile.u1(), tile.v0()); glVertex2f(x + 16, y);
        glTexCoord2f(tile.u1(), tile.v1()); glVertex2f(x + 16, y + 16);
        glTexCoord2f(tile.u0(), tile.v1()); glVertex2f(x, y + 16);
        glEnd();
    }

    private static void drawGuiRegion(float sx, float sy, float sw, float sh, float x, float y, float w, float h) {
        float u0 = sx / 256f, v0 = sy / 256f, u1 = (sx + sw) / 256f, v1 = (sy + sh) / 256f;
        glBegin(GL_QUADS);
        glTexCoord2f(u0, v0); glVertex2f(x, y);
        glTexCoord2f(u1, v0); glVertex2f(x + w, y);
        glTexCoord2f(u1, v1); glVertex2f(x + w, y + h);
        glTexCoord2f(u0, v1); glVertex2f(x, y + h);
        glEnd();
    }

    // ---- gameplay input -----------------------------------------------------------------------

    /**
     * One tick of mouse input, as 1.8.9's runTick: a press of attack starts
     * breaking (instantly in Creative), holding it keeps damaging the block,
     * use places on press and again every 4 ticks while held, pick block
     * copies the targeted block to the hotbar.
     */
    private void interact() {
        boolean attackPressed = gameSettings.keyBindAttack.isPressed();
        boolean usePressed = gameSettings.keyBindUseItem.isPressed();
        boolean pickPressed = gameSettings.keyBindPickBlock.isPressed();
        if (!mouseCaptured || player.dead || isSpectator()) {
            playerController.resetBlockRemoving();
            return;
        }
        if (player.isUsingItem()) {
            // Minecraft.runTick: letting go of the use key stops blocking; meanwhile clicks do nothing
            // and mining pauses where it was.
            if (!gameSettings.keyBindUseItem.isKeyDown()) player.clearItemInUse();
            return;
        }
        WorldRenderer.Hit hit = renderer.raycast(player, reach());
        // The arm swings on every attack click, every tick a block is being mined, and on each placement.
        if (attackPressed) playerAnimation.swing();
        if (pickPressed && hit != null) {
            Item picked = itemForBlock(world.getState(hit.x(), hit.y(), hit.z()));
            if (picked != null) inventory.pickItem(picked, isCreative());
        }
        boolean canBreak = levelInfo.gameType != WorldSettings.GameType.ADVENTURE;
        if (attackPressed && hit != null && canBreak) {
            // Fire on the clicked face goes out first (World.extinguishFire): in Creative that is all the click
            // does; in Survival the block behind starts being mined as well.
            boolean extinguished = extinguishFire(hit);
            if (playerController.clickBlock(hit.x(), hit.y(), hit.z(), isCreative(), breakProgress(hit))
                && !(extinguished && isCreative()))
                breakBlock(hit.x(), hit.y(), hit.z());
        }
        if (gameSettings.keyBindAttack.isKeyDown() && hit != null && canBreak) {
            playerAnimation.swing();
            if (particles != null) particles.addBlockHitEffects(hit.x(), hit.y(), hit.z(), hit.face());
            // Moving onto a new block in Survival clicks it again, putting out fire on it.
            int[] current = playerController.currentBlock();
            boolean newBlock = !isCreative() && !attackPressed
                && (current == null || current[0] != hit.x() || current[1] != hit.y() || current[2] != hit.z());
            if (newBlock) extinguishFire(hit);
            boolean broken = playerController.onPlayerDamageBlock(hit.x(), hit.y(), hit.z(), isCreative(), breakProgress(hit));
            // Creative's repeat break (every 5 ticks while held) also puts fire out instead of breaking.
            if (broken && isCreative() && extinguishFire(hit)) broken = false;
            if (playerController.takeHitSound()) {
                // PlayerControllerMP: every fourth mining tick, the step sound at (volume + 1) / 8, frequency x 0.5.
                net.minecraft.block.BlockSoundType type = net.minecraft.block.BlockSoundType.of(world.get(hit.x(), hit.y(), hit.z()));
                playSound(type.stepSound(), hit.x() + 0.5, hit.y() + 0.5, hit.z() + 0.5, (type.volume() + 1f) / 8f, type.frequency() * 0.5f);
            }
            if (broken) breakBlock(hit.x(), hit.y(), hit.z());
        } else {
            playerController.resetBlockRemoving();
        }
        if (usePressed || gameSettings.keyBindUseItem.isKeyDown() && rightClickDelayTimer == 0) {
            rightClickDelayTimer = 4;
            if (activateBlock(hit)) playerAnimation.swing();
            else if (useFlintAndSteel(hit)) playerAnimation.swing();
            else if (!useBucket()) {
                if (hit != null && placeHeldBlock(hit)) playerAnimation.swing();
                else useHeldItem();
            }
        }
    }

    /**
     * World.extinguishFire: fire on the clicked face of a block goes out
     * with a fizz (0.5 volume, pitch 2.6 +- 0.8). Fire itself can't be
     * targeted, so this is how it is punched out. Returns whether it was.
     */
    private boolean extinguishFire(WorldRenderer.Hit hit) {
        int x = hit.x() + hit.face().xOffset, y = hit.y() + hit.face().yOffset, z = hit.z() + hit.face().zOffset;
        if (!world.inBounds(x, y, z) || !world.get(x, y, z).name().path().equals("fire")) return false;
        playSound("random.fizz", x + 0.5, y + 0.5, z + 0.5, 0.5f, 2.6f + (random.nextFloat() - random.nextFloat()) * 0.8f);
        world.setStateAndUpdate(x, y, z, BlockState.of(blockRegistry.air(), 0));
        return true;
    }

    /** Fraction of the targeted block broken per tick with the held item. */
    private float breakProgress(WorldRenderer.Hit hit) {
        Block block = world.get(hit.x(), hit.y(), hit.z());
        ItemStack held = inventory.getCurrentItem();
        int eyeX = (int) Math.floor(player.x), eyeY = (int) Math.floor(player.eyeY()), eyeZ = (int) Math.floor(player.z);
        boolean headInWater = eyeY >= 0 && eyeY < World.HEIGHT && world.get(eyeX, eyeY, eyeZ).material().equals("water");
        int efficiency = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.EFFICIENCY, held);
        boolean aquaAffinity = net.minecraft.enchantment.EnchantmentHelper.maxLevel(
            net.minecraft.enchantment.Enchantment.AQUA_AFFINITY, inventory.armorInventory) > 0;
        return ToolValues.breakProgressPerTick(held == null ? null : held.item(), block.name().path(), block.material(),
            block.hardness(), player.onGround, headInWater, efficiency, aquaAffinity);
    }

    /**
     * Breaks a block as the player: Creative removes it (both halves of a
     * door, bed or tall plant) and drops nothing, and cannot break with a
     * sword in hand; Survival drops what the held item harvests, wears the
     * tool, and turns ice over a solid or liquid block into water. Blocks
     * that lose their support pop off and drop their item in both modes.
     */
    private void breakBlock(int x, int y, int z) {
        BlockState state = world.getState(x, y, z);
        Block block = state.block();
        if (block.isAir()) return;
        ItemStack held = inventory.getCurrentItem();
        boolean creative = isCreative();
        if (creative && held != null && ToolValues.type(held.item()) == ToolValues.Type.SWORD) return;
        if (!creative && block.hardness() < 0) return;
        String name = block.name().path();
        if (creative) {
            int[] other = otherHalf(x, y, z, state);
            if (other != null && world.get(other[0], other[1], other[2]) == block) {
                world.setState(other[0], other[1], other[2], BlockState.of(blockRegistry.air(), 0));
                BlockUpdates.afterChange(world, other[0], other[1], other[2], this::blockPopped);
            }
        }
        BlockState replacement = BlockState.of(blockRegistry.air(), 0);
        if (!creative) {
            if (dropsEnabled()) {
                int meta = state.metadata();
                if (name.equals("double_plant") && (meta & 8) != 0 && y > 0 && world.get(x, y - 1, z) == block)
                    meta = world.getState(x, y - 1, z).metadata();
                int fortune = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.FORTUNE, held);
                boolean silk = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.SILK_TOUCH, held) > 0;
                for (ItemStack drop : blockDrops.drops(name, block.material(), meta, held == null ? null : held.item(),
                        fortune, silk, block.isFullCube(), random))
                    spawnItem(EntityItem.fromBlock(world, x, y, z, drop, random));
            }
            if (name.equals("ice") && y > 0) {
                Block below = world.get(x, y - 1, z);
                if (below.isSolid() || below.material().equals("water") || below.material().equals("lava"))
                    replacement = BlockState.of(blockRegistry.get("minecraft:flowing_water"), 0);
            }
            if (held != null) {
                int wear = ToolValues.damageForBreak(held.item(), name, block.material(), block.hardness());
                if (net.minecraft.enchantment.EnchantmentHelper.damageItem(held, wear, random)) {
                    inventory.mainInventory[inventory.currentItem] = null;
                    playSound("random.break", player.x, player.y, player.z, 0.8f, 0.8f + random.nextFloat() * 0.4f);
                }
            }
        }
        if (particles != null) particles.addBlockDestroyEffects(x, y, z, state);
        playBreakSound(x, y, z, state);
        world.setState(x, y, z, replacement);
        BlockUpdates.afterChange(world, x, y, z, this::blockPopped);
    }

    /**
     * A held bucket (ItemBucket.onItemRightClick), aimed 5 blocks out: an
     * empty one scoops up a water or lava source (Creative keeps the empty
     * bucket), a full one pours a source against the face it points at,
     * breaking a non-solid block there, and empties unless in Creative.
     * Returns whether a bucket was held.
     */
    private boolean useBucket() {
        ItemStack held = inventory.getCurrentItem();
        if (held == null) return false;
        String name = held.item().name().path();
        boolean empty = name.equals("bucket");
        if (!empty && !name.equals("water_bucket") && !name.equals("lava_bucket")) return false;
        WorldRenderer.Hit hit = WorldRenderer.raycast(world, player.x, player.eyeY(), player.z,
            player.dirX(), player.dirY(), player.dirZ(), 5.0, empty);
        if (hit == null || levelInfo.gameType == WorldSettings.GameType.ADVENTURE) return true;
        if (empty) {
            BlockState state = world.getState(hit.x(), hit.y(), hit.z());
            String liquid = net.minecraft.block.Liquids.liquid(state.block());
            if (liquid == null || state.metadata() != 0) return true;
            world.setStateAndUpdate(hit.x(), hit.y(), hit.z(), BlockState.of(blockRegistry.air(), 0));
            if (isCreative()) return true;
            ItemStack full = new ItemStack(itemRegistry.get("minecraft:" + liquid + "_bucket"));
            if (held.count() <= 1) {
                inventory.mainInventory[inventory.currentItem] = full;
            } else {
                held.split(1);
                if (!inventory.addItemStackToInventory(full)) dropPlayerItem(full);
            }
            return true;
        }
        int x = hit.x() + hit.face().xOffset, y = hit.y() + hit.face().yOffset, z = hit.z() + hit.face().zOffset;
        if (!world.inBounds(x, y, z)) return true;
        BlockState target = world.getState(x, y, z);
        boolean nonSolid = !net.minecraft.block.Liquids.isSolid(target.block());
        if (!target.block().isAir() && !nonSolid) return true;
        if (nonSolid && !target.block().isAir() && !net.minecraft.block.Liquids.isLiquid(target.block())) {
            if (particles != null) particles.addBlockDestroyEffects(x, y, z, target);
            playBreakSound(x, y, z, target);
            dropBlock(x, y, z, target);
        }
        Block liquid = blockRegistry.get(name.equals("water_bucket") ? "minecraft:flowing_water" : "minecraft:flowing_lava");
        world.setStateAndUpdate(x, y, z, BlockState.of(liquid, 0));
        if (!isCreative()) inventory.mainInventory[inventory.currentItem] = new ItemStack(itemRegistry.get("minecraft:bucket"));
        return true;
    }

    /**
     * Right-click on a block that opens something (Block.onBlockActivated):
     * a crafting table opens the 3x3 grid and a furnace (lit or not) its
     * screen, unless the player sneaks with something in hand (then the item
     * is used or placed instead).
     */
    private boolean activateBlock(WorldRenderer.Hit hit) {
        if (hit == null || player.sneaking && inventory.getCurrentItem() != null) return false;
        String name = world.get(hit.x(), hit.y(), hit.z()).name().path();
        if (name.equals("crafting_table")) {
            displayGuiScreen(new net.minecraft.client.gui.GuiCrafting(hit.x(), hit.y(), hit.z()));
            return true;
        }
        if (name.equals("furnace") || name.equals("lit_furnace")) {
            displayGuiScreen(new net.minecraft.client.gui.GuiFurnace(hit.x(), hit.y(), hit.z()));
            return true;
        }
        if (name.equals("enchanting_table")) {
            displayGuiScreen(new net.minecraft.client.gui.GuiEnchantment(hit.x(), hit.y(), hit.z()));
            return true;
        }
        if (name.equals("anvil")) {
            displayGuiScreen(new net.minecraft.client.gui.GuiRepair(hit.x(), hit.y(), hit.z()));
            return true;
        }
        return false;
    }

    /**
     * A broken container's items spill where it stood
     * (InventoryHelper.dropInventoryItems): each stack at a random point in
     * the block, in random handfuls of 10 to 30, tossed up and out a little.
     * They drop even when block drops are off, as in vanilla.
     */
    private void spillContents(int x, int y, int z, List<ItemStack> contents) {
        if (server == null) return;
        for (ItemStack stack : contents) {
            double px = x + random.nextFloat() * 0.8 + 0.1, py = y + random.nextFloat() * 0.8 + 0.1;
            double pz = z + random.nextFloat() * 0.8 + 0.1;
            while (!stack.isEmpty()) {
                int handful = Math.min(random.nextInt(21) + 10, stack.count());
                EntityItem item = new EntityItem(world, px, py, pz, stack.split(handful));
                item.motionX = random.nextGaussian() * 0.05;
                item.motionY = random.nextGaussian() * 0.05 + 0.2;
                item.motionZ = random.nextGaussian() * 0.05;
                spawnItem(item);
            }
        }
    }

    /**
     * Flint and steel on a block (ItemFlintAndSteel.onItemUse): fire in the
     * air beside the face clicked, with the ignite sound, and one use of
     * durability either way (none in Creative). Fire lit inside an obsidian
     * frame becomes a portal.
     */
    private boolean useFlintAndSteel(WorldRenderer.Hit hit) {
        ItemStack held = inventory.getCurrentItem();
        if (hit == null || held == null || !held.item().name().path().equals("flint_and_steel")) return false;
        if (levelInfo.gameType == WorldSettings.GameType.ADVENTURE) return false;
        int x = hit.x() + hit.face().xOffset, y = hit.y() + hit.face().yOffset, z = hit.z() + hit.face().zOffset;
        if (!world.inBounds(x, y, z)) return false;
        if (world.get(x, y, z).isAir()) {
            playSound("fire.ignite", x + 0.5, y + 0.5, z + 0.5, 1f, random.nextFloat() * 0.4f + 0.8f);
            world.setStateAndUpdate(x, y, z, BlockState.of(blockRegistry.get("minecraft:fire"), 0));
        }
        if (!isCreative() && net.minecraft.enchantment.EnchantmentHelper.damageItem(held, 1, random)) {
            inventory.mainInventory[inventory.currentItem] = null;
            playSound("random.break", player.x, player.y, player.z, 0.8f, 0.8f + random.nextFloat() * 0.4f);
        }
        return true;
    }

    /**
     * Standing in a portal (EntityPlayerSP.onLivingUpdate): the purple haze
     * builds up by 0.0125 a tick (full after 4 seconds) with the trigger
     * sound as it starts, and a screen that doesn't pause the game closes;
     * out of the portal it fades by 0.05 a tick. With no Nether yet nothing
     * else happens (DEV-026).
     */
    private void updatePortalTime() {
        player.prevTimeInPortal = player.timeInPortal;
        if (player.inPortal) {
            if (currentScreen != null && !currentScreen.doesGuiPauseGame()) displayGuiScreen(null);
            if (player.timeInPortal == 0f && soundHandler != null) soundHandler.playUi("portal.trigger", random.nextFloat() * 0.4f + 0.8f);
            player.timeInPortal = Math.min(1f, player.timeInPortal + 0.0125f);
            player.inPortal = false;
        } else {
            player.timeInPortal = Math.max(0f, player.timeInPortal - 0.05f);
        }
    }

    /**
     * The portal haze over the view (GuiIngame.renderPortal): the portal
     * texture stretched over the screen, fading in with the fourth power of
     * the time spent in the portal from 20% opacity.
     */
    private void renderPortalOverlay(float time, int width, int height) {
        if (time < 1f) {
            time = time * time;
            time = time * time;
            time = time * 0.8f + 0.2f;
        }
        TextureAtlas.Tile tile = atlas.tile(new net.minecraft.util.ResourceLocation("minecraft:textures/blocks/portal.png"));
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1f, 1f, 1f, time);
        atlas.bind();
        glBegin(GL_QUADS);
        glTexCoord2f(tile.u0(), tile.v1()); glVertex2f(0, height);
        glTexCoord2f(tile.u1(), tile.v1()); glVertex2f(width, height);
        glTexCoord2f(tile.u1(), tile.v0()); glVertex2f(width, 0);
        glTexCoord2f(tile.u0(), tile.v0()); glVertex2f(0, 0);
        glEnd();
        glDepthMask(true);
        glEnable(GL_ALPHA_TEST);
        glColor4f(1f, 1f, 1f, 1f);
    }

    /**
     * Right-click with an item that is used from the hand (Item.onItemRightClick):
     * a piece of armour goes on if its slot is empty (ItemArmor, silent in
     * 1.8.9; also when a block is targeted, since armour has no block use);
     * a sword goes up to block for as long as the key is held (72000 ticks
     * at most). The hand snaps straight into the block pose with no
     * dip-and-rise, so blocking is either fully up or fully down.
     * Returns whether anything happened.
     */
    private boolean useHeldItem() {
        if (inventory.equipHeldArmor()) return true;
        ItemStack held = inventory.getCurrentItem();
        if (held == null || held.item().useAction() != Item.UseAction.BLOCK) return false;
        player.setItemInUse(held, 72000);
        return true;
    }

    /** Drops a block's items where it stood (Block.dropBlockAsItem), if tile drops are on. */
    private void dropBlock(int x, int y, int z, BlockState previous) {
        if (!dropsEnabled()) return;
        Block block = previous.block();
        for (ItemStack drop : blockDrops.drops(block.name().path(), block.material(), previous.metadata(), null, random))
            spawnItem(EntityItem.fromBlock(world, x, y, z, drop, random));
    }

    /** A block broke because its support went: it drops as if broken by hand (not in Creative for the other half of a pair). */
    private void blockPopped(int x, int y, int z, BlockState previous) {
        if (particles != null) particles.addBlockDestroyEffects(x, y, z, previous);
        playBreakSound(x, y, z, previous);
        if (!dropsEnabled()) return;
        Block block = previous.block();
        int meta = previous.metadata();
        String name = block.name().path();
        if (name.equals("double_plant") && (meta & 8) != 0) return; // the lower half drops the plant
        for (ItemStack drop : blockDrops.drops(name, block.material(), meta, null, random))
            spawnItem(EntityItem.fromBlock(world, x, y, z, drop, random));
    }

    private boolean dropsEnabled() { return Boolean.parseBoolean(levelInfo.gameRules.getOrDefault("doTileDrops", "true")); }

    /** The other half of a door, bed or double plant, or null. Door/plant upper halves have bit 8; a bed's head has bit 8. */
    private int[] otherHalf(int x, int y, int z, BlockState state) {
        Block block = state.block();
        int meta = state.metadata();
        if (block instanceof net.minecraft.block.BlockDoor || block instanceof net.minecraft.block.BlockDoublePlant)
            return (meta & 8) != 0 ? new int[]{x, y - 1, z} : new int[]{x, y + 1, z};
        if (block instanceof net.minecraft.block.BlockBed) {
            // Facing (bits 0-1): 0 south, 1 west, 2 north, 3 east; the head lies one step that way from the foot.
            int[][] steps = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
            int[] step = steps[meta & 3];
            int sign = (meta & 8) != 0 ? -1 : 1;
            return new int[]{x + step[0] * sign, y, z + step[1] * sign};
        }
        return null;
    }

    private void spawnItem(EntityItem item) { server.entities().add(item); }

    /** The drop key: one item from the held stack, or all of it with Ctrl. */
    private void dropHeldItem(boolean wholeStack) {
        ItemStack held = inventory.getCurrentItem();
        if (held == null) return;
        ItemStack thrown = wholeStack ? held.split(held.count()) : held.split(1);
        if (held.isEmpty()) inventory.mainInventory[inventory.currentItem] = null;
        dropPlayerItem(thrown);
    }

    /** On death every stack is scattered around the player unless the keepInventory rule is on. */
    private void dropInventoryOnDeath() {
        if (inventory == null || Boolean.parseBoolean(levelInfo.gameRules.getOrDefault("keepInventory", "false"))) return;
        for (int slot = 0; slot < InventoryPlayer.MAIN_SIZE; slot++) scatter(inventory.mainInventory, slot);
        for (int slot = 0; slot < InventoryPlayer.ARMOR_SIZE; slot++) scatter(inventory.armorInventory, slot);
    }

    private void scatter(ItemStack[] slots, int slot) {
        if (slots[slot] == null) return;
        spawnItem(EntityItem.thrown(world, player.x, player.eyeY(), player.z, 0, 0, 0, slots[slot], true, random));
        slots[slot] = null;
    }

    @Override
    public void dropPlayerItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || world == null || player == null) return;
        spawnItem(EntityItem.thrown(world, player.x, player.eyeY(), player.z, player.dirX(), player.dirY(), player.dirZ(),
            stack, false, random));
    }

    /** Places the held block against the targeted face (Survival and Creative; not Adventure). */
    /** Places the held block against the hit face; returns whether anything was placed. */
    private boolean placeHeldBlock(WorldRenderer.Hit hit) {
        ItemStack held = inventory.getCurrentItem();
        if (held != null && held.item().placedBlock() != null
            && levelInfo.gameType != WorldSettings.GameType.ADVENTURE) {
            var item = held.item();
            var context = new BlockPlacement.Context(hit.x(), hit.y(), hit.z(), hit.face(),
                hit.hitX(), hit.hitY(), hit.hitZ(), playerFacing(), player.x, player.eyeY(), player.z);
            List<BlockPlacement.Placement> placements = BlockPlacement.place(world, blockRegistry,
                item.placedBlock(), item.placedMetadata(), context);
            boolean blocked = false;
            for (BlockPlacement.Placement placement : placements) {
                List<AxisAlignedBB> boxes = new ArrayList<>();
                for (AxisAlignedBB box : placement.state().block().collisionBoxes(ActualState.withoutWorld(placement.state()),
                    world, placement.x(), placement.y(), placement.z()))
                    boxes.add(box.offset(placement.x(), placement.y(), placement.z()));
                if (!world.inBounds(placement.x(), placement.y(), placement.z()) || player.intersects(boxes)) blocked = true;
            }
            if (!blocked && !placements.isEmpty()) {
                for (BlockPlacement.Placement placement : placements)
                    world.setState(placement.x(), placement.y(), placement.z(), placement.state());
                for (BlockPlacement.Placement placement : placements)
                    BlockUpdates.afterChange(world, placement.x(), placement.y(), placement.z());
                // ItemBlock.onItemUse: the placed block's place sound at (volume + 1) / 2, frequency x 0.8.
                BlockPlacement.Placement first = placements.get(0);
                net.minecraft.block.BlockSoundType type = net.minecraft.block.BlockSoundType.of(first.state().block());
                playSound(type.placeSound(), first.x() + 0.5, first.y() + 0.5, first.z() + 0.5,
                    (type.volume() + 1f) / 2f, type.frequency() * 0.8f);
                if (!isCreative()) inventory.consumeCurrentItem();
                return true;
            }
        }
        return false;
    }

    /** The item that places this block variant (pick block), or null. */
    private Item itemForBlock(BlockState state) { return blockItems.placing(state.block().name().path(), state.metadata()); }

    private Direction playerFacing() { return Direction.nearest(player.dirX(), 0, player.dirZ()); }

    private void saveScreenshot(String path) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        glReadBuffer(GL_BACK);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = (x + y * width) * 4;
                int r = pixels.get(index) & 0xff, g = pixels.get(index + 1) & 0xff;
                int b = pixels.get(index + 2) & 0xff;
                image.setRGB(x, height - 1 - y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        try {
            File file = gameDirectory.resolve(path).toFile().getAbsoluteFile();
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            ImageIO.write(image, "png", file);
            System.out.println(I18n.format("screenshot.success", file.getName()));
        } catch (IOException error) {
            System.err.println(I18n.format("screenshot.failure", error.getMessage()));
        }
    }

    // ---- GuiContext ---------------------------------------------------------------------------

    @Override
    public void displayGuiScreen(GuiScreen screen) {
        if (currentScreen != null && currentScreen != screen) currentScreen.onGuiClosed();
        if (screen == null && world == null && preparing == null) screen = new GuiMainMenu();
        if (screen == null && player != null && player.dead) screen = new GuiGameOver(levelInfo.hardcore, 0);
        currentScreen = screen;
        if (screen != null) {
            if (mouseCaptured) releaseMouse();
            KeyBinding.unpressAll(gameSettings.keyBindings);
            ScaledResolution resolution = new ScaledResolution(width, height, gameSettings.getGuiScale(), gameSettings.isUnicodeFont());
            lastScaledWidth = resolution.getScaledWidth();
            lastScaledHeight = resolution.getScaledHeight();
            lastScaleFactor = resolution.getScaleFactor();
            screen.setWorldAndResolution(this, lastScaledWidth, lastScaledHeight);
        } else {
            captureMouse();
        }
    }

    @Override public GuiScreen currentScreen() { return currentScreen; }

    // ---- chat and commands ------------------------------------------------------------------

    @Override public GuiNewChat chatGui() { return ingameGui == null ? null : ingameGui.getChatGUI(); }

    @Override public void printChatLine(String text, int id) {
        if (ingameGui != null) ingameGui.getChatGUI().printChatMessage(text, id, ingameGui.updateCounter(), false);
    }

    /**
     * What the integrated server does with a chat line (1.8.9, HYPOTHESIS):
     * refused in red while chat is Hidden; runs of spaces collapse; "/"
     * runs a command as the player; anything else is said as
     * {@code <name> message}.
     */
    @Override public void sendChatMessage(String message) {
        if (world == null || player == null || ingameGui == null) return;
        if (gameSettings.chatVisibility == 2) {
            printChatLine(CommandHandler.RED + net.minecraft.client.gui.I18n.translateChat("chat.cannotSend"), 0);
            return;
        }
        String text = message.trim().replaceAll("\\s+", " ");
        if (text.startsWith("/")) commands.execute(chatSender, text);
        else ingameGui.getChatGUI().printChatMessage(
            net.minecraft.client.gui.I18n.translateChat("chat.type.text", playerName, text), 0, ingameGui.updateCounter(), true);
    }

    @Override public List<String> tabComplete(String beforeCursor) {
        if (world == null || player == null) return List.of();
        return commands.complete(chatSender, beforeCursor);
    }

    /** The local player as a command sender on the integrated server. */
    private final class ChatSender implements CommandSender {
        @Override public String name() { return playerName; }
        @Override public void addChatMessage(String message, boolean playerChat) {
            if (ingameGui != null) ingameGui.getChatGUI().printChatMessage(message, 0, ingameGui.updateCounter(), playerChat);
        }
        @Override public boolean commandsAllowed() { return levelInfo != null && levelInfo.allowCommands; }
        @Override public LevelInfo level() { return levelInfo; }
        @Override public double x() { return player.x; }
        @Override public double y() { return player.y; }
        @Override public double z() { return player.z; }
        // The player model's yaw is vanilla's minus 180 (0 = north).
        @Override public float yaw() { return wrapDegrees(player.yaw + 180f); }
        @Override public float pitch() { return player.pitch; }
        @Override public void teleport(double x, double y, double z, float yaw, float pitch) {
            player.setPositionAndRotation(x, y, z, yaw - 180f, pitch);
        }
        @Override public WorldSettings.GameType gameType() { return levelInfo.gameType; }
        @Override public void setGameType(WorldSettings.GameType type) {
            levelInfo.gameType = type;
            applyGameMode();
        }
        @Override public void setDifficulty(int difficulty) {
            levelInfo.difficulty = difficulty;
            gameSettings.difficulty = difficulty;
        }
        @Override public void kill() {
            // CommandKill: EntityLivingBase.onKillCommand, out-of-world damage of Float.MAX_VALUE.
            player.attack(net.minecraft.entity.DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
        }
        @Override public ItemStack heldItem() { return inventory.getCurrentItem(); }
        @Override public void addExperience(int points) { player.addExperience(points); }
        @Override public void addExperienceLevels(int levels) { player.addExperienceLevel(levels); }
        @Override public net.minecraft.stats.StatFile achievements() { return stats; }
        @Override public void awardAchievement(net.minecraft.stats.Achievement achievement) {
            if (stats != null && stats.unlock(achievement)) announceAchievement(achievement);
        }
        @Override public void revokeAchievement(net.minecraft.stats.Achievement achievement) {
            if (stats == null || !stats.has(achievement)) return;
            stats.set(achievement, false);
            String colour = achievement.special() ? "\u00a75" : "\u00a7a";
            printChatLine(net.minecraft.client.gui.I18n.translateChat("chat.type.achievement.taken", playerName,
                colour + "[" + I18n.format(achievement.nameKey()) + "]\u00a7r"), 0);
        }
        @Override public int clearInventory() {
            int removed = 0;
            for (int slot = 0; slot < InventoryPlayer.MAIN_SIZE; slot++) removed += count(inventory.getBySaveSlot(slot));
            for (int slot = 100; slot < 100 + InventoryPlayer.ARMOR_SIZE; slot++) removed += count(inventory.getBySaveSlot(slot));
            removed += count(inventory.getItemStack());
            inventory.clear();
            return removed;
        }
        private int count(ItemStack stack) { return stack == null ? 0 : stack.count(); }
        private float wrapDegrees(float degrees) {
            degrees %= 360f;
            if (degrees >= 180f) degrees -= 360f;
            if (degrees < -180f) degrees += 360f;
            return degrees;
        }
    }
    @Override public ItemRenderer itemRenderer() { return itemRenderer; }
    @Override public CreativeTabs creativeTabs() { return creativeTabs; }
    @Override public net.minecraft.item.crafting.CraftingManager crafting() { return crafting; }
    @Override public net.minecraft.item.crafting.FurnaceRecipes smelting() { return smelting; }
    @Override public net.minecraft.inventory.Furnace furnaceAt(int x, int y, int z) { return world == null ? null : world.furnaceAt(x, y, z); }

    /**
     * Smelted items were taken out (SlotFurnaceOutput.onCrafting): the
     * experience goes straight to the player, since experience orbs don't
     * exist yet (DEV-028), and iron ingots and cooked fish unlock achievements.
     */
    @Override public void onItemSmelted(Item item, int count) {
        if (player == null || count <= 0) return;
        int xp = net.minecraft.inventory.Furnace.experienceFor(count, smelting == null ? 0f : smelting.experience(new ItemStack(item)), random);
        if (xp > 0) player.addExperience(xp);
        String id = net.minecraft.stats.AchievementList.forSmelted(item.name().path());
        if (id != null) triggerAchievement(id);
    }
    @Override public Item itemByName(String name) {
        return itemRegistry == null ? null : itemRegistry.get(new net.minecraft.util.ResourceLocation("minecraft", name));
    }
    @Override public boolean canInteractWith(int x, int y, int z, String block) {
        if (world == null || player == null || player.dead || !world.get(x, y, z).name().path().equals(block)) return false;
        double dx = player.x - (x + 0.5), dy = player.y - (y + 0.5), dz = player.z - (z + 0.5);
        return dx * dx + dy * dy + dz * dz <= 64.0;
    }
    @Override public FontRenderer galacticFont() {
        if (galacticFont == null) {
            try {
                FontRenderer font = new FontRenderer(resourceManager, new net.minecraft.util.ResourceLocation("minecraft:textures/font/ascii_sga.png"));
                font.load();
                galacticFont = font;
            } catch (RuntimeException missing) {
                galacticFont = fontRenderer;
            }
        }
        return galacticFont;
    }
    @Override public int experienceLevel() { return player == null ? 0 : player.experienceLevel; }
    @Override public int xpSeed() { return player == null ? 0 : player.xpSeed; }
    @Override public void removeExperienceLevels(int levels) { if (player != null) player.removeExperienceLevel(levels); }
    @Override public int bookshelvesAround(int x, int y, int z) {
        if (world == null) return 0;
        return net.minecraft.inventory.EnchantingTable.countBookshelves(
            (bx, by, bz) -> by < 0 || by >= World.HEIGHT ? "air" : world.get(bx, by, bz).name().path(), x, y, z);
    }

    /**
     * SlotRepair.onPickupFromSlot: the levels are charged, then outside
     * Creative a 12% chance wears the anvil a step (slightly, then very
     * damaged, then gone, with the break sound); otherwise the use sound.
     */
    @Override public void onAnvilUsed(int x, int y, int z, int levels) {
        if (player == null || world == null) return;
        if (levels > 0) player.addExperienceLevel(-levels);
        BlockState state = world.getState(x, y, z);
        float pitch = random.nextFloat() * 0.1f + 0.9f;
        if (!isCreative() && state.block().name().path().equals("anvil") && random.nextFloat() < 0.12f) {
            int damage = (state.metadata() >> 2) + 1;
            if (damage > 2) {
                world.setStateAndUpdate(x, y, z, BlockState.of(blockRegistry.air(), 0));
                playSound("random.anvil_break", x + 0.5, y + 0.5, z + 0.5, 1f, pitch);
                return;
            }
            world.setState(x, y, z, BlockState.of(state.block(), (state.metadata() & 3) | damage << 2));
        }
        playSound("random.anvil_use", x + 0.5, y + 0.5, z + 0.5, 1f, pitch);
    }
    @Override public InventoryPlayer playerInventory() { return inventory; }
    @Override public boolean isCreative() { return levelInfo != null && levelInfo.gameType == WorldSettings.GameType.CREATIVE; }
    @Override public FontRenderer font() { return fontRenderer; }
    @Override public GuiTextures textures() { return guiTextures; }
    @Override public GameSettings settings() { return gameSettings; }
    @Override public IResourceManager resources() { return resourceManager; }
    @Override public BlockRegistry blocks() { return blockRegistry; }
    @Override public BiomeRegistry biomes() { return biomeRegistry; }
    @Override public Path gameDirectory() { return gameDirectory; }
    @Override public SaveFormat saves() { return saveFormat; }
    @Override public boolean isInGame() { return world != null && preparing == null; }
    @Override public LevelInfo currentLevel() { return world != null ? levelInfo : null; }
    @Override public int displayWidth() { return width; }
    @Override public int displayHeight() { return height; }
    @Override public boolean isKeyDown(int glfwKey) { return glfwGetKey(window, glfwKey) == GLFW_PRESS; }

    /** No audio output exists yet, so the button click is silent (DEV-010). */
    @Override public void playClickSound() { if (soundHandler != null) soundHandler.playUi("gui.button.press", 1f); }

    @Override public String getClipboard() {
        String text = glfwGetClipboardString(window);
        return text == null ? "" : text;
    }

    @Override public void setClipboard(String text) { glfwSetClipboardString(window, text); }

    @Override
    public void saveOptions() {
        try {
            gameSettings.save(gameDirectory.resolve("options.txt"));
        } catch (IOException error) {
            System.err.println("Couldn't save options: " + error.getMessage());
        }
    }

    @Override
    public void refreshLanguage() { I18n.load(resourceManager, gameSettings.language); }

    @Override
    public void applyDisplaySettings() {
        glfwSwapInterval(gameSettings.enableVsync ? 1 : 0);
        if (gameSettings.fullScreen == fullscreenApplied) return;
        fullscreenApplied = gameSettings.fullScreen;
        if (gameSettings.fullScreen) {
            int[] x = new int[1], y = new int[1];
            glfwGetWindowPos(window, x, y);
            windowedX = x[0]; windowedY = y[0];
            windowedWidth = windowWidth; windowedHeight = windowHeight;
            long monitor = glfwGetPrimaryMonitor();
            GLFWVidMode mode = glfwGetVideoMode(monitor);
            if (mode != null) glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
        } else {
            glfwSetWindowMonitor(window, NULL, windowedX, windowedY, windowedWidth, windowedHeight, GLFW_DONT_CARE);
        }
        glfwSwapInterval(gameSettings.enableVsync ? 1 : 0);
    }

    public static void main(String[] args) {
        Minecraft game = new Minecraft();
        for (String arg : args) {
            if (arg.equals("--debug")) game.gameSettings.showDebugInfo = true;
            else if (arg.startsWith("--seed=")) { game.cliSeed = net.minecraft.world.WorldSettings.parseSeed(arg.substring(7), 889L); game.cliThrowawayWorld = true; }
            else if (arg.startsWith("--spawn=")) {
                String[] parts = arg.substring(8).split(",");
                game.cliSpawnX = Double.parseDouble(parts[0]) + 0.5;
                game.cliSpawnZ = Double.parseDouble(parts[1]) + 0.5;
                game.cliThrowawayWorld = true;
            } else if (arg.startsWith("--pos=")) {
                String[] parts = arg.substring(6).split(",");
                game.cliPos = new double[5];
                for (int i = 0; i < 5; i++) game.cliPos[i] = i < parts.length ? Double.parseDouble(parts[i].trim()) : 0;
                game.cliSpawnX = game.cliPos[0];
                game.cliSpawnZ = game.cliPos[2];
                game.cliThrowawayWorld = true;
            } else if (arg.startsWith("--capture=")) {
                game.screenshotRequested = arg.substring(10);
                game.quitAfterScreenshot = true;
                game.cliThrowawayWorld = true;
            } else if (arg.startsWith("--world=")) {
                game.cliWorld = arg.substring(8);
            } else if (arg.startsWith("--username=")) {
                game.playerName = arg.substring(11);
            } else if (arg.startsWith("--gamedir=")) {
                game.gameDirectory = Path.of(arg.substring(10));
            }
        }
        game.run();
    }
}

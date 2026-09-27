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
import net.minecraft.client.gui.FontRenderer;
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
    private BlockModelManager modelManager;
    private TextureAtlas atlas;
    private FontRenderer fontRenderer;
    private GuiTextures guiTextures;
    private ItemRenderer itemRenderer;
    private CreativeTabs creativeTabs;
    private GuiIngame ingameGui;
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
        modelManager = new BlockModelManager(resourceManager, blockRegistry);
        atlas = new TextureAtlas(resourceManager);
        atlas.load();
        fontRenderer = new FontRenderer(resourceManager);
        fontRenderer.load();
        guiTextures = new GuiTextures(resourceManager);
        biomeRegistry = BiomeRegistry.load(resourceManager, blockRegistry);
        biomeColors = BiomeColors.load(resourceManager);
        itemRenderer = new ItemRenderer(resourceManager, guiTextures, atlas, modelManager, biomeColors);
        creativeTabs = CreativeTabs.load(resourceManager, itemRegistry);
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
            case GLFW_KEY_D -> { /* chat is not implemented yet; clearing it is a no-op */ }
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
        double physicsAccumulator = 0, tickAccumulator = 0;
        final double physicsStep = 1.0 / 120.0;
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
            if (player != null && (currentScreen == null || !currentScreen.doesGuiPauseGame()) && preparing == null) {
                physicsAccumulator += frame;
                while (physicsAccumulator >= physicsStep) {
                    player.update(gameSettings, physicsStep);
                    physicsAccumulator -= physicsStep;
                }
            } else {
                physicsAccumulator = 0;
            }
            float partialTicks = (float) (tickAccumulator / tickStep);
            render(partialTicks);
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
        if (world == null || preparing != null) return;
        boolean paused = currentScreen != null && currentScreen.doesGuiPauseGame();
        if (!paused) {
            server.tick();
            tickItemEntities();
            levelInfo.totalTime++;
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
        if (rightClickDelayTimer > 0) rightClickDelayTimer--;
        if (currentScreen != null) {
            playerController.resetBlockRemoving();
            return;
        }
        for (int i = 0; i < 9; i++) if (gameSettings.keyBindsHotbar[i].isPressed() && !isSpectator()) inventory.currentItem = i;
        if (gameSettings.keyBindInventory.isPressed())
            displayGuiScreen(isCreative() ? new GuiContainerCreative() : new GuiInventory());
        else {
            while (gameSettings.keyBindDrop.isPressed()) if (!isSpectator()) dropHeldItem(isKeyDown(GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW_KEY_RIGHT_CONTROL));
            interact();
        }
    }

    /** Merges nearby dropped stacks and lets the player pick up what it touches (server side of 1.8.9's EntityItem). */
    private void tickItemEntities() {
        List<EntityItem> items = new ArrayList<>();
        for (Entity entity : server.entities().values()) if (entity instanceof EntityItem item && !item.isRemoved()) items.add(item);
        for (EntityItem item : items) {
            if (item.isRemoved() || item.age % 25 != 1) continue;
            for (EntityItem other : items) if (other != item && item.isNear(other)) item.tryMerge(other);
        }
        if (player == null || player.dead || isSpectator()) return;
        AxisAlignedBB reach = player.boundingBox();
        reach = new AxisAlignedBB(reach.minX() - 1, reach.minY() - 0.5, reach.minZ() - 1, reach.maxX() + 1, reach.maxY() + 0.5, reach.maxZ() + 1);
        for (EntityItem item : items) {
            if (item.isRemoved() || item.pickupDelay > 0 || !item.boundingBox().intersects(reach)) continue;
            inventory.addItemStackToInventory(item.stack());
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
        if (info.terrainType == WorldType.FLAT)
            return new World(blockRegistry, biomeRegistry, seed, FlatGeneratorInfo.parse(info.generatorOptions, blockRegistry));
        return new World(blockRegistry, biomeRegistry, seed);
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
        if (levelInfo.player != null && cliSpawnX == null) {
            LevelInfo.PlayerPosition saved = levelInfo.player;
            // level.dat stores vanilla yaw (0 = south); the player model uses 0 = north.
            player.setPositionAndRotation(saved.x(), saved.y(), saved.z(), saved.yaw() - 180f, saved.pitch());
        }
        inventory = new InventoryPlayer();
        deathHandled = false;
        playerController.resetBlockRemoving();
        loadInventory();
        applyGameMode();
        if (levelInfo.abilities != null && levelInfo.abilities.flying() && player.allowFlying && cliSpawnX == null)
            player.isFlying = true;
        if (cliPos != null) {
            // F3's yaw is vanilla's (0 = south); the player model uses 0 = north.
            player.setPositionAndRotation(cliPos[0], cliPos[1], cliPos[2], (float) cliPos[3] - 180f, (float) cliPos[4]);
            if (player.allowFlying) player.isFlying = true;
        }
        server = new IntegratedServer(world);
        renderer = new WorldRenderer(world, atlas, modelManager, biomeColors);
        sky = new SkyRenderer(guiTextures);
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
        if (server != null) server.close();
        if (renderer != null) renderer.destroy();
        if (sky != null) sky.destroy();
        server = null;
        renderer = null;
        sky = null;
        world = null;
        player = null;
        inventory = null;
        preparing = null;
    }

    /** Fills the player's inventory from level.dat (unknown item names are dropped with a warning). */
    private void loadInventory() {
        inventory.clear();
        if (levelInfo.inventory == null) return;
        for (LevelInfo.SavedStack saved : levelInfo.inventory) {
            Item item = itemRegistry.get(new net.minecraft.util.ResourceLocation(saved.id()));
            if (item == null || saved.count() <= 0) {
                System.err.println("Dropping unknown saved item " + saved.id());
                continue;
            }
            inventory.setBySaveSlot(saved.slot(), new ItemStack(item, Math.min(saved.count(), item.maxStackSize()),
                Math.max(0, Math.min(item.maxDamage(), saved.damage()))));
        }
        inventory.currentItem = Math.max(0, Math.min(InventoryPlayer.HOTBAR_SIZE - 1, levelInfo.selectedItemSlot));
    }

    private void saveInventory() {
        List<LevelInfo.SavedStack> saved = new ArrayList<>();
        for (int slot = 0; slot < InventoryPlayer.MAIN_SIZE; slot++) addSaved(saved, slot);
        for (int slot = 100; slot < 100 + InventoryPlayer.ARMOR_SIZE; slot++) addSaved(saved, slot);
        levelInfo.inventory = saved;
        levelInfo.selectedItemSlot = inventory.currentItem;
    }

    private void addSaved(List<LevelInfo.SavedStack> saved, int slot) {
        ItemStack stack = inventory.getBySaveSlot(slot);
        if (stack != null) saved.add(new LevelInfo.SavedStack(slot, stack.item().name().toString(), stack.count(), stack.damage()));
    }

    /** Gives the player the current game mode's abilities (flight in Creative, noclip flight in Spectator). */
    private void applyGameMode() {
        if (player == null || levelInfo == null) return;
        player.setAbilities(levelInfo.gameType == WorldSettings.GameType.CREATIVE, isSpectator());
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
        float[] fog = inWorld ? prepareSky() : null;
        if (fog != null) glClearColor(fog[0], fog[1], fog[2], 0.0f);
        else glClearColor(0.50f, 0.69f, 1.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        if (inWorld) {
            renderWorld(fog);
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
            gameSettings.renderDistanceChunks, player.y, world.isFlat());
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
        glFogi(GL_FOG_MODE, GL_LINEAR);
        glHint(GL_FOG_HINT, GL_FASTEST);
        if (org.lwjgl.opengl.GL.getCapabilities().GL_NV_fog_distance) glFogi(0x855A, 0x855B); // eye-radial fog
        glFogfv(GL_FOG_COLOR, new float[]{fog[0], fog[1], fog[2], 1.0f});
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glRotatef(player.pitch, 1, 0, 0);
        glRotatef(player.yaw, 0, 1, 0);
        if (skyVisible) {
            glFogf(GL_FOG_START, 0f);
            glFogf(GL_FOG_END, renderDistance);
            perspective(gameSettings.getFov(), width / (double) height, 0.05, renderDistance * 2.0);
            glMatrixMode(GL_MODELVIEW);
            sky.renderSky(levelInfo.worldTime, eyeY);
        }
        glEnable(GL_FOG);
        glFogf(GL_FOG_START, renderDistance * 0.75f);
        glFogf(GL_FOG_END, renderDistance);
        if (eyeY < SkyRenderer.CLOUD_HEIGHT) renderClouds(renderDistance, skyVisible);
        perspective(gameSettings.getFov(), width / (double) height, 0.05, renderDistance * SQRT_2);
        glMatrixMode(GL_MODELVIEW);
        glTranslated(-player.x, -eyeY, -player.z);
        glColor4f(1, 1, 1, 1);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        renderer.render(player, renderDistance);
        if (gameSettings.showChunkBorders) DebugRenderer.renderChunkBorders(player.x, player.y, player.z);
        renderItemEntities(renderDistance);
        WorldRenderer.Hit hit = currentScreen == null && !isSpectator() ? renderer.raycast(player, reach()) : null;
        drawDestroyProgress();
        if (hit != null && !gameSettings.hideGUI) drawSelection(hit);
        if (eyeY >= SkyRenderer.CLOUD_HEIGHT) {
            glPushMatrix();
            glTranslated(player.x, eyeY, player.z);
            renderClouds(renderDistance, skyVisible);
            glPopMatrix();
        }
    }

    /** Clouds with the terrain fog and a far plane four times the render distance (the modelview holds only the camera's rotation). */
    private void renderClouds(int renderDistance, boolean skyVisible) {
        if (!skyVisible || !gameSettings.clouds) return;
        perspective(gameSettings.getFov(), width / (double) height, 0.05, renderDistance * 4.0);
        glMatrixMode(GL_MODELVIEW);
        sky.renderClouds(player.x, player.eyeY(), player.z, partialTicks, gameSettings.fancyGraphics);
        perspective(gameSettings.getFov(), width / (double) height, 0.05, renderDistance * SQRT_2);
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
                itemRenderer.renderItemInWorld(item.stack().item(), light[0], light[1], light[2]);
                glPopMatrix();
            }
            glPopMatrix();
        }
        glEnable(GL_TEXTURE_2D);
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

        boolean showDebug = gameSettings.showDebugInfo;
        boolean axisCrosshair = showDebug && !gameSettings.reducedDebugInfo;
        GuiIngame.Status status = new GuiIngame.Status(survivalHud(), isSpectator(), levelInfo.hardcore, player.health,
            player.foodLevel, inventory.getTotalArmorValue(), player.experienceLevel, player.experience);
        ingameGui.renderGameOverlay(guiWidth, guiHeight, inventory, status, !axisCrosshair);
        if (axisCrosshair) GuiOverlayDebug.renderAxisCrosshair(guiWidth, guiHeight, player.yaw + 180f, player.pitch);
        if (showDebug) debugOverlay.render(fontRenderer, guiWidth, guiHeight, debugSnapshot(), gameSettings.showLagometer);
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
            0, server.entities().values().size(), 0, loaded,
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
        WorldRenderer.Hit hit = renderer.raycast(player, reach());
        if (pickPressed && hit != null) {
            Item picked = itemForBlock(world.getState(hit.x(), hit.y(), hit.z()));
            if (picked != null) inventory.pickItem(picked, isCreative());
        }
        boolean canBreak = levelInfo.gameType != WorldSettings.GameType.ADVENTURE;
        if (attackPressed && hit != null && canBreak
            && playerController.clickBlock(hit.x(), hit.y(), hit.z(), isCreative(), breakProgress(hit)))
            breakBlock(hit.x(), hit.y(), hit.z());
        if (gameSettings.keyBindAttack.isKeyDown() && hit != null && canBreak) {
            if (playerController.onPlayerDamageBlock(hit.x(), hit.y(), hit.z(), isCreative(), breakProgress(hit)))
                breakBlock(hit.x(), hit.y(), hit.z());
        } else {
            playerController.resetBlockRemoving();
        }
        if (usePressed || gameSettings.keyBindUseItem.isKeyDown() && rightClickDelayTimer == 0) {
            rightClickDelayTimer = 4;
            if (hit != null) placeHeldBlock(hit);
        }
    }

    /** Fraction of the targeted block broken per tick with the held item. */
    private float breakProgress(WorldRenderer.Hit hit) {
        Block block = world.get(hit.x(), hit.y(), hit.z());
        ItemStack held = inventory.getCurrentItem();
        int eyeX = (int) Math.floor(player.x), eyeY = (int) Math.floor(player.eyeY()), eyeZ = (int) Math.floor(player.z);
        boolean headInWater = eyeY >= 0 && eyeY < World.HEIGHT && world.get(eyeX, eyeY, eyeZ).material().equals("water");
        return ToolValues.breakProgressPerTick(held == null ? null : held.item(), block.name().path(), block.material(),
            block.hardness(), player.onGround, headInWater);
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
                for (ItemStack drop : blockDrops.drops(name, block.material(), meta, held == null ? null : held.item(), random))
                    spawnItem(EntityItem.fromBlock(world, x, y, z, drop, random));
            }
            if (name.equals("ice") && y > 0) {
                Block below = world.get(x, y - 1, z);
                if (below.isSolid() || below.material().equals("water") || below.material().equals("lava"))
                    replacement = BlockState.of(blockRegistry.get("minecraft:flowing_water"), 0);
            }
            if (held != null) {
                int wear = ToolValues.damageForBreak(held.item(), name, block.material(), block.hardness());
                if (held.damage(wear)) inventory.mainInventory[inventory.currentItem] = null;
            }
        }
        world.setState(x, y, z, replacement);
        BlockUpdates.afterChange(world, x, y, z, this::blockPopped);
    }

    /** A block broke because its support went: it drops as if broken by hand (not in Creative for the other half of a pair). */
    private void blockPopped(int x, int y, int z, BlockState previous) {
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
    private void placeHeldBlock(WorldRenderer.Hit hit) {
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
                if (!isCreative()) inventory.consumeCurrentItem();
            }
        }
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
    @Override public ItemRenderer itemRenderer() { return itemRenderer; }
    @Override public CreativeTabs creativeTabs() { return creativeTabs; }
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
    @Override public void playClickSound() {}

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
            } else if (arg.startsWith("--gamedir=")) {
                game.gameDirectory = Path.of(arg.substring(10));
            }
        }
        game.run();
    }
}

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
import net.minecraft.client.renderer.LocalCape;
import net.minecraft.client.renderer.LocalSkin;
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
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityTNTPrimed;
import net.minecraft.entity.EntityXPOrb;
import net.minecraft.entity.MovingEntity;
import net.minecraft.entity.monster.EntityCreeper;
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
import java.util.Locale;
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
 *   <li>{@code --skin=<file.png>} uses a local 64x64 skin supplied by an offline launcher.</li>
 *   <li>{@code --skinModel=slim|classic} picks the arm width for that skin (slim = ModelPlayer's small arms).</li>
 *   <li>{@code --cape=<file.png>} gives the player a local 64x32 cape.</li>
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
    /** This instance's Additions (additions.txt in the game folder); all off is plain 1.8.9. */
    private net.minecraft.client.additions.Additions additions = net.minecraft.client.additions.Additions.defaults();
    private final net.minecraft.client.additions.ToggleKeys toggleKeys = new net.minecraft.client.additions.ToggleKeys();
    private final net.minecraft.client.additions.ShaderPass shaderPass = new net.minecraft.client.additions.ShaderPass();
    /** The built-in 1.8.9 pack with the selected resource packs layered over it. */
    private final net.minecraft.client.resources.ResourcePackRepository resourceManager =
        new net.minecraft.client.resources.ResourcePackRepository(ClasspathResourceManager.fromSystemProperty());
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
    private LocalSkin localSkin;
    private LocalCape localCape;
    private PlayerModel playerModel = new PlayerModel();
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
    /** The dimensions' worlds and entity lists this session ({@link #world}/{@link #server} are the current ones). */
    private World overworld, netherWorld;
    private IntegratedServer overworldServer, netherServer;
    private net.minecraft.world.Dimension dimension = net.minecraft.world.Dimension.OVERWORLD;
    /** Portal travel (Entity.portalCounter / timeUntilPortal): ticks spent in a portal, and the cooldown after travelling. */
    private int portalCounter, timeUntilPortal;
    private boolean eventsInstalled;
    private Player player;
    private IntegratedServer server;
    private WorldRenderer renderer;
    private SkyRenderer sky;
    private ParticleManager particles;
    private net.minecraft.client.renderer.entity.MobRenderer mobRenderer;
    /** Chests, signs, banners and the end portal (their tile-entity renderers). */
    private net.minecraft.client.renderer.entity.BlockEntityRenderer blockEntityRenderer;
    /** The mob or lit TNT under the crosshair, nearer than any block (objectMouseOver ENTITY), or null. */
    private MovingEntity pointedEntity;
    /** The boat the player sits in, or null (Entity.ridingEntity). */
    private net.minecraft.entity.EntityBoat ridingBoat;

    /** What entities may ask of the game (the World/WorldServer calls they make). */
    private final net.minecraft.entity.EntityHost entityHost = new net.minecraft.entity.EntityHost() {
        @Override public Player player() { return player; }
        @Override public List<Entity> entities() { return server == null ? List.of() : server.entities().values(); }
        @Override public void spawn(Entity entity) { spawnEntity(entity); }
        @Override public void playSound(String event, double x, double y, double z, float volume, float pitch) {
            Minecraft.this.playSound(event, x, y, z, volume, pitch);
        }
        @Override public void particle(String kind, double x, double y, double z, double vx, double vy, double vz) {
            if (particles == null) return;
            // EnumParticleTypes.SPELL_MOB: the "velocity" is the swirl's colour.
            if (kind.equals("mobSpell")) {
                particles.spawnSpell(false, x, y, z, 0, 0, 0, (float) vx, (float) vy, (float) vz);
                return;
            }
            ParticleManager.Kind type = switch (kind) {
                case "explode" -> ParticleManager.Kind.EXPLODE;
                case "largeexplode" -> ParticleManager.Kind.LARGE_EXPLODE;
                case "hugeexplosion" -> ParticleManager.Kind.HUGE_EXPLOSION;
                case "smoke" -> ParticleManager.Kind.SMOKE;
                case "crit" -> ParticleManager.Kind.CRIT;
                case "magicCrit" -> ParticleManager.Kind.MAGIC_CRIT;
                case "bubble" -> ParticleManager.Kind.BUBBLE;
                case "splash" -> ParticleManager.Kind.SPLASH;
                case "heart" -> ParticleManager.Kind.HEART;
                case "largesmoke" -> ParticleManager.Kind.LARGE_SMOKE;
                case "flame" -> ParticleManager.Kind.FLAME;
                case "happyVillager" -> ParticleManager.Kind.HAPPY_VILLAGER;
                default -> null;
            };
            if (type != null) particles.spawn(type, x, y, z, vx, vy, vz, null);
        }
        @Override public void explode(Entity exploder, double x, double y, double z, float size, boolean smoking, String attacker) {
            Minecraft.this.explode(exploder, x, y, z, size, false, smoking, attacker);
        }
        @Override public void explode(Entity exploder, double x, double y, double z, float size, boolean flaming, boolean smoking, String attacker) {
            Minecraft.this.explode(exploder, x, y, z, size, flaming, smoking, attacker);
        }
        @Override public int difficulty() { return levelInfo == null ? 2 : levelInfo.difficulty; }
        @Override public boolean gameRule(String name) {
            return levelInfo == null || Boolean.parseBoolean(levelInfo.gameRules.getOrDefault(name, "true"));
        }
        @Override public long worldTime() { return levelInfo == null ? 6000 : levelInfo.worldTime; }
        @Override public void giveExperience(int amount) { if (player != null) player.addExperience(amount); }
        @Override public void globalSound(String event, double x, double y, double z) {
            // RenderGlobal.broadcastSound: 2 blocks from the camera toward the source.
            if (player == null) return;
            double dx = x - player.x, dy = y - player.eyeY(), dz = z - player.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double px = player.x, py = player.eyeY(), pz = player.z;
            if (length > 0) { px += dx / length * 2.0; py += dy / length * 2.0; pz += dz / length * 2.0; }
            Minecraft.this.playSound(event, px, py, pz, 1f, 1f);
        }
        @Override public void potionSmash(int x, int y, int z, int potionData) { Minecraft.this.potionSmash(x, y, z, potionData); }
        @Override public void achievement(String id) { triggerAchievement(id); }
        @Override public void breakBlock(int x, int y, int z, boolean drop) {
            if (world == null || y < 0 || y >= World.HEIGHT) return;
            BlockState previous = world.getState(x, y, z);
            if (previous.block().isAir()) return;
            if (particles != null) particles.addBlockDestroyEffects(x, y, z, previous);
            playBreakSound(x, y, z, previous);
            world.setStateAndUpdate(x, y, z, BlockState.of(blockRegistry.air(), 0));
            if (drop) dropBlock(x, y, z, previous);
        }
        @Override public void blockBreakEffects(int x, int y, int z, BlockState state) {
            if (particles != null) particles.addBlockDestroyEffects(x, y, z, state);
            playBreakSound(x, y, z, state);
        }
        @Override public void setBlock(int x, int y, int z, String block, int metadata) {
            Block type = blockRegistry.get("minecraft:" + block);
            if (world != null && type != null && y >= 0 && y < World.HEIGHT) world.setStateAndUpdate(x, y, z, BlockState.of(type, metadata));
        }
        @Override public void itemCrack(String item, double x, double y, double z, double vx, double vy, double vz) {
            if (particles == null || itemRenderer == null) return;
            Item type = itemByName(item);
            if (type != null) particles.spawnItemCrack(itemRenderer.particleTexture(type), x, y, z, vx, vy, vz);
        }
        @Override public void waterSplash(MovingEntity entity) {
            if (particles != null)
                particles.addWaterSplash(entity.x(), entity.y(), entity.z(), entity.width, entity.motionX, entity.motionY, entity.motionZ);
        }
    };
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
    /** A vanilla world being converted on a background thread before it opens. */
    private volatile WorldConversion converting;

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
    /** Set by the standalone launcher; intentionally not persisted by the game. */
    private Path launcherSkin, launcherCape;
    private boolean launcherSlim;
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
            if (localSkin != null) localSkin.destroy();
            if (localCape != null) localCape.destroy();
            if (soundSystem != null) soundSystem.destroy();
            resourceManager.close();
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
        additions = net.minecraft.client.additions.Additions.load(gameDirectory.resolve("additions.txt"));
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
        glfwShowWindow(window);
        drawSplashScreen();

        resourceManager.setDirectory(gameDirectory.resolve("resourcepacks"));
        applySelectedResourcePacks();
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
        atlas.setMipmapLevels(gameSettings.mipmapLevels);
        atlas.load();
        fontRenderer = new FontRenderer(resourceManager);
        fontRenderer.load();
        guiTextures = new GuiTextures(resourceManager);
        guiTextures.setSpriteMipmapLevels(atlas.mipmapLevels());
        biomeRegistry = BiomeRegistry.load(resourceManager, blockRegistry);
        biomeColors = BiomeColors.load(resourceManager);
        itemRenderer = new ItemRenderer(resourceManager, guiTextures, atlas, modelManager, biomeColors);
        itemRenderer.modelState = this::heldModelState;
        itemRenderer.builtin = builtinItems;
        mobRenderer = new net.minecraft.client.renderer.entity.MobRenderer(guiTextures, itemRenderer);
        blockEntityRenderer = new net.minecraft.client.renderer.entity.BlockEntityRenderer(guiTextures);
        localSkin = new LocalSkin(launcherSkin);
        localSkin.load();
        localCape = new LocalCape(launcherCape);
        localCape.load();
        playerModel = new PlayerModel(launcherSlim);
        firstPersonRenderer = new FirstPersonRenderer(guiTextures, itemRenderer, localSkin, launcherSlim);
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
            case GLFW_KEY_T -> refreshResources();
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

            if (converting != null) stepConversion();
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
            world.setRaining(levelInfo.raining && !world.isNether());
            world.tick();
            atlas.tickAnimations();
            if (preparing == null && player != null)
                net.minecraft.world.MobSpawner.spawnMonsters(world, entityHost, random, levelInfo.spawnX, levelInfo.spawnY, levelInfo.spawnZ);
            if (preparing == null && player != null)
                net.minecraft.world.MobSpawner.spawnCreatures(world, entityHost, random, levelInfo.totalTime,
                    levelInfo.spawnX, levelInfo.spawnY, levelInfo.spawnZ);
            spawnGeneratedCreatures();
            server.tick();
            tickItemEntities();
            levelInfo.totalTime++;
            player.difficulty = levelInfo.difficulty;
            player.naturalRegeneration = Boolean.parseBoolean(levelInfo.gameRules.getOrDefault("naturalRegeneration", "true"));
            if (preparing == null) tickPlayer();
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
        if (world.isNether()) return net.minecraft.client.audio.MusicTicker.Type.NETHER;
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
    /**
     * EntityArrow.onCollideWithPlayer: an arrow the player may take, lying
     * within reach (their box grown 1 across and 0.5 up and down), goes into
     * the inventory with the pickup pop.
     */
    private void pickUpArrows() {
        if (player == null || player.dead || isSpectator()) return;
        AxisAlignedBB reach = player.boundingBox().grow(1.0, 0.5, 1.0);
        for (Entity entity : server.entities().values()) {
            if (!(entity instanceof net.minecraft.entity.EntityArrow arrow) || arrow.isRemoved() || !arrow.canPickUp(isCreative())) continue;
            if (!arrow.boundingBox().intersects(reach)) continue;
            Item item = itemByName("arrow");
            if (arrow.pickup == 1 && (item == null || !inventory.addItemStackToInventory(new ItemStack(item)))) continue;
            playSound("random.pop", player.x, player.y, player.z, 0.2f, ((random.nextFloat() - random.nextFloat()) * 0.7f + 1f) * 2f);
            arrow.remove();
        }
    }

    /** Each spawner's countdown (MobSpawnerBaseLogic.spawnDelay), by position; -1 waits to be reset. */
    private final java.util.Map<World.BlockPos, Integer> spawnerDelays = new java.util.HashMap<>();

    /**
     * MobSpawnerBaseLogic.updateSpawner: with the player within 16 blocks, a
     * spawner smokes and flickers, and when its countdown (200-799 ticks,
     * 20 at first) runs out tries 4 times to put its mob within 4 blocks
     * (where the mob could spawn and has room), unless 6 of them are
     * already near; a success puffs flames and restarts the countdown.
     */
    private void tickSpawners() {
        if (world.spawners().isEmpty() || player == null) return;
        Block spawnerBlock = blockRegistry.get("minecraft:mob_spawner");
        for (var entry : List.copyOf(world.spawners().entrySet())) {
            World.BlockPos pos = entry.getKey();
            if (world.generatedChunk(pos.x() >> 4, pos.z() >> 4) == null) continue;
            if (world.get(pos.x(), pos.y(), pos.z()) != spawnerBlock) {
                world.spawners().remove(pos);
                spawnerDelays.remove(pos);
                continue;
            }
            double dx = player.x - (pos.x() + 0.5), dy = player.y - (pos.y() + 0.5), dz = player.z - (pos.z() + 0.5);
            if (dx * dx + dy * dy + dz * dz > 256.0) continue;
            if (particles != null) {
                particles.spawn(ParticleManager.Kind.SMOKE, pos.x() + random.nextFloat(), pos.y() + random.nextFloat(), pos.z() + random.nextFloat(), 0, 0, 0, null);
                particles.spawn(ParticleManager.Kind.FLAME, pos.x() + random.nextFloat(), pos.y() + random.nextFloat(), pos.z() + random.nextFloat(), 0, 0, 0, null);
            }
            int delay = spawnerDelays.getOrDefault(pos, 20);
            if (delay == -1) delay = 200 + random.nextInt(600);
            if (delay > 0) {
                spawnerDelays.put(pos, delay - 1);
                continue;
            }
            boolean spawnedAny = false;
            for (int i = 0; i < 4; i++) {
                MovingEntity entity = net.minecraft.entity.EntityList.create(entry.getValue(), world, 0, 0, 0);
                if (entity == null) break;
                AxisAlignedBB near = new AxisAlignedBB(pos.x() - 4, pos.y() - 4, pos.z() - 4, pos.x() + 5, pos.y() + 5, pos.z() + 5);
                int count = 0;
                for (Entity other : server.entities().values())
                    if (other.getClass() == entity.getClass() && !other.isRemoved() && ((MovingEntity) other).boundingBox().intersects(near)) count++;
                if (count >= 6) {
                    spawnedAny = true;
                    break;
                }
                double sx = pos.x() + (random.nextDouble() - random.nextDouble()) * 4.0 + 0.5;
                double sy = pos.y() + random.nextInt(3) - 1;
                double sz = pos.z() + (random.nextDouble() - random.nextDouble()) * 4.0 + 0.5;
                entity.setPositionAndRotation(sx, sy, sz, random.nextFloat() * 360f, 0f);
                entity.setHost(entityHost);
                if (entity instanceof EntityLiving living) {
                    living.renderYawOffset = living.rotationYawHead = living.rotationYaw;
                    if (!net.minecraft.world.MobSpawner.canMobSpawnHere(world, entityHost, random, living)
                        || !net.minecraft.world.MobSpawner.isNotColliding(world, entityHost, living)) continue;
                    living.onInitialSpawn();
                }
                spawnEntity(entity);
                if (particles != null)
                    for (int p = 0; p < 20; p++) {
                        particles.spawn(ParticleManager.Kind.SMOKE, pos.x() + random.nextFloat(), pos.y() + random.nextFloat(), pos.z() + random.nextFloat(), 0, 0, 0, null);
                        particles.spawn(ParticleManager.Kind.FLAME, pos.x() + random.nextFloat(), pos.y() + random.nextFloat(), pos.z() + random.nextFloat(), 0, 0, 0, null);
                        particles.spawn(ParticleManager.Kind.EXPLODE, sx + (random.nextFloat() - 0.5) * entity.width, sy + random.nextFloat() * entity.height,
                            sz + (random.nextFloat() - 0.5) * entity.width, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, null);
                    }
                spawnedAny = true;
            }
            spawnerDelays.put(pos, spawnedAny ? -1 : 0);
        }
    }

    private void tickItemEntities() {
        pickUpArrows();
        tickSpawners();
        itemPickups.removeIf(pickup -> ++pickup.ticks >= ItemPickup.DURATION);
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
                itemPickups.add(new ItemPickup(item, item.stack().withCount(before - item.stack().count())));
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

    private static final class WorldConversion {
        final String folder, name;
        volatile int progress;
        volatile WorldStorage.Conversion result;
        volatile Exception error;
        volatile boolean done;
        WorldConversion(String folder, String name) { this.folder = folder; this.name = name; }
    }

    @Override
    public void convertAndLaunch(String folderName, String worldName) {
        LevelInfo info = saveFormat.getWorldInfo(folderName);
        if (info == null) {
            displayGuiScreen(new net.minecraft.client.gui.GuiDisconnected(new GuiMainMenu(), "selectWorld.title",
                "Couldn't read " + folderName + "/level.dat", "gui.toTitle"));
            return;
        }
        Path directory = saveFormat.worldDirectory(folderName);
        WorldConversion conversion = new WorldConversion(folderName, worldName);
        loadingScreen.setTitle("Converting world");
        loadingScreen.setMessage(worldName);
        loadingScreen.setProgress(0);
        displayGuiScreen(loadingScreen);
        int registrySize = blockRegistry.size();
        Thread worker = new Thread(() -> {
            try {
                WorldStorage.ItemNames items = (id, data) -> {
                    Item item = itemRegistry.resolve(id, data);
                    return item == null ? null : item.name().toString();
                };
                conversion.result = WorldStorage.convertAnvil(directory, info.randomSeed, registrySize, items,
                    p -> conversion.progress = p);
            } catch (IOException | RuntimeException error) {
                conversion.error = error;
            }
            conversion.done = true;
        }, "World conversion");
        worker.setDaemon(true);
        converting = conversion;
        worker.start();
    }

    private void stepConversion() {
        WorldConversion conversion = converting;
        loadingScreen.setProgress(conversion.progress);
        if (!conversion.done) return;
        converting = null;
        if (conversion.error != null) {
            System.err.println("Couldn't convert " + conversion.folder + ": " + conversion.error);
            displayGuiScreen(new net.minecraft.client.gui.GuiDisconnected(new GuiMainMenu(), "selectWorld.title",
                "Couldn't convert world: " + conversion.error.getMessage(), "gui.toTitle"));
            return;
        }
        WorldStorage.Conversion result = conversion.result;
        System.out.println("Converted " + conversion.folder + ": " + result.chunks() + " chunks"
            + (result.damagedChunks() > 0 ? ", " + result.damagedChunks() + " damaged chunks skipped" : "")
            + (result.unknownBlocks() > 0 ? ", " + result.unknownBlocks() + " unknown blocks became air" : "")
            + ", " + result.blockEntities() + " spawners/heads/furnaces and " + result.entities() + " entities carried over"
            + (result.droppedBlockEntities() > 0 ? ", " + result.droppedBlockEntities()
                + " other block entities (chest contents, signs...) not carried over" : ""));
        launchIntegratedServer(conversion.folder, conversion.name, null);
    }

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
        overworld = world;
        dimension = net.minecraft.world.Dimension.OVERWORLD;
        chooseSpawn(info);
        if (settings == null && info.playerDimension == -1 && info.player != null && cliSpawnX == null) {
            dimension = net.minecraft.world.Dimension.NETHER;
            world = dimensionWorld(dimension);
        }
        loadingScreen.setTitle(I18n.format("menu.loadingLevel"));
        loadingScreen.setMessage(I18n.format("menu.generatingTerrain"));
        loadingScreen.setProgress(0);
        displayGuiScreen(loadingScreen);
        int radius = Math.min(MAX_SPAWN_RADIUS, gameSettings.renderDistanceChunks + 1);
        // Prepare the land where the player will actually appear: their saved position in an existing world.
        int centerX = info.spawnX >> 4, centerZ = info.spawnZ >> 4;
        if (info.player != null && cliSpawnX == null) {
            centerX = (int) Math.floor(info.player.x()) >> 4;
            centerZ = (int) Math.floor(info.player.z()) >> 4;
        }
        preparing = new SpawnPreparation(centerX, centerZ, radius, this::finishLaunch);
    }

    /** A new world's spawn point: vanilla's search for grass in a spawn-friendly biome near the origin. */
    private void chooseSpawn(LevelInfo info) {
        if (info.initialized || overworld.isFlat()) return;
        int[] spawn = overworld.findSpawn();
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
            levelInfo.spawnY = overworld.surfaceHeight(levelInfo.spawnX, levelInfo.spawnZ) + 1;
            levelInfo.initialized = true;
        }
        double spawnX = cliSpawnX != null ? cliSpawnX : levelInfo.spawnX + 0.5;
        double spawnZ = cliSpawnZ != null ? cliSpawnZ : levelInfo.spawnZ + 0.5;
        player = new Player(world, spawnX, spawnZ);
        player.entityBoxes = this::boatBoxes;
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
        player.itemUseListener = new Player.ItemUseListener() {
            @Override public void update(ItemStack stack, int particles) { itemUseEffects(stack, particles); }
            @Override public void finish(ItemStack stack) { finishItemUse(stack); }
        };
        server = dimensionServer(dimension);
        spawnGeneratedCreatures();
        player.heldItem = () -> inventory == null ? null : inventory.getCurrentItem();
        player.fireListener = this::setPlayerOnFire;
        renderer = new WorldRenderer(world, atlas, modelManager, biomeColors);
        sky = new SkyRenderer(guiTextures);
        particles = new ParticleManager(world, atlas, guiTextures, modelManager, biomeColors);
        particles.sounds = this::playSound;
        eventsInstalled = true;
        installEvents(overworld);
        if (netherWorld != null) installEvents(netherWorld);
        sky.setNether(world.isNether());
        gameSettings.difficulty = levelInfo.difficulty;
        if (worldFolder != null) saveWorld();
        displayGuiScreen(null);
    }

    /** What block logic in a dimension's world asks of the game (drops, effects, lit TNT). */
    private void installEvents(World target) {
        target.setEvents(new World.Events() {
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
            @Override public void tntIgnited(int x, int y, int z) { primeTnt(x, y, z, null, false); }
        });
    }

    // ---- dimensions ---------------------------------------------------------------------------

    /** A dimension's world, loaded from its folder (DIM-1) or created the first time it is visited. */
    private World dimensionWorld(net.minecraft.world.Dimension target) {
        if (target == net.minecraft.world.Dimension.OVERWORLD) return overworld;
        if (netherWorld != null) return netherWorld;
        long seed = levelInfo.randomSeed;
        Path directory = worldFolder == null ? null : saveFormat.worldDirectory(worldFolder).resolve(target.folder());
        try {
            if (directory != null && WorldStorage.exists(directory))
                netherWorld = WorldStorage.load(directory, blockRegistry, s -> createNetherWorld(s));
        } catch (IOException error) {
            System.err.println("Couldn't load the Nether; it will be regenerated: " + error.getMessage());
        }
        if (netherWorld == null) netherWorld = createNetherWorld(seed);
        if (eventsInstalled) installEvents(netherWorld);
        return netherWorld;
    }

    private World createNetherWorld(long seed) {
        World created = new World(blockRegistry, biomeRegistry, seed, null, net.minecraft.world.Dimension.NETHER);
        created.setSmelting(smelting, this::itemByName);
        return created;
    }

    /** A dimension's entity list, its saved entities loaded the first time. */
    private IntegratedServer dimensionServer(net.minecraft.world.Dimension target) {
        boolean nether = target == net.minecraft.world.Dimension.NETHER;
        IntegratedServer existing = nether ? netherServer : overworldServer;
        if (existing != null) return existing;
        IntegratedServer created = new IntegratedServer(dimensionWorld(target));
        if (nether) netherServer = created;
        else overworldServer = created;
        IntegratedServer previous = server;
        server = created;
        loadEntities(target);
        server = previous;
        return created;
    }

    /**
     * Entity.travelToDimension and ServerConfigurationManager.transferEntityToWorld:
     * the player leaves for the other dimension at a position divided (into
     * the Nether) or multiplied (out of it) by 8, then, through a portal, the
     * Teleporter finds or builds the portal there. The terrain around is
     * prepared behind the "Downloading terrain" screen, then the travel
     * sound plays.
     */
    private void changeDimension(net.minecraft.world.Dimension target, boolean throughPortal) {
        if (player == null || target == dimension) return;
        double scale = target == net.minecraft.world.Dimension.NETHER ? 1.0 / 8.0 : 8.0;
        double x = Math.max(-29999872, Math.min(29999872, player.x * scale));
        double z = Math.max(-29999872, Math.min(29999872, player.z * scale));
        double y = player.y;
        world = dimensionWorld(target);
        server = dimensionServer(target);
        dimension = target;
        levelInfo.playerDimension = target.id;
        player.setWorld(world);
        if (target == net.minecraft.world.Dimension.NETHER && throughPortal) triggerAchievement("portal");
        if (throughPortal) {
            double[] spot = net.minecraft.world.Teleporter.placeInPortal(world, x, y, z, random);
            x = spot[0];
            y = spot[1];
            z = spot[2];
        }
        player.setPositionAndRotation(x, y, z, player.yaw, player.pitch);
        player.motionX = player.motionY = player.motionZ = 0;
        player.fallDistance = 0;
        pointedEntity = null;
        playerController.resetBlockRemoving();
        if (renderer != null) renderer.destroy();
        renderer = new WorldRenderer(world, atlas, modelManager, biomeColors);
        if (particles != null) particles.clear();
        particles = new ParticleManager(world, atlas, guiTextures, modelManager, biomeColors);
        particles.sounds = this::playSound;
        sky.setNether(world.isNether());
        loadingScreen.setTitle(I18n.format("multiplayer.downloadingTerrain"));
        loadingScreen.setMessage("");
        loadingScreen.setProgress(0);
        displayGuiScreen(loadingScreen);
        int radius = Math.min(MAX_SPAWN_RADIUS, gameSettings.renderDistanceChunks + 1);
        preparing = new SpawnPreparation((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, radius, () -> {
            displayGuiScreen(null);
            if (soundHandler != null && throughPortal) soundHandler.playUi("portal.travel", random.nextFloat() * 0.4f + 0.8f);
        });
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
        overworld = world;
        dimension = net.minecraft.world.Dimension.OVERWORLD;
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
            WorldStorage.save(directory, overworld);
            saveEntities(directory, overworldServer);
            if (netherWorld != null) {
                Path netherDirectory = directory.resolve(net.minecraft.world.Dimension.NETHER.folder());
                Files.createDirectories(netherDirectory);
                WorldStorage.save(netherDirectory, netherWorld);
                saveEntities(netherDirectory, netherServer);
            }
            levelInfo.sizeOnDisk = directorySize(directory);
            saveFormat.saveWorldInfo(worldFolder, levelInfo);
        } catch (IOException error) {
            System.err.println("Couldn't save world " + worldFolder + ": " + error.getMessage());
        }
    }

    /**
     * The world's mobs, lit TNT and experience orbs, saved in their vanilla
     * NBT form to entities.dat (vanilla keeps them per chunk; dropped items
     * aren't saved yet).
     */
    private void saveEntities(Path directory, IntegratedServer entities) throws IOException {
        if (entities == null) return;
        List<net.minecraft.nbt.NbtTag> list = new ArrayList<>();
        for (Entity entity : entities.entities().values()) {
            if (!(entity instanceof MovingEntity moving) || entity.isRemoved()) continue;
            if (entity instanceof EntityLiving living && living.health <= 0) continue;
            net.minecraft.nbt.NbtTag.Compound tag = new net.minecraft.nbt.NbtTag.Compound();
            moving.writeNbt(tag);
            list.add(tag);
        }
        net.minecraft.nbt.NbtTag.Compound root = new net.minecraft.nbt.NbtTag.Compound();
        root.put("Entities", new net.minecraft.nbt.NbtTag.NbtList(list.isEmpty() ? net.minecraft.nbt.NbtTag.END
            : net.minecraft.nbt.NbtTag.COMPOUND, list));
        net.minecraft.nbt.NbtIo.writeCompressed(root, directory.resolve("entities.dat"));
    }

    private void loadEntities(net.minecraft.world.Dimension target) {
        if (worldFolder == null) return;
        Path file = saveFormat.worldDirectory(worldFolder).resolve(target.folder()).resolve("entities.dat");
        World world = dimensionWorld(target);
        if (!Files.isRegularFile(file)) return;
        try {
            net.minecraft.nbt.NbtTag.Compound root = net.minecraft.nbt.NbtIo.readCompressed(file);
            for (net.minecraft.nbt.NbtTag element : root.getList("Entities").values()) {
                if (!(element instanceof net.minecraft.nbt.NbtTag.Compound tag)) continue;
                // One entity the game can't read (say, from a converted vanilla world) must not lose the rest.
                MovingEntity entity;
                try {
                    entity = net.minecraft.entity.EntityList.fromNbt(tag, world);
                } catch (RuntimeException unreadable) {
                    System.err.println("Skipping unreadable entity " + tag.get("id") + ": " + unreadable.getMessage());
                    continue;
                }
                if (entity != null) spawnEntity(entity);
            }
        } catch (IOException | RuntimeException error) {
            System.err.println("Couldn't load entities: " + error.getMessage());
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
        if (overworldServer != null) overworldServer.close();
        if (netherServer != null) netherServer.close();
        if (server != null && server != overworldServer && server != netherServer) server.close();
        overworldServer = netherServer = null;
        overworld = netherWorld = null;
        eventsInstalled = false;
        dimension = net.minecraft.world.Dimension.OVERWORLD;
        portalCounter = timeUntilPortal = 0;
        if (renderer != null) renderer.destroy();
        if (sky != null) sky.destroy();
        itemPickups.clear();
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
            // Vanilla ID and data value ("minecraft:dye", 1); older saves name the LegacyCraft item ("minecraft:dye_red").
            Item item = itemRegistry.resolve(saved.id(), saved.damage());
            if (item == null || saved.count() <= 0) {
                System.err.println("Dropping unknown saved item " + saved.id() + ":" + saved.damage());
                continue;
            }
            int durability = item.isDamageable() ? Math.max(0, Math.min(item.maxDamage(), saved.damage())) : 0;
            ItemStack stack = new ItemStack(item, Math.min(saved.count(), item.maxStackSize()), durability);
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
        if (levelInfo.food != null) {
            LevelInfo.Food food = levelInfo.food;
            player.foodStats.load(food.level(), food.saturation(), food.exhaustion(), food.timer());
        }
        if (levelInfo.effects != null)
            for (LevelInfo.SavedEffect saved : levelInfo.effects) {
                net.minecraft.potion.Potion potion = net.minecraft.potion.Potion.byId(saved.id());
                if (potion != null && saved.duration() > 0)
                    player.loadPotionEffect(new net.minecraft.potion.PotionEffect(potion, saved.duration(), saved.amplifier(),
                        saved.ambient(), saved.showParticles()));
            }
        player.absorptionAmount = Math.max(0f, levelInfo.absorption);
    }

    private void saveInventory() {
        List<LevelInfo.SavedStack> saved = new ArrayList<>();
        for (int slot = 0; slot < InventoryPlayer.MAIN_SIZE; slot++) addSaved(saved, slot);
        for (int slot = 100; slot < 100 + InventoryPlayer.ARMOR_SIZE; slot++) addSaved(saved, slot);
        levelInfo.inventory = saved;
        levelInfo.selectedItemSlot = inventory.currentItem;
        levelInfo.experience = new LevelInfo.Experience(player.experienceLevel, player.experience, player.experienceTotal);
        levelInfo.vitals = new LevelInfo.Vitals(player.health, player.air, player.xpSeed);
        levelInfo.food = new LevelInfo.Food(player.foodStats.getFoodLevel(), player.foodStats.getSaturationLevel(),
            player.foodStats.getExhaustionLevel(), player.foodStats.getFoodTimer());
        List<LevelInfo.SavedEffect> effects = new ArrayList<>();
        for (net.minecraft.potion.PotionEffect effect : player.getActivePotionEffects())
            effects.add(new LevelInfo.SavedEffect(effect.potion().id, effect.amplifier(), effect.duration(), effect.ambient(),
                effect.showParticles()));
        levelInfo.effects = effects;
        levelInfo.absorption = player.absorptionAmount;
    }

    private void addSaved(List<LevelInfo.SavedStack> saved, int slot) {
        ItemStack stack = inventory.getBySaveSlot(slot);
        if (stack != null)
            saved.add(new LevelInfo.SavedStack(slot, stack.item().vanillaId().toString(), stack.count(),
                stack.item().isDamageable() ? stack.damage() : stack.item().vanillaData(), stack.writeTag()));
    }

    /** Gives the player the current game mode's abilities (flight in Creative, noclip flight in Spectator). */
    private void applyGameMode() {
        if (player == null || levelInfo == null) return;
        player.setAbilities(levelInfo.gameType == WorldSettings.GameType.CREATIVE, isSpectator());
        player.disableDamage = levelInfo.gameType == WorldSettings.GameType.CREATIVE || isSpectator();
    }

    /** The death message in chat (CombatTracker.getDeathMessage for a death with no attacker). */
    private void announceDeath(net.minecraft.entity.DamageSource source) {
        String attacker = player == null ? null : player.lastAttacker;
        if (source.hasAttacker() && attacker != null) {
            String who = attacker.startsWith("entity.") ? I18n.format(attacker) : attacker;
            printChatLine(net.minecraft.client.gui.I18n.translateChat(source.deathMessageKey(), playerName, who), 0);
            return;
        }
        printChatLine(net.minecraft.client.gui.I18n.translateChat(source.deathMessageKey(), playerName), 0);
    }

    // ---- entities: targeting, fighting, explosions -------------------------------------------

    /**
     * EntityRenderer.getMouseOver: the block in reach, unless a mob or lit TNT
     * (its box grown 0.1) is nearer along the view, in which case that is
     * {@link #pointedEntity} and no block is targeted. Out of Creative an
     * entity more than 3 blocks away can't be hit and blocks the block behind it.
     */
    private WorldRenderer.Hit target() {
        WorldRenderer.Hit hit = renderer.raycast(player, reach());
        pointedEntity = null;
        if (server == null || isSpectator()) return hit;
        double ex = player.x, ey = player.eyeY(), ez = player.z, dx = player.dirX(), dy = player.dirY(), dz = player.dirZ();
        boolean limited = !isCreative();
        double best = limited ? reach() : 6.0;
        if (hit != null) {
            double hx = hit.x() + hit.hitX() - ex, hy = hit.y() + hit.hitY() - ey, hz = hit.z() + hit.hitZ() - ez;
            best = Math.sqrt(hx * hx + hy * hy + hz * hz);
        }
        MovingEntity found = null;
        double foundDistance = best;
        for (Entity entity : server.entities().values()) {
            if (entity == ridingBoat) continue;
            if (entity.isRemoved() || !(entity instanceof EntityLiving || entity instanceof EntityTNTPrimed
                || entity instanceof net.minecraft.entity.EntityBoat
                || entity instanceof net.minecraft.entity.EntityFireball fb && fb.deflectable())) continue;
            AxisAlignedBB box = ((MovingEntity) entity).boundingBox();
            box = new AxisAlignedBB(box.minX() - 0.1, box.minY() - 0.1, box.minZ() - 0.1, box.maxX() + 0.1, box.maxY() + 0.1, box.maxZ() + 0.1);
            if (ex >= box.minX() && ex <= box.maxX() && ey >= box.minY() && ey <= box.maxY() && ez >= box.minZ() && ez <= box.maxZ()) {
                if (foundDistance >= 0) {
                    found = (MovingEntity) entity;
                    foundDistance = 0;
                }
                continue;
            }
            AxisAlignedBB.RayHit entry = box.rayEntry(ex, ey, ez, dx, dy, dz);
            if (entry == null || entry.distance() > best) continue;
            if (entry.distance() < foundDistance || foundDistance == 0) {
                found = (MovingEntity) entity;
                foundDistance = entry.distance();
            }
        }
        if (found == null) return hit;
        if (limited && foundDistance > 3.0) return null;
        pointedEntity = found;
        return null;
    }

    /**
     * EntityPlayer.attackTargetEntityWithCurrentItem: the held item's attack
     * damage (x1.5 for a critical hit while falling), plus Sharpness 1.25 a
     * level; Knockback (and sprinting) throw the mob further and stop the
     * sprint; Fire Aspect sets it alight; a sword wears by 1 and a tool by 2;
     * the swing costs 0.3 exhaustion.
     */
    private void attackEntity(MovingEntity target) {
        if (target instanceof net.minecraft.entity.EntityBoat boat) {
            if (!isSpectator() && boat.hitByPlayer(isCreative()) && boat == ridingBoat) dismountBoat();
            return;
        }
        if (!isSpectator() && target instanceof net.minecraft.entity.EntityFireball fireball && fireball.deflectable()) {
            fireball.deflect(player);
            return;
        }
        if (isSpectator() || !(target instanceof EntityLiving mob)) return;
        ItemStack held = inventory.getCurrentItem();
        float damage = player.attackDamage(ToolValues.attackDamage(held == null ? null : held.item()));
        float bonus = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.SHARPNESS, held) * 1.25f;
        int knockback = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.KNOCKBACK, held)
            + (player.sprinting ? 1 : 0);
        if (damage <= 0 && bonus <= 0) return;
        boolean crit = player.fallDistance > 0 && !player.onGround && !player.onLadder() && !player.inWater
            && !player.isPotionActive(net.minecraft.potion.Potion.BLINDNESS);
        if (crit && damage > 0) damage *= 1.5f;
        damage += bonus;
        int fireAspect = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.FIRE_ASPECT, held);
        boolean lit = false;
        if (fireAspect > 0 && !mob.isBurning()) {
            lit = true;
            mob.setFire(1);
        }
        int looting = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.LOOTING, held);
        if (!mob.attackFromPlayer(player, damage, looting)) {
            if (lit) mob.fire = 0;
            return;
        }
        if (knockback > 0) {
            double yaw = Math.toRadians(player.yaw);
            mob.addVelocity(Math.sin(yaw) * knockback * 0.5, 0.1, -Math.cos(yaw) * knockback * 0.5);
            player.motionX *= 0.6;
            player.motionZ *= 0.6;
            player.sprinting = false;
        }
        if (particles != null) {
            if (crit) particles.emitAtEntity(mob, ParticleManager.Kind.CRIT);
            if (bonus > 0) particles.emitAtEntity(mob, ParticleManager.Kind.MAGIC_CRIT);
        }
        if (damage >= 18f) triggerAchievement("overkill");
        if (held != null && !isCreative()) {
            ToolValues.Type type = ToolValues.type(held.item());
            int wear = type == ToolValues.Type.SWORD ? 1
                : type == ToolValues.Type.AXE || type == ToolValues.Type.PICKAXE || type == ToolValues.Type.SHOVEL ? 2 : 0;
            if (wear > 0 && net.minecraft.enchantment.EnchantmentHelper.damageItem(held, wear, random)) {
                inventory.mainInventory[inventory.currentItem] = null;
                playSound("random.break", player.x, player.y, player.z, 0.8f, 0.8f + random.nextFloat() * 0.4f);
            }
        }
        if (fireAspect > 0) mob.setFire(fireAspect * 4);
        player.addExhaustion(0.3f);
    }

    /** Entity.interactFirst: flint and steel lights a creeper for good. */
    private boolean interactWithEntity(MovingEntity target) {
        if (target instanceof net.minecraft.entity.EntityBoat boat) {
            // EntityBoat.interactFirst: an empty boat takes the player aboard.
            if (isSpectator() || boat.ridden || ridingBoat != null) return false;
            ridingBoat = boat;
            boat.ridden = true;
            return true;
        }
        if (target instanceof net.minecraft.entity.passive.EntityAnimal animal) {
            if (isSpectator() || !animal.isAlive()) return false;
            return animal.interact(animalInteractor);
        }
        ItemStack held = inventory.getCurrentItem();
        if (!(target instanceof EntityCreeper creeper) || held == null || !held.item().name().path().equals("flint_and_steel")) return false;
        creeper.ignite();
        if (!isCreative() && net.minecraft.enchantment.EnchantmentHelper.damageItem(held, 1, random)) {
            inventory.mainInventory[inventory.currentItem] = null;
            playSound("random.break", player.x, player.y, player.z, 0.8f, 0.8f + random.nextFloat() * 0.4f);
        }
        return true;
    }

    /** The player's hand as animals see it when right-clicked (EntityAnimal.interact and friends). */
    private final net.minecraft.entity.passive.EntityAnimal.Interactor animalInteractor = new net.minecraft.entity.passive.EntityAnimal.Interactor() {
        @Override public ItemStack held() { return inventory.getCurrentItem(); }
        @Override public boolean creative() { return isCreative(); }
        @Override public void consumeHeld() {
            ItemStack held = inventory.getCurrentItem();
            if (held == null || isCreative()) return;
            held.split(1);
            if (held.isEmpty()) inventory.mainInventory[inventory.currentItem] = null;
        }
        @Override public void exchangeHeld(ItemStack result) {
            ItemStack held = inventory.getCurrentItem();
            if (held == null) return;
            if (held.count() <= 1) {
                inventory.mainInventory[inventory.currentItem] = result;
            } else {
                held.split(1);
                if (!inventory.addItemStackToInventory(result)) dropPlayerItem(result);
            }
        }
        @Override public void damageHeld(int amount) {
            ItemStack held = inventory.getCurrentItem();
            if (held == null || isCreative()) return;
            if (net.minecraft.enchantment.EnchantmentHelper.damageItem(held, amount, random)) {
                inventory.mainInventory[inventory.currentItem] = null;
                playSound("random.break", player.x, player.y, player.z, 0.8f, 0.8f + random.nextFloat() * 0.4f);
            }
        }
        @Override public ItemStack stack(String name) { return itemStack(name); }
        @Override public void awardAchievement(String id) { triggerAchievement(id); }
    };

    /** BlockTNT.explode / onBlockDestroyedByExplosion: lit TNT where the block was (a short fuse when an explosion lit it). */
    private void primeTnt(int x, int y, int z, String placedBy, boolean byExplosion) {
        if (server == null) return;
        EntityTNTPrimed tnt = new EntityTNTPrimed(world, x + 0.5, y, z + 0.5, placedBy);
        if (byExplosion) tnt.fuse = random.nextInt(tnt.fuse / 4) + tnt.fuse / 8;
        spawnEntity(tnt);
        if (!byExplosion) playSound("game.tnt.primed", tnt.x(), tnt.y(), tnt.z(), 1f, 1f);
    }

    /**
     * World.newExplosion (Explosion.doExplosionA and B): entities within
     * twice the strength take {@code (i^2 + i) / 2 x 7 x 2 x strength + 1}
     * damage, i being their closeness times how much of them sees the
     * centre, and are pushed away by i; then the blast sound and flash, and
     * when it breaks blocks, a puff over each block the rays reached, each
     * broken block dropping with chance 1 / strength (TNT lights instead).
     */
    private void explode(Entity exploder, double x, double y, double z, float size, boolean smoking, String attacker) {
        explode(exploder, x, y, z, size, false, smoking, attacker);
    }

    private void explode(Entity exploder, double x, double y, double z, float size, boolean flaming, boolean smoking, String attacker) {
        if (server == null || world == null) return;
        net.minecraft.world.Explosion explosion = new net.minecraft.world.Explosion(x, y, z, size);
        if (exploder instanceof net.minecraft.entity.EntityWitherSkull skull && skull.invulnerable) explosion.resistanceCap = 0.8f;
        explosion.computeAffectedBlocks(world, random);
        float reachDistance = size * 2f;
        AxisAlignedBB area = new AxisAlignedBB(x - reachDistance - 1, y - reachDistance - 1, z - reachDistance - 1,
            x + reachDistance + 1, y + reachDistance + 1, z + reachDistance + 1);
        for (Entity entity : server.entities().values()) {
            if (entity == exploder || entity.isRemoved()) continue;
            AxisAlignedBB box;
            double eyeHeight;
            if (entity instanceof MovingEntity moving) {
                if (moving.isImmuneToExplosions()) continue;
                box = moving.boundingBox();
                eyeHeight = moving.eyeHeight();
            } else if (entity instanceof EntityItem item) {
                box = item.boundingBox();
                eyeHeight = EntityItem.SIZE * 0.85;
            } else {
                continue;
            }
            if (!box.intersects(area)) continue;
            double ddx = entity.x() - x, ddy = entity.y() - y, ddz = entity.z() - z;
            double closeness = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) / reachDistance;
            if (closeness > 1.0) continue;
            double px = entity.x() - x, py = entity.y() + eyeHeight - y, pz = entity.z() - z;
            double length = Math.sqrt(px * px + py * py + pz * pz);
            if (length == 0) continue;
            px /= length; py /= length; pz /= length;
            double impact = (1.0 - closeness) * net.minecraft.world.Explosion.exposure(world, x, y, z, box);
            float damage = (int) ((impact * impact + impact) / 2.0 * 7.0 * reachDistance + 1.0);
            if (entity instanceof MovingEntity moving) {
                moving.attackFromExplosion(damage, attacker, exploder instanceof MovingEntity m ? m : null);
                moving.motionX += px * impact;
                moving.motionY += py * impact;
                moving.motionZ += pz * impact;
            } else {
                EntityItem item = (EntityItem) entity;
                item.explosionDamage(damage);
                item.motionX += px * impact;
                item.motionY += py * impact;
                item.motionZ += pz * impact;
            }
        }
        if (player != null && !player.dead && player.boundingBox().intersects(area)) {
            double ddx = player.x - x, ddy = player.y - y, ddz = player.z - z;
            double closeness = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) / reachDistance;
            double px = ddx, py = player.y + player.eyeHeight() - y, pz = ddz;
            double length = Math.sqrt(px * px + py * py + pz * pz);
            if (closeness <= 1.0 && length != 0) {
                px /= length; py /= length; pz /= length;
                double impact = (1.0 - closeness) * net.minecraft.world.Explosion.exposure(world, x, y, z, player.boundingBox());
                float damage = (int) ((impact * impact + impact) / 2.0 * 7.0 * reachDistance + 1.0);
                player.attackByExplosion(damage, attacker);
                // The client takes the push straight from the explosion packet (no Blast Protection), unless flying in Creative.
                if (!isSpectator() && !(isCreative() && player.isFlying)) {
                    player.motionX += px * impact;
                    player.motionY += py * impact;
                    player.motionZ += pz * impact;
                }
            }
        }
        playSound("random.explode", x, y, z, 4f, (1f + (random.nextFloat() - random.nextFloat()) * 0.2f) * 0.7f);
        if (particles != null) {
            if (size >= 2f && smoking) particles.spawn(ParticleManager.Kind.HUGE_EXPLOSION, x, y, z, 1, 0, 0, null);
            else particles.spawn(ParticleManager.Kind.LARGE_EXPLODE, x, y, z, 1, 0, 0, null);
        }
        if (smoking) destroyExploded(explosion, x, y, z, size, attacker);
        if (flaming) {
            // Explosion.doExplosionB with isFlaming: a third of the cleared spots over full blocks catch fire.
            Block fire = blockRegistry.get("minecraft:fire");
            for (int[] pos : explosion.affectedBlocks()) {
                int bx = pos[0], by = pos[1], bz = pos[2];
                if (by < 1 || by >= World.HEIGHT || !world.get(bx, by, bz).isAir() || !world.get(bx, by - 1, bz).isOpaque()) continue;
                if (random.nextInt(3) == 0) world.setStateAndUpdate(bx, by, bz, BlockState.defaultOf(fire));
            }
        }
    }

    private void destroyExploded(net.minecraft.world.Explosion explosion, double x, double y, double z, float size, String attacker) {
        for (int[] pos : explosion.affectedBlocks()) {
            int bx = pos[0], by = pos[1], bz = pos[2];
            if (particles != null) {
                double px = bx + random.nextFloat(), py = by + random.nextFloat(), pz = bz + random.nextFloat();
                double dx = px - x, dy = py - y, dz = pz - z;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= length; dy /= length; dz /= length;
                double speed = 0.5 / (length / size + 0.1);
                speed *= random.nextFloat() * random.nextFloat() + 0.3f;
                dx *= speed; dy *= speed; dz *= speed;
                particles.spawn(ParticleManager.Kind.EXPLODE, (px + x) / 2.0, (py + y) / 2.0, (pz + z) / 2.0, dx, dy, dz, null);
                particles.spawn(ParticleManager.Kind.SMOKE, px, py, pz, dx, dy, dz, null);
            }
            BlockState state = world.getState(bx, by, bz);
            Block block = state.block();
            if (block.isAir()) continue;
            String name = block.name().path();
            if (!name.equals("tnt") && dropsEnabled())
                for (ItemStack drop : blockDrops.explosionDrops(name, block.material(), state.metadata(), 1f / size, random))
                    spawnItem(EntityItem.fromBlock(world, bx, by, bz, drop, random));
            if (name.equals("skull") && dropsEnabled() && random.nextFloat() <= 1f / size) {
                ItemStack head = skullDrop(bx, by, bz, state.metadata());
                if (head != null) spawnItem(EntityItem.fromBlock(world, bx, by, bz, head, random));
            }
            world.setState(bx, by, bz, BlockState.of(blockRegistry.air(), 0));
            BlockUpdates.afterChange(world, bx, by, bz, this::blockPopped);
            if (name.equals("tnt")) primeTnt(bx, by, bz, attacker, true);
        }
    }

    /** Mobs, lit TNT and experience orbs, and the shadows under entities (Fancy graphics with Entity Shadows on). */
    private void renderMobs(int renderDistance) {
        if (server == null || mobRenderer == null) return;
        double limit = (double) renderDistance * renderDistance;
        boolean front = gameSettings.thirdPersonView == 2;
        mobRenderer.setView(world, levelInfo.worldTime, player.x, player.eyeY(), player.z,
            PlayerAnimation.vanillaYaw(player) + (front ? 180f : 0f), front ? -player.pitch : player.pitch);
        boolean shadows = gameSettings.fancyGraphics && gameSettings.entityShadows;
        mobRenderer.fireRequests.clear();
        mobRenderer.bossRendered = false;
        glEnable(GL_DEPTH_TEST);
        BlockState tntBlock = null;
        for (Entity entity : server.entities().values()) {
            if (entity.isRemoved()) continue;
            if (entity instanceof EntityItem item) {
                if (!shadows) continue;
                double ix = item.prevX + (item.x() - item.prevX) * partialTicks, iz = item.prevZ + (item.z() - item.prevZ) * partialTicks;
                double iy = item.prevY + (item.y() - item.prevY) * partialTicks;
                if ((ix - player.x) * (ix - player.x) + (iz - player.z) * (iz - player.z) <= limit)
                    mobRenderer.renderShadow(ix, iy, iz, 0.15f, 0.75f);
                continue;
            }
            if (!(entity instanceof MovingEntity moving)) continue;
            double ex = moving.renderX(partialTicks), ey = moving.renderY(partialTicks), ez = moving.renderZ(partialTicks);
            if ((ex - player.x) * (ex - player.x) + (ez - player.z) * (ez - player.z) > limit) continue;
            if (entity instanceof EntityCreeper creeper) {
                mobRenderer.renderCreeper(creeper, partialTicks);
                if (shadows) mobRenderer.renderShadow(ex, ey, ez, 0.5f, 1f);
            } else if (entity instanceof EntityTNTPrimed tnt) {
                if (tntBlock == null) tntBlock = BlockState.defaultOf(blockRegistry.get("minecraft:tnt"));
                mobRenderer.renderTNT(tnt, tntBlock, partialTicks);
                if (shadows) mobRenderer.renderShadow(ex, ey, ez, 0.5f, 1f);
            } else if (entity instanceof net.minecraft.entity.passive.EntityIronGolem golem) {
                mobRenderer.renderGuardian(golem, partialTicks);
                if (shadows) mobRenderer.renderShadow(ex, ey, ez, 0.5f, 1f);
            } else if (entity instanceof net.minecraft.entity.EntityBoat boat) {
                mobRenderer.renderBoat(boat, partialTicks);
                if (shadows) mobRenderer.renderShadow(ex, ey, ez, 0.5f, 1f);
            } else if (entity instanceof EntityXPOrb orb) {
                mobRenderer.renderXPOrb(orb, partialTicks);
                if (shadows) mobRenderer.renderShadow(ex, ey, ez, 0.15f, 0.75f);
            } else if (entity instanceof net.minecraft.entity.monster.EntityPigZombie || entity instanceof net.minecraft.entity.monster.EntitySkeleton
                || entity instanceof net.minecraft.entity.monster.EntityZombie || entity instanceof net.minecraft.entity.monster.EntitySpider
                || entity instanceof net.minecraft.entity.monster.EntityBlaze || entity instanceof net.minecraft.entity.monster.EntityGhast
                || entity instanceof net.minecraft.entity.monster.EntityMagmaCube) {
                EntityLiving mob = (EntityLiving) entity;
                mobRenderer.renderMob(mob, partialTicks);
                if (shadows) mobRenderer.renderShadow(ex, ey, ez, net.minecraft.client.renderer.entity.MobRenderer.shadowSize(mob), 1f);
            } else if (entity instanceof net.minecraft.entity.boss.EntityWither wither) {
                mobRenderer.renderWither(wither, partialTicks);
                if (shadows) mobRenderer.renderShadow(ex, ey, ez, 1f, 1f);
            } else if (entity instanceof net.minecraft.entity.EntityWitherSkull skull) {
                mobRenderer.renderWitherSkull(skull, partialTicks);
            } else if (entity instanceof net.minecraft.entity.EntityArrow arrow) {
                mobRenderer.renderArrow(arrow, partialTicks);
            } else if (entity instanceof net.minecraft.entity.EntityFireball fireball) {
                Item item = itemByName(fireball.itemName());
                if (item != null) mobRenderer.renderFireball(fireball, new ItemStack(item), partialTicks);
            } else if (entity instanceof net.minecraft.entity.passive.EntityAnimal animal) {
                mobRenderer.renderAnimal(animal, partialTicks);
                // RenderPig/Cow/Sheep 0.7, RenderChicken 0.3; babies half (Render.renderShadow).
                float size = animal instanceof net.minecraft.entity.passive.EntityChicken ? 0.3f : 0.7f;
                if (animal.isChild()) size *= 0.5f;
                if (shadows) mobRenderer.renderShadow(ex, ey, ez, size, 1f);
            } else if (entity instanceof net.minecraft.entity.EntityPotion potion) {
                Item item = itemRegistry.potion(potion.potionData);
                if (item != null) mobRenderer.renderThrownItem(potion, new ItemStack(item), partialTicks);
            } else if (entity instanceof net.minecraft.entity.EntityThrowable thrown) {
                Item item = itemByName(thrown.itemName());
                if (item != null) mobRenderer.renderThrownItem(thrown, new ItemStack(item), partialTicks);
            }
        }
        renderSkulls(limit);
        renderBlockEntities(limit);
        if (mobRenderer.bossRendered && ingameGui != null)
            ingameGui.setBossStatus(I18n.format(mobRenderer.bossName), mobRenderer.bossHealthScale);
        if (shadows && gameSettings.thirdPersonView != 0 && !player.dead)
            mobRenderer.renderShadow(player.renderX(partialTicks), player.renderY(partialTicks), player.renderZ(partialTicks), 0.5f, 1f);
        for (double[] fire : mobRenderer.fireRequests) renderEntityFire(fire[0], fire[1], fire[2], (float) fire[3], (float) fire[4]);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
    }

    /**
     * TileEntityItemStackRenderer: mob heads standing on the floor of the item's block, turned round;
     * chests and banners drawn by their block's renderer.
     */
    private final ItemRenderer.Builtin builtinItems = new ItemRenderer.Builtin() {
        @Override public boolean handles(Item item) {
            return net.minecraft.client.renderer.entity.SkullRenderer.typeOf(item.name().path()) >= 0
                || net.minecraft.client.renderer.entity.BlockEntityRenderer.drawsItem(item.vanillaId().path());
        }
        @Override public void draw(Item item, float red, float green, float blue) {
            int type = net.minecraft.client.renderer.entity.SkullRenderer.typeOf(item.name().path());
            if (type >= 0) {
                net.minecraft.client.renderer.entity.SkullRenderer.render(guiTextures, 0, 0, 0, 1, 180f, type, new float[] {red, green, blue});
                return;
            }
            blockEntityRenderer.renderItem(item.vanillaId().path(), item.vanillaData(), red, green, blue);
        }
    };

    /**
     * The chests, signs, banners and end portals in the sections drawn this frame, within the
     * render distance (TileEntityRendererDispatcher), lit by the light where they stand.
     */
    private void renderBlockEntities(double limit) {
        double[] camera = {player.x, player.y, player.z};
        renderer.forEachBlockEntity((x, y, z) -> {
            double dx = x + 0.5 - player.x, dz = z + 0.5 - player.z;
            if (dx * dx + dz * dz > limit) return;
            BlockState state = world.getState(x, y, z);
            if (!net.minecraft.client.renderer.entity.BlockEntityRenderer.draws(state.block().name().path())) return;
            float[] light = net.minecraft.client.renderer.LightMap.color(world.getSkyLight(x, y, z), world.getBlockLight(x, y, z));
            boolean lit = !state.block().name().path().equals("end_portal");
            if (lit) net.minecraft.client.renderer.StandardLighting.enable();
            blockEntityRenderer.render(state, x, y, z, world::getState, light, levelInfo.totalTime, partialTicks, camera);
            if (lit) net.minecraft.client.renderer.StandardLighting.disable();
        });
    }

    /** Placed mob heads within the render distance (TileEntitySkullRenderer); heads whose block has gone are forgotten. */
    private void renderSkulls(double limit) {
        Block skullBlock = blockRegistry.get("minecraft:skull");
        for (var iterator = world.skulls().entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            World.BlockPos pos = entry.getKey();
            double dx = pos.x() + 0.5 - player.x, dz = pos.z() + 0.5 - player.z;
            if (dx * dx + dz * dz > limit) continue;
            if (world.generatedChunk(Math.floorDiv(pos.x(), 16), Math.floorDiv(pos.z(), 16)) == null) continue;
            BlockState state = world.getState(pos.x(), pos.y(), pos.z());
            if (state.block() != skullBlock) {
                iterator.remove();
                continue;
            }
            mobRenderer.renderSkullBlock(pos.x(), pos.y(), pos.z(), state.metadata() & 7, entry.getValue());
        }
    }

    /**
     * ItemSkull.onItemUse: a head goes on the clicked face (never under a
     * block), replacing a replaceable block or beside a solid one; on the
     * floor it turns to the nearest sixteenth of the player's facing. A
     * wither skeleton skull completing the pattern builds a wither.
     */
    private boolean useSkull(WorldRenderer.Hit hit) {
        ItemStack held = inventory.getCurrentItem();
        if (hit == null || held == null || levelInfo.gameType == WorldSettings.GameType.ADVENTURE || isSpectator()) return false;
        int type = net.minecraft.client.renderer.entity.SkullRenderer.typeOf(held.item().name().path());
        if (type < 0) return false;
        net.minecraft.util.Direction face = hit.face();
        if (face == net.minecraft.util.Direction.DOWN) return false;
        int x = hit.x(), y = hit.y(), z = hit.z();
        BlockState clicked = world.getState(x, y, z);
        if (!net.minecraft.block.BlockPlacement.isReplaceable(clicked) || clicked.block().isAir()) {
            if (!clicked.block().isSolid()) return false;
            x += face.xOffset; y += face.yOffset; z += face.zOffset;
        }
        if (!world.inBounds(x, y, z) || !net.minecraft.block.BlockPlacement.isReplaceable(world.getState(x, y, z))) return false;
        int facing = switch (face) { case UP -> 1; case NORTH -> 2; case SOUTH -> 3; case WEST -> 4; case EAST -> 5; default -> 1; };
        world.setStateAndUpdate(x, y, z, BlockState.of(blockRegistry.get("minecraft:skull"), facing));
        int rotation = face == net.minecraft.util.Direction.UP
            ? (int) Math.floor((player.yaw + 180f) * 16f / 360f + 0.5) & 15 : 0;
        world.setSkull(x, y, z, type, rotation);
        checkWitherSpawn(x, y, z, type);
        if (!isCreative()) inventory.consumeCurrentItem();
        return true;
    }

    /** BlockSkull.checkWitherSpawn: the pattern's blocks vanish and a wither rises from the bottom soul sand. */
    private void checkWitherSpawn(int x, int y, int z, int type) {
        if (type != 1 || y < 2 || levelInfo.difficulty == 0 || server == null) return;
        net.minecraft.world.WitherPattern.Match match = net.minecraft.world.WitherPattern.find(world, x, y, z);
        if (match == null) return;
        Block air = blockRegistry.air();
        for (int[] cell : match.all()) world.setState(cell[0], cell[1], cell[2], BlockState.of(air, 0));
        int[] bottom = match.spawnBlock();
        float yaw = match.alongX() ? 0f : 90f;
        net.minecraft.entity.boss.EntityWither wither = new net.minecraft.entity.boss.EntityWither(world,
            bottom[0] + 0.5, bottom[1] + 0.55, bottom[2] + 0.5);
        wither.setPositionAndRotation(bottom[0] + 0.5, bottom[1] + 0.55, bottom[2] + 0.5, yaw, 0f);
        wither.prevRotationYaw = yaw;
        wither.renderYawOffset = wither.prevRenderYawOffset = yaw;
        wither.rotationYawHead = wither.prevRotationYawHead = yaw;
        wither.startSpawning();
        if (player != null && player.boundingBox().intersects(wither.boundingBox().grow(50.0, 50.0, 50.0)))
            triggerAchievement("spawnWither");
        spawnEntity(wither);
        int[] top = match.cells()[0][1];
        Item snowball = itemByName("snowball");
        for (int i = 0; i < 120 && particles != null && itemRenderer != null; i++)
            particles.spawnItemCrack(itemRenderer.particleTexture(snowball), top[0] + random.nextDouble(),
                top[1] - 2 + random.nextDouble() * 3.9, top[2] + random.nextDouble(), 0, 0, 0);
        for (int[] cell : match.all()) BlockUpdates.afterChange(world, cell[0], cell[1], cell[2], this::blockPopped);
    }

    /** BlockSkull.getDrops: the head it holds, unless it was marked not to drop. */
    private ItemStack skullDrop(int x, int y, int z, int metadata) {
        if ((metadata & 8) != 0) return null;
        World.Skull skull = world.skull(x, y, z);
        Item item = itemByName(net.minecraft.client.renderer.entity.SkullRenderer.ITEMS[
            net.minecraft.client.renderer.entity.SkullRenderer.clampType(skull == null ? 0 : skull.type())]);
        return item == null ? null : new ItemStack(item);
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
        // Dying in the Nether brings you back to the overworld spawn.
        if (dimension != net.minecraft.world.Dimension.OVERWORLD) changeDimension(net.minecraft.world.Dimension.OVERWORLD, false);
        player.respawn();
        if (preparing != null)
            preparing = new SpawnPreparation((int) Math.floor(player.x) >> 4, (int) Math.floor(player.z) >> 4,
                Math.min(MAX_SPAWN_RADIUS, gameSettings.renderDistanceChunks + 1), preparing.onDone);
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
            if (inWorld) {
                net.minecraft.client.shaders.Shaders.frame.dimension = world.isNether() ? "world-1" : "world0";
                net.minecraft.client.shaders.Shaders.select(shaderPackFile(), message -> printChatLine(message, 0));
                renderWorld(fog);
                shaderPass.apply(additions, width, height, message -> printChatLine(message, 0));
            }
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
        LightMap.update(SkyRenderer.sunBrightness(sky.angle()), gameSettings.gammaSetting,
            Math.max(player == null ? 0f : player.nightVisionBrightness(partialTicks),
                additions.isOn(net.minecraft.client.additions.Additions.FULL_BRIGHT)
                    ? (float) additions.number(net.minecraft.client.additions.Additions.FULL_BRIGHT, "brightness") / 100f : 0f),
            world.isNether());
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
        boolean shaders = net.minecraft.client.shaders.Shaders.active();
        if (shaders) {
            // The pack gets the terrain's projection and the camera's rotation, then draws its shadow map first.
            fillShaderFrame(fog, liquid);
            perspective(worldFov(), width / (double) height, 0.05, renderDistance * SQRT_2);
            glMatrixMode(GL_MODELVIEW);
            net.minecraft.client.shaders.Shaders.beginWorld(width, height);
            net.minecraft.client.shaders.Shaders.shadowPass(renderer::renderShadow);
        }
        if (skyVisible) {
            setupFog(liquid, 0f, renderDistance);
            perspective(worldFov(), width / (double) height, 0.05, renderDistance * 2.0);
            glMatrixMode(GL_MODELVIEW);
            sky.renderSky(levelInfo.worldTime, eyeY);
        }
        glEnable(GL_FOG);
        // WorldProviderHell.doesXZShowFog: the Nether's fog starts almost at once and ends halfway out.
        if (world.isNether() && liquid == null) setupFog(null, renderDistance * 0.05f, Math.min(renderDistance, 192f) * 0.5f);
        else setupFog(liquid, renderDistance * 0.75f, renderDistance);
        if (eyeY < SkyRenderer.CLOUD_HEIGHT) renderClouds(renderDistance, skyVisible);
        perspective(worldFov(), width / (double) height, 0.05, renderDistance * SQRT_2);
        glMatrixMode(GL_MODELVIEW);
        glTranslated(-player.x, -eyeY, -player.z);
        glColor4f(1, 1, 1, 1);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        renderer.render(player, renderDistance);
        net.minecraft.client.shaders.Shaders.use("gbuffers_basic");
        if (gameSettings.showChunkBorders) DebugRenderer.renderChunkBorders(player.x, player.y, player.z);
        net.minecraft.client.shaders.Shaders.use("gbuffers_entities");
        renderItemEntities(renderDistance);
        renderMobs(renderDistance);
        tableBooks.render(world, guiTextures, partialTicks, player.x, player.z, Math.min(64.0, renderDistance));
        if (gameSettings.thirdPersonView != 0 && !player.dead) renderPlayerModel();
        WorldRenderer.Hit hit = currentScreen == null && !isSpectator() ? target() : null;
        net.minecraft.client.shaders.Shaders.use("gbuffers_damagedblock");
        drawDestroyProgress();
        net.minecraft.client.shaders.Shaders.use("gbuffers_line");
        if (hit != null && !gameSettings.hideGUI) drawSelection(hit);
        if (shaders) {
            net.minecraft.client.shaders.Shaders.endOpaque();
            renderer.renderTranslucent();
        }
        net.minecraft.client.shaders.Shaders.use("gbuffers_textured_lit");
        renderParticles();
        if (eyeY >= SkyRenderer.CLOUD_HEIGHT) {
            glPushMatrix();
            glTranslated(player.x, eyeY, player.z);
            renderClouds(renderDistance, skyVisible);
            glPopMatrix();
        }
        // With a shader pack the hand keeps the world's depth (packs tell it apart by MC_HAND_DEPTH).
        if (shaders) net.minecraft.client.shaders.Shaders.beforeHand();
        else glClear(GL_DEPTH_BUFFER_BIT);
        net.minecraft.client.shaders.Shaders.use("gbuffers_hand");
        renderHand(renderDistance);
        if (shaders) {
            net.minecraft.client.shaders.Shaders.endWorld();
            if (gameSettings.thirdPersonView == 0 && !player.dead) renderOverlays();
        }
    }

    /** The Shader Packs addition's pack, or null when it's off or none is picked. */
    private Path shaderPackFile() {
        String id = net.minecraft.client.additions.Additions.SHADER_PACKS;
        if (!additions.isOn(id)) return null;
        String name = additions.setting(id, "pack");
        if (name == null || name.isEmpty()) return null;
        return additions.folder(additions.find(id).setting("pack")).resolve(name);
    }

    /** What the shader pack's uniforms read this frame. */
    private void fillShaderFrame(float[] fog, String liquid) {
        net.minecraft.client.shaders.Shaders.Frame f = net.minecraft.client.shaders.Shaders.frame;
        f.worldTime = levelInfo.worldTime;
        f.celestialAngle = sky.angle();
        f.rain = world.isRaining() ? 1f : 0f;
        f.far = gameSettings.renderDistanceChunks * 16f;
        f.cameraX = player.x;
        f.cameraY = player.eyeY();
        f.cameraZ = player.z;
        f.look[0] = (float) player.dirX();
        f.look[1] = (float) player.dirY();
        f.look[2] = (float) player.dirZ();
        int x = (int) Math.floor(player.x), y = (int) Math.floor(player.eyeY()), z = (int) Math.floor(player.z);
        boolean inside = y >= 0 && y < World.HEIGHT;
        f.eyeSkyLight = inside ? world.getSkyLight(x, y, z) : y >= World.HEIGHT ? 15 : 0;
        f.eyeBlockLight = inside ? world.getBlockLight(x, y, z) : 0;
        f.eyeInWater = "water".equals(liquid) ? 1 : "lava".equals(liquid) ? 2 : 0;
        f.moonPhase = (int) (levelInfo.worldTime / 24000L % 8L);
        f.nightVision = player.nightVisionBrightness(partialTicks);
        f.blindness = player.isPotionActive(net.minecraft.potion.Potion.BLINDNESS) ? 1f : 0f;
        f.hideGui = gameSettings.hideGUI;
        System.arraycopy(fog, 0, f.fog, 0, 3);
        System.arraycopy(sky.skyColor(), 0, f.sky, 0, 3);
        net.minecraft.client.shaders.Shaders.atlasTexture = atlas.textureId();
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
        float fov = gameSettings.getFov();
        if (!additions.isOn(net.minecraft.client.additions.Additions.STEADY_FOV)) fov *= playerAnimation.fov(partialTicks);
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
        if (additions.isOn(net.minecraft.client.additions.Additions.NO_HURT_TILT)) return;
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
            float turn = (rendererUpdateCount + partialTicks) * (player.isPotionActive(net.minecraft.potion.Potion.CONFUSION) ? 7f : 20f);
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
        if (net.minecraft.client.shaders.Shaders.rendering()) {
            // OptiFine squeezes the hand's depth to an eighth (MC_HAND_DEPTH) so it stays in front of the world.
            float[] projection = new float[16];
            glGetFloatv(GL_PROJECTION_MATRIX, projection);
            glLoadIdentity();
            glScalef(1f, 1f, 0.125f);
            glMultMatrixf(projection);
        }
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
        // With a shader pack the overlays go over the pack's finished picture instead.
        if (!net.minecraft.client.shaders.Shaders.rendering()) renderOverlays();
    }

    /** The underwater tint and the flames of a burning player, over the view (ItemRenderer.renderOverlays). */
    private void renderOverlays() {
        if (isSpectator()) return;
        glDisable(GL_ALPHA_TEST);
        net.minecraft.client.shaders.ShaderPack pack = net.minecraft.client.shaders.Shaders.pack();
        boolean waterOverlay = pack == null || !"false".equals(pack.property("underwaterOverlay"));
        if (waterOverlay && eyesUnderWater()) renderWaterOverlay();
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
        playerModel.riding = ridingBoat != null;
        if (playerModel.riding) limbAmount = 0f;
        playerModel.setPose(new PlayerModel.Pose(limbSwing, limbAmount, age, headYaw, pitch, attackSwing,
            player.sneaking, held != null, held != null && player.isBlocking()));
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        localSkin.bind(guiTextures);
        glColor4f(light[0], light[1], light[2], 1f);
        playerModel.head.showOverlay = gameSettings.isModelPartEnabled("hat");
        playerModel.body.showOverlay = gameSettings.isModelPartEnabled("jacket");
        playerModel.rightArm.showOverlay = gameSettings.isModelPartEnabled("right_sleeve");
        playerModel.leftArm.showOverlay = gameSettings.isModelPartEnabled("left_sleeve");
        playerModel.rightLeg.showOverlay = gameSettings.isModelPartEnabled("right_pants_leg");
        playerModel.leftLeg.showOverlay = gameSettings.isModelPartEnabled("left_pants_leg");
        playerModel.render(player.sneaking);
        if (inventory != null)
            net.minecraft.client.renderer.ArmorLayer.render(playerModel, inventory.armorInventory, player.sneaking, guiTextures, light,
                player.ticksExisted() + partialTicks);
        // LayerCustomHead: a mob head in the helmet slot sits on the head, 1.1875 times its size.
        ItemStack helmet = inventory == null ? null : inventory.armorInventory[3];
        int headType = helmet == null ? -1 : net.minecraft.client.renderer.entity.SkullRenderer.typeOf(helmet.item().name().path());
        if (headType >= 0) {
            glPushMatrix();
            if (player.sneaking) glTranslatef(0f, 0.2f, 0f);
            playerModel.head.transform();
            glScalef(1.1875f, -1.1875f, -1.1875f);
            net.minecraft.client.renderer.entity.SkullRenderer.render(guiTextures, -0.5, 0, -0.5, 1, 180f, headType, light);
            glPopMatrix();
        }
        if (localCape.present() && gameSettings.isModelPartEnabled("cape")) renderCape(bodyYaw, light);
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
     * LayerCape (1.8.9; HYPOTHESIS: recalled maths): the cape hangs from the
     * shoulders, one-eighth of a block behind the body, and swings back by how
     * far the chasing position trails the player: forward movement lifts it
     * (100 degrees per block of lag, never forward of 6 degrees), sideways
     * movement twists it, vertical movement adds up to 32 degrees (-6 when
     * falling behind upward), walking bobs it with the view bob, and sneaking
     * adds 25 degrees. Called in drawPlayer's model space.
     */
    private void renderCape(float bodyYaw, float[] light) {
        PlayerAnimation a = playerAnimation;
        localCape.bind();
        glColor4f(light[0], light[1], light[2], 1f);
        glPushMatrix();
        glTranslatef(0f, 0f, 0.125f);
        double dx = a.prevChasingX + (a.chasingX - a.prevChasingX) * partialTicks - player.x;
        double dy = a.prevChasingY + (a.chasingY - a.prevChasingY) * partialTicks - player.y;
        double dz = a.prevChasingZ + (a.chasingZ - a.prevChasingZ) * partialTicks - player.z;
        double sin = Math.sin(Math.toRadians(bodyYaw)), cos = -Math.cos(Math.toRadians(bodyYaw));
        float lift = Math.max(-6f, Math.min(32f, (float) dy * 10f));
        float back = (float) (dx * sin + dz * cos) * 100f;
        float side = (float) (dx * cos - dz * sin) * 100f;
        if (back < 0f) back = 0f;
        float bob = PlayerAnimation.lerp(a.prevCameraYaw, a.cameraYaw, partialTicks);
        lift += (float) Math.sin(PlayerAnimation.lerp(a.prevDistanceWalked, a.distanceWalked, partialTicks) * 6f) * 32f * bob;
        if (player.sneaking) lift += 25f;
        glRotatef(6f + back / 2f + lift, 1f, 0f, 0f);
        glRotatef(side / 2f, 0f, 0f, 1f);
        glRotatef(-side / 2f, 0f, 1f, 0f);
        glRotatef(180f, 0f, 1f, 0f);
        playerModel.renderCape();
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
        // Packs that draw their own clouds turn the game's off (clouds=off in shaders.properties).
        net.minecraft.client.shaders.ShaderPack pack = net.minecraft.client.shaders.Shaders.pack();
        if (pack != null && "off".equals(pack.property("clouds"))) return;
        net.minecraft.client.shaders.Shaders.use("gbuffers_clouds");
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
     * copies for bigger stacks (2 above 1 item, 3 above 16, 4 above 32, 5 above 48);
     * then the items being picked up, on their way to the player.
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
            renderDroppedItem(item.stack(), x, y, z, item.age + partialTicks, item.hoverStart);
            if (item.isBurning()) renderEntityFire(x, y, z, (float) EntityItem.SIZE, (float) EntityItem.SIZE);
        }
        for (ItemPickup pickup : itemPickups) {
            // EntityPickupFX.renderParticle: from where the item was to half a block above the player's feet, easing in.
            float progress = (pickup.ticks + partialTicks) / ItemPickup.DURATION;
            progress *= progress;
            double x = pickup.x + (player.x - pickup.x) * progress;
            double y = pickup.y + (player.y + 0.5 - pickup.y) * progress;
            double z = pickup.z + (player.z - pickup.z) * progress;
            renderDroppedItem(pickup.stack, x, y, z, pickup.age + partialTicks, pickup.hoverStart);
        }
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
    }

    /** One dropped stack at a point, {@code time} ticks old (bob and spin), under the standard item lighting. */
    private void renderDroppedItem(ItemStack stack, double x, double y, double z, float time, float hoverStart) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        boolean inWorld = by >= 0 && by < World.HEIGHT;
        float[] light = net.minecraft.client.renderer.LightMap.color(inWorld ? world.getSkyLight(bx, by, bz) : 15,
            inWorld ? world.getBlockLight(bx, by, bz) : 0);
        float bob = (float) Math.sin(time / 10.0f + hoverStart) * 0.1f + 0.1f;
        float spin = (float) Math.toDegrees(time / 20.0f + hoverStart);
        int count = stack.count();
        int copies = count > 48 ? 5 : count > 32 ? 4 : count > 16 ? 3 : count > 1 ? 2 : 1;
        boolean block = itemRenderer.isBlockModel(stack.item());
        Random spread = new Random(187L);
        StandardLighting.enable();
        glPushMatrix();
        glTranslated(x, y + bob + 0.25, z);
        glRotatef(spin, 0, 1, 0);
        for (int copy = 0; copy < copies; copy++) {
            glPushMatrix();
            if (copy > 0) {
                if (block) glTranslatef((spread.nextFloat() * 2 - 1) * 0.15f * 0.5f, (spread.nextFloat() * 2 - 1) * 0.15f * 0.5f,
                    (spread.nextFloat() * 2 - 1) * 0.15f * 0.5f);
                // Flat items stack up one behind the other, 0.046875 apart.
                else glTranslatef(0, 0, copy * 0.046875f);
            }
            itemRenderer.renderDropped(stack, light[0], light[1], light[2]);
            glPopMatrix();
        }
        glPopMatrix();
        StandardLighting.disable();
    }

    /**
     * An item the player just picked up, still drawn for three ticks as it
     * slides into them (1.8.9 EntityPickupFX, spawned by onItemPickup).
     */
    private static final class ItemPickup {
        static final int DURATION = 3;
        final ItemStack stack;
        final double x, y, z;
        final int age;
        final float hoverStart;
        int ticks;

        ItemPickup(EntityItem item, ItemStack stack) {
            this.stack = stack;
            this.x = item.x(); this.y = item.y(); this.z = item.z();
            this.age = item.age;
            this.hoverStart = item.hoverStart;
        }
    }

    private final List<ItemPickup> itemPickups = new ArrayList<>();

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

        // GuiIngame.renderGameOverlay: the purple haze is the portal's, not nausea's.
        float portal = PlayerAnimation.lerp(player.prevTimeInPortal, player.timeInPortal, partialTicks);
        if (portal > 0f && !player.isPotionActive(net.minecraft.potion.Potion.CONFUSION)) renderPortalOverlay(portal, guiWidth, guiHeight);

        boolean showDebug = gameSettings.showDebugInfo;
        boolean axisCrosshair = showDebug && !gameSettings.reducedDebugInfo;
        GuiIngame.Status status = new GuiIngame.Status(survivalHud(), isSpectator(), levelInfo.hardcore, player.health,
            player.foodLevel(), inventory.getTotalArmorValue(), player.experienceLevel, player.experience,
            player.headInWater(), player.air, player.hurtResistantTime, player.foodStats.getSaturationLevel(),
            player.absorptionAmount, player.isPotionActive(net.minecraft.potion.Potion.POISON),
            player.isPotionActive(net.minecraft.potion.Potion.WITHER), player.isPotionActive(net.minecraft.potion.Potion.REGENERATION),
            player.isPotionActive(net.minecraft.potion.Potion.HUNGER));
        ingameGui.renderGameOverlay(guiWidth, guiHeight, inventory, status, !axisCrosshair);
        if (axisCrosshair) GuiOverlayDebug.renderAxisCrosshair(guiWidth, guiHeight, player.yaw + 180f, player.pitch);
        if (showDebug) debugOverlay.render(fontRenderer, guiWidth, guiHeight, debugSnapshot(), gameSettings.showLagometer);
        else drawAdditionsHud(guiWidth);
        ingameGui.renderChat(guiHeight, currentScreen instanceof GuiChat);
        glEnable(GL_DEPTH_TEST);
        glColor4f(1, 1, 1, 1);
    }

    /** The Coordinates and FPS Counter additions, in the corners F3 uses (so they hide while it is open). */
    private void drawAdditionsHud(int guiWidth) {
        if (additions.isOn(net.minecraft.client.additions.Additions.COORDINATES)) {
            String[] facings = {"south", "west", "north", "east"};
            // The player's yaw is LegacyCraft's; vanilla's (what F3 shows) is half a turn round from it.
            String facing = facings[Math.floorMod((int) Math.floor((player.yaw + 180f) * 4f / 360f + 0.5f), 4)];
            fontRenderer.drawStringWithShadow(String.format(Locale.ROOT, "XYZ: %.1f / %.1f / %.1f", player.x, player.y, player.z), 2, 2, 0xFFFFFF);
            if (additions.settingOn(net.minecraft.client.additions.Additions.COORDINATES, "showFacing"))
                fontRenderer.drawStringWithShadow("Facing: " + facing, 2, 12, 0xFFFFFF);
        }
        if (additions.isOn(net.minecraft.client.additions.Additions.FPS_COUNTER)) {
            String text = fps + " fps";
            fontRenderer.drawStringWithShadow(text, guiWidth - 2 - fontRenderer.getStringWidth(text), 2, 0xFFFFFF);
        }
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
            if (!gameSettings.keyBindUseItem.isKeyDown()) {
                ItemStack using = player.itemInUse();
                int used = 72000 - player.itemInUseCount();
                player.clearItemInUse();
                if (using != null && using.item().useAction() == Item.UseAction.BOW) releaseBow(using, used);
            }
            return;
        }
        WorldRenderer.Hit hit = target();
        // The arm swings on every attack click, every tick a block is being mined, and on each placement.
        if (attackPressed) playerAnimation.swing();
        if (attackPressed && pointedEntity != null) attackEntity(pointedEntity);
        if (pickPressed && hit != null) {
            BlockState pickedState = world.getState(hit.x(), hit.y(), hit.z());
            Item picked = itemForBlock(pickedState);
            if (pickedState.block().name().path().equals("skull")) {
                World.Skull skull = world.skull(hit.x(), hit.y(), hit.z());
                picked = itemByName(net.minecraft.client.renderer.entity.SkullRenderer.ITEMS[
                    net.minecraft.client.renderer.entity.SkullRenderer.clampType(skull == null ? 0 : skull.type())]);
            }
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
            // Minecraft.rightClickMouse: an entity under the crosshair is interacted with first.
            if (pointedEntity != null) {
                if (interactWithEntity(pointedEntity)) playerAnimation.swing();
                else useHeldItem();
                return;
            }
            if (activateBlock(hit)) playerAnimation.swing();
            else if (useFlintAndSteel(hit)) playerAnimation.swing();
            else if (useSpawnEgg(hit)) playerAnimation.swing();
            else if (useSkull(hit)) playerAnimation.swing();
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
        // Block.harvestBlock: breaking a block the held item can harvest costs 0.025 exhaustion.
        if (!creative && ToolValues.canHarvest(held == null ? null : held.item(), name, block.material())) player.addExhaustion(0.025f);
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
                ItemStack head = name.equals("skull") ? skullDrop(x, y, z, meta) : null;
                if (head != null) spawnItem(EntityItem.fromBlock(world, x, y, z, head, random));
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
        if (name.equals("water_bucket") && world.dimension().waterVaporizes()) {
            // ItemBucket.tryPlaceContainedLiquid: in the Nether the water boils away with a fizz and 8 puffs.
            playSound("random.fizz", x + 0.5, y + 0.5, z + 0.5, 0.5f, 2.6f + (random.nextFloat() - random.nextFloat()) * 0.8f);
            if (particles != null)
                for (int i = 0; i < 8; i++)
                    particles.spawn(ParticleManager.Kind.LARGE_SMOKE, x + Math.random(), y + Math.random(), z + Math.random(), 0, 0, 0, null);
        } else {
            Block liquid = blockRegistry.get(name.equals("water_bucket") ? "minecraft:flowing_water" : "minecraft:flowing_lava");
            world.setStateAndUpdate(x, y, z, BlockState.of(liquid, 0));
        }
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
        if (name.equals("cake")) {
            eatCake(hit.x(), hit.y(), hit.z());
            return true;
        }
        ItemStack held = inventory.getCurrentItem();
        if (name.equals("tnt") && held != null && held.item().name().path().equals("flint_and_steel")) {
            // BlockTNT.onBlockActivated: flint and steel lights the TNT and takes a use.
            primeTnt(hit.x(), hit.y(), hit.z(), playerName, false);
            world.setStateAndUpdate(hit.x(), hit.y(), hit.z(), BlockState.of(blockRegistry.air(), 0));
            if (!isCreative() && net.minecraft.enchantment.EnchantmentHelper.damageItem(held, 1, random)) {
                inventory.mainInventory[inventory.currentItem] = null;
                playSound("random.break", player.x, player.y, player.z, 0.8f, 0.8f + random.nextFloat() * 0.4f);
            }
            return true;
        }
        return false;
    }

    /**
     * BlockCake.eatCake: if the player can eat (hungry, not Creative) a slice
     * gives 2 food and 0.4 saturation and the cake loses a bite; the seventh
     * bite finishes it. Silent, as in vanilla.
     */
    private void eatCake(int x, int y, int z) {
        if (!player.canEat(false)) return;
        player.foodStats.addStats(2, 2 * 0.1f * 2f);
        BlockState state = world.getState(x, y, z);
        int bites = state.metadata();
        if (bites < 6) world.setStateAndUpdate(x, y, z, BlockState.of(state.block(), bites + 1));
        else world.setStateAndUpdate(x, y, z, BlockState.of(blockRegistry.air(), 0));
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
     * out of the portal it fades by 0.05 a tick. Entity.onEntityUpdate and
     * setPortal: after 80 ticks in a portal (1 in Creative) the player goes
     * to the other dimension; arriving starts a 10-tick cooldown that
     * standing in a portal keeps renewing, so they must step out first.
     */
    private void updatePortalTime() {
        player.prevTimeInPortal = player.timeInPortal;
        if (player.inPortal && timeUntilPortal > 0) {
            timeUntilPortal = 10;
            player.inPortal = false;
        }
        boolean travel = false;
        if (player.inPortal) {
            int max = player.disableDamage ? 0 : 80;
            if (portalCounter++ >= max) {
                portalCounter = max;
                timeUntilPortal = 10;
                travel = true;
            }
        } else {
            portalCounter = Math.max(0, portalCounter - 4);
        }
        if (timeUntilPortal > 0) timeUntilPortal--;
        if (player.inPortal) {
            if (currentScreen != null && !currentScreen.doesGuiPauseGame()) displayGuiScreen(null);
            if (player.timeInPortal == 0f && soundHandler != null) soundHandler.playUi("portal.trigger", random.nextFloat() * 0.4f + 0.8f);
            player.timeInPortal = Math.min(1f, player.timeInPortal + 0.0125f);
            player.inPortal = false;
        } else if (player.isPotionActive(net.minecraft.potion.Potion.CONFUSION)
            && player.getActivePotionEffect(net.minecraft.potion.Potion.CONFUSION).duration() > 60) {
            // Nausea warps the view the same way, building up over 150 ticks.
            player.timeInPortal = Math.min(1f, player.timeInPortal + 0.006666667f);
        } else {
            player.timeInPortal = Math.max(0f, player.timeInPortal - 0.05f);
        }
        if (travel && !player.dead)
            changeDimension(dimension == net.minecraft.world.Dimension.NETHER ? net.minecraft.world.Dimension.OVERWORLD
                : net.minecraft.world.Dimension.NETHER, true);
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
        if (held == null) return false;
        Item item = held.item();
        if (item.vanillaId().path().equals("potion") && net.minecraft.item.Potions.isSplash(item.vanillaData())) {
            throwPotion(held);
            return true;
        }
        if (item.kind() == Item.Kind.SPAWN_EGG) return spawnEggOnLiquid(held);
        if (item.name().path().equals("boat")) return placeBoat(held);
        if (item.name().path().equals("glass_bottle")) return fillBottle(held);
        if (item.name().path().equals("egg") || item.name().path().equals("snowball")) {
            throwItem(held);
            return true;
        }
        switch (item.useAction()) {
            case BLOCK -> player.setItemInUse(held, 72000);
            case EAT -> {
                // ItemFood.onItemRightClick: only when hungry (golden apples always), never in Creative; 32 ticks.
                if (!item.isFood()) return false;
                if (player.canEat(item.vanillaId().path().equals("golden_apple"))) player.setItemInUse(held, 32);
            }
            // ItemBucketMilk / ItemPotion.onItemRightClick: drinking always starts, 32 ticks.
            case DRINK -> player.setItemInUse(held, 32);
            case BOW -> {
                // ItemBow.onItemRightClick: drawn only with an arrow to shoot (or in Creative, or with Infinity).
                boolean infinite = isCreative() || net.minecraft.enchantment.EnchantmentHelper.level(
                    net.minecraft.enchantment.Enchantment.INFINITY, held) > 0;
                if (!infinite && !hasItem("arrow")) return false;
                player.setItemInUse(held, 72000);
            }
            default -> { return false; }
        }
        return true;
    }

    /**
     * ItemBow.getModel (1.8.9): a bow being drawn shows pulling_0 from the
     * first tick, pulling_1 after 13 and pulling_2 from 18. The cast rod
     * (fishing_rod_cast) would show while a bobber is out; LegacyCraft has
     * no fishing yet, so it never does.
     */
    private net.minecraft.util.ResourceLocation heldModelState(Item item) {
        if (player == null || !player.isUsingItem() || player.itemInUse().item() != item || !item.name().path().equals("bow")) return null;
        int drawn = 72000 - player.itemInUseCount();
        String stage = drawn >= 18 ? "bow_pulling_2" : drawn > 13 ? "bow_pulling_1" : drawn > 0 ? "bow_pulling_0" : null;
        return stage == null ? null : new net.minecraft.util.ResourceLocation("minecraft", "item/" + stage);
    }

    private boolean hasItem(String name) {
        for (ItemStack stack : inventory.mainInventory)
            if (stack != null && stack.item().name().path().equals(name)) return true;
        return false;
    }

    /**
     * ItemBow.onPlayerStoppedUsing: the draw's power is (t^2 + 2t) / 3 for t
     * seconds drawn, capped at 1 (a full draw is critical); under 0.1
     * nothing happens. The arrow flies at 3 x power, with Power, Punch and
     * Flame applied; the bow wears by 1 and an arrow is used up (not in
     * Creative or with Infinity, when the arrow can't be picked up).
     */
    private void releaseBow(ItemStack bow, int used) {
        int infinity = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.INFINITY, bow);
        boolean infinite = isCreative() || infinity > 0;
        if (!infinite && !hasItem("arrow")) return;
        float power = used / 20f;
        power = (power * power + power * 2f) / 3f;
        if (power < 0.1f) return;
        if (power > 1f) power = 1f;
        net.minecraft.entity.EntityArrow arrow = net.minecraft.entity.EntityArrow.shotBy(world, player, power * 2f, random);
        if (power == 1f) arrow.critical = true;
        int powerLevel = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.POWER, bow);
        if (powerLevel > 0) arrow.damage += powerLevel * 0.5 + 0.5;
        int punch = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.PUNCH, bow);
        if (punch > 0) arrow.knockbackStrength = punch;
        if (net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.FLAME, bow) > 0) arrow.setFire(100);
        if (!isCreative() && net.minecraft.enchantment.EnchantmentHelper.damageItem(bow, 1, random)) {
            for (int i = 0; i < inventory.mainInventory.length; i++) if (inventory.mainInventory[i] == bow) inventory.mainInventory[i] = null;
            playSound("random.break", player.x, player.y, player.z, 0.8f, 0.8f + random.nextFloat() * 0.4f);
        }
        playSound("random.bow", player.x, player.y, player.z, 1f, 1f / (random.nextFloat() * 0.4f + 1.2f) + power * 0.5f);
        if (infinite) {
            arrow.pickup = 2;
        } else {
            for (int i = 0; i < inventory.mainInventory.length; i++) {
                ItemStack stack = inventory.mainInventory[i];
                if (stack != null && stack.item().name().path().equals("arrow")) {
                    stack.split(1);
                    if (stack.isEmpty()) inventory.mainInventory[i] = null;
                    break;
                }
            }
        }
        spawnEntity(arrow);
    }

    /**
     * ItemPotion.onItemRightClick for a splash potion: thrown at once with
     * the bow sound (0.5, pitch 0.4 / 0.8-1.2), used up unless in Creative.
     */
    private void throwPotion(ItemStack held) {
        if (!isCreative()) {
            held.split(1);
            if (held.isEmpty()) inventory.mainInventory[inventory.currentItem] = null;
        }
        playSound("random.bow", player.x, player.y, player.z, 0.5f, 0.4f / (random.nextFloat() * 0.4f + 0.8f));
        spawnEntity(net.minecraft.entity.EntityPotion.thrownBy(world, player, held.item().vanillaData(), random));
    }

    /**
     * ItemEgg / ItemSnowball.onItemRightClick: thrown at once with the bow
     * sound (0.5, pitch 0.4 / 0.8-1.2), used up unless in Creative.
     */
    private void throwItem(ItemStack held) {
        boolean egg = held.item().name().path().equals("egg");
        if (!isCreative()) {
            held.split(1);
            if (held.isEmpty()) inventory.mainInventory[inventory.currentItem] = null;
        }
        playSound("random.bow", player.x, player.y, player.z, 0.5f, 0.4f / (random.nextFloat() * 0.4f + 0.8f));
        net.minecraft.entity.EntityThrowable thrown = egg ? new net.minecraft.entity.EntityEgg(world, player.x, player.y, player.z)
            : new net.minecraft.entity.EntitySnowball(world, player.x, player.y, player.z);
        spawnEntity(thrown.aimFrom(player, random));
    }

    /**
     * ItemMonsterPlacer.onItemUse: a spawn egg on a block hatches its mob
     * standing in the middle of the space against the clicked face (half a
     * block up on top of a fence), facing a random way, and is used up
     * unless in Creative. Eggs of mobs LegacyCraft doesn't have do nothing
     * but still swing. Returns whether a spawn egg was used on a block.
     */
    private boolean useSpawnEgg(WorldRenderer.Hit hit) {
        ItemStack held = inventory.getCurrentItem();
        if (hit == null || held == null || held.item().kind() != Item.Kind.SPAWN_EGG) return false;
        if (levelInfo.gameType == WorldSettings.GameType.ADVENTURE || isSpectator()) return false;
        String clicked = world.get(hit.x(), hit.y(), hit.z()).name().path();
        double up = hit.face() == net.minecraft.util.Direction.UP && clicked.endsWith("fence") ? 0.5 : 0.0;
        hatch(held, hit.placeX() + 0.5, hit.placeY() + up, hit.placeZ() + 0.5);
        return true;
    }

    /**
     * ItemMonsterPlacer.onItemRightClick: aimed at a liquid source within 5
     * blocks (water or lava), a spawn egg hatches its mob in the middle of it.
     */
    private boolean spawnEggOnLiquid(ItemStack held) {
        WorldRenderer.Hit hit = WorldRenderer.raycast(world, player.x, player.eyeY(), player.z,
            player.dirX(), player.dirY(), player.dirZ(), 5.0, true);
        if (hit == null || levelInfo.gameType == WorldSettings.GameType.ADVENTURE) return false;
        if (net.minecraft.block.Liquids.liquid(world.get(hit.x(), hit.y(), hit.z())) == null) return false;
        hatch(held, hit.x() + 0.5, hit.y() + 0.5, hit.z() + 0.5);
        return true;
    }

    /** ItemMonsterPlacer.spawnCreature: the egg's mob at a random yaw, head and body turned the same way. */
    /** The animals world generation placed (SpawnerAnimals.performWorldGenSpawning), created now there is an entity list. */
    private void spawnGeneratedCreatures() {
        if (server == null || world == null) return;
        for (World.CreatureSpawn spawn : world.takeCreatureSpawns()) {
            MovingEntity entity = net.minecraft.entity.EntityList.create(spawn.entity(), world, spawn.x(), spawn.y(), spawn.z());
            if (!(entity instanceof EntityLiving living)) continue;
            living.setPositionAndRotation(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), 0f);
            living.prevRotationYaw = spawn.yaw();
            living.rotationYawHead = living.prevRotationYawHead = living.renderYawOffset = living.prevRenderYawOffset = spawn.yaw();
            living.onInitialSpawn();
            spawnEntity(living);
        }
    }

    private void hatch(ItemStack held, double x, double y, double z) {
        net.minecraft.item.VanillaItems.Egg egg = net.minecraft.item.VanillaItems.egg(held.item().vanillaData());
        MovingEntity entity = egg == null ? null : net.minecraft.entity.EntityList.create(egg.entity(), world, x, y, z);
        if (entity == null) return;
        float yaw = wrap180(random.nextFloat() * 360f);
        entity.setPositionAndRotation(x, y, z, yaw, 0f);
        entity.prevRotationYaw = yaw;
        if (entity instanceof EntityLiving living) {
            living.rotationYawHead = living.prevRotationYawHead = yaw;
            living.renderYawOffset = living.prevRenderYawOffset = yaw;
            living.persistenceRequired = false;
            living.onInitialSpawn();
        }
        spawnEntity(entity);
        if (entity instanceof EntityLiving living) living.playLivingSound();
        if (!isCreative()) {
            held.split(1);
            if (held.isEmpty()) inventory.mainInventory[inventory.currentItem] = null;
        }
    }

    private static float wrap180(float angle) {
        angle %= 360f;
        if (angle >= 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    /**
     * ItemGlassBottle.onItemRightClick: aimed at a water source within 5
     * blocks, one bottle becomes a water bottle (even in Creative); the
     * water stays.
     */
    private boolean fillBottle(ItemStack held) {
        WorldRenderer.Hit hit = WorldRenderer.raycast(world, player.x, player.eyeY(), player.z,
            player.dirX(), player.dirY(), player.dirZ(), 5.0, true);
        if (hit == null || levelInfo.gameType == WorldSettings.GameType.ADVENTURE) return false;
        if (!world.get(hit.x(), hit.y(), hit.z()).material().equals("water")) return false;
        Item water = itemRegistry.potion(0);
        if (water == null) return false;
        held.split(1);
        if (held.isEmpty()) {
            inventory.mainInventory[inventory.currentItem] = new ItemStack(water);
        } else if (!inventory.addItemStackToInventory(new ItemStack(water))) {
            dropPlayerItem(new ItemStack(water));
        }
        return true;
    }

    /**
     * World.playAuxSFX 2002 (RenderGlobal): eight glass crumbs and a ring of
     * 100 spell swirls (instant ones for Healing and Harming) in the
     * potion's colour from the block corner, then the smash (pitch 0.9-1.0).
     */
    private void potionSmash(int bx, int by, int bz, int data) {
        if (particles != null) {
            net.minecraft.util.ResourceLocation crumb = itemRenderer == null ? null : itemRenderer.particleTexture(itemRegistry.potion(data));
            for (int i = 0; i < 8; i++)
                particles.spawnItemCrack(crumb, bx, by, bz, random.nextGaussian() * 0.15, random.nextDouble() * 0.2, random.nextGaussian() * 0.15);
            int color = net.minecraft.item.Potions.color(data);
            float red = (color >> 16 & 255) / 255f, green = (color >> 8 & 255) / 255f, blue = (color & 255) / 255f;
            boolean instant = net.minecraft.item.Potions.isInstant(data);
            for (int i = 0; i < 100; i++) {
                double spread = random.nextDouble() * 4.0;
                double angle = random.nextDouble() * Math.PI * 2.0;
                double vx = Math.cos(angle) * spread, vy = 0.01 + random.nextDouble() * 0.5, vz = Math.sin(angle) * spread;
                float shade = 0.75f + random.nextFloat() * 0.25f;
                var spell = particles.spawnSpell(instant, bx + vx * 0.1, by + 0.3, bz + vz * 0.1, vx, vy, vz,
                    red * shade, green * shade, blue * shade);
                if (spell != null) spell.multiplyVelocity((float) spread);
            }
        }
        playSound("game.potion.smash", bx + 0.5, by + 0.5, bz + 0.5, 1f, random.nextFloat() * 0.1f + 0.9f);
    }

    /**
     * EntityPlayer.updateItemUse: drinking plays the gulp (0.5, pitch
     * 0.9-1.0); eating throws {@code count} crumbs of the item from in front
     * of the mouth and plays the munch (volume 0.5 or 1, pitch 0.8-1.2).
     */
    private void itemUseEffects(ItemStack stack, int count) {
        Item.UseAction action = stack.item().useAction();
        if (action == Item.UseAction.DRINK)
            player.sounds.play("random.drink", 0.5f, random.nextFloat() * 0.1f + 0.9f);
        if (action != Item.UseAction.EAT) return;
        if (particles != null) {
            net.minecraft.util.ResourceLocation texture = itemRenderer == null ? null : itemRenderer.particleTexture(stack.item());
            double yaw = -Math.toRadians(player.yaw + 180f), pitch = -Math.toRadians(player.pitch);
            for (int i = 0; i < count; i++) {
                double[] velocity = rotate((random.nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0, pitch, yaw);
                double[] offset = rotate((random.nextFloat() - 0.5) * 0.3, -random.nextFloat() * 0.6 - 0.3, 0.6, pitch, yaw);
                particles.spawnItemCrack(texture, player.x + offset[0], player.eyeY() + offset[1], player.z + offset[2],
                    velocity[0], velocity[1] + 0.05, velocity[2]);
            }
        }
        player.sounds.play("random.eat", 0.5f + 0.5f * random.nextInt(2), (random.nextFloat() - random.nextFloat()) * 0.2f + 1f);
    }

    /** Vec3.rotatePitch then Vec3.rotateYaw. */
    private static double[] rotate(double x, double y, double z, double pitch, double yaw) {
        double cos = Math.cos(pitch), sin = Math.sin(pitch);
        double y1 = y * cos + z * sin, z1 = z * cos - y * sin;
        cos = Math.cos(yaw);
        sin = Math.sin(yaw);
        return new double[] {x * cos + z1 * sin, y1, z1 * cos - x * sin};
    }

    /**
     * EntityPlayer.onItemUseFinish and the items' onItemUseFinish: food is
     * used up (not in Creative), fills the bar, burps (0.5, pitch 0.9-1.0)
     * and applies its effects; stew leaves its bowl. Milk clears every effect
     * and leaves the bucket; a bottle leaves an empty glass bottle.
     */
    private void finishItemUse(ItemStack stack) {
        if (inventory.getCurrentItem() != stack) return;
        Item item = stack.item();
        String name = item.name().path();
        boolean creative = isCreative();
        ItemStack result = stack;
        int before = stack.count();
        if (item.useAction() == Item.UseAction.EAT && item.isFood()) {
            if (!creative) stack.split(1);
            player.foodStats.addStats(item.food(), item.saturation());
            player.sounds.play("random.burp", 0.5f, random.nextFloat() * 0.1f + 0.9f);
            applyFoodEffects(name);
            if (name.equals("mushroom_stew") || name.equals("rabbit_stew")) result = itemStack("bowl");
        } else if (name.equals("milk_bucket")) {
            if (!creative) stack.split(1);
            player.clearActivePotions();
            if (stack.isEmpty()) result = itemStack("bucket");
        } else if (item.vanillaId().path().equals("potion")) {
            // ItemPotion.onItemUseFinish: the effects (Healing and Harming at once), then the empty bottle.
            for (net.minecraft.potion.PotionEffect effect : net.minecraft.item.Potions.effects(item.vanillaData())) {
                if (effect.potion().isInstant())
                    net.minecraft.entity.EntityPotion.instant(player, effect.potion(), effect.amplifier(), 1.0, null);
                else player.addPotionEffect(effect);
            }
            if (!creative) {
                stack.split(1);
                if (stack.isEmpty()) result = itemStack("glass_bottle");
                else {
                    ItemStack bottle = itemStack("glass_bottle");
                    if (bottle != null) inventory.addItemStackToInventory(bottle);
                }
            }
        } else {
            return;
        }
        if (result != stack || result != null && result.count() != before)
            inventory.mainInventory[inventory.currentItem] = result == null || result.isEmpty() ? null : result;
    }

    private ItemStack itemStack(String name) {
        Item item = itemByName(name);
        return item == null ? null : new ItemStack(item);
    }

    /**
     * ItemFood.onFoodEaten (HYPOTHESIS: recalled table): golden apple
     * Absorption 2:00 and Regeneration II 0:05; raw chicken 30% and rotten
     * flesh 80% Hunger 0:30; spider eye Poison 0:05; poisonous potato 60%
     * Poison 0:05; pufferfish Poison IV 1:00, Hunger III 0:15, Nausea II 0:15.
     */
    private void applyFoodEffects(String name) {
        switch (name) {
            case "enchanted_golden_apple" -> {
                // ItemAppleGold.onFoodEaten with data 1: the Notch apple.
                player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.ABSORPTION, 2400, 0));
                player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.REGENERATION, 600, 4));
                player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.RESISTANCE, 6000, 0));
                player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.FIRE_RESISTANCE, 6000, 0));
            }
            case "golden_apple" -> {
                player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.ABSORPTION, 2400, 0));
                player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.REGENERATION, 100, 1));
            }
            case "chicken" -> foodEffect(net.minecraft.potion.Potion.HUNGER, 600, 0, 0.3f);
            case "rotten_flesh" -> foodEffect(net.minecraft.potion.Potion.HUNGER, 600, 0, 0.8f);
            case "spider_eye" -> foodEffect(net.minecraft.potion.Potion.POISON, 100, 0, 1f);
            case "poisonous_potato" -> foodEffect(net.minecraft.potion.Potion.POISON, 100, 0, 0.6f);
            case "pufferfish" -> {
                player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.POISON, 1200, 3));
                player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.HUNGER, 300, 2));
                player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.CONFUSION, 300, 1));
            }
            default -> { }
        }
    }

    private void foodEffect(net.minecraft.potion.Potion potion, int duration, int amplifier, float chance) {
        if (random.nextFloat() < chance) player.addPotionEffect(new net.minecraft.potion.PotionEffect(potion, duration, amplifier));
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

    /**
     * The player's tick: on foot as usual, or carried in a boat, steering it
     * with forward/back and the way they look; sneak climbs out on top of it.
     */
    private void tickPlayer() {
        Player.Input input = Player.Input.of(gameSettings);
        Player.Input toggled = toggleKeys.apply(input, gameSettings.keyBindSprint, gameSettings.keyBindSneak,
            additions.isOn(net.minecraft.client.additions.Additions.TOGGLE_SPRINT), additions.isOn(net.minecraft.client.additions.Additions.TOGGLE_SNEAK));
        net.minecraft.entity.EntityBoat boat = ridingBoat;
        // Only a real press of Sneak climbs out of a boat, not a latched Toggle Sneak.
        if (boat != null && (boat.isRemoved() || player.dead || input.sneak())) {
            dismountBoat();
            boat = null;
        }
        if (boat == null) {
            player.tick(toggled);
            return;
        }
        float forward = (input.forward() ? 1f : 0f) - (input.back() ? 1f : 0f);
        boat.setRiderInput(forward, PlayerAnimation.vanillaYaw(player));
        player.tick(Player.Input.NONE);
        player.rideAt(boat.prevX, boat.prevY - 0.3, boat.prevZ, boat.x(), boat.riderY(), boat.z());
    }

    /** Climbs out on top of the boat (Entity.dismountEntity), or just lets go of it if it is gone. */
    private void dismountBoat() {
        net.minecraft.entity.EntityBoat boat = ridingBoat;
        ridingBoat = null;
        if (boat == null) return;
        boat.ridden = false;
        boat.setRiderInput(0f, 0f);
        if (!boat.isRemoved()) {
            double top = boat.y() + boat.height + 0.01;
            player.rideAt(player.x, player.y, player.z, boat.x(), top, boat.z());
        }
    }

    /**
     * ItemBoat.onItemRightClick: a boat goes on the water or block under the
     * crosshair (within 5 blocks, turned the way the player faces) if there
     * is room for it; used up out of Creative.
     */
    private boolean placeBoat(ItemStack held) {
        if (isSpectator() || levelInfo.gameType == WorldSettings.GameType.ADVENTURE) return false;
        WorldRenderer.Hit hit = WorldRenderer.raycast(world, player.x, player.eyeY(), player.z,
            player.dirX(), player.dirY(), player.dirZ(), 5.0, true);
        if (hit == null) return false;
        boolean water = world.get(hit.x(), hit.y(), hit.z()).material().equals("water");
        if (!water && hit.face() != net.minecraft.util.Direction.UP) return false;
        net.minecraft.entity.EntityBoat boat = new net.minecraft.entity.EntityBoat(world, hit.x() + 0.5, hit.y() + 1.0, hit.z() + 0.5);
        float yaw = PlayerAnimation.vanillaYaw(player);
        boat.setPositionAndRotation(boat.x(), boat.y(), boat.z(), yaw, 0f);
        if (!boat.isFreeOfBlocks(boat.boundingBox().grow(-0.1))) return false;
        spawnEntity(boat);
        if (!isCreative()) {
            held.split(1);
            if (held.isEmpty()) inventory.mainInventory[inventory.currentItem] = null;
        }
        return true;
    }

    /** Boats near the player, solid like blocks (all but the one the player sits in). */
    private java.util.List<AxisAlignedBB> boatBoxes(AxisAlignedBB area) {
        if (server == null) return java.util.List.of();
        java.util.List<AxisAlignedBB> boxes = new java.util.ArrayList<>();
        for (Entity entity : server.entities().values())
            if (entity instanceof net.minecraft.entity.EntityBoat boat && boat != ridingBoat && !boat.isRemoved()
                && boat.boundingBox().intersects(area)) boxes.add(boat.boundingBox());
        return boxes;
    }

    /** World.spawnEntityInWorld. */
    private void spawnEntity(Entity entity) {
        if (server == null) return;
        if (entity instanceof MovingEntity moving) moving.setHost(entityHost);
        server.entities().add(entity);
    }

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
                String placed = first.state().block().name().path();
                if (placed.equals("pumpkin") || placed.equals("lit_pumpkin")) checkGolemSpawn(first.x(), first.y(), first.z());
                return true;
            }
        }
        return false;
    }

    /** BlockPumpkin.onBlockAdded: a pumpkin topping a T of iron blocks becomes an iron golem. */
    private void checkGolemSpawn(int x, int y, int z) {
        if (server == null) return;
        net.minecraft.world.GolemPattern.Match match = net.minecraft.world.GolemPattern.find(world, x, y, z);
        if (match == null) return;
        Block air = blockRegistry.air();
        for (int[] cell : match.cells()) {
            BlockState was = world.getState(cell[0], cell[1], cell[2]);
            world.setState(cell[0], cell[1], cell[2], BlockState.of(air, 0));
            if (particles != null) particles.addBlockDestroyEffects(cell[0], cell[1], cell[2], was);
        }
        for (int[] cell : match.cells()) BlockUpdates.afterChange(world, cell[0], cell[1], cell[2]);
        int[] feet = match.feet();
        float yaw = match.alongX() ? 0f : 90f;
        net.minecraft.entity.passive.EntityIronGolem golem = new net.minecraft.entity.passive.EntityIronGolem(world,
            feet[0] + 0.5, feet[1] + 0.05, feet[2] + 0.5);
        golem.setPositionAndRotation(feet[0] + 0.5, feet[1] + 0.05, feet[2] + 0.5, yaw, 0f);
        golem.renderYawOffset = golem.prevRenderYawOffset = golem.rotationYawHead = golem.prevRotationYawHead = yaw;
        spawnEntity(golem);
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
        @Override public String give(String id, int count, int data) {
            Item item = itemRegistry.resolve(id, data);
            if (item == null && data != 0) item = itemRegistry.resolve(id, 0);
            if (item == null) return null;
            ItemStack stack = new ItemStack(item, count, item.isDamageable() ? Math.max(0, data) : 0);
            String name = stackName(stack);
            int before = stack.count();
            inventory.addItemStackToInventory(stack);
            if (stack.count() < before)
                playSound("random.pop", player.x, player.y, player.z, 0.2f, ((random.nextFloat() - random.nextFloat()) * 0.7f + 1f) * 2f);
            if (!stack.isEmpty()) {
                // CommandGive: what doesn't fit is dropped, ready to be picked up at once.
                EntityItem drop = EntityItem.thrown(world, player.x, player.eyeY(), player.z, player.dirX(), player.dirY(), player.dirZ(),
                    stack, false, random);
                drop.pickupDelay = 0;
                spawnItem(drop);
            }
            return name;
        }
        @Override public boolean summon(String id, double x, double y, double z) {
            MovingEntity entity = net.minecraft.entity.EntityList.create(id, world, x, y, z);
            if (entity == null) return false;
            entity.setPositionAndRotation(x, y, z, entity.rotationYaw, entity.rotationPitch);
            if (entity instanceof EntityTNTPrimed || entity instanceof EntityXPOrb) entity.motionX = entity.motionY = entity.motionZ = 0;
            if (entity instanceof EntityXPOrb orb) orb.xpValue = 0;
            if (entity instanceof EntityLiving living) living.onInitialSpawn();
            spawnEntity(entity);
            return true;
        }
        @Override public void addEffect(int id, int ticks, int amplifier, boolean showParticles) {
            net.minecraft.potion.Potion potion = net.minecraft.potion.Potion.byId(id);
            if (potion == null) return;
            if (potion == net.minecraft.potion.Potion.HEAL) player.heal(Math.max(4 << amplifier, 0));
            else if (potion == net.minecraft.potion.Potion.HARM) player.attack(net.minecraft.entity.DamageSource.MAGIC, 6 << amplifier);
            else if (potion == net.minecraft.potion.Potion.SATURATION) player.foodStats.addStats(amplifier + 1, (amplifier + 1) * 2f);
            else player.addPotionEffect(new net.minecraft.potion.PotionEffect(potion, ticks, amplifier, false, showParticles));
        }
        @Override public boolean removeEffect(int id) {
            if (id < 0) {
                boolean any = !player.getActivePotionEffects().isEmpty();
                player.clearActivePotions();
                return any;
            }
            net.minecraft.potion.Potion potion = net.minecraft.potion.Potion.byId(id);
            return potion != null && player.removePotionEffect(potion);
        }
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
    @Override public java.util.Collection<net.minecraft.potion.PotionEffect> activePotionEffects() {
        return player == null ? List.of() : player.getActivePotionEffects();
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
    @Override public net.minecraft.client.additions.Additions additions() { return additions; }
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

    @Override public net.minecraft.client.resources.ResourcePackRepository resourcePacks() { return resourceManager; }

    private static final String MOJANG_LOGO = "/assets/minecraft/textures/gui/title/mojang.png";

    /**
     * The Mojang screen shown while the game starts and while resources reload
     * (Minecraft.drawSplashScreen): the window filled with the logo texture's
     * white corner and the 256x256 logo centred in GUI coordinates, read from
     * the built-in default pack so a resource pack can't replace it. One frame
     * is presented; the loading work that follows keeps it on screen.
     */
    private void drawSplashScreen() {
        int[] fbWidth = new int[1], fbHeight = new int[1];
        glfwGetFramebufferSize(window, fbWidth, fbHeight);
        int w = Math.max(1, fbWidth[0]), h = Math.max(1, fbHeight[0]);
        java.awt.image.BufferedImage logo;
        try (java.io.InputStream stream = Minecraft.class.getResourceAsStream(MOJANG_LOGO)) {
            logo = stream == null ? null : javax.imageio.ImageIO.read(stream);
        } catch (IOException error) {
            logo = null;
        }
        ScaledResolution resolution = new ScaledResolution(w, h, gameSettings.getGuiScale(), gameSettings.isUnicodeFont());
        glViewport(0, 0, w, h);
        glClearColor(1f, 1f, 1f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, resolution.getScaledWidth(), resolution.getScaledHeight(), 0, 1000, 3000);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glTranslatef(0f, 0f, -2000f);
        glDisable(GL_LIGHTING);
        glDisable(GL_FOG);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_CULL_FACE);
        if (logo != null) {
            int texture = net.minecraft.client.gui.GuiTextures.upload(logo, false);
            float sw = resolution.getScaledWidth(), sh = resolution.getScaledHeight();
            int x = (resolution.getScaledWidth() - 256) / 2, y = (resolution.getScaledHeight() - 256) / 2;
            glEnable(GL_TEXTURE_2D);
            glColor4f(1f, 1f, 1f, 1f);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(0, sh);
            glTexCoord2f(0, 0); glVertex2f(sw, sh);
            glTexCoord2f(0, 0); glVertex2f(sw, 0);
            glTexCoord2f(0, 0); glVertex2f(0, 0);
            glTexCoord2f(0, 1); glVertex2f(x, y + 256);
            glTexCoord2f(1, 1); glVertex2f(x + 256, y + 256);
            glTexCoord2f(1, 0); glVertex2f(x + 256, y);
            glTexCoord2f(0, 0); glVertex2f(x, y);
            glEnd();
            glDeleteTextures(texture);
        }
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        // No glfwPollEvents here: a reload can start from inside an input callback (the Done button).
        glfwSwapBuffers(window);
    }

    /**
     * Opens the packs listed in options.txt (lowest priority first), dropping
     * ones that are gone and incompatible ones the player never confirmed
     * (1.8.9 keeps those consents in incompatibleResourcePacks).
     */
    private void applySelectedResourcePacks() {
        java.util.Map<String, net.minecraft.client.resources.ResourcePackRepository.Entry> found = new java.util.HashMap<>();
        for (var entry : resourceManager.scan()) found.put(entry.name, entry);
        List<String> wanted = new ArrayList<>();
        for (String name : gameSettings.resourcePacks) {
            var entry = found.get(name);
            if (entry == null || wanted.contains(name)) continue;
            if (entry.compatible()) gameSettings.incompatibleResourcePacks.remove(name);
            else if (!gameSettings.incompatibleResourcePacks.contains(name)) continue;
            wanted.add(name);
        }
        List<String> opened = resourceManager.apply(wanted);
        if (!opened.equals(gameSettings.resourcePacks)) {
            gameSettings.resourcePacks = new ArrayList<>(opened);
            if (window != NULL) saveOptions();
        }
    }

    /**
     * Reloads everything that comes from resources (Minecraft.refreshResources,
     * F3+T, closing the resource pack screen, a new mipmap level): language,
     * sounds, block models, the block atlas and its mipmaps, fonts, GUI and
     * item textures and colour maps; chunk meshes are rebuilt with the new UVs.
     */
    @Override public void refreshResources() {
        drawSplashScreen();
        applySelectedResourcePacks();
        if (soundHandler != null) soundHandler.stopAll();
        soundSystem.flushCache();
        I18n.load(resourceManager, gameSettings.language);
        soundRegistry = SoundRegistry.load(resourceManager);
        soundHandler = new net.minecraft.client.audio.SoundHandler(soundRegistry, gameSettings, soundSystem);
        musicTicker = new net.minecraft.client.audio.MusicTicker(soundHandler, new Random());
        modelManager.reload();
        atlas.setMipmapLevels(gameSettings.mipmapLevels);
        atlas.reload();
        if (galacticFont != null && galacticFont != fontRenderer) galacticFont.destroy();
        galacticFont = null;
        fontRenderer.destroy();
        fontRenderer = new FontRenderer(resourceManager);
        fontRenderer.load();
        guiTextures.setSpriteMipmapLevels(atlas.mipmapLevels());
        guiTextures.reload();
        biomeColors.reload(resourceManager);
        itemRenderer = new ItemRenderer(resourceManager, guiTextures, atlas, modelManager, biomeColors);
        itemRenderer.modelState = this::heldModelState;
        itemRenderer.builtin = builtinItems;
        mobRenderer = new net.minecraft.client.renderer.entity.MobRenderer(guiTextures, itemRenderer);
        firstPersonRenderer = new FirstPersonRenderer(guiTextures, itemRenderer, localSkin, launcherSlim);
        if (renderer != null) renderer.markAllDirty();
        if (particles != null) particles.clear();
        if (currentScreen != null) currentScreen.setWorldAndResolution(this, lastScaledWidth, lastScaledHeight);
    }

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
            } else if (arg.startsWith("--skin=")) {
                game.launcherSkin = Path.of(arg.substring(7));
            } else if (arg.startsWith("--skinModel=")) {
                game.launcherSlim = arg.substring(12).equalsIgnoreCase("slim");
            } else if (arg.startsWith("--cape=")) {
                game.launcherCape = Path.of(arg.substring(7));
            } else if (arg.startsWith("--gamedir=")) {
                game.gameDirectory = Path.of(arg.substring(10));
            }
        }
        game.run();
    }
}

package net.minecraft.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.client.entity.Player;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.block.model.BakedModel;
import net.minecraft.client.renderer.block.model.BlockModelManager;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class Minecraft {
    private static final int INITIAL_WIDTH = 1280;
    private static final int INITIAL_HEIGHT = 720;
    private long window;
    private World world;
    private Player player;
    private TextureAtlas atlas;
    private FontRenderer fontRenderer;
    private WorldRenderer renderer;
    private BlockRegistry blockRegistry;
    private BlockModelManager modelManager;
    private Block[] hotbar;
    private int width = INITIAL_WIDTH, height = INITIAL_HEIGHT;
    private int selectedSlot;
    private final GameSettings gameSettings = new GameSettings();
    private final IResourceManager resourceManager = new ClasspathResourceManager();
    private boolean mouseCaptured = true;
    private boolean firstMouse = true;
    private boolean breakPressed, placePressed;
    private boolean showDebug;
    private double lastMouseX, lastMouseY;
    private double titleTimer;
    private String screenshotRequested;
    private boolean quitAfterScreenshot;

    public void run() {
        try {
            init();
            loop();
        } finally {
            if (renderer != null) renderer.destroy();
            if (fontRenderer != null) fontRenderer.destroy();
            if (atlas != null) atlas.destroy();
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
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DEPTH_BITS, 24);

        window = glfwCreateWindow(width, height, "LegacyCraft 1.8.9", NULL, NULL);
        if (window == NULL) throw new IllegalStateException("Failed to create the GLFW window");
        glfwSetWindowPos(window, 90, 70);
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        glfwSetFramebufferSizeCallback(window, (ignored, w, h) -> {
            width = Math.max(1, w); height = Math.max(1, h);
            glViewport(0, 0, width, height);
        });
        glfwSetCursorPosCallback(window, (ignored, mx, my) -> {
            if (!mouseCaptured || player == null) return;
            if (firstMouse) { lastMouseX = mx; lastMouseY = my; firstMouse = false; return; }
            player.turn(mx - lastMouseX, my - lastMouseY);
            lastMouseX = mx; lastMouseY = my;
        });
        glfwSetMouseButtonCallback(window, (ignored, button, action, mods) -> {
            if (action != GLFW_PRESS) return;
            if (!mouseCaptured) { captureMouse(); return; }
            if (button == GLFW_MOUSE_BUTTON_LEFT) breakPressed = true;
            if (button == GLFW_MOUSE_BUTTON_RIGHT) placePressed = true;
        });
        glfwSetScrollCallback(window, (ignored, dx, dy) -> {
            if (dy > 0) selectedSlot = (selectedSlot + 8) % 9;
            if (dy < 0) selectedSlot = (selectedSlot + 1) % 9;
        });
        glfwSetKeyCallback(window, (ignored, key, scancode, action, mods) -> {
            if (action != GLFW_PRESS) return;
            if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) selectedSlot = key - GLFW_KEY_1;
            if (key == GLFW_KEY_F) gameSettings.cycleRenderDistance();
            if (key == GLFW_KEY_F3) showDebug = !showDebug;
            if (key == GLFW_KEY_F2) screenshotRequested = "screenshots/LegacyCraft-" + System.currentTimeMillis() + ".png";
            if (key == GLFW_KEY_ESCAPE) {
                if (mouseCaptured) releaseMouse(); else glfwSetWindowShouldClose(window, true);
            }
        });

        blockRegistry = BlockRegistry.load(resourceManager);
        modelManager = new BlockModelManager(resourceManager, blockRegistry);
        hotbar = new Block[]{
            blockRegistry.get("minecraft:grass"), blockRegistry.get("minecraft:cobblestone"),
            blockRegistry.get("minecraft:oak_planks"), blockRegistry.get("minecraft:brick_block"),
            blockRegistry.get("minecraft:glass"), blockRegistry.get("minecraft:oak_log"),
            blockRegistry.get("minecraft:sand"), blockRegistry.get("minecraft:crafting_table"),
            blockRegistry.get("minecraft:diamond_ore")
        };
        atlas = new TextureAtlas(resourceManager);
        atlas.load();
        fontRenderer = new FontRenderer(resourceManager);
        fontRenderer.load();
        world = new World(blockRegistry, 889L);
        player = new Player(world);
        renderer = new WorldRenderer(world, atlas, modelManager);

        glClearColor(0.50f, 0.69f, 1.0f, 1.0f);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_FOG);
        glFogi(GL_FOG_MODE, GL_LINEAR);
        glHint(GL_FOG_HINT, GL_FASTEST);
        glFogfv(GL_FOG_COLOR, new float[]{0.50f, 0.69f, 1.0f, 1.0f});

        captureMouse();
        glfwShowWindow(window);
    }

    private void loop() {
        double previous = glfwGetTime();
        double accumulator = 0;
        final double tick = 1.0 / 120.0;
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            double frame = Math.min(0.1, now - previous);
            previous = now;
            accumulator += frame;
            glfwPollEvents();

            while (accumulator >= tick) {
                if (mouseCaptured) player.update(window, tick);
                accumulator -= tick;
            }
            interact();
            render(now);
            if (screenshotRequested != null) {
                saveScreenshot(screenshotRequested);
                screenshotRequested = null;
                if (quitAfterScreenshot) glfwSetWindowShouldClose(window, true);
            }
            glfwSwapBuffers(window);

            titleTimer += frame;
            if (titleTimer >= 0.25) {
                titleTimer = 0;
                glfwSetWindowTitle(window, String.format(Locale.ROOT,
                    "LegacyCraft 1.8.9  |  XYZ %.1f / %.1f / %.1f  |  %s",
                    player.x, player.y, player.z, hotbar[selectedSlot].name().path().replace('_', ' ')));
            }
        }
    }

    private void interact() {
        if (!mouseCaptured) { breakPressed = placePressed = false; return; }
        WorldRenderer.Hit hit = renderer.raycast(player, 5.0);
        if (breakPressed && hit != null) {
            Block block = world.get(hit.x(), hit.y(), hit.z());
            if (block.hardness() >= 0) {
                world.set(hit.x(), hit.y(), hit.z(), blockRegistry.air());
                renderer.changed(hit.x(), hit.z());
            }
        }
        if (placePressed && hit != null && world.inBounds(hit.placeX(), hit.placeY(), hit.placeZ())
            && world.get(hit.placeX(), hit.placeY(), hit.placeZ()).isAir()
            && !player.intersectsBlock(hit.placeX(), hit.placeY(), hit.placeZ())) {
            world.set(hit.placeX(), hit.placeY(), hit.placeZ(), hotbar[selectedSlot]);
            renderer.changed(hit.placeX(), hit.placeZ());
        }
        breakPressed = placePressed = false;
    }

    private void render(double time) {
        int renderDistance = gameSettings.getRenderDistanceBlocks();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glFogf(GL_FOG_START, renderDistance * 0.60f);
        glFogf(GL_FOG_END, renderDistance);
        perspective(gameSettings.getFov(), width / (double) height, 0.05, renderDistance + 18);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glRotatef(player.pitch, 1, 0, 0);
        glRotatef(player.yaw, 0, 1, 0);
        glTranslated(-player.x, -player.eyeY(), -player.z);

        drawCelestial(time);
        renderer.render(player, renderDistance);
        WorldRenderer.Hit hit = renderer.raycast(player, 5.0);
        if (hit != null) drawSelection(hit);
        drawHud();
    }

    private void perspective(double fov, double aspect, double near, double far) {
        double top = near * Math.tan(Math.toRadians(fov) * 0.5);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glFrustum(-top * aspect, top * aspect, -top, top, near, far);
    }

    private void drawCelestial(double time) {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_FOG);
        glDepthMask(false);
        glColor4f(1f, .97f, .72f, 1f);
        double sx = player.x - 35, sy = player.eyeY() + 36, sz = player.z - 55;
        glBegin(GL_QUADS);
        glVertex3d(sx - 4, sy - 4, sz); glVertex3d(sx + 4, sy - 4, sz);
        glVertex3d(sx + 4, sy + 4, sz); glVertex3d(sx - 4, sy + 4, sz);
        glEnd();

        glColor4f(1, 1, 1, .78f);
        double drift = (time * 0.7) % 32;
        glBegin(GL_QUADS);
        for (int z = -96; z <= 96; z += 16) {
            for (int x = -96; x <= 96; x += 16) {
                int pattern = ((x / 16) * 31 + (z / 16) * 17) & 7;
                if (pattern > 3) continue;
                double cx = player.x + x + drift, cz = player.z + z;
                glVertex3d(cx, 57, cz); glVertex3d(cx + 14, 57, cz);
                glVertex3d(cx + 14, 57, cz + 10); glVertex3d(cx, 57, cz + 10);
            }
        }
        glEnd();
        glDepthMask(true);
        glEnable(GL_FOG);
    }

    private void drawSelection(WorldRenderer.Hit hit) {
        double e = 0.002;
        double x0 = hit.x() - e, y0 = hit.y() - e, z0 = hit.z() - e;
        double x1 = hit.x() + 1 + e, y1 = hit.y() + 1 + e, z1 = hit.z() + 1 + e;
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_FOG);
        glColor4f(0, 0, 0, .75f);
        glLineWidth(2f);
        glBegin(GL_LINES);
        line(x0,y0,z0,x1,y0,z0); line(x1,y0,z0,x1,y0,z1); line(x1,y0,z1,x0,y0,z1); line(x0,y0,z1,x0,y0,z0);
        line(x0,y1,z0,x1,y1,z0); line(x1,y1,z0,x1,y1,z1); line(x1,y1,z1,x0,y1,z1); line(x0,y1,z1,x0,y1,z0);
        line(x0,y0,z0,x0,y1,z0); line(x1,y0,z0,x1,y1,z0); line(x1,y0,z1,x1,y1,z1); line(x0,y0,z1,x0,y1,z1);
        glEnd();
        glLineWidth(1f);
        glEnable(GL_FOG);
    }

    private void line(double ax,double ay,double az,double bx,double by,double bz) {
        glVertex3d(ax,ay,az); glVertex3d(bx,by,bz);
    }

    private void drawHud() {
        ScaledResolution resolution = new ScaledResolution(width, height,
            gameSettings.getGuiScale(), gameSettings.isUnicodeFont());
        int guiWidth = resolution.getScaledWidth();
        int guiHeight = resolution.getScaledHeight();
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_FOG);
        glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(0, guiWidth, guiHeight, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity();

        float slot = 20, barW = 182;
        float left = (guiWidth - barW) * 0.5f, top = guiHeight - 22;
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
        atlas.bindWidgets();
        drawGuiRegion(0, 0, 182, 22, left, top, barW, 22);
        drawGuiRegion(0, 22, 24, 24,
            left - 1 + selectedSlot * slot, top - 1, 24, 24);
        for (int i = 0; i < 9; i++) {
            float x = left + 3 + i * 20;
            drawHotbarTile(hotbar[i], x, top + 3, 16);
        }

        atlas.bindIcons();
        glColor4f(1, 1, 1, 1);
        drawGuiRegion(0, 0, 16, 16, guiWidth/2f - 8, guiHeight/2f - 8, 16, 16);
        drawTextOverlay();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_FOG);
        glColor4f(1, 1, 1, 1);
    }

    private void drawHotbarTile(Block block, float x, float y, float size) {
        BakedModel model = modelManager.getModel(block);
        TextureAtlas.Tile t = atlas.tile(model.particleTexture());
        atlas.bind(); glEnable(GL_TEXTURE_2D); glColor4f(1, 1, 1, 1);
        glBegin(GL_QUADS);
        glTexCoord2f(t.u0(), t.v0()); glVertex2f(x, y);
        glTexCoord2f(t.u1(), t.v0()); glVertex2f(x+size, y);
        glTexCoord2f(t.u1(), t.v1()); glVertex2f(x+size, y+size);
        glTexCoord2f(t.u0(), t.v1()); glVertex2f(x, y+size);
        glEnd();
    }

    private void drawTextOverlay() {
        fontRenderer.drawStringWithShadow("Minecraft 1.8.9", 2, 2, 0xFFFFFF);
        if (showDebug) {
            int line = 12;
            fontRenderer.drawStringWithShadow("§eLegacyCraft clean-room runtime", 2, line, 0xFFFFFF);
            line += 10;
            fontRenderer.drawStringWithShadow(String.format(Locale.ROOT,
                "XYZ: %.3f / %.3f / %.3f", player.x, player.y, player.z), 2, line, 0xFFFFFF);
            line += 10;
            fontRenderer.drawStringWithShadow("Facing: " + facingName(), 2, line, 0xFFFFFF);
            line += 10;
            fontRenderer.drawStringWithShadow("Selected: §f" + hotbar[selectedSlot].name().path(),
                2, line, 0xA0A0A0);
            line += 10;
            fontRenderer.drawStringWithShadow("Blocks: " + blockRegistry.size() + "  Chunks: " + world.loadedChunkCount(),
                2, line, 0xA0A0A0);
        }
    }

    private String facingName() {
        int quadrant = Math.floorMod(Math.round(player.yaw / 90f), 4);
        return switch (quadrant) {
            case 0 -> "north (-Z)";
            case 1 -> "east (+X)";
            case 2 -> "south (+Z)";
            default -> "west (-X)";
        };
    }

    private void drawGuiRegion(float sx, float sy, float sw, float sh,
                               float x, float y, float w, float h) {
        float u0 = sx / 256f, v0 = sy / 256f;
        float u1 = (sx + sw) / 256f, v1 = (sy + sh) / 256f;
        glBegin(GL_QUADS);
        glTexCoord2f(u0, v0); glVertex2f(x, y);
        glTexCoord2f(u1, v0); glVertex2f(x+w, y);
        glTexCoord2f(u1, v1); glVertex2f(x+w, y+h);
        glTexCoord2f(u0, v1); glVertex2f(x, y+h);
        glEnd();
    }

    private void rect(float x, float y, float w, float h) {
        glBegin(GL_QUADS);
        glVertex2f(x,y); glVertex2f(x+w,y); glVertex2f(x+w,y+h); glVertex2f(x,y+h);
        glEnd();
    }

    private void captureMouse() {
        mouseCaptured = true; firstMouse = true;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        if (glfwRawMouseMotionSupported()) glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
    }
    private void releaseMouse() {
        mouseCaptured = false;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }

    private void saveScreenshot(String path) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        glReadBuffer(GL_BACK);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = (x + y * width) * 4;
                int r = pixels.get(index) & 0xff, g = pixels.get(index + 1) & 0xff;
                int b = pixels.get(index + 2) & 0xff, a = pixels.get(index + 3) & 0xff;
                image.setRGB(x, height - 1 - y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        try {
            File file = new File(path).getAbsoluteFile();
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            ImageIO.write(image, "png", file);
            System.out.println("Saved screenshot to " + file);
        } catch (IOException error) {
            System.err.println("Could not save screenshot: " + error.getMessage());
        }
    }

    public static void main(String[] args) {
        Minecraft game = new Minecraft();
        for (String arg : args) {
            if (arg.equals("--debug")) game.showDebug = true;
            if (arg.startsWith("--capture=")) {
                game.screenshotRequested = arg.substring("--capture=".length());
                game.quitAfterScreenshot = true;
            }
        }
        game.run();
    }
}

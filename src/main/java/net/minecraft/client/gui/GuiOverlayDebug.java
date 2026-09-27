package net.minecraft.client.gui;

import net.minecraft.client.Branding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * The F3 debug screen (master context §67) with 1.8.9's line set, order,
 * labels and number formats. Each line sits on a translucent grey strip
 * (0x90505050) and is drawn in 0xE0E0E0 without a shadow; the left column is
 * gameplay/world state, the right column is Java/memory/CPU/GL and — when a
 * block is targeted — its registry name and state properties (booleans in
 * green/red).
 *
 * <p>The line builders are pure so tests can pin the exact text; values the
 * engine does not track yet (chunk dispatcher queues, entity culling,
 * particles, chunk inhabited time) are reported as zero (DEV-014).
 * HYPOTHESIS until compared with a vanilla 1.8.9 screenshot.
 */
public final class GuiOverlayDebug extends Gui {
    /** Everything the overlay shows, gathered by the client each frame. */
    public record Snapshot(
        int fps, int chunkUpdates,
        int renderedChunks, int totalChunks, int renderDistance,
        int entitiesRendered, int entitiesTotal, int particles, int loadedChunks,
        double x, double y, double z, float yaw, float pitch,
        String biome, boolean lightKnown, int skyLight, int blockLight,
        float localDifficulty, long worldTime,
        Target target, boolean reducedDebugInfo,
        int displayWidth, int displayHeight, String glVendor, String glRenderer, String glVersion, String cpu) {}

    /** The block under the crosshair: position, registry name and property values. */
    public record Target(int x, int y, int z, String name, Map<String, String> properties) {}

    private static final String[] FACING_NAMES = {"south", "west", "north", "east"};
    private static final String[] FACING_TOWARDS = {"Towards positive Z", "Towards negative X", "Towards negative Z", "Towards positive X"};
    private static final float[] MOON_PHASE_FACTORS = {1.0f, 0.75f, 0.5f, 0.25f, 0.0f, 0.25f, 0.5f, 0.75f};

    private final long[] frameTimes = new long[240];
    private int frameIndex;

    public static List<String> leftLines(Snapshot s) {
        List<String> lines = new ArrayList<>();
        lines.add(Branding.debugVersionLine());
        lines.add(s.fps() + " fps (" + s.chunkUpdates() + " chunk updates)");
        lines.add(String.format(Locale.ROOT, "C: %d/%d %sD: %d, pC: %03d, pU: %1d, aB: %1d",
            s.renderedChunks(), s.totalChunks(), "(s) ", s.renderDistance(), 0, 0, 0));
        lines.add("E: " + s.entitiesRendered() + "/" + s.entitiesTotal() + ", B: 0, I: " + (s.entitiesTotal() - s.entitiesRendered()));
        lines.add("P: " + s.particles() + ". T: All: " + s.entitiesTotal());
        lines.add("MultiplayerChunkCache: " + s.loadedChunks() + ", " + s.loadedChunks());
        lines.add("");
        int blockX = floor(s.x()), blockY = floor(s.y()), blockZ = floor(s.z());
        if (s.reducedDebugInfo()) {
            lines.add(String.format(Locale.ROOT, "Chunk-relative: %d %d %d", blockX & 15, blockY & 15, blockZ & 15));
            return lines;
        }
        int facing = Math.floorMod(floor(s.yaw() * 4.0f / 360.0f + 0.5), 4);
        lines.add(String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", s.x(), s.y(), s.z()));
        lines.add(String.format(Locale.ROOT, "Block: %d %d %d", blockX, blockY, blockZ));
        lines.add(String.format(Locale.ROOT, "Chunk: %d %d %d in %d %d %d",
            blockX & 15, blockY & 15, blockZ & 15, blockX >> 4, blockY >> 4, blockZ >> 4));
        lines.add(String.format(Locale.ROOT, "Facing: %s (%s) (%.1f / %.1f)", FACING_NAMES[facing], FACING_TOWARDS[facing],
            wrapDegrees(s.yaw()), wrapDegrees(s.pitch())));
        if (s.lightKnown()) {
            lines.add("Biome: " + s.biome());
            lines.add("Light: " + Math.max(s.skyLight(), s.blockLight()) + " (" + s.skyLight() + " sky, " + s.blockLight() + " block)");
            lines.add(String.format(Locale.ROOT, "Local Difficulty: %.2f (Day %d)", s.localDifficulty(), s.worldTime() / 24000L));
        }
        if (s.target() != null)
            lines.add(String.format(Locale.ROOT, "Looking at: %d %d %d", s.target().x(), s.target().y(), s.target().z()));
        return lines;
    }

    public static List<String> rightLines(Snapshot s) {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory(), total = runtime.totalMemory(), used = total - runtime.freeMemory();
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "Java: %s %dbit", System.getProperty("java.version"), is64Bit() ? 64 : 32));
        lines.add(String.format(Locale.ROOT, "Mem: % 2d%% %03d/%03dMB", used * 100L / max, used / 1024L / 1024L, max / 1024L / 1024L));
        lines.add(String.format(Locale.ROOT, "Allocated: % 2d%% %03dMB", total * 100L / max, total / 1024L / 1024L));
        lines.add("");
        lines.add(String.format(Locale.ROOT, "CPU: %s", s.cpu()));
        lines.add("");
        lines.add(String.format(Locale.ROOT, "Display: %dx%d (%s)", s.displayWidth(), s.displayHeight(), s.glVendor()));
        lines.add(s.glRenderer());
        lines.add(s.glVersion());
        if (s.target() != null && !s.reducedDebugInfo()) {
            lines.add("");
            lines.add(s.target().name());
            for (var property : s.target().properties().entrySet()) {
                String value = property.getValue();
                if (value.equals("true")) value = "§a" + value;
                else if (value.equals("false")) value = "§c" + value;
                lines.add(property.getKey() + ": " + value);
            }
        }
        return lines;
    }

    /**
     * 1.8.9's regional ("local") difficulty. Chunk inhabited time is not
     * tracked yet, so it contributes 0 (DEV-014).
     */
    public static float localDifficulty(int difficulty, long worldTime, long inhabitedTime) {
        if (difficulty == 0) return 0f;
        boolean hard = difficulty == 3;
        float base = 0.75f;
        float timeFactor = clamp((worldTime - 72000.0f) / 1440000.0f, 0, 1) * 0.25f;
        base += timeFactor;
        float local = clamp(inhabitedTime / 3600000.0f, 0, 1) * (hard ? 1.0f : 0.75f);
        int moonPhase = (int) (worldTime / 24000L % 8L + 8L) % 8;
        local += clamp(MOON_PHASE_FACTORS[moonPhase] * 0.25f, 0, timeFactor);
        if (difficulty == 1) local *= 0.5f;
        base += local;
        return difficulty * base;
    }

    /** Records a frame's duration (nanoseconds) for the Alt+F3 frame-time graph. */
    public void addFrameTime(long nanos) {
        frameTimes[frameIndex] = nanos;
        frameIndex = (frameIndex + 1) % frameTimes.length;
    }

    public void render(FontRenderer font, int width, int height, Snapshot snapshot, boolean lagometer) {
        drawColumn(font, leftLines(snapshot), width, false);
        drawColumn(font, rightLines(snapshot), width, true);
        if (lagometer) renderLagometer(height);
    }

    private static void drawColumn(FontRenderer font, List<String> lines, int width, boolean right) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isEmpty()) continue;
            int lineHeight = FontRenderer.FONT_HEIGHT;
            int textWidth = font.getStringWidth(line);
            int x = right ? width - 2 - textWidth : 2;
            int y = 2 + lineHeight * i;
            drawRect(x - 1, y - 1, x + textWidth + 1, y + lineHeight - 1, 0x90505050);
            font.drawString(line, x, y, 0xE0E0E0);
        }
    }

    /**
     * Frame-time bars along the bottom left, one per frame for the last 240
     * frames, with reference lines at 60 and 30 fps (HYPOTHESIS styling).
     */
    private void renderLagometer(int height) {
        drawRect(0, height - 60, 240, height, 0x90505050);
        for (int i = 0; i < frameTimes.length; i++) {
            long nanos = frameTimes[(frameIndex + i) % frameTimes.length];
            int bar = (int) Math.min(60, nanos / 1_000_000.0 * 1.8);
            int color = nanos <= 16_700_000L ? 0xFF00FF00 : nanos <= 33_300_000L ? 0xFFFFFF00 : 0xFFFF0000;
            drawRect(i, height - bar, i + 1, height, color);
        }
        drawHorizontalLine(0, 239, height - 30, 0xFFFFFFFF);
        drawHorizontalLine(0, 239, height - 60, 0xFFFFFFFF);
    }

    /**
     * The red/green/blue X/Y/Z gizmo that replaces the crosshair while F3 is
     * open, turned with the camera ({@code yaw}/{@code pitch} in vanilla's
     * convention).
     */
    public static void renderAxisCrosshair(int width, int height, float yaw, float pitch) {
        glPushMatrix();
        glTranslatef(width / 2f, height / 2f, 0);
        glRotatef(pitch, -1, 0, 0);
        glRotatef(yaw, 0, 1, 0);
        glScalef(-1, -1, -1);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);
        glLineWidth(2f);
        glBegin(GL_LINES);
        glColor4f(1, 0, 0, 1); glVertex3f(0, 0, 0); glVertex3f(10, 0, 0);
        glColor4f(0, 1, 0, 1); glVertex3f(0, 0, 0); glVertex3f(0, 10, 0);
        glColor4f(0, 0, 1, 1); glVertex3f(0, 0, 0); glVertex3f(0, 0, 10);
        glEnd();
        glLineWidth(1f);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
        glPopMatrix();
    }

    /** CPU description like vanilla's "%dx %s" (core count x processor name). */
    public static String cpuDescription() {
        String name = System.getenv("PROCESSOR_IDENTIFIER");
        if (name == null || name.isBlank()) name = System.getProperty("os.arch");
        return Runtime.getRuntime().availableProcessors() + "x " + name;
    }

    public static float wrapDegrees(float degrees) {
        degrees %= 360.0f;
        if (degrees >= 180.0f) degrees -= 360.0f;
        if (degrees < -180.0f) degrees += 360.0f;
        return degrees;
    }

    private static boolean is64Bit() {
        String model = System.getProperty("sun.arch.data.model", "");
        return model.equals("64") || System.getProperty("os.arch", "").contains("64");
    }

    private static int floor(double value) { return (int) Math.floor(value); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}

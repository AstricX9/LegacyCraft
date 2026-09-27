package net.minecraft.client.gui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class GuiOverlayDebugTest {
    private static GuiOverlayDebug.Snapshot snapshot(double x, double y, double z, float yaw, GuiOverlayDebug.Target target, boolean reduced) {
        return new GuiOverlayDebug.Snapshot(60, 3, 81, 81, 4, 0, 0, 0, 289,
            x, y, z, yaw, 12.5f, "Plains", true, 15, 3, 1.5f, 30000L, target, reduced,
            854, 480, "Vendor", "Renderer", "4.6", "8x CPU");
    }

    @Test
    void leftColumnMatchesVanillaFormats() {
        List<String> lines = GuiOverlayDebug.leftLines(snapshot(-0.5, 64, 17.25, 0f, null, false));
        assertEquals("Minecraft 1.8.9 (1.8.9/legacycraft)", lines.get(0));
        assertEquals("60 fps (3 chunk updates)", lines.get(1));
        assertEquals("C: 81/81 (s) D: 4, pC: 000, pU: 0, aB: 0", lines.get(2));
        assertEquals("", lines.get(6));
        assertEquals("XYZ: -0.500 / 64.00000 / 17.250", lines.get(7));
        assertEquals("Block: -1 64 17", lines.get(8));
        assertEquals("Chunk: 15 0 1 in -1 4 1", lines.get(9));
        assertEquals("Facing: south (Towards positive Z) (0.0 / 12.5)", lines.get(10));
        assertEquals("Biome: Plains", lines.get(11));
        assertEquals("Light: 15 (15 sky, 3 block)", lines.get(12));
        assertEquals("Local Difficulty: 1.50 (Day 1)", lines.get(13));
    }

    @Test
    void facingFollowsVanillaYaw() {
        assertTrue(GuiOverlayDebug.leftLines(snapshot(0, 0, 0, 90f, null, false)).get(10).startsWith("Facing: west (Towards negative X) (90.0"));
        assertTrue(GuiOverlayDebug.leftLines(snapshot(0, 0, 0, 180f, null, false)).get(10).startsWith("Facing: north (Towards negative Z) (-180.0"));
        assertTrue(GuiOverlayDebug.leftLines(snapshot(0, 0, 0, -90f, null, false)).get(10).startsWith("Facing: east (Towards positive X) (-90.0"));
    }

    @Test
    void targetedBlockAddsLookingAtAndColouredBooleans() {
        Map<String, String> properties = new TreeMap<>(Map.of("north", "true", "south", "false", "variant", "oak"));
        GuiOverlayDebug.Target target = new GuiOverlayDebug.Target(1, 2, 3, "minecraft:oak_fence", properties);
        GuiOverlayDebug.Snapshot snapshot = snapshot(0, 0, 0, 0f, target, false);
        List<String> left = GuiOverlayDebug.leftLines(snapshot);
        assertEquals("Looking at: 1 2 3", left.get(left.size() - 1));
        List<String> right = GuiOverlayDebug.rightLines(snapshot);
        assertTrue(right.get(0).startsWith("Java: "));
        assertTrue(right.get(1).startsWith("Mem: "));
        assertEquals("Display: 854x480 (Vendor)", right.get(6));
        assertEquals("minecraft:oak_fence", right.get(10));
        assertEquals(List.of("north: §atrue", "south: §cfalse", "variant: oak"), right.subList(11, 14));
    }

    @Test
    void reducedDebugInfoHidesCoordinates() {
        List<String> lines = GuiOverlayDebug.leftLines(snapshot(-0.5, 64, 17.25, 0f, null, true));
        assertEquals("Chunk-relative: 15 0 1", lines.get(lines.size() - 1));
        assertTrue(lines.stream().noneMatch(line -> line.startsWith("XYZ")));
    }

    @Test
    void localDifficulty() {
        assertEquals(0f, GuiOverlayDebug.localDifficulty(0, 500000, 0));
        assertEquals(1.5f, GuiOverlayDebug.localDifficulty(2, 0, 0), 1e-6f);
        assertEquals(0.75f, GuiOverlayDebug.localDifficulty(1, 0, 0), 1e-6f);
        // Fully aged world (time factor 0.25) on a full moon (phase 0, capped at the time factor) on Hard
        assertEquals(3 * (0.75f + 0.25f + 0.25f), GuiOverlayDebug.localDifficulty(3, 24000L * 8 * 100, 0), 1e-5f);
    }

    @Test
    void wrapsDegreesLikeMathHelper() {
        assertEquals(-170f, GuiOverlayDebug.wrapDegrees(190f));
        assertEquals(-180f, GuiOverlayDebug.wrapDegrees(180f));
        assertEquals(10f, GuiOverlayDebug.wrapDegrees(370f));
    }
}

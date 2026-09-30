package net.minecraft.stats;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Achievement unlocking and the stats file (HYPOTHESIS: recalled StatFileWriter rules and file shape). */
final class StatFileTest {
    private static Achievement a(String id) { return AchievementList.get(id); }

    @Test
    void anAchievementNeedsItsParentFirst() {
        StatFile stats = new StatFile();
        assertFalse(stats.unlock(a("mineWood")), "needs Taking Inventory");
        assertTrue(stats.unlock(a("openInventory")));
        assertFalse(stats.unlock(a("openInventory")), "only once");
        assertTrue(stats.unlock(a("mineWood")));
        assertTrue(stats.has(a("mineWood")));
    }

    @Test
    void theMapCountsLockedStepsToTheNearestEarnedAncestor() {
        StatFile stats = new StatFile();
        stats.unlock(a("openInventory"));
        assertEquals(0, stats.distanceToUnlock(a("mineWood")), "next up");
        assertEquals(1, stats.distanceToUnlock(a("buildWorkBench")));
        assertEquals(3, stats.distanceToUnlock(a("buildFurnace")));
        assertEquals(0, stats.distanceToUnlock(a("openInventory")), "earned");
    }

    @Test
    void theFileKeepsAchievementsAndBiomeProgress(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        StatFile stats = new StatFile();
        stats.unlock(a("openInventory"));
        stats.unlock(a("mineWood"));
        stats.exploreBiome("Plains");
        stats.exploreBiome("Forest");
        JsonObject json = stats.toJson();
        assertEquals(1, json.get("achievement.mineWood").getAsInt());
        assertEquals(2, json.getAsJsonObject("achievement.exploreAllBiomes").getAsJsonArray("progress").size());
        Path file = directory.resolve("stats").resolve("Player.json");
        stats.save(file);
        assertTrue(Files.isRegularFile(file));
        StatFile loaded = StatFile.load(file);
        assertTrue(loaded.has(a("mineWood")));
        assertFalse(loaded.has(a("buildWorkBench")));
        assertEquals(stats.biomesExplored(), loaded.biomesExplored());
    }

    @Test
    void parentsAndDependentsFollowTheTree() {
        assertEquals(List.of(a("buildWorkBench"), a("mineWood"), a("openInventory")), StatFile.ancestors(a("buildPickaxe")));
        List<Achievement> after = StatFile.descendants(a("theEnd"));
        assertTrue(after.containsAll(List.of(a("theEnd2"), a("spawnWither"), a("killWither"), a("fullBeacon"), a("exploreAllBiomes"))));
        assertFalse(after.contains(a("theEnd")));
    }
}

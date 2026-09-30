package net.minecraft.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The achievement list's shape (HYPOTHESIS: recalled 1.8.9 AchievementList). */
final class AchievementListTest {
    @Test
    void thirtyFourAchievementsWhoseParentsComeFirst() throws Exception {
        assertEquals(34, AchievementList.ALL.size());
        Set<String> seen = new HashSet<>();
        for (Achievement achievement : AchievementList.ALL) {
            if (achievement.parent() != null)
                assertTrue(seen.contains(achievement.parent()), achievement.id() + " comes before its parent");
            assertTrue(seen.add(achievement.id()), "duplicate " + achievement.id());
        }
        assertNull(AchievementList.get("openInventory").parent());
        assertEquals(AchievementList.get("mineWood"), AchievementList.get("achievement.mineWood"));
    }

    @Test
    void everyIconIsAnItemAndEveryNameHasText() throws Exception {
        JsonObject tabs;
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/assets/legacycraft/creative_tabs.json"))) {
            tabs = JsonParser.parseReader(reader).getAsJsonObject();
        }
        Set<String> items = new HashSet<>(Set.of("dragon_egg"));
        for (var tab : tabs.getAsJsonArray("tabs"))
            for (var entry : tab.getAsJsonObject().getAsJsonArray("items")) items.add(entry.getAsJsonObject().get("item").getAsString());
        for (Achievement achievement : AchievementList.ALL) assertTrue(items.contains(achievement.icon()), achievement.icon());
    }

    @Test
    void adventuringTimeCountsThirtySixBiomes() {
        assertEquals(36, AchievementList.EXPLORATION_BIOMES.size());
        assertFalse(AchievementList.EXPLORATION_BIOMES.containsKey(8), "not the Nether");
        assertFalse(AchievementList.EXPLORATION_BIOMES.containsKey(10), "not FrozenOcean");
        assertEquals("Extreme Hills+", AchievementList.EXPLORATION_BIOMES.get(34));
    }

    @Test
    void craftingAndPickingUpTriggers() {
        assertEquals("buildWorkBench", AchievementList.forCrafted("crafting_table"));
        assertEquals("buildPickaxe", AchievementList.forCrafted("wooden_pickaxe"));
        assertNull(AchievementList.forCrafted("stick"));
        assertEquals("mineWood", AchievementList.forPickedUp("dark_oak_log"));
        assertEquals("diamonds", AchievementList.forPickedUp("diamond"));
    }

    @Test
    void smeltingTriggers() {
        assertEquals("acquireIron", AchievementList.forSmelted("iron_ingot"));
        assertEquals("cookFish", AchievementList.forSmelted("cooked_cod"));
        assertEquals("cookFish", AchievementList.forSmelted("cooked_salmon"));
        assertNull(AchievementList.forSmelted("gold_ingot"));
    }
}

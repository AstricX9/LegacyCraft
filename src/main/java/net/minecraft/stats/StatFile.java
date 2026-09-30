package net.minecraft.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A player's achievements (1.8.9 StatFileWriter / StatisticsFile): which are
 * unlocked, and the biomes seen toward Adventuring Time. Saved as the
 * world's {@code stats/<player>.json} in vanilla's shape:
 * {@code {"achievement.mineWood": 1, "achievement.exploreAllBiomes":
 * {"value": 0, "progress": ["Plains", ...]}}}. An achievement can only be
 * unlocked once its parent is.
 */
public final class StatFile {
    private final Set<String> unlocked = new LinkedHashSet<>();
    private final Set<String> biomesExplored = new LinkedHashSet<>();

    public boolean has(Achievement achievement) { return unlocked.contains(achievement.id()); }

    /** StatFileWriter.canUnlockAchievement: no parent, or the parent is unlocked. */
    public boolean canUnlock(Achievement achievement) {
        Achievement parent = AchievementList.parent(achievement);
        return parent == null || has(parent);
    }

    /** Unlocks it if its parent allows and it isn't already; true when newly unlocked. */
    public boolean unlock(Achievement achievement) {
        if (achievement == null || has(achievement) || !canUnlock(achievement)) return false;
        unlocked.add(achievement.id());
        return true;
    }

    /** Unlocks regardless of the parent (the /achievement command gives the parents itself). */
    public void set(Achievement achievement, boolean value) {
        if (value) unlocked.add(achievement.id());
        else unlocked.remove(achievement.id());
    }

    /**
     * How many locked achievements lie between this one and an unlocked
     * ancestor (GuiAchievements.func_146664_a): 0 when it can be unlocked now.
     * The map hides achievements more than 4 steps away.
     */
    public int distanceToUnlock(Achievement achievement) {
        if (has(achievement)) return 0;
        int distance = 0;
        for (Achievement parent = AchievementList.parent(achievement); parent != null && !has(parent);
             parent = AchievementList.parent(parent))
            distance++;
        return distance;
    }

    public Set<String> biomesExplored() { return Set.copyOf(biomesExplored); }

    /** Adds a biome seen; true when it was new. */
    public boolean exploreBiome(String name) { return biomesExplored.add(name); }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        for (Achievement achievement : AchievementList.ALL) {
            boolean has = has(achievement);
            if (achievement.id().equals("exploreAllBiomes") && !biomesExplored.isEmpty()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("value", has ? 1 : 0);
                JsonArray progress = new JsonArray();
                for (String biome : biomesExplored) progress.add(biome);
                entry.add("progress", progress);
                json.add(achievement.statName(), entry);
            } else if (has) {
                json.addProperty(achievement.statName(), 1);
            }
        }
        return json;
    }

    public static StatFile fromJson(JsonObject json) {
        StatFile file = new StatFile();
        for (var entry : json.entrySet()) {
            Achievement achievement = AchievementList.get(entry.getKey());
            if (achievement == null) continue;
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                if (object.has("value") && object.get("value").getAsInt() > 0) file.unlocked.add(achievement.id());
                if (object.has("progress"))
                    for (JsonElement biome : object.getAsJsonArray("progress")) file.biomesExplored.add(biome.getAsString());
            } else if (value.getAsInt() > 0) {
                file.unlocked.add(achievement.id());
            }
        }
        return file;
    }

    public static StatFile load(Path path) {
        if (!Files.isRegularFile(path)) return new StatFile();
        try {
            return fromJson(JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException | RuntimeException error) {
            System.err.println("Couldn't read statistics file " + path + ": " + error.getMessage());
            return new StatFile();
        }
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, toJson().toString(), StandardCharsets.UTF_8);
    }

    /** Every achievement the given one needs first, nearest first. */
    public static List<Achievement> ancestors(Achievement achievement) {
        List<Achievement> result = new ArrayList<>();
        for (Achievement parent = AchievementList.parent(achievement); parent != null; parent = AchievementList.parent(parent))
            result.add(parent);
        return result;
    }

    /** Every achievement that needs the given one, directly or not. */
    public static List<Achievement> descendants(Achievement achievement) {
        List<Achievement> result = new ArrayList<>();
        for (Achievement other : AchievementList.ALL)
            if (ancestors(other).contains(achievement)) result.add(other);
        return result;
    }
}

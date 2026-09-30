package net.minecraft.command;

import net.minecraft.client.gui.I18n;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.LevelInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Command parsing and effects against a fake player (messages use a stand-in lang table). */
final class CommandHandlerTest {
    private final CommandHandler handler = new CommandHandler();
    private final Sender sender = new Sender();

    @BeforeEach
    void lang() {
        I18n.set(Map.ofEntries(
            Map.entry("commands.time.set", "Set the time to %d"),
            Map.entry("commands.time.added", "Added %d to the time"),
            Map.entry("commands.time.query", "Time is %d"),
            Map.entry("commands.time.usage", "/time <set|add|query> <value>"),
            Map.entry("commands.generic.usage", "Usage: %s"),
            Map.entry("commands.generic.notFound", "Unknown command. Try /help for a list of commands"),
            Map.entry("commands.generic.permission", "You do not have permission to use this command"),
            Map.entry("commands.generic.num.invalid", "'%s' is not a valid number"),
            Map.entry("commands.generic.num.tooSmall", "The number you have entered (%d) is too small, it must be at least %d"),
            Map.entry("commands.generic.boolean.invalid", "'%s' is not true or false"),
            Map.entry("commands.gamerule.success", "Game rule has been updated"),
            Map.entry("commands.gamerule.norule", "No game rule called '%s' is available"),
            Map.entry("commands.gamemode.success.self", "Set own game mode to %s"),
            Map.entry("gameMode.creative", "Creative Mode"),
            Map.entry("commands.tp.success.coordinates", "Teleported %s to %s, %s, %s"),
            Map.entry("commands.seed.success", "Seed: %s"),
            Map.entry("chat.type.emote", "* %s %s")));
    }

    @Test
    void timeSetAcceptsNumbersAndDayNight() {
        assertTrue(handler.execute(sender, "/time set day"));
        assertEquals(1000, sender.level.worldTime);
        assertEquals("Set the time to 1000", sender.last());
        handler.execute(sender, "/time set night");
        assertEquals(13000, sender.level.worldTime);
        handler.execute(sender, "/time set 6000");
        assertEquals(6000, sender.level.worldTime);
        handler.execute(sender, "/time add 500");
        assertEquals(6500, sender.level.worldTime);
        handler.execute(sender, "/time query daytime");
        assertEquals("Time is 6500", sender.last());
    }

    @Test
    void achievementGiveEarnsTheParentsAndTakeTheDependents() {
        var pickaxe = net.minecraft.stats.AchievementList.get("buildPickaxe");
        var wood = net.minecraft.stats.AchievementList.get("mineWood");
        assertTrue(handler.execute(sender, "/achievement give achievement.buildPickaxe"));
        assertTrue(sender.stats.has(pickaxe));
        assertTrue(sender.stats.has(wood), "parents come too");
        assertFalse(handler.execute(sender, "/achievement give achievement.buildPickaxe"), "already has it");
        assertTrue(handler.execute(sender, "/achievement take achievement.mineWood"));
        assertFalse(sender.stats.has(wood));
        assertFalse(sender.stats.has(pickaxe), "what needs it goes too");
        assertTrue(sender.stats.has(net.minecraft.stats.AchievementList.get("openInventory")));
        assertTrue(handler.execute(sender, "/achievement give *"));
        for (var each : net.minecraft.stats.AchievementList.ALL) assertTrue(sender.stats.has(each), each.id());
        assertFalse(handler.execute(sender, "/achievement give mineWood"), "names need the achievement. prefix");
    }

    @Test
    void badArgumentsAreRedErrors() {
        assertFalse(handler.execute(sender, "/time set morning"));
        assertEquals("§c'morning' is not a valid number", sender.last());
        handler.execute(sender, "/time set -5");
        assertEquals("§cThe number you have entered (-5) is too small, it must be at least 0", sender.last());
        handler.execute(sender, "/time");
        assertEquals("§cUsage: /time <set|add|query> <value>", sender.last());
        handler.execute(sender, "/nothing");
        assertEquals("§cUnknown command. Try /help for a list of commands", sender.last());
    }

    @Test
    void cheatsOffLeavesOnlyEveryoneCommands() {
        sender.cheats = false;
        assertFalse(handler.execute(sender, "/time set 0"));
        assertEquals("§cYou do not have permission to use this command", sender.last());
        assertTrue(handler.execute(sender, "/me waves"));
        assertEquals("* Player waves", sender.last());
        assertTrue(handler.execute(sender, "/seed"));
    }

    @Test
    void gameRulesQuerySetAndValidateBooleans() {
        handler.execute(sender, "/gamerule doDaylightCycle false");
        assertEquals("false", sender.level.gameRules.get("doDaylightCycle"));
        handler.execute(sender, "/gamerule doDaylightCycle");
        assertEquals("doDaylightCycle = false", sender.last());
        handler.execute(sender, "/gamerule doDaylightCycle maybe");
        assertEquals("§c'maybe' is not true or false", sender.last());
        handler.execute(sender, "/gamerule randomTickSpeed 10");
        assertEquals("10", sender.level.gameRules.get("randomTickSpeed"));
        handler.execute(sender, "/gamerule sendCommandFeedback false");
        int before = sender.messages.size();
        handler.execute(sender, "/time set 0");
        assertEquals(before, sender.messages.size(), "no feedback");
    }

    @Test
    void gameModeByNameLetterOrNumber() {
        handler.execute(sender, "/gamemode c");
        assertEquals(WorldSettings.GameType.CREATIVE, sender.level.gameType);
        assertEquals("Set own game mode to Creative Mode", sender.last());
        handler.execute(sender, "/gamemode 0");
        assertEquals(WorldSettings.GameType.SURVIVAL, sender.level.gameType);
        handler.execute(sender, "/gamemode spectator");
        assertEquals(WorldSettings.GameType.SPECTATOR, sender.level.gameType);
    }

    @Test
    void teleportCentresWholeCoordinatesAndSupportsRelative() {
        handler.execute(sender, "/tp 10 70 -3");
        assertEquals(10.5, sender.x, 0);
        assertEquals(70.0, sender.y, 0);
        assertEquals(-2.5, sender.z, 0);
        assertEquals("Teleported Player to 10.5, 70.0, -2.5", sender.last());
        handler.execute(sender, "/tp ~1 ~-5 ~ 90 30");
        assertEquals(11.5, sender.x, 0);
        assertEquals(65.0, sender.y, 0);
        assertEquals(90f, sender.yaw, 0);
        assertEquals(30f, sender.pitch, 0);
    }

    @Test
    void tabCompletion() {
        assertEquals(List.of("/time", "/tp"), handler.complete(sender, "/t"));
        assertEquals(List.of("set"), handler.complete(sender, "/time s"));
        assertEquals(List.of("day"), handler.complete(sender, "/time set d"));
        assertEquals(List.of("doDaylightCycle"), handler.complete(sender, "/gamerule doD"));
        assertEquals(List.of("Player"), handler.complete(sender, "hi pl"));
    }

    private static final class Sender implements CommandSender {
        final LevelInfo level = new LevelInfo();
        final List<String> messages = new ArrayList<>();
        boolean cheats = true;
        double x, y = 64, z;
        float yaw, pitch;

        String last() { return messages.get(messages.size() - 1); }
        @Override public String name() { return "Player"; }
        @Override public void addChatMessage(String message, boolean playerChat) { messages.add(message); }
        @Override public boolean commandsAllowed() { return cheats; }
        @Override public LevelInfo level() { return level; }
        @Override public double x() { return x; }
        @Override public double y() { return y; }
        @Override public double z() { return z; }
        @Override public float yaw() { return yaw; }
        @Override public float pitch() { return pitch; }
        @Override public void teleport(double x, double y, double z, float yaw, float pitch) {
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }
        @Override public WorldSettings.GameType gameType() { return level.gameType; }
        @Override public void setGameType(WorldSettings.GameType type) { level.gameType = type; }
        @Override public void setDifficulty(int difficulty) { level.difficulty = difficulty; }
        @Override public void kill() {}
        @Override public int clearInventory() { return 0; }
        final net.minecraft.stats.StatFile stats = new net.minecraft.stats.StatFile();
        @Override public net.minecraft.stats.StatFile achievements() { return stats; }
        @Override public void awardAchievement(net.minecraft.stats.Achievement achievement) { stats.unlock(achievement); }
        @Override public void revokeAchievement(net.minecraft.stats.Achievement achievement) { stats.set(achievement, false); }
    }
}

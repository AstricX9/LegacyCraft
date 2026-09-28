package net.minecraft.world.storage;

import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTag;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Save folders: folder names, level.dat round trips, hardcore worlds and the world list. */
final class SaveFormatTest {
    @Test
    void folderNamesFollowTheCreateWorldRules() {
        Set<String> taken = Set.of("New World", "New World-", "_CON_");
        assertEquals("New World--", SaveFormat.makeUsableName("New World", taken::contains));
        assertEquals("World", SaveFormat.makeUsableName("   ", name -> false));
        assertEquals("a_b_c_d", SaveFormat.makeUsableName("a/b:c?d", name -> false));
        assertEquals("v1_2", SaveFormat.makeUsableName("v1.2", name -> false));
        assertEquals("_LPT1_", SaveFormat.makeUsableName("LPT1", name -> false));
        assertEquals("_con_-", SaveFormat.makeUsableName("con", name -> name.equalsIgnoreCase("_CON_")), "case is kept");
        assertEquals("CONSOLE", SaveFormat.makeUsableName("CONSOLE", name -> false), "only whole device names are reserved");
    }

    @Test
    void levelDatRoundTripsAndKeepsUnknownTags(Path directory) throws Exception {
        WorldSettings settings = new WorldSettings(-42L, WorldSettings.GameType.CREATIVE, false, false,
            WorldType.FLAT, true, false, "3;7,2*3,2;1;village");
        LevelInfo info = LevelInfo.create("My World", settings);
        info.player = new LevelInfo.PlayerPosition(1.5, 70, -2.5, 90f, 10f, true);
        info.worldTime = 25000;
        Path file = directory.resolve("level.dat");
        info.write(file);

        NbtTag.Compound data = NbtIo.readCompressed(file).getCompound("Data");
        assertEquals(-42L, data.getLong("RandomSeed"));
        assertEquals("flat", data.getString("generatorName"));
        assertEquals(1, data.getInt("GameType"));
        assertEquals(1, data.getByte("allowCommands"));
        assertEquals(0, data.getByte("MapFeatures"));
        assertEquals(19133, data.getInt("version"));
        assertEquals("true", data.getCompound("GameRules").getString("doDaylightCycle"));
        assertEquals(3, data.getCompound("Player").getList("Pos").size());

        data.put("SomeModTag", new NbtTag.Str("kept"));
        NbtTag.Compound root = new NbtTag.Compound();
        root.put("Data", data);
        NbtIo.writeCompressed(root, file);
        LevelInfo loaded = LevelInfo.read(file);
        assertEquals("My World", loaded.levelName);
        assertEquals(WorldType.FLAT, loaded.terrainType);
        assertEquals("3;7,2*3,2;1;village", loaded.generatorOptions);
        assertEquals(25000, loaded.worldTime);
        assertEquals(90f, loaded.player.yaw());
        loaded.write(file);
        assertEquals("kept", NbtIo.readCompressed(file).getCompound("Data").getString("SomeModTag"));
    }

    @Test
    void hardcoreWorldsStartOnHard() {
        LevelInfo info = LevelInfo.create("H", new WorldSettings(1, WorldSettings.GameType.SURVIVAL, true, true,
            WorldType.DEFAULT, false, false, ""));
        assertTrue(info.hardcore);
        assertEquals(3, info.difficulty);
    }

    @Test
    void listsRenamesAndDeletesWorlds(Path directory) throws Exception {
        SaveFormat saves = new SaveFormat(directory.resolve("saves"));
        LevelInfo older = LevelInfo.create("Older", new WorldSettings(1, WorldSettings.GameType.SURVIVAL, true, false,
            WorldType.DEFAULT, false, false, ""));
        older.lastPlayed = 1000;
        LevelInfo newer = LevelInfo.create("Newer", new WorldSettings(2, WorldSettings.GameType.SURVIVAL, true, false,
            WorldType.DEFAULT, false, false, ""));
        newer.lastPlayed = 2000;
        saves.saveWorldInfo("a", older);
        saves.saveWorldInfo("b", newer);
        Files.createDirectories(saves.worldDirectory("not a world"));

        List<SaveFormat.SaveSummary> list = saves.getSaveList();
        assertEquals(List.of("b", "a"), list.stream().map(SaveFormat.SaveSummary::fileName).toList(), "most recent first");
        assertTrue(list.get(0).requiresConversion(), "no LegacyCraft chunk data written yet");

        saves.renameWorld("a", "Renamed");
        assertEquals("Renamed", saves.getWorldInfo("a").levelName);
        assertTrue(saves.folderExists("A"), "folder clashes are case-insensitive");
        assertTrue(saves.deleteWorldDirectory("a"));
        assertNull(saves.getWorldInfo("a"));
        assertEquals(1, saves.getSaveList().size());
    }
}

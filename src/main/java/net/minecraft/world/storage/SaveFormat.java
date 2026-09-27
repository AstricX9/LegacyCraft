package net.minecraft.world.storage;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The {@code saves/} directory: listing, naming, renaming and deleting world
 * folders the way the 1.8.9 Singleplayer screens do (master context §66).
 */
public final class SaveFormat {
    public static final String LEVEL_DAT = "level.dat";
    /** Characters vanilla replaces with '_' in folder names (ChatAllowedCharacters.allowedCharactersArray). */
    private static final char[] DISALLOWED = {'/', '\n', '\r', '\t', '\u0000', '\f', '`', '?', '*', '\\', '<', '>', '|', '"', ':'};
    /** Windows device names vanilla refuses as folder names (matched whole, ignoring case). */
    private static final Pattern RESERVED = Pattern.compile("CON|COM|PRN|AUX|CLOCK\\$|NUL|COM[1-9]|LPT[1-9]",
        Pattern.CASE_INSENSITIVE);

    private final Path savesDirectory;

    /** One row of the world list. */
    public record SaveSummary(String fileName, String displayName, long lastTimePlayed, long sizeOnDisk,
                              LevelInfo info, boolean requiresConversion) {}

    public SaveFormat(Path savesDirectory) { this.savesDirectory = savesDirectory; }

    public Path directory() { return savesDirectory; }
    public Path worldDirectory(String folder) { return savesDirectory.resolve(folder); }

    /** Every folder with a readable level.dat, most recently played first (then by folder name). */
    public List<SaveSummary> getSaveList() throws IOException {
        List<SaveSummary> summaries = new ArrayList<>();
        if (!Files.isDirectory(savesDirectory)) return summaries;
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(savesDirectory, Files::isDirectory)) {
            for (Path folder : folders) {
                String name = folder.getFileName().toString();
                LevelInfo info = getWorldInfo(name);
                if (info == null) continue;
                String display = info.levelName.isEmpty() ? name : info.levelName;
                boolean convert = !WorldStorage.exists(folder);
                summaries.add(new SaveSummary(name, display, info.lastPlayed, info.sizeOnDisk, info, convert));
            }
        }
        summaries.sort(Comparator.comparingLong(SaveSummary::lastTimePlayed).reversed()
            .thenComparing(SaveSummary::fileName));
        return summaries;
    }

    /** The folder's level data, or null when there is no readable level.dat. */
    public LevelInfo getWorldInfo(String folder) {
        Path levelDat = worldDirectory(folder).resolve(LEVEL_DAT);
        if (!Files.isRegularFile(levelDat)) return null;
        try {
            return LevelInfo.read(levelDat);
        } catch (IOException | RuntimeException unreadable) {
            System.err.println("Could not read " + levelDat + ": " + unreadable.getMessage());
            return null;
        }
    }

    public boolean folderExists(String folder) {
        if (Files.exists(worldDirectory(folder))) return true;
        // Windows folders are case-insensitive; treat a case-only clash as taken too.
        if (!Files.isDirectory(savesDirectory)) return false;
        try (Stream<Path> children = Files.list(savesDirectory)) {
            return children.anyMatch(child -> child.getFileName().toString().equalsIgnoreCase(folder));
        } catch (IOException error) {
            return false;
        }
    }

    public void saveWorldInfo(String folder, LevelInfo info) throws IOException {
        Path directory = worldDirectory(folder);
        Files.createDirectories(directory);
        info.write(directory.resolve(LEVEL_DAT));
    }

    /** Changes the display name only; the folder keeps its name, as in vanilla. */
    public void renameWorld(String folder, String newName) throws IOException {
        LevelInfo info = getWorldInfo(folder);
        if (info == null) return;
        info.levelName = newName;
        saveWorldInfo(folder, info);
    }

    /** Deletes the whole world folder. Returns whether it is gone. */
    public boolean deleteWorldDirectory(String folder) {
        Path directory = worldDirectory(folder);
        if (!Files.exists(directory)) return true;
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            return true;
        } catch (IOException error) {
            System.err.println("Could not delete " + directory + ": " + error.getMessage());
            return false;
        }
    }

    /** A folder name for a new world called {@code worldName} that does not clash with an existing one. */
    public String findFreeFolderName(String worldName) { return makeUsableName(worldName, this::folderExists); }

    /**
     * Vanilla's folder-name rule (HYPOTHESIS): trim; disallowed characters
     * become '_'; blank becomes "World"; '.', '/' and '"' become '_'; Windows
     * device names are wrapped in '_'; then '-' is appended until the name is
     * free. LegacyCraft counts any existing folder as taken (vanilla only
     * counts folders with a readable level.dat) so it never writes into an
     * unrelated directory.
     */
    public static String makeUsableName(String worldName, Predicate<String> taken) {
        String name = worldName.trim();
        for (char character : DISALLOWED) name = name.replace(character, '_');
        if (name.isEmpty()) name = "World";
        name = name.replaceAll("[\\./\"]", "_");
        if (RESERVED.matcher(name).matches()) name = "_" + name + "_";
        while (taken.test(name)) name = name + "-";
        return name;
    }

    /** Date text for the world list, e.g. "9/26/26 7:05 PM" in the default locale (vanilla uses {@code new SimpleDateFormat()}). */
    public static String formatDate(long millis) {
        return new java.text.SimpleDateFormat().format(new java.util.Date(millis));
    }
}

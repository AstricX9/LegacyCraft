package net.legacycraft.launcher;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Lists the worlds in a game directory's {@code saves/} folder and imports
 * world folders or .zip files into it. The game reads vanilla 1.8.9 (Anvil)
 * worlds and converts them to its own chunk format the first time they're
 * opened, so importing is a checked copy: find level.dat, refuse worlds the
 * game can't read, and never write over an existing world.
 */
final class WorldImporter {
    static final String LEVEL_DAT = "level.dat";
    /** level.dat {@code version} of Anvil worlds (1.2 to 1.12). */
    static final int ANVIL = 19133;
    /** First {@code DataVersion} with the 1.13 block palette, which has no numeric block IDs. */
    static final int FLATTENING = 1451;
    /** First {@code DataVersion} after 1.8.9 (1.9 snapshots started at 100). */
    static final int AFTER_1_8 = 100;

    private static final char[] DISALLOWED = {'/', '\n', '\r', '\t', '\u0000', '\f', '`', '?', '*', '\\', '<', '>', '|', '"', ':'};
    private static final Pattern RESERVED = Pattern.compile("CON|COM|PRN|AUX|CLOCK\\$|NUL|COM[1-9]|LPT[1-9]", Pattern.CASE_INSENSITIVE);

    /** What level.dat and the folder say about a world. */
    record World(Path folder, String name, long lastPlayed, int version, int dataVersion,
                 boolean anvilRegions, boolean oldRegions, boolean legacyCraft) {
        String folderName() { return folder.getFileName().toString(); }

        /** Why the game can't open this world, or null when it can. */
        String problem() {
            if (dataVersion >= FLATTENING)
                return "it was saved by Minecraft 1.13 or newer. LegacyCraft reads worlds from 1.8.9 (and up to 1.12).";
            if (!legacyCraft && !anvilRegions && oldRegions)
                return "it is in the old Beta (McRegion) format. Open it once in Minecraft 1.8.9 to convert it, then import it.";
            if (version != 0 && version != ANVIL && !legacyCraft)
                return "its level.dat has save version " + version + ", not the Anvil format (" + ANVIL + ").";
            return null;
        }

        /** Things that won't come along, for the confirmation message. */
        List<String> warnings() {
            List<String> list = new ArrayList<>();
            if (legacyCraft) return list;
            if (dataVersion >= AFTER_1_8)
                list.add("It was saved by a version newer than 1.8.9: blocks added after 1.8.9 become air.");
            if (anvilRegions)
                list.add("Chest contents, sign text and dropped items are not carried over yet (mobs, spawners, mob heads and furnaces are).");
            else
                list.add("It has no region files, so the game will generate its terrain from the seed.");
            return list;
        }

        String status() {
            String problem = problem();
            if (problem != null) return "Can't open";
            return legacyCraft ? "Ready" : "Converts on first play";
        }
    }

    private WorldImporter() {}

    static Path savesDir(Path gameDir) { return gameDir.resolve("saves"); }

    /** Every folder in {@code saves} with a readable level.dat, most recently played first. */
    static List<World> list(Path saves) {
        List<World> worlds = new ArrayList<>();
        if (!Files.isDirectory(saves)) return worlds;
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(saves, Files::isDirectory)) {
            for (Path folder : folders) {
                if (folder.getFileName().toString().startsWith(".")) continue;
                try {
                    if (Files.isRegularFile(folder.resolve(LEVEL_DAT))) worlds.add(inspect(folder));
                } catch (IOException | RuntimeException unreadable) {
                    // Not a world we can describe; the game skips it too.
                }
            }
        } catch (IOException ignored) {}
        worlds.sort(Comparator.comparingLong(World::lastPlayed).reversed().thenComparing(World::folderName));
        return worlds;
    }

    static World inspect(Path folder) throws IOException {
        Map<String, Object> root;
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(folder.resolve(LEVEL_DAT))))) {
            root = Nbt.read(in);
        } catch (IOException | RuntimeException unreadable) {
            throw new IOException("level.dat in " + folder.getFileName() + " can't be read: " + unreadable.getMessage(), unreadable);
        }
        if (!(root.get("Data") instanceof Map<?, ?> data)) throw new IOException("level.dat in " + folder.getFileName() + " has no Data tag");
        String name = data.get("LevelName") instanceof String s ? s : "";
        long lastPlayed = data.get("LastPlayed") instanceof Number n ? n.longValue() : 0;
        int version = data.get("version") instanceof Number n ? n.intValue() : 0;
        int dataVersion = data.get("DataVersion") instanceof Number n ? n.intValue() : -1;
        return new World(folder, name.isEmpty() ? folder.getFileName().toString() : name, lastPlayed, version, dataVersion,
            hasFiles(folder.resolve("region"), ".mca"), hasFiles(folder.resolve("region"), ".mcr"),
            Files.isRegularFile(folder.resolve("level.lci")));
    }

    private static boolean hasFiles(Path dir, String suffix) {
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(f -> f.getFileName().toString().endsWith(suffix));
        } catch (IOException e) {
            return false;
        }
    }

    // ---- importing ----

    /** A world found in a folder or archive, staged in {@code saves} and waiting to be kept or discarded. */
    record Staged(Path stagingDir, Path worldDir, String suggestedFolder, World world) {}

    /**
     * Copies {@code source} (a world folder, a folder holding one, or a .zip)
     * into a hidden staging folder in {@code saves} and inspects it. Call
     * {@link #commit} to keep it or {@link #discard} to throw it away.
     */
    static Staged stage(Path source, Path saves) throws IOException {
        source = source.toAbsolutePath().normalize();
        Path savesDir = saves.toAbsolutePath().normalize();
        if (source.startsWith(savesDir) && !source.equals(savesDir))
            throw new IOException("That world is already in this game folder's saves.");
        boolean zip = Files.isRegularFile(source) && source.getFileName().toString().toLowerCase().endsWith(".zip");
        if (!zip && !Files.isDirectory(source)) throw new IOException("Choose a world folder or a .zip file.");
        Path found = zip ? null : findWorld(source);
        if (!zip && found == null) throw new IOException(source.getFileName() + " doesn't contain a world (no level.dat).");
        Files.createDirectories(savesDir);
        Path staging = Files.createTempDirectory(savesDir, ".importing-");
        try {
            Path copy = staging.resolve("world");
            String folder;
            if (zip) {
                unzip(source, copy);
                Path world = findWorld(copy);
                if (world == null) throw new IOException(source.getFileName() + " doesn't contain a world (no level.dat).");
                String archive = source.getFileName().toString();
                folder = world.equals(copy) ? archive.substring(0, archive.length() - 4) : world.getFileName().toString();
                copy = world;
            } else {
                copyTree(found, copy);
                // Vanilla names save folders after the world's folder, so keep it.
                folder = found.getFileName() == null ? "World" : found.getFileName().toString();
            }
            return new Staged(staging, copy, folder, inspect(copy));
        } catch (IOException | RuntimeException e) {
            deleteTree(staging);
            throw e;
        }
    }

    /** Moves a staged world into {@code saves} under a free folder name, which is returned. */
    static String commit(Staged staged, Path saves) throws IOException {
        String base = staged.suggestedFolder;
        while (true) {
            String folder = usableName(base, name -> taken(saves, name));
            try {
                Files.move(staged.worldDir, saves.resolve(folder));
                deleteTree(staged.stagingDir);
                return folder;
            } catch (FileAlreadyExistsException raced) {
                base = folder + "-";
            }
        }
    }

    static void discard(Staged staged) { deleteTree(staged.stagingDir); }

    /** Any existing entry counts, ignoring case (Windows folders are case-insensitive). */
    private static boolean taken(Path saves, String name) {
        if (Files.exists(saves.resolve(name))) return true;
        try (Stream<Path> children = Files.list(saves)) {
            return children.anyMatch(c -> c.getFileName().toString().equalsIgnoreCase(name));
        } catch (IOException e) {
            return false;
        }
    }

    /** The game's (and vanilla's) folder-name rule: bad characters become '_', then '-' is added until the name is free. */
    static String usableName(String worldName, java.util.function.Predicate<String> taken) {
        String name = worldName.trim();
        for (char c : DISALLOWED) name = name.replace(c, '_');
        if (name.isEmpty()) name = "World";
        name = name.replaceAll("[\\./\"]", "_");
        if (RESERVED.matcher(name).matches()) name = "_" + name + "_";
        while (taken.test(name)) name = name + "-";
        return name;
    }

    /** The shallowest folder (up to three levels down) holding a level.dat. */
    static Path findWorld(Path dir) throws IOException {
        if (Files.isRegularFile(dir.resolve(LEVEL_DAT))) return dir;
        try (Stream<Path> walk = Files.walk(dir, 3)) {
            return walk.filter(p -> p.getFileName().toString().equals(LEVEL_DAT) && Files.isRegularFile(p))
                .map(Path::getParent)
                .filter(p -> !p.getFileName().toString().equals("__MACOSX"))
                .min(Comparator.comparingInt(Path::getNameCount)).orElse(null);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else if (!p.getFileName().toString().equals("session.lock"))
                    Files.copy(p, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    private static void unzip(Path zip, Path to) throws IOException {
        Files.createDirectories(to);
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path target = to.resolve(entry.getName().replace('\\', '/')).normalize();
                if (!target.startsWith(to)) throw new IOException("The archive has an entry outside its folder: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else if (!target.getFileName().toString().equals("session.lock")) {
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IllegalArgumentException badName) {
            throw new IOException(zip.getFileName() + " isn't a readable .zip: " + badName.getMessage(), badName);
        }
    }

    static void deleteTree(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException ignored) {}
    }

    /** Just enough NBT to read level.dat: compounds become maps, lists become lists. */
    static final class Nbt {
        private Nbt() {}

        static Map<String, Object> read(InputStream stream) throws IOException {
            DataInputStream in = new DataInputStream(stream);
            if (in.readByte() != 10) throw new IOException("Not an NBT compound");
            in.readUTF();
            return compound(in, 0);
        }

        private static Map<String, Object> compound(DataInputStream in, int depth) throws IOException {
            Map<String, Object> map = new HashMap<>();
            while (true) {
                byte type = in.readByte();
                if (type == 0) return map;
                String name = in.readUTF();
                map.put(name, payload(in, type, depth + 1));
            }
        }

        private static Object payload(DataInputStream in, int type, int depth) throws IOException {
            if (depth > 512) throw new IOException("NBT nested too deeply");
            return switch (type) {
                case 1 -> in.readByte();
                case 2 -> in.readShort();
                case 3 -> in.readInt();
                case 4 -> in.readLong();
                case 5 -> in.readFloat();
                case 6 -> in.readDouble();
                case 7 -> { in.skipNBytes(length(in)); yield null; }
                case 8 -> in.readUTF();
                case 9 -> {
                    int element = in.readByte(), count = length(in);
                    List<Object> list = new ArrayList<>();
                    for (int i = 0; i < count; i++) list.add(payload(in, element, depth + 1));
                    yield list;
                }
                case 10 -> compound(in, depth);
                case 11 -> { in.skipNBytes(4L * length(in)); yield null; }
                case 12 -> { in.skipNBytes(8L * length(in)); yield null; }
                default -> throw new IOException("Unknown NBT tag type " + type);
            };
        }

        private static int length(DataInputStream in) throws IOException {
            int length = in.readInt();
            if (length < 0) throw new IOException("Negative NBT length");
            return length;
        }
    }
}

package net.minecraft.server.dedicated;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * {@code server.properties}, using vanilla's key names for the settings this
 * server has. Missing keys get their defaults and the file is written back,
 * so a first start leaves an editable file behind.
 */
public final class ServerProperties {
    public final int port;
    public final String motd;
    public final int maxPlayers;
    public final int viewDistance;
    public final int gameMode;
    public final int difficulty;
    public final String levelName;
    public final String levelSeed;
    public final String serverIp;

    private ServerProperties(Properties p) {
        port = integer(p, "server-port", 25565);
        motd = string(p, "motd", "A LegacyCraft Server");
        maxPlayers = integer(p, "max-players", 8);
        viewDistance = Math.max(3, Math.min(15, integer(p, "view-distance", 8)));
        gameMode = Math.max(0, Math.min(3, integer(p, "gamemode", 0)));
        difficulty = Math.max(0, Math.min(3, integer(p, "difficulty", 1)));
        levelName = string(p, "level-name", "world");
        levelSeed = string(p, "level-seed", "");
        serverIp = string(p, "server-ip", "");
    }

    public static ServerProperties load(Path file) throws IOException {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(in); }
        }
        ServerProperties result = new ServerProperties(p);
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { p.store(out, "Minecraft server properties"); }
        return result;
    }

    private static String string(Properties p, String key, String fallback) {
        String value = p.getProperty(key);
        if (value == null) { p.setProperty(key, fallback); return fallback; }
        return value;
    }

    private static int integer(Properties p, String key, int fallback) {
        try {
            return Integer.parseInt(string(p, key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

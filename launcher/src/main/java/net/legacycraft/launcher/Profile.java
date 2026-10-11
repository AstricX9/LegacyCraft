package net.legacycraft.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One instance: an entry of launcher_profiles.json, in the shape the 1.6-era
 * launcher wrote, plus the memory and last-played time the instance manager
 * shows. Each instance plays from its own game directory (saves,
 * resourcepacks, options.txt, servers.dat).
 */
final class Profile {
    enum Visibility {
        CLOSE_ON_START("close launcher when game starts", "Close launcher when game starts"),
        HIDE_LAUNCHER("hide launcher and re-open when game closes", "Hide launcher and re-open when game closes"),
        KEEP_OPEN("keep the launcher open", "Keep the launcher open");

        final String id, caption;

        Visibility(String id, String caption) { this.id = id; this.caption = caption; }

        static Visibility byId(String id) {
            for (Visibility v : values()) if (v.id.equals(id)) return v;
            return null;
        }

        @Override public String toString() { return caption; }
    }

    static final Visibility DEFAULT_VISIBILITY = Visibility.HIDE_LAUNCHER;
    static final String DEFAULT_JAVA_ARGS = "";
    static final int DEFAULT_MEMORY_MB = 2048;

    String name;
    String gameDir;           // null = the LegacyCraft install folder (profiles from before instances)
    String lastVersionId;     // null = "Use Latest Version"
    String javaDir;           // null = default Java
    String javaArgs;          // null = defaults from the game's build
    Integer memoryMb;         // null = whatever javaArgs or the game's build says
    Long lastPlayed;          // epoch millis, null = never
    Integer resolutionWidth, resolutionHeight;
    Visibility visibility;    // null = DEFAULT_VISIBILITY
    boolean useHopperCrashService;
    final Set<String> allowedReleaseTypes = new LinkedHashSet<>();

    Profile(String name) { this.name = name; }

    Profile copy(String newName) {
        Profile p = new Profile(newName);
        p.gameDir = gameDir; p.lastVersionId = lastVersionId; p.javaDir = javaDir; p.javaArgs = javaArgs;
        p.memoryMb = memoryMb;
        p.resolutionWidth = resolutionWidth; p.resolutionHeight = resolutionHeight;
        p.visibility = visibility; p.useHopperCrashService = useHopperCrashService;
        p.allowedReleaseTypes.addAll(allowedReleaseTypes);
        return p;
    }

    /** The JVM arguments for the game: the memory setting first, so an -Xmx in javaArgs still wins. */
    String jvmArgs() {
        String args = javaArgs == null ? "" : javaArgs.trim();
        if (memoryMb != null) args = ("-Xmx" + memoryMb + "M " + args).trim();
        return args;
    }

    Visibility effectiveVisibility() { return visibility == null ? DEFAULT_VISIBILITY : visibility; }

    Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", name);
        if (gameDir != null) json.put("gameDir", gameDir);
        if (lastVersionId != null) json.put("lastVersionId", lastVersionId);
        if (javaDir != null) json.put("javaDir", javaDir);
        if (javaArgs != null) json.put("javaArgs", javaArgs);
        if (memoryMb != null) json.put("memoryMb", memoryMb);
        if (lastPlayed != null) json.put("lastPlayed", lastPlayed);
        if (resolutionWidth != null && resolutionHeight != null) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("width", resolutionWidth);
            res.put("height", resolutionHeight);
            json.put("resolution", res);
        }
        if (!allowedReleaseTypes.isEmpty()) json.put("allowedReleaseTypes", new ArrayList<>(allowedReleaseTypes));
        if (visibility != null) json.put("launcherVisibilityOnGameClose", visibility.id);
        if (useHopperCrashService) json.put("useHopperCrashService", true);
        return json;
    }

    static Profile fromJson(String key, Map<String, Object> json) {
        Profile p = new Profile(Json.string(json.get("name"), key));
        p.gameDir = Json.string(json.get("gameDir"), null);
        p.lastVersionId = Json.string(json.get("lastVersionId"), null);
        p.javaDir = Json.string(json.get("javaDir"), null);
        p.javaArgs = Json.string(json.get("javaArgs"), null);
        p.memoryMb = Json.integer(json.get("memoryMb"));
        if (json.get("lastPlayed") instanceof Number n) p.lastPlayed = n.longValue();
        Map<String, Object> res = Json.object(json.get("resolution"));
        p.resolutionWidth = Json.integer(res.get("width"));
        p.resolutionHeight = Json.integer(res.get("height"));
        p.visibility = Visibility.byId(Json.string(json.get("launcherVisibilityOnGameClose"), ""));
        p.useHopperCrashService = Json.bool(json.get("useHopperCrashService"), false);
        List<Object> types = Json.array(json.get("allowedReleaseTypes"));
        for (Object t : types) if (t instanceof String s) p.allowedReleaseTypes.add(s);
        return p;
    }

    @Override public String toString() { return name; }
}

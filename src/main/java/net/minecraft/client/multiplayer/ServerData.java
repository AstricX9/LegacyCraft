package net.minecraft.client.multiplayer;

/**
 * One saved server: the {@code servers.dat} fields ({@code name}, {@code ip},
 * {@code icon}, {@code acceptTextures}) plus the transient ping result the
 * server list shows.
 */
public final class ServerData {
    public enum ResourceMode { ENABLED, DISABLED, PROMPT }

    public String serverName;
    public String serverIP;
    /** Base64 PNG favicon from the last successful ping, or null. */
    public String serverIcon;
    public ResourceMode resourceMode = ResourceMode.PROMPT;

    // Ping state, never saved.
    public String serverMOTD = "";
    public String populationInfo = "";
    public long pingToServer = -2;
    public boolean field_78841_f;

    public ServerData(String name, String ip) {
        this.serverName = name;
        this.serverIP = ip;
    }

    public void copyFrom(ServerData other) {
        serverName = other.serverName;
        serverIP = other.serverIP;
        serverIcon = other.serverIcon;
        resourceMode = other.resourceMode;
    }
}

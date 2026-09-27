package net.minecraft.client.multiplayer;

import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTag;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The multiplayer server list, persisted as vanilla's uncompressed
 * {@code servers.dat}: {@code {servers: [{name, ip, icon?, acceptTextures?}]}}
 * (COMMUNITY_CORROBORATED format). {@code acceptTextures} is absent for
 * "Prompt", 1 for "Enabled" and 0 for "Disabled".
 */
public final class ServerList {
    private final Path file;
    private final List<ServerData> servers = new ArrayList<>();

    public ServerList(Path file) {
        this.file = file;
        load();
    }

    public void load() {
        servers.clear();
        try {
            NbtTag.Compound root = NbtIo.readUncompressed(file);
            if (root == null) return;
            NbtTag.NbtList list = root.getList("servers");
            for (int i = 0; i < list.size(); i++) {
                NbtTag.Compound entry = list.compound(i);
                ServerData data = new ServerData(string(entry, "name"), string(entry, "ip"));
                if (entry.get("icon") instanceof NbtTag.Str icon) data.serverIcon = icon.value();
                if (entry.get("acceptTextures") instanceof NbtTag.Byte accept)
                    data.resourceMode = accept.value() != 0 ? ServerData.ResourceMode.ENABLED : ServerData.ResourceMode.DISABLED;
                servers.add(data);
            }
        } catch (IOException | RuntimeException unreadable) {
            System.err.println("Couldn't load server list: " + unreadable.getMessage());
        }
    }

    public void save() {
        List<NbtTag> entries = new ArrayList<>();
        for (ServerData data : servers) {
            NbtTag.Compound entry = new NbtTag.Compound();
            entry.put("name", new NbtTag.Str(data.serverName));
            entry.put("ip", new NbtTag.Str(data.serverIP));
            if (data.serverIcon != null) entry.put("icon", new NbtTag.Str(data.serverIcon));
            if (data.resourceMode == ServerData.ResourceMode.ENABLED) entry.put("acceptTextures", new NbtTag.Byte((byte) 1));
            else if (data.resourceMode == ServerData.ResourceMode.DISABLED) entry.put("acceptTextures", new NbtTag.Byte((byte) 0));
            entries.add(entry);
        }
        NbtTag.Compound root = new NbtTag.Compound();
        root.put("servers", new NbtTag.NbtList(NbtTag.COMPOUND, entries));
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream out = Files.newOutputStream(file)) { NbtIo.write(root, out); }
        } catch (IOException error) {
            System.err.println("Couldn't save server list: " + error.getMessage());
        }
    }

    public ServerData getServerData(int index) { return servers.get(index); }
    public void removeServerData(int index) { servers.remove(index); }
    public void addServerData(ServerData data) { servers.add(data); }
    public int countServers() { return servers.size(); }

    public void swapServers(int first, int second) {
        ServerData held = servers.get(first);
        servers.set(first, servers.get(second));
        servers.set(second, held);
        save();
    }

    public void setServer(int index, ServerData data) { servers.set(index, data); }

    private static String string(NbtTag.Compound entry, String name) {
        return entry.get(name) instanceof NbtTag.Str value ? value.value() : "";
    }
}

package net.minecraft.client.multiplayer;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The multiplayer server list saved in servers.dat. */
final class ServerListTest {
    @Test
    void serversDatRoundTripsNamesAddressesAndPackPolicy(Path directory) {
        Path file = directory.resolve("servers.dat");
        ServerList list = new ServerList(file);
        assertEquals(0, list.countServers());
        ServerData first = new ServerData("Hypixel-ish", "mc.example.net");
        first.resourceMode = ServerData.ResourceMode.ENABLED;
        list.addServerData(first);
        list.addServerData(new ServerData("Local", "localhost:25565"));
        list.save();
        list.swapServers(0, 1);

        ServerList reloaded = new ServerList(file);
        assertEquals(2, reloaded.countServers());
        assertEquals("Local", reloaded.getServerData(0).serverName);
        assertEquals("mc.example.net", reloaded.getServerData(1).serverIP);
        assertEquals(ServerData.ResourceMode.ENABLED, reloaded.getServerData(1).resourceMode);
        assertEquals(ServerData.ResourceMode.PROMPT, reloaded.getServerData(0).resourceMode);
    }
}

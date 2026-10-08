package net.minecraft.server.dedicated;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketCodec;
import net.minecraft.network.PacketRegistry.Direction;
import net.minecraft.network.ProtocolState;
import net.minecraft.network.v47.Clientbound;
import net.minecraft.network.v47.Handshake;
import net.minecraft.network.v47.LoginPackets;
import net.minecraft.network.v47.Protocol47;
import net.minecraft.network.v47.Serverbound;
import net.minecraft.network.v47.Slot;
import net.minecraft.network.v47.StatusPackets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives a real server over TCP the way a 1.8.9 client does: handshake,
 * status ping, offline login, chunks, a second player, chat and block edits.
 */
class DedicatedServerTest {
    private static DedicatedServer server;
    private static Thread serverThread;
    private static int port;

    @BeforeAll
    static void start() throws Exception {
        Path directory = Files.createTempDirectory("legacycraft-server");
        Files.writeString(directory.resolve("server.properties"),
            "server-port=0\nlevel-seed=12345\nview-distance=3\ngamemode=1\nmotd=Test server\n");
        server = new DedicatedServer(directory);
        serverThread = new Thread(() -> {
            try { server.run(); } catch (IOException e) { throw new RuntimeException(e); }
        }, "test server");
        serverThread.start();
        for (int i = 0; i < 200 && server.boundPort() <= 0; i++) Thread.sleep(25);
        port = server.boundPort();
        assertTrue(port > 0, "server did not start listening");
    }

    @AfterAll
    static void stop() throws InterruptedException {
        server.stop();
        serverThread.join(10_000);
    }

    @Test
    void statusPingReportsVersionAndMotd() throws Exception {
        try (TestClient client = new TestClient(port)) {
            client.send(new Handshake(Protocol47.VERSION, "localhost", port, ProtocolState.STATUS));
            client.codec.setState(ProtocolState.STATUS);
            client.send(new StatusPackets.Request());
            StatusPackets.Response response = client.expect(StatusPackets.Response.class, p -> true);
            assertTrue(response.json().contains("\"protocol\":47"), response.json());
            assertTrue(response.json().contains("Test server"), response.json());
            client.send(new StatusPackets.Ping(42));
            assertEquals(42, client.expect(StatusPackets.Ping.class, p -> true).payload());
        }
    }

    @Test
    void wrongProtocolVersionIsTurnedAway() throws Exception {
        try (TestClient client = new TestClient(port)) {
            client.send(new Handshake(5, "localhost", port, ProtocolState.LOGIN));
            client.codec.setState(ProtocolState.LOGIN);
            client.send(new LoginPackets.Start("Old"));
            assertTrue(client.expect(LoginPackets.Disconnect.class, p -> true).reason().contains("Outdated client"));
        }
    }

    @Test
    void twoPlayersSeeEachOtherChatAndBuild() throws Exception {
        try (TestClient alice = new TestClient(port); TestClient bob = new TestClient(port)) {
            Clientbound.JoinGame aliceJoin = alice.login("Alice");
            assertEquals(1, aliceJoin.gameMode());
            Clientbound.ChunkData firstChunk = alice.expect(Clientbound.ChunkData.class, c -> c.sectionMask() != 0);
            assertTrue(firstChunk.data().length > 256);
            Clientbound.PlayerPositionLook spawn = alice.expect(Clientbound.PlayerPositionLook.class, p -> true);
            alice.send(new Serverbound.Move(true, true, spawn.x(), spawn.y(), spawn.z(), 0, 0, true));

            bob.login("Bob");
            Clientbound.PlayerPositionLook bobSpawn = bob.expect(Clientbound.PlayerPositionLook.class, p -> true);
            bob.send(new Serverbound.Move(true, true, bobSpawn.x(), bobSpawn.y(), bobSpawn.z(), 0, 0, true));

            // Alice learns about Bob: tab list first (the client needs it for the skin), then the entity.
            alice.expect(Clientbound.PlayerListItem.class, p -> p.action() == Clientbound.PlayerListItem.ADD
                && p.entries().stream().anyMatch(e -> "Bob".equals(e.name())));
            Clientbound.SpawnPlayer bobEntity = alice.expect(Clientbound.SpawnPlayer.class, p -> true);
            assertEquals(ServerConnection.offlineUuid("Bob"), bobEntity.uuid());

            // Bob walks; Alice sees it as a relative move.
            bob.send(new Serverbound.Move(true, false, bobSpawn.x() + 1, bobSpawn.y(), bobSpawn.z(), 0, 0, true));
            alice.expect(Packet.class, p -> p instanceof Clientbound.EntityRelativeMove m && m.entityId() == bobEntity.entityId() && m.dx() == 32
                || p instanceof Clientbound.EntityLookAndRelativeMove l && l.entityId() == bobEntity.entityId() && l.dx() == 32);

            // Chat reaches everyone.
            bob.send(new Serverbound.Chat("hello there"));
            Clientbound.Chat chat = alice.expect(Clientbound.Chat.class, c -> c.json().contains("hello there"));
            assertTrue(chat.json().contains("Bob"), chat.json());

            // Alice places stone on top of the block under her, then Bob sees it; then she breaks it.
            // A nearby column whose top is within reach (not her own: the block would go inside her).
            int feetX = (int) Math.floor(spawn.x()), feetY = (int) Math.floor(spawn.y()), feetZ = (int) Math.floor(spawn.z());
            int[] found = null;
            for (int dx = -3; dx <= 3 && found == null; dx++)
                for (int dz = -3; dz <= 3 && found == null; dz++) {
                    int surface = server.surfaceY(feetX + dx, feetZ + dz);
                    if ((dx != 0 || dz != 0) && Math.abs(dx) + Math.abs(dz) > 1 && surface >= feetY - 2 && surface <= feetY + 1)
                        found = new int[]{feetX + dx, surface - 1, feetZ + dz};
                }
            assertNotNull(found, "no buildable column near spawn");
            int x = found[0], top = found[1], z = found[2];
            alice.send(new Serverbound.CreativeInventoryAction(36, new Slot(1, 64, 0, null)));
            alice.send(new Serverbound.BlockPlacement(x, top, z, 1, new Slot(1, 1, 0, null), 8, 16, 8));
            bob.expectBlock(x, top + 1, z, 1 << 4);              // stone, metadata 0

            alice.send(new Serverbound.Digging(Serverbound.Digging.START, x, top + 1, z, 1));
            bob.expectBlock(x, top + 1, z, 0);
        }
    }
}

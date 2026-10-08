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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** A bare protocol-47 client for driving a test server over TCP. */
final class TestClient implements AutoCloseable {
    final int port;
    final Socket socket;
    final PacketCodec codec = new PacketCodec(Protocol47.registry(), Direction.CLIENTBOUND);
    final InputStream in;
    final OutputStream out;
    final byte[] buffer = new byte[65536];

    TestClient(int port) throws IOException {
        this.port = port;
        socket = new Socket("localhost", port);
        socket.setSoTimeout(15_000);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    /** Starts a server on a free port in a fresh directory with these extra properties; returns it once listening. */
    static DedicatedServer startServer(String properties) throws Exception {
        Path directory = Files.createTempDirectory("legacycraft-server");
        Files.writeString(directory.resolve("server.properties"), "server-port=0\nlevel-seed=12345\nview-distance=3\n" + properties);
        DedicatedServer server = new DedicatedServer(directory);
        Thread thread = new Thread(() -> {
            try { server.run(); } catch (IOException e) { throw new RuntimeException(e); }
        }, "test server");
        thread.setDaemon(true);
        thread.start();
        for (int i = 0; i < 200 && server.boundPort() <= 0; i++) Thread.sleep(25);
        assertTrue(server.boundPort() > 0, "server did not start listening");
        return server;
    }

    void send(Packet packet) throws IOException { out.write(codec.encode(packet)); out.flush(); }

    Clientbound.JoinGame login(String name) throws IOException {
        send(new Handshake(Protocol47.VERSION, "localhost", port, ProtocolState.LOGIN));
        codec.setState(ProtocolState.LOGIN);
        send(new LoginPackets.Start(name));
        LoginPackets.SetCompression compression = expect(LoginPackets.SetCompression.class, p -> true);
        codec.setCompressionThreshold(compression.threshold());
        LoginPackets.Success success = expect(LoginPackets.Success.class, p -> true);
        assertEquals(name, success.name());
        assertEquals(ServerConnection.offlineUuid(name).toString(), success.uuid());
        codec.setState(ProtocolState.PLAY);
        return expect(Clientbound.JoinGame.class, p -> true);
    }

    /** Reads until a packet of this type passes the test, answering keep-alives on the way. */
    <P extends Packet> P expect(Class<P> type, Predicate<P> test) throws IOException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            Packet packet;
            while ((packet = codec.next()) != null) {
                if (packet instanceof Clientbound.KeepAlive keepAlive) send(new Serverbound.KeepAlive(keepAlive.id()));
                if (type.isInstance(packet) && test.test(type.cast(packet))) return type.cast(packet);
            }
            int n;
            try { n = in.read(buffer); } catch (SocketTimeoutException e) { break; }
            if (n < 0) fail("Server closed the connection while waiting for " + type.getSimpleName());
            codec.feed(buffer, 0, n);
        }
        return fail("No " + type.getSimpleName() + " arrived");
    }

    @Override public void close() throws IOException { socket.close(); }
}

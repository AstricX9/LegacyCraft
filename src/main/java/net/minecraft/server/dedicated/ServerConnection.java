package net.minecraft.server.dedicated;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketCodec;
import net.minecraft.network.PacketException;
import net.minecraft.network.PacketRegistry.Direction;
import net.minecraft.network.ProtocolState;
import net.minecraft.network.v47.Clientbound;
import net.minecraft.network.v47.Handshake;
import net.minecraft.network.v47.LoginPackets;
import net.minecraft.network.v47.Protocol47;
import net.minecraft.network.v47.StatusPackets;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * One client socket. A reader thread decodes everything that arrives and
 * answers the handshake, status ping and login itself; once the client is in
 * the play state its packets queue up for the server tick. A writer thread
 * sends encoded frames in order.
 *
 * <p>Encoding happens on the thread that calls {@link #send}: the reader
 * thread until login completes, then only the server tick thread (the hand-off
 * goes through the server's join queue).
 */
public final class ServerConnection {
    /** A packet the writer thread takes as "flush what you have and close". */
    private static final byte[] CLOSE = new byte[0];
    private static final int COMPRESSION_THRESHOLD = 256;
    private static final int LOGIN_TIMEOUT_MS = 30_000;

    private final DedicatedServer server;
    private final Socket socket;
    private final PacketCodec decoder = new PacketCodec(Protocol47.registry(), Direction.SERVERBOUND);
    private final PacketCodec encoder = new PacketCodec(Protocol47.registry(), Direction.SERVERBOUND);
    private final BlockingQueue<byte[]> outgoing = new LinkedBlockingQueue<>();
    private final Queue<Packet> incoming = new ConcurrentLinkedQueue<>();
    private volatile boolean closed;
    private volatile String closeReason = "";

    ServerConnection(DedicatedServer server, Socket socket) {
        this.server = server;
        this.socket = socket;
    }

    public String address() { return String.valueOf(socket.getRemoteSocketAddress()); }
    public boolean isClosed() { return closed; }
    public String closeReason() { return closeReason; }

    void start() {
        Thread reader = new Thread(this::readLoop, "Reader " + address());
        Thread writer = new Thread(this::writeLoop, "Writer " + address());
        reader.setDaemon(true);
        writer.setDaemon(true);
        reader.start();
        writer.start();
    }

    /** Encodes now, sends soon. Ignored once the connection is closed. */
    public void send(Packet packet) {
        if (closed) return;
        outgoing.add(encoder.encode(packet));
    }

    /** The next play-state packet from the client, or null. */
    public Packet poll() { return incoming.poll(); }

    /** Sends the play-state disconnect screen with this reason, then closes. */
    public void kick(String reason) {
        if (closed) return;
        JsonObject text = new JsonObject();
        text.addProperty("text", reason);
        send(encoder.state() == ProtocolState.LOGIN ? new LoginPackets.Disconnect(text.toString()) : new Clientbound.Disconnect(text.toString()));
        close(reason);
    }

    /** Stops both threads after anything already queued has been written. */
    public void close(String reason) {
        if (closed) return;
        closeReason = reason;
        closed = true;
        outgoing.add(CLOSE);
    }

    // ---- reader thread ---------------------------------------------------------------------

    private void readLoop() {
        byte[] buffer = new byte[8192];
        // The socket itself is closed by the writer, after the last queued packet is out.
        try {
            InputStream in = socket.getInputStream();
            socket.setSoTimeout(LOGIN_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            while (!closed) {
                int n;
                try {
                    n = in.read(buffer);
                } catch (SocketTimeoutException timeout) {
                    if (decoder.state() == ProtocolState.PLAY) continue;   // play-state liveness is the keep-alive's job
                    close("Took too long to log in");
                    break;
                }
                if (n < 0) { close("Disconnected"); break; }
                decoder.feed(buffer, 0, n);
                for (Packet packet; !closed && (packet = decoder.next()) != null; ) handle(packet);
            }
        } catch (PacketException e) {
            close("Bad packet: " + e.getMessage());
        } catch (IOException e) {
            close(closed ? closeReason : "Connection lost: " + e.getMessage());
        } finally {
            decoder.close();
        }
    }

    private void handle(Packet packet) {
        switch (decoder.state()) {
            case HANDSHAKE -> {
                if (!(packet instanceof Handshake handshake)) throw new PacketException("Expected a handshake");
                decoder.setState(handshake.next());
                encoder.setState(handshake.next());
                if (handshake.next() == ProtocolState.LOGIN && handshake.protocolVersion() != Protocol47.VERSION) {
                    kick(handshake.protocolVersion() < Protocol47.VERSION ? "Outdated client! Please use " + Protocol47.VERSION_NAME
                        : "Outdated server! I'm still on " + Protocol47.VERSION_NAME);
                }
            }
            case STATUS -> {
                if (packet instanceof StatusPackets.Request) send(new StatusPackets.Response(statusJson()));
                else if (packet instanceof StatusPackets.Ping ping) {
                    send(ping);
                    close("Status ping");
                }
            }
            case LOGIN -> {
                if (!(packet instanceof LoginPackets.Start start)) return;
                String name = start.name();
                if (!name.matches("[A-Za-z0-9_]{1,16}")) { kick("Invalid name"); return; }
                UUID uuid = offlineUuid(name);
                send(new LoginPackets.SetCompression(COMPRESSION_THRESHOLD));
                encoder.setCompressionThreshold(COMPRESSION_THRESHOLD);
                decoder.setCompressionThreshold(COMPRESSION_THRESHOLD);
                send(new LoginPackets.Success(uuid.toString(), name));
                encoder.setState(ProtocolState.PLAY);
                decoder.setState(ProtocolState.PLAY);
                server.queueJoin(this, name, uuid);
            }
            case PLAY -> incoming.add(packet);
        }
    }

    private String statusJson() {
        JsonObject version = new JsonObject();
        version.addProperty("name", Protocol47.VERSION_NAME);
        version.addProperty("protocol", Protocol47.VERSION);
        JsonObject players = new JsonObject();
        players.addProperty("max", server.properties().maxPlayers);
        players.addProperty("online", server.playerCount());
        players.add("sample", new JsonArray());
        JsonObject description = new JsonObject();
        description.addProperty("text", server.properties().motd);
        JsonObject root = new JsonObject();
        root.add("version", version);
        root.add("players", players);
        root.add("description", description);
        return root.toString();
    }

    /** Offline-mode players are identified by a name-based UUID of "OfflinePlayer:" + name. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    // ---- writer thread ---------------------------------------------------------------------

    private void writeLoop() {
        try (OutputStream out = new BufferedOutputStream(socket.getOutputStream(), 64 * 1024)) {
            while (true) {
                byte[] frame = outgoing.take();
                if (frame == CLOSE) break;
                out.write(frame);
                if (outgoing.isEmpty()) out.flush();
            }
            out.flush();
        } catch (IOException | InterruptedException e) {
            close("Connection lost");
        } finally {
            closed = true;
            encoder.close();
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}

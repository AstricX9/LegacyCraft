package net.minecraft.network;

import net.minecraft.network.PacketRegistry.Direction;
import net.minecraft.network.packet.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.*;

final class PacketCodecTest {
    private PacketRegistry registry;
    private PacketCodec server, client;      // server decodes serverbound and encodes clientbound

    @BeforeEach
    void setUp() {
        registry = Protocol.standard();
        server = new PacketCodec(registry, Direction.SERVERBOUND);
        client = new PacketCodec(registry, Direction.CLIENTBOUND);
    }

    private void both(ProtocolState state) { server.setState(state); client.setState(state); }

    private static List<Packet> drain(PacketCodec codec) {
        List<Packet> out = new ArrayList<>();
        for (Packet p = codec.next(); p != null; p = codec.next()) out.add(p);
        return out;
    }

    private static Packet[] samplePlay() {
        return new Packet[] {
            new KeepAlivePacket(Long.MIN_VALUE), new KeepAlivePacket(42),
            new EntityMovePacket(70000, (short) -32767, (short) 0, (short) 32767, (byte) -128, (byte) 127, true),
            new BlockChangePacket(-30000000, 255, 29999999, 4095 << 4 | 7),
            new MultiBlockChangePacket(-5, 123456, new short[] {MultiBlockChangePacket.pack(15, 255, 0), 0}, new int[] {1, 70000}),
            new MultiBlockChangePacket(0, 0, new short[0], new int[0]),
            new DisconnectPacket("bye \u00fc"),
        };
    }

    @Test
    void everyPacketRoundTripsInItsOwnState() {
        server.setState(ProtocolState.HANDSHAKE);
        client.setState(ProtocolState.HANDSHAKE);
        client.setState(ProtocolState.HANDSHAKE);
        // Serverbound packets are encoded by the client side codec, which decodes clientbound.
        PacketCodec clientEnd = client, serverEnd = server;
        Packet hello = new HandshakePacket(HandshakePacket.PROTOCOL_VERSION, ProtocolState.LOGIN);
        serverEnd.feed(encodeFrom(clientEnd, hello));
        assertEquals(hello, serverEnd.next());

        both(ProtocolState.LOGIN);
        Packet start = new LoginStartPacket("Steve");
        serverEnd.feed(encodeFrom(clientEnd, start));
        assertEquals(start, serverEnd.next());
        Packet ok = new LoginSuccessPacket(1L, -2L, "Steve");
        clientEnd.feed(serverEnd.encode(ok));
        assertEquals(ok, clientEnd.next());

        both(ProtocolState.PLAY);
        PlayerPositionPacket move = new PlayerPositionPacket(1.5, 64, -3.25, 90f, -10f, true);
        serverEnd.feed(encodeFrom(clientEnd, move));
        assertEquals(move, serverEnd.next());
        for (Packet p : samplePlay()) {
            if (p instanceof KeepAlivePacket) {
                serverEnd.feed(encodeFrom(clientEnd, p));
                assertEquals(p, serverEnd.next());
            }
            clientEnd.feed(serverEnd.encode(p));
            assertEquals(p, clientEnd.next());
        }
        assertEquals(0, serverEnd.buffered());
        assertEquals(0, clientEnd.buffered());
    }

    /** The client end encodes serverbound packets: its codec's "opposite" direction. */
    private static byte[] encodeFrom(PacketCodec clientEnd, Packet serverbound) {
        // clientEnd decodes CLIENTBOUND, so it encodes SERVERBOUND, which is what we want.
        return clientEnd.encode(serverbound);
    }

    @Test
    void decodesWhenFedOneByteAtATime() {
        both(ProtocolState.PLAY);
        List<Packet> sent = List.of(samplePlay());
        PacketBuffer wire = PacketBuffer.writing();
        for (Packet p : sent) server.encode(p, wire);
        byte[] bytes = wire.toByteArray();
        List<Packet> got = new ArrayList<>();
        for (byte b : bytes) {
            client.feed(new byte[] {b});
            got.addAll(drain(client));
        }
        assertEquals(sent, got);
        assertEquals(0, client.buffered());
    }

    @Test
    void decodesManyPacketsFromOneRead() {
        both(ProtocolState.PLAY);
        PacketBuffer wire = PacketBuffer.writing();
        List<Packet> sent = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            Packet p = new EntityMovePacket(i, (short) i, (short) -i, (short) 0, (byte) i, (byte) 0, i % 2 == 0);
            sent.add(p);
            server.encode(p, wire);
        }
        client.feed(wire.array(), 0, wire.writerIndex());
        assertEquals(sent, drain(client));
    }

    @Test
    void smallPacketsStayRawAndLargeOnesShrinkWhenCompressionIsOn() {
        both(ProtocolState.PLAY);
        server.setCompressionThreshold(64);
        client.setCompressionThreshold(64);
        Packet small = new KeepAlivePacket(7);
        byte[] smallFrame = server.encode(small);
        assertEquals(1 + 1 + 1 + 8, smallFrame.length);              // length, 0, id, long
        short[] pos = new short[4000];
        int[] states = new int[4000];
        for (int i = 0; i < pos.length; i++) { pos[i] = MultiBlockChangePacket.pack(i & 15, i & 255, i >> 4 & 15); states[i] = 1; }
        Packet big = new MultiBlockChangePacket(3, 4, pos, states);
        byte[] bigFrame = server.encode(big);
        PacketCodec plain = new PacketCodec(registry, Direction.SERVERBOUND);
        plain.setState(ProtocolState.PLAY);
        assertTrue(bigFrame.length < plain.encode(big).length / 2, "compressed " + bigFrame.length);
        client.feed(smallFrame);
        client.feed(bigFrame);
        assertEquals(small, client.next());
        assertEquals(big, client.next());
        assertNull(client.next());
    }

    @Test
    void compressionKeepsWorkingAcrossManyPackets() {
        both(ProtocolState.PLAY);
        server.setCompressionThreshold(0);
        client.setCompressionThreshold(0);
        Random random = new Random(1);
        for (int i = 0; i < 2000; i++) {
            int n = random.nextInt(300);
            short[] pos = new short[n];
            int[] states = new int[n];
            for (int k = 0; k < n; k++) { pos[k] = (short) random.nextInt(); states[k] = random.nextInt(5000); }
            Packet p = new MultiBlockChangePacket(random.nextInt(), random.nextInt(), pos, states);
            client.feed(server.encode(p));
            assertEquals(p, client.next());
        }
        server.close();
        client.close();
    }

    private byte[] rawFrame(byte[] body) {
        PacketBuffer w = PacketBuffer.writing();
        w.writeVarInt(body.length).writeBytes(body, 0, body.length);
        return w.toByteArray();
    }

    private byte[] deflated(byte[] data) {
        Deflater d = new Deflater();
        d.setInput(data);
        d.finish();
        byte[] buf = new byte[data.length + 64];
        int n = d.deflate(buf);
        d.end();
        return java.util.Arrays.copyOf(buf, n);
    }

    private byte[] compressedBody(int declared, byte[] payloadBytes) {
        PacketBuffer w = PacketBuffer.writing();
        w.writeVarInt(declared).writeBytes(payloadBytes, 0, payloadBytes.length);
        return w.toByteArray();
    }

    @Test
    void aTinyFrameCannotDeclareAHugeDecompressedSize() {
        both(ProtocolState.PLAY);
        server.setCompressionThreshold(256);
        client.setCompressionThreshold(256);
        client.feed(rawFrame(compressedBody(8_000_000, new byte[10])));
        assertThrows(PacketException.class, client::next);
    }

    @Test
    void declaredAndRealDecompressedSizesMustMatch() {
        both(ProtocolState.PLAY);
        byte[] thousand = new byte[1000];
        for (int declared : new int[] {2000, 500}) {
            client = new PacketCodec(registry, Direction.CLIENTBOUND);
            client.setState(ProtocolState.PLAY);
            client.setCompressionThreshold(256);
            client.feed(rawFrame(compressedBody(declared, deflated(thousand))));
            assertThrows(PacketException.class, client::next);
        }
        client = new PacketCodec(registry, Direction.CLIENTBOUND);
        client.setState(ProtocolState.PLAY);
        client.setCompressionThreshold(256);
        client.feed(rawFrame(compressedBody(1000, new byte[] {1, 2, 3, 4, 5, 6})));   // not deflate data at all
        assertThrows(PacketException.class, client::next);
        client = new PacketCodec(registry, Direction.CLIENTBOUND);
        client.setState(ProtocolState.PLAY);
        client.setCompressionThreshold(256);
        client.feed(rawFrame(compressedBody(100, deflated(new byte[100]))));           // under the threshold but compressed
        assertThrows(PacketException.class, client::next);
    }

    @Test
    void badFramesAreRejected() {
        both(ProtocolState.PLAY);
        assertThrows(PacketException.class, () -> { client.feed(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F}); client.next(); });
        client = new PacketCodec(registry, Direction.CLIENTBOUND);
        client.setState(ProtocolState.PLAY);
        client.feed(new byte[] {0});                                                  // zero-length frame
        assertThrows(PacketException.class, client::next);
        client = new PacketCodec(registry, Direction.CLIENTBOUND);
        client.setState(ProtocolState.PLAY);
        client.feed(rawFrame(new byte[] {(byte) 99, 1, 2}));                          // unknown id
        assertThrows(PacketException.class, client::next);
        client = new PacketCodec(registry, Direction.CLIENTBOUND);
        client.setState(ProtocolState.PLAY);
        PacketBuffer body = PacketBuffer.writing();
        body.writeVarInt(0).writeLong(5).writeByte(1);                                // keepalive with a stray byte
        client.feed(rawFrame(body.toByteArray()));
        assertThrows(PacketException.class, client::next);
    }

    @Test
    void anIncompleteFrameWaitsAndOversizeIsCaughtFromTheHeaderAlone() {
        both(ProtocolState.PLAY);
        byte[] frame = server.encode(new KeepAlivePacket(1));
        client.feed(frame, 0, frame.length - 1);
        assertNull(client.next());
        client.feed(frame, frame.length - 1, 1);
        assertEquals(new KeepAlivePacket(1), client.next());
        PacketCodec c = new PacketCodec(registry, Direction.CLIENTBOUND);
        c.setState(ProtocolState.PLAY);
        PacketBuffer w = PacketBuffer.writing();
        w.writeVarInt(PacketCodec.MAX_FRAME + 1);
        c.feed(w.toByteArray());
        assertThrows(PacketException.class, c::next);       // rejected before any body arrives
    }

    @Test
    void packetsFromTheWrongStateOrDirectionCannotBeSent() {
        server.setState(ProtocolState.LOGIN);
        assertThrows(IllegalArgumentException.class, () -> server.encode(new PlayerPositionPacket(0, 0, 0, 0, 0, true)));
        server.setState(ProtocolState.PLAY);
        assertThrows(IllegalArgumentException.class, () -> server.encode(new LoginStartPacket("x")));
    }

    @Test
    void duplicateRegistrationsAreRefused() {
        PacketRegistry r = new PacketRegistry().register(ProtocolState.PLAY, Direction.CLIENTBOUND, 0, KeepAlivePacket.class, KeepAlivePacket::read);
        assertThrows(IllegalStateException.class, () -> r.register(ProtocolState.PLAY, Direction.CLIENTBOUND, 0, DisconnectPacket.class, DisconnectPacket::read));
        assertThrows(IllegalStateException.class, () -> r.register(ProtocolState.PLAY, Direction.CLIENTBOUND, 1, KeepAlivePacket.class, KeepAlivePacket::read));
    }

    @Test
    void lyingBlockChangeCountsAndNonFinitePositionsAreRejected() {
        both(ProtocolState.PLAY);
        PacketBuffer body = PacketBuffer.writing();
        body.writeVarInt(4).writeInt(0).writeInt(0).writeVarInt(60000);               // claims 60000 changes, sends none
        client.feed(rawFrame(body.toByteArray()));
        assertThrows(PacketException.class, client::next);
        PacketCodec s = new PacketCodec(registry, Direction.SERVERBOUND);
        s.setState(ProtocolState.PLAY);
        PacketBuffer nan = PacketBuffer.writing();
        nan.writeVarInt(1).writeDouble(Double.NaN).writeDouble(0).writeDouble(0).writeFloat(0).writeFloat(0).writeBoolean(true);
        s.feed(rawFrame(nan.toByteArray()));
        assertThrows(PacketException.class, s::next);
    }

    @Test
    void entityMovesQuantiseWithinTheirStatedPrecision() {
        Random random = new Random(7);
        for (int i = 0; i < 10000; i++) {
            double d = (random.nextDouble() * 2 - 1) * 7.99;
            assertTrue(EntityMovePacket.fits(d, 0, 0));
            double back = EntityMovePacket.fixed(d) / EntityMovePacket.SCALE;
            assertEquals(d, back, 0.5 / EntityMovePacket.SCALE + 1e-12);
            float deg = random.nextFloat() * 360f;
            float round = EntityMovePacket.degrees(EntityMovePacket.angle(deg));
            float diff = ((round - deg) % 360f + 540f) % 360f - 180f;      // signed difference, wrapped to [-180, 180)
            assertTrue(Math.abs(diff) <= 360f / 256f + 1e-3f, "angle " + deg + " -> " + round);
        }
        assertFalse(EntityMovePacket.fits(8.0, 0, 0));
        assertFalse(EntityMovePacket.fits(0, -9, 0));
        both(ProtocolState.PLAY);
        byte[] frame = server.encode(new EntityMovePacket(1, (short) 1, (short) 1, (short) 1, (byte) 1, (byte) 1, false));
        assertEquals(1 + 1 + 1 + 6 + 2 + 1, frame.length);        // length, id, entity id, three shorts, two angles, flag
    }

    @Test
    void multiBlockChangeIsSmallerThanSeparatePackets() {
        both(ProtocolState.PLAY);
        int n = 100;
        short[] pos = new short[n];
        int[] states = new int[n];
        int separate = 0;
        for (int i = 0; i < n; i++) {
            pos[i] = MultiBlockChangePacket.pack(i & 15, 60 + i % 5, i >> 4 & 15);
            states[i] = 16;
            separate += server.encode(new BlockChangePacket(i & 15, 60 + i % 5, i >> 4 & 15, 16)).length;
        }
        int batched = server.encode(new MultiBlockChangePacket(0, 0, pos, states)).length;
        assertTrue(batched * 3 < separate * 1, "batched " + batched + " vs separate " + separate);
    }

    @Test
    void randomBytesNeverCrashTheDecoderWithAnythingButAPacketException() {
        Random random = new Random(12345);
        for (ProtocolState state : ProtocolState.values()) {
            for (int threshold : new int[] {-1, 0, 100}) {
                for (int round = 0; round < 3000; round++) {
                    PacketCodec c = new PacketCodec(registry, Direction.SERVERBOUND);
                    c.setState(state);
                    c.setCompressionThreshold(threshold);
                    byte[] junk = new byte[random.nextInt(64) + 1];
                    random.nextBytes(junk);
                    if (random.nextBoolean()) junk[0] = (byte) (random.nextInt(40) + 1);   // plausible frame length
                    try {
                        c.feed(junk);
                        for (int k = 0; k < 8 && c.next() != null; k++) { }
                    } catch (PacketException expected) { }
                    c.close();
                }
            }
        }
    }

    @Test
    void mutatedValidFramesNeverCrashTheDecoderEither() {
        Random random = new Random(99);
        PacketCodec encoder = new PacketCodec(registry, Direction.SERVERBOUND);   // decodes serverbound, so encodes clientbound
        encoder.setState(ProtocolState.PLAY);
        for (int threshold : new int[] {-1, 0}) {
            encoder.setCompressionThreshold(threshold);
            for (Packet p : samplePlay()) {
                byte[] good = encoder.encode(p);
                for (int round = 0; round < 1500; round++) {
                    byte[] bad = good.clone();
                    for (int m = random.nextInt(3) + 1; m > 0; m--) bad[random.nextInt(bad.length)] = (byte) random.nextInt();
                    if (random.nextInt(4) == 0) bad = java.util.Arrays.copyOf(bad, random.nextInt(bad.length) + 1);
                    PacketCodec c = new PacketCodec(registry, Direction.CLIENTBOUND);
                    c.setState(ProtocolState.PLAY);
                    c.setCompressionThreshold(threshold);
                    try {
                        c.feed(bad);
                        for (int k = 0; k < 4 && c.next() != null; k++) { }
                    } catch (PacketException expected) { }
                    c.close();
                }
            }
        }
    }

    @Test
    void theInputBufferCompactsAndDoesNotGrowWithoutBound() {
        both(ProtocolState.PLAY);
        byte[] frame = server.encode(new KeepAlivePacket(9));
        for (int i = 0; i < 200_000; i++) {
            client.feed(frame);
            assertEquals(new KeepAlivePacket(9), client.next());
        }
        assertEquals(0, client.buffered());
    }
}

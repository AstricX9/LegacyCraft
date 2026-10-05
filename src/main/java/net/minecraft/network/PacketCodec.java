package net.minecraft.network;

import net.minecraft.network.PacketRegistry.Direction;

import java.util.Arrays;
import java.util.function.Function;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Turns packets into length-prefixed frames and a byte stream back into
 * packets, for one end of one connection (not thread-safe: use one per
 * connection, from the connection's own thread).
 *
 * <p>Frame: {@code VarInt length | body}. With compression off the body is
 * {@code VarInt id | payload}. With compression on it is {@code VarInt
 * uncompressedLength | data}: length 0 means the data is the raw id+payload
 * (it was under the threshold), otherwise the data is deflated and must
 * inflate to exactly that many bytes. Frame length is capped at
 * {@link #MAX_FRAME}, and decompressed size is capped both absolutely and by
 * what deflate can physically produce from the bytes received, so a tiny
 * frame can never make the server allocate megabytes.
 *
 * <p>Decoding is incremental: {@link #feed} whatever the socket read gave,
 * then call {@link #next()} until it returns null. A partial frame simply
 * waits for more bytes. Any {@link PacketException} means the stream is
 * corrupt or hostile and the connection should be closed.
 */
public final class PacketCodec {
    public static final int MAX_FRAME = 2 * 1024 * 1024;
    public static final int MAX_DECOMPRESSED = 8 * 1024 * 1024;
    private static final int MAX_FRAME_HEADER = 3;     // MAX_FRAME < 2^21 fits in three VarInt bytes

    private final PacketRegistry registry;
    private final Direction decodes;
    private ProtocolState state = ProtocolState.HANDSHAKE;
    private int threshold = -1;

    private final PacketBuffer scratch = PacketBuffer.writing(1024);
    private Deflater deflater;
    private Inflater inflater;
    private byte[] deflateBuf = new byte[1024];

    private byte[] in = new byte[4096];
    private int inStart, inEnd;

    /** @param decodes the direction this end receives: SERVERBOUND on the server, CLIENTBOUND on the client. */
    public PacketCodec(PacketRegistry registry, Direction decodes) {
        this.registry = registry;
        this.decodes = decodes;
    }

    public ProtocolState state() { return state; }
    public void setState(ProtocolState state) { this.state = state; }
    /** Packets whose id+payload is at least this many bytes are deflated; negative turns compression off. Both ends must agree. */
    public void setCompressionThreshold(int threshold) { this.threshold = threshold; }
    public int compressionThreshold() { return threshold; }

    // ---- encoding ------------------------------------------------------------------------------

    /** Appends one framed packet to {@code out}. */
    public void encode(Packet packet, PacketBuffer out) {
        int id = registry.idOf(state, decodes.opposite(), packet);
        scratch.clear();
        scratch.writeVarInt(id);
        packet.write(scratch);
        int size = scratch.writerIndex();
        if (size > MAX_FRAME) throw new PacketException("Packet too large: " + size);
        if (threshold < 0) {
            out.writeVarInt(size);
            out.writeBytes(scratch.array(), 0, size);
        } else if (size < threshold) {
            out.writeVarInt(size + 1);
            out.writeByte(0);
            out.writeBytes(scratch.array(), 0, size);
        } else {
            int compressed = deflate(scratch.array(), size);
            out.writeVarInt(PacketBuffer.varIntSize(size) + compressed);
            out.writeVarInt(size);
            out.writeBytes(deflateBuf, 0, compressed);
        }
    }

    public byte[] encode(Packet packet) {
        PacketBuffer out = PacketBuffer.writing(64);
        encode(packet, out);
        return out.toByteArray();
    }

    private int deflate(byte[] data, int length) {
        if (deflater == null) deflater = new Deflater(Deflater.BEST_SPEED);   // speed over ratio: this runs on the tick path
        deflater.setInput(data, 0, length);
        deflater.finish();
        int total = 0;
        while (!deflater.finished()) {
            if (total == deflateBuf.length) deflateBuf = Arrays.copyOf(deflateBuf, total * 2);
            total += deflater.deflate(deflateBuf, total, deflateBuf.length - total);
        }
        deflater.reset();
        return total;
    }

    // ---- decoding ------------------------------------------------------------------------------

    public void feed(byte[] data, int offset, int length) {
        if (length < 0 || offset < 0 || offset + length > data.length) throw new PacketException("Bad slice");
        if (inEnd + length > in.length) {
            if (inStart > 0) {
                System.arraycopy(in, inStart, in, 0, inEnd - inStart);
                inEnd -= inStart;
                inStart = 0;
            }
            if (inEnd + length > in.length) {
                long want = Math.max((long) inEnd + length, (long) in.length * 2);
                if (want > 2L * MAX_FRAME + 64) throw new PacketException("Too much unread data buffered");
                in = Arrays.copyOf(in, (int) want);
            }
        }
        System.arraycopy(data, offset, in, inEnd, length);
        inEnd += length;
    }

    public void feed(byte[] data) { feed(data, 0, data.length); }

    /** The next complete packet, or null if more bytes are needed. */
    public Packet next() {
        int available = inEnd - inStart;
        int frameLength = 0, header = 0;
        while (true) {
            if (header == available) return null;                    // length prefix not complete yet
            int b = in[inStart + header++] & 0xFF;
            frameLength |= (b & 0x7F) << (7 * (header - 1));
            if ((b & 0x80) == 0) break;
            if (header == MAX_FRAME_HEADER) throw new PacketException("Frame length prefix too long");
        }
        if (frameLength <= 0 || frameLength > MAX_FRAME) throw new PacketException("Invalid frame length " + frameLength);
        if (available - header < frameLength) return null;           // body not complete yet
        int bodyStart = inStart + header;
        inStart = bodyStart + frameLength;                           // consume first: a bad packet must not be re-read
        if (inStart == inEnd) inStart = inEnd = 0;

        byte[] data = in;
        int offset = bodyStart, length = frameLength;
        if (threshold >= 0) {
            PacketBuffer head = PacketBuffer.reading(in, bodyStart, frameLength);
            int declared = head.readVarInt();
            offset = head.readerIndex();
            length = bodyStart + frameLength - offset;
            if (declared != 0) {
                if (declared < threshold) throw new PacketException("Compressed packet under the threshold: " + declared);
                long limit = Math.min(MAX_DECOMPRESSED, (long) length * 1100L);
                if (declared > limit) throw new PacketException("Implausible decompressed size " + declared);
                data = inflate(in, offset, length, declared);
                offset = 0;
                length = declared;
            }
        }
        PacketBuffer body = PacketBuffer.reading(data, offset, length);
        int id = body.readVarInt();
        Function<PacketBuffer, ? extends Packet> decoder = registry.decoder(state, decodes, id);
        if (decoder == null) throw new PacketException("Unknown packet id " + id + " in " + state + "/" + decodes);
        Packet packet = decoder.apply(body);
        if (body.readableBytes() != 0)
            throw new PacketException(packet.getClass().getSimpleName() + " left " + body.readableBytes() + " unread bytes");
        return packet;
    }

    private byte[] inflate(byte[] src, int offset, int length, int expected) {
        if (inflater == null) inflater = new Inflater();
        byte[] out = new byte[expected];
        try {
            inflater.setInput(src, offset, length);
            int got = 0;
            while (got < expected) {
                int n = inflater.inflate(out, got, expected - got);
                if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary()))
                    throw new PacketException("Compressed data ended after " + got + " of " + expected + " bytes");
                got += n;
            }
            if (!inflater.finished() && inflater.inflate(new byte[1]) != 0) throw new PacketException("Compressed data longer than declared");
            return out;
        } catch (DataFormatException e) {
            throw new PacketException("Corrupt compressed data", e);
        } finally {
            inflater.reset();
        }
    }

    /** Bytes received but not yet consumed as whole packets. */
    public int buffered() { return inEnd - inStart; }

    /** Frees the native deflate and inflate state; call when the connection closes. */
    public void close() {
        if (deflater != null) deflater.end();
        if (inflater != null) inflater.end();
        deflater = null;
        inflater = null;
    }
}

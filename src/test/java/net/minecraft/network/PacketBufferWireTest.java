package net.minecraft.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PacketBufferWireTest {
    @Test
    void primitivesRoundTrip() {
        PacketBuffer w = PacketBuffer.writing(16);
        w.writeByte(-128).writeByte(127).writeBoolean(true).writeShort(Short.MIN_VALUE).writeShort(Short.MAX_VALUE)
            .writeInt(Integer.MIN_VALUE).writeInt(Integer.MAX_VALUE).writeLong(Long.MIN_VALUE).writeLong(Long.MAX_VALUE)
            .writeFloat(-1.5f).writeDouble(Math.PI).writeDouble(Double.NaN);
        PacketBuffer r = PacketBuffer.reading(w.toByteArray());
        assertEquals(-128, r.readByte());
        assertEquals(127, r.readByte());
        assertTrue(r.readBoolean());
        assertEquals(Short.MIN_VALUE, r.readShort());
        assertEquals(Short.MAX_VALUE, r.readShort());
        assertEquals(Integer.MIN_VALUE, r.readInt());
        assertEquals(Integer.MAX_VALUE, r.readInt());
        assertEquals(Long.MIN_VALUE, r.readLong());
        assertEquals(Long.MAX_VALUE, r.readLong());
        assertEquals(-1.5f, r.readFloat(), 0f);
        assertEquals(Math.PI, r.readDouble(), 0d);
        assertTrue(Double.isNaN(r.readDouble()));
        assertEquals(0, r.readableBytes());
    }

    @Test
    void varIntsRoundTripAndSizesMatch() {
        int[] values = {0, 1, 127, 128, 255, 16383, 16384, 2097151, 2097152, 268435455, 268435456, Integer.MAX_VALUE, -1, Integer.MIN_VALUE};
        for (int v : values) {
            PacketBuffer w = PacketBuffer.writing();
            w.writeVarInt(v);
            assertEquals(PacketBuffer.varIntSize(v), w.writerIndex(), "size of " + v);
            assertEquals(v, PacketBuffer.reading(w.toByteArray()).readVarInt());
        }
        long[] longs = {0, 127, 128, Long.MAX_VALUE, -1L, Long.MIN_VALUE};
        for (long v : longs) {
            PacketBuffer w = PacketBuffer.writing();
            w.writeVarLong(v);
            assertEquals(v, PacketBuffer.reading(w.toByteArray()).readVarLong());
        }
    }

    @Test
    void overlongVarIntsAreRejected() {
        byte[] six = {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01};
        assertThrows(PacketException.class, () -> PacketBuffer.reading(six).readVarInt());
        byte[] eleven = new byte[11];
        java.util.Arrays.fill(eleven, (byte) 0x80);
        assertThrows(PacketException.class, () -> PacketBuffer.reading(eleven).readVarLong());
    }

    @Test
    void truncatedReadsThrowAndDoNotConsume() {
        PacketBuffer r = PacketBuffer.reading(new byte[] {1, 2, 3});
        assertThrows(PacketException.class, r::readInt);
        assertEquals(3, r.readableBytes());
        assertThrows(PacketException.class, () -> PacketBuffer.reading(new byte[] {(byte) 0x80}).readVarInt());
        assertThrows(PacketException.class, () -> PacketBuffer.reading(new byte[0]).readByte());
    }

    @Test
    void stringsRoundTripAndAreBounded() {
        String s = "Gr\u00fc\u00dfe \u4e16\u754c \ud83d\ude00";
        PacketBuffer w = PacketBuffer.writing();
        w.writeString(s);
        assertEquals(s, PacketBuffer.reading(w.toByteArray()).readString(100));
        assertThrows(PacketException.class, () -> PacketBuffer.reading(w.toByteArray()).readString(3));
        // A length that lies about what follows must fail without allocating that much.
        PacketBuffer lie = PacketBuffer.writing();
        lie.writeVarInt(1_000_000).writeByte(65);
        assertThrows(PacketException.class, () -> PacketBuffer.reading(lie.toByteArray()).readString(2_000_000));
        PacketBuffer negative = PacketBuffer.writing();
        negative.writeVarInt(-5);
        assertThrows(PacketException.class, () -> PacketBuffer.reading(negative.toByteArray()).readString(10));
        assertThrows(PacketException.class, () -> PacketBuffer.writing().writeString("x".repeat(32768)));
    }

    @Test
    void byteArrayLengthsAreChecked() {
        PacketBuffer w = PacketBuffer.writing();
        w.writeByteArray(new byte[] {1, 2, 3});
        assertArrayEquals(new byte[] {1, 2, 3}, PacketBuffer.reading(w.toByteArray()).readByteArray(10));
        assertThrows(PacketException.class, () -> PacketBuffer.reading(w.toByteArray()).readByteArray(2));
        PacketBuffer lie = PacketBuffer.writing();
        lie.writeVarInt(1 << 30);
        assertThrows(PacketException.class, () -> PacketBuffer.reading(lie.toByteArray()).readByteArray(Integer.MAX_VALUE));
    }

    @Test
    void growsPastItsStartingCapacity() {
        PacketBuffer w = PacketBuffer.writing(16);
        for (int i = 0; i < 100_000; i++) w.writeInt(i);
        PacketBuffer r = PacketBuffer.reading(w.toByteArray());
        for (int i = 0; i < 100_000; i++) assertEquals(i, r.readInt());
    }

    @Test
    void readBuffersAreReadOnlyAndWritersWriteOnly() {
        assertThrows(IllegalStateException.class, () -> PacketBuffer.reading(new byte[4]).writeInt(1));
    }

    @Test
    void blockPositionsPackIncludingNegativesAndLimits() {
        int[][] cases = {{0, 0, 0}, {-1, 255, -1}, {33554431, 4095, 33554431}, {-33554432, 0, -33554432}, {12345, 64, -54321}};
        for (int[] c : cases) {
            PacketBuffer w = PacketBuffer.writing();
            w.writeBlockPos(c[0], c[1], c[2]);
            long p = PacketBuffer.reading(w.toByteArray()).readLong();
            assertEquals(c[0], PacketBuffer.unpackX(p));
            assertEquals(c[1], PacketBuffer.unpackY(p));
            assertEquals(c[2], PacketBuffer.unpackZ(p));
        }
    }

    @Test
    void slicesRespectTheirBounds() {
        byte[] data = {9, 9, 1, 2, 9};
        PacketBuffer r = PacketBuffer.reading(data, 2, 2);
        assertEquals(1, r.readByte());
        assertEquals(2, r.readByte());
        assertThrows(PacketException.class, r::readByte);
        assertThrows(PacketException.class, () -> PacketBuffer.reading(data, 3, 5));
    }
}

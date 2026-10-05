package net.minecraft.network;

import net.minecraft.network.PacketRegistry.Direction;
import net.minecraft.network.packet.*;

/** Micro-benchmark for the codec: {@code java -cp ... net.minecraft.network.PacketBench}. Not a test; numbers depend on the machine. */
public final class PacketBench {
    private static volatile long sink;

    private static double run(String name, Packet packet, int threshold) {
        PacketRegistry registry = Protocol.standard();
        PacketCodec server = new PacketCodec(registry, Direction.SERVERBOUND), client = new PacketCodec(registry, Direction.CLIENTBOUND);
        server.setState(ProtocolState.PLAY);
        client.setState(ProtocolState.PLAY);
        server.setCompressionThreshold(threshold);
        client.setCompressionThreshold(threshold);
        PacketBuffer wire = PacketBuffer.writing(1 << 16);
        int bytes = 0;
        double best = Double.MAX_VALUE;
        final int batch = 2000;
        for (int round = 0; round < 25; round++) {
            long start = System.nanoTime();
            for (int i = 0; i < batch; i++) {
                wire.clear();
                server.encode(packet, wire);
                bytes = wire.writerIndex();
                client.feed(wire.array(), 0, bytes);
                sink += client.next() == null ? 0 : 1;
            }
            double per = (System.nanoTime() - start) / (double) batch;
            if (round >= 5) best = Math.min(best, per);
        }
        System.out.printf("%-34s %6d B/frame  %9.0f ns/round-trip  %6.2f M packets/s%n", name, bytes, best, 1000.0 / best);
        return best;
    }

    public static void main(String[] args) {
        short[] pos = new short[256];
        int[] states = new int[256];
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < pos.length; i++) { pos[i] = MultiBlockChangePacket.pack(i & 15, 60 + (i >> 4), i >> 2 & 15); states[i] = 16 + r.nextInt(4); }
        Packet move = new EntityMovePacket(1234, (short) 100, (short) -20, (short) 3000, (byte) 40, (byte) 3, true);
        Packet single = new BlockChangePacket(-1234, 70, 5678, 16);
        Packet blocks = new MultiBlockChangePacket(3, -7, pos, states);
        run("EntityMove", move, -1);
        run("BlockChange", single, -1);
        run("MultiBlockChange x256", blocks, -1);
        run("MultiBlockChange x256 +deflate", blocks, 0);
    }
}

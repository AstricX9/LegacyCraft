package net.minecraft.network;

import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Packet buffer encoding of VarInts, strings and identifiers. */
final class PacketBufferTest {
    @Test
    void roundTripsVarIntsStringsAndIdentifiers() {
        byte[] encoded = PacketBuffer.writing().writeVarInt(2_097_151)
            .writeString("LegacyCraft")
            .writeResourceLocation(new ResourceLocation("minecraft:stone"))
            .toByteArray();
        PacketBuffer input = PacketBuffer.reading(encoded);
        assertEquals(2_097_151, input.readVarInt());
        assertEquals("LegacyCraft", input.readString(32));
        assertEquals(new ResourceLocation("minecraft:stone"), input.readResourceLocation());
    }
}

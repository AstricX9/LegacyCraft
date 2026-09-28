package net.minecraft.nbt;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** NBT reading and writing keeps every tag type exact. */
final class NbtIoTest {
    @Test
    void everyTagTypeRoundTripsWithItsExactType(Path directory) throws Exception {
        NbtTag.Compound root = new NbtTag.Compound();
        root.put("b", new NbtTag.Byte((byte) -3));
        root.put("s", new NbtTag.Short((short) 300));
        root.put("i", new NbtTag.Int(70000));
        root.put("l", new NbtTag.Long(-5L));
        root.put("f", new NbtTag.Float(1.5f));
        root.put("d", new NbtTag.Double(6.0E7));
        root.put("ba", new NbtTag.ByteArray(new byte[]{1, 2, 3}));
        root.put("ia", new NbtTag.IntArray(new int[]{4, 5}));
        root.put("str", new NbtTag.Str("héllo"));
        root.put("list", new NbtTag.NbtList(NbtTag.DOUBLE, List.of(new NbtTag.Double(1), new NbtTag.Double(2))));
        root.put("empty", new NbtTag.NbtList(NbtTag.END, List.of()));
        NbtTag.Compound nested = new NbtTag.Compound();
        nested.put("x", new NbtTag.Int(1));
        root.put("nested", nested);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.write(root, out);
        NbtTag.Compound back = NbtIo.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals((byte) -3, back.getByte("b"));
        assertEquals((short) 300, back.getShort("s"));
        assertEquals(70000, back.getInt("i"));
        assertEquals(-5L, back.getLong("l"));
        assertEquals(1.5f, ((NbtTag.Float) back.get("f")).value());
        assertEquals(6.0E7, ((NbtTag.Double) back.get("d")).value());
        assertArrayEquals(new byte[]{1, 2, 3}, back.getByteArray("ba"));
        assertArrayEquals(new int[]{4, 5}, back.getIntArray("ia"));
        assertEquals("héllo", back.getString("str"));
        assertEquals(2, back.getList("list").size());
        assertEquals(0, back.getList("empty").size());
        assertEquals(1, back.getCompound("nested").getInt("x"));

        Path file = directory.resolve("level.dat");
        NbtIo.writeCompressed(root, file);
        assertEquals(70000, NbtIo.readCompressed(file).getInt("i"));
    }
}

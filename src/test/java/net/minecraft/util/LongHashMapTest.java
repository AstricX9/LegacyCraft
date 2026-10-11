package net.minecraft.util;

import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The chunk map: same answers as a HashMap, insertion-ordered values, packed chunk keys spread out. */
final class LongHashMapTest {
    @Test
    void agreesWithAHashMapThroughGrowth() {
        LongHashMap<String> map = new LongHashMap<>();
        Map<Long, String> reference = new HashMap<>();
        Random random = new Random(7);
        for (int i = 0; i < 20_000; i++) {
            long key = random.nextInt(5000) - 2500L << 32 | random.nextInt(64);
            String value = "v" + i;
            assertEquals(reference.put(key, value), map.put(key, value));
        }
        assertEquals(reference.size(), map.size());
        for (Map.Entry<Long, String> entry : reference.entrySet()) {
            assertTrue(map.containsKey(entry.getKey()));
            assertEquals(entry.getValue(), map.get(entry.getKey()));
        }
        assertNull(map.get(Long.MIN_VALUE));
        assertFalse(map.containsKey(Long.MIN_VALUE));
    }

    @Test
    void putIfAbsentKeepsTheFirstValue() {
        LongHashMap<String> map = new LongHashMap<>();
        assertNull(map.putIfAbsent(0L, "first"));
        assertEquals("first", map.putIfAbsent(0L, "second"));
        assertEquals("first", map.get(0L));
        assertEquals(1, map.size());
    }

    @Test
    void valuesComeOutInTheOrderTheyWentIn() {
        LongHashMap<Integer> map = new LongHashMap<>();
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            map.put(World.chunkKey(50 - i, i * 3), i);
            expected.add(i);
        }
        map.put(World.chunkKey(50, 0), 0);
        assertEquals(expected, new ArrayList<>(map.values()));
        assertTrue(new LongHashMap<String>().isEmpty());
    }

    @Test
    void aSquareOfChunksSpreadsOverManySlots() {
        // Long.hashCode of these keys is x ^ z: only 64 distinct values for 4096 chunks.
        Set<Integer> hashes = new HashSet<>(), slots = new HashSet<>();
        for (int x = 0; x < 64; x++)
            for (int z = 0; z < 64; z++) {
                long key = World.chunkKey(x, z);
                hashes.add(Long.hashCode(key));
                slots.add(LongHashMap.hash(key) & 8191);
            }
        assertEquals(64, hashes.size());
        assertTrue(slots.size() > 3000, "only " + slots.size() + " slots used");
    }
}

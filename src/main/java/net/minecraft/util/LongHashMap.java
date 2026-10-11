package net.minecraft.util;

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A map from {@code long} keys to values, for packed chunk coordinates
 * (vanilla 1.8.9 keeps its loaded chunks in a LongHashMap as well). Keys
 * are never boxed, and they are spread with a 64-bit mixer before picking a
 * slot: a {@code HashMap<Long, V>} hashes a packed (x, z) pair as x ^ z,
 * so every diagonal of a square of chunks shares one bucket, the buckets
 * turn into trees, and each lookup then compares keys through reflection
 * (see docs/performance-mod-research.md). Values iterate in the order they
 * were first put. There is no removal yet, and like HashMap it is not
 * thread-safe.
 */
public final class LongHashMap<V> {
    private long[] keys = new long[16];
    private Object[] values = new Object[16];
    private int size;
    /** Open-addressing table of entry index + 1 (0 = free), kept at most half full. */
    private int[] slots = new int[32];

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    @SuppressWarnings("unchecked")
    public V get(long key) {
        int index = indexOf(key);
        return index < 0 ? null : (V) values[index];
    }

    public boolean containsKey(long key) { return indexOf(key) >= 0; }

    /** Sets the value for a key; returns the previous value, or null if there was none. */
    @SuppressWarnings("unchecked")
    public V put(long key, V value) {
        int index = indexOf(key);
        if (index >= 0) {
            V previous = (V) values[index];
            values[index] = value;
            return previous;
        }
        add(key, value);
        return null;
    }

    /** Sets the value only if the key has none; returns the value already there, or null if this one was added. */
    @SuppressWarnings("unchecked")
    public V putIfAbsent(long key, V value) {
        int index = indexOf(key);
        if (index >= 0) return (V) values[index];
        add(key, value);
        return null;
    }

    /** A live, read-only view of the values in insertion order. */
    public Collection<V> values() {
        return new AbstractCollection<>() {
            @Override public int size() { return size; }
            @Override public Iterator<V> iterator() {
                return new Iterator<>() {
                    private int next;
                    @Override public boolean hasNext() { return next < size; }
                    @SuppressWarnings("unchecked")
                    @Override public V next() {
                        if (next >= size) throw new NoSuchElementException();
                        return (V) values[next++];
                    }
                };
            }
        };
    }

    private int indexOf(long key) {
        int mask = slots.length - 1;
        for (int slot = hash(key) & mask; ; slot = (slot + 1) & mask) {
            int entry = slots[slot];
            if (entry == 0) return -1;
            if (keys[entry - 1] == key) return entry - 1;
        }
    }

    private void add(long key, V value) {
        if (size == keys.length) {
            keys = Arrays.copyOf(keys, size * 2);
            values = Arrays.copyOf(values, size * 2);
        }
        keys[size] = key;
        values[size] = value;
        size++;
        if (size * 2 <= slots.length) {
            insert(size - 1);
            return;
        }
        slots = new int[slots.length * 2];
        for (int index = 0; index < size; index++) insert(index);
    }

    private void insert(int index) {
        int mask = slots.length - 1;
        int slot = hash(keys[index]) & mask;
        while (slots[slot] != 0) slot = (slot + 1) & mask;
        slots[slot] = index + 1;
    }

    /** The MurmurHash3 64-bit finaliser: every key bit affects every hash bit. */
    static int hash(long key) {
        key ^= key >>> 33;
        key *= 0xff51afd7ed558ccdL;
        key ^= key >>> 33;
        key *= 0xc4ceb9fe1a85ec53L;
        key ^= key >>> 33;
        return (int) key;
    }
}

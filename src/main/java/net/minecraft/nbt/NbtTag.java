package net.minecraft.nbt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named Binary Tag values (master context §11.5). Types are kept exactly as
 * written — a byte never becomes an int — so data read from a vanilla save
 * round-trips unchanged.
 */
public sealed interface NbtTag {
    int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
        BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11;

    int type();

    record Byte(byte value) implements NbtTag { public int type() { return BYTE; } }
    record Short(short value) implements NbtTag { public int type() { return SHORT; } }
    record Int(int value) implements NbtTag { public int type() { return INT; } }
    record Long(long value) implements NbtTag { public int type() { return LONG; } }
    record Float(float value) implements NbtTag { public int type() { return FLOAT; } }
    record Double(double value) implements NbtTag { public int type() { return DOUBLE; } }
    record ByteArray(byte[] value) implements NbtTag { public int type() { return BYTE_ARRAY; } }
    record IntArray(int[] value) implements NbtTag { public int type() { return INT_ARRAY; } }
    record Str(String value) implements NbtTag { public int type() { return STRING; } }

    /** A list of tags, all of the same type. */
    record NbtList(int elementType, List<NbtTag> values) implements NbtTag {
        public NbtList { values = List.copyOf(values); }
        public int type() { return LIST; }
        public int size() { return values.size(); }
        public Compound compound(int index) { return (Compound) values.get(index); }
    }

    final class Compound implements NbtTag {
        private final Map<String, NbtTag> entries = new LinkedHashMap<>();

        public int type() { return COMPOUND; }
        public Map<String, NbtTag> entries() { return entries; }
        public void put(String name, NbtTag tag) { entries.put(name, tag); }
        public boolean has(String name) { return entries.containsKey(name); }
        public NbtTag get(String name) { return entries.get(name); }

        public byte getByte(String name) { return ((Byte) require(name)).value(); }
        public short getShort(String name) { return ((Short) require(name)).value(); }
        public int getInt(String name) { return ((Int) require(name)).value(); }
        public long getLong(String name) { return ((Long) require(name)).value(); }
        public String getString(String name) { return ((Str) require(name)).value(); }
        public byte[] getByteArray(String name) { return ((ByteArray) require(name)).value(); }
        public int[] getIntArray(String name) { return ((IntArray) require(name)).value(); }
        public Compound getCompound(String name) { return (Compound) require(name); }
        public NbtList getList(String name) {
            NbtTag tag = entries.get(name);
            return tag == null ? new NbtList(END, new ArrayList<>()) : (NbtList) tag;
        }

        private NbtTag require(String name) {
            NbtTag tag = entries.get(name);
            if (tag == null) throw new IllegalArgumentException("Missing NBT tag " + name + " (have " + entries.keySet() + ")");
            return tag;
        }

        @Override public String toString() { return entries.toString(); }
    }
}

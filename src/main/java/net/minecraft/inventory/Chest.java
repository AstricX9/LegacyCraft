package net.minecraft.inventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtTag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A chest's 27 slots and its lid (1.8.9 TileEntityChest, HYPOTHESIS:
 * recalled). The lid opens 0.1 a tick while anyone has it open and closes
 * the same way, with the open sound as it starts to lift and the close
 * sound as it passes halfway down.
 */
public final class Chest {
    public static final int SIZE = 27;
    public final ItemStack[] slots = new ItemStack[SIZE];
    /** numPlayersUsing: open screens looking into this chest. */
    public int viewers;
    public float lidAngle, prevLidAngle;

    public Slot slot(int index) { return Slot.of(slots, index); }

    /** Empties the chest, returning what was in it (for spilling when it breaks). */
    public List<ItemStack> takeContents() {
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            if (slots[i] != null) contents.add(slots[i]);
            slots[i] = null;
        }
        return contents;
    }

    /** What the lid did this tick, for its sound. */
    public enum LidSound { NONE, OPEN, CLOSE }

    /** TileEntityChest.update's lid part. */
    public LidSound tickLid() {
        prevLidAngle = lidAngle;
        LidSound sound = LidSound.NONE;
        if (viewers > 0 && lidAngle == 0f) sound = LidSound.OPEN;
        if (viewers == 0 && lidAngle > 0f || viewers > 0 && lidAngle < 1f) {
            float before = lidAngle;
            lidAngle = viewers > 0 ? lidAngle + 0.1f : lidAngle - 0.1f;
            if (lidAngle > 1f) lidAngle = 1f;
            if (lidAngle < 0.5f && before >= 0.5f) sound = LidSound.CLOSE;
            if (lidAngle < 0f) lidAngle = 0f;
        }
        return sound;
    }

    // ---- saving (TileEntityChest.writeToNBT: Items [{Slot, id, Count, Damage, tag}]) --------------

    public NbtTag.NbtList writeItems() {
        List<NbtTag> items = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = slots[i];
            if (stack == null) continue;
            NbtTag.Compound tag = new NbtTag.Compound();
            tag.put("Slot", new NbtTag.Byte((byte) i));
            tag.put("id", new NbtTag.Str(stack.item().name().toString()));
            tag.put("Count", new NbtTag.Byte((byte) stack.count()));
            tag.put("Damage", new NbtTag.Short((short) stack.damage()));
            NbtTag.Compound extra = stack.writeTag();
            if (extra != null) tag.put("tag", extra);
            items.add(tag);
        }
        return new NbtTag.NbtList(items.isEmpty() ? NbtTag.END : NbtTag.COMPOUND, items);
    }

    /** Reads {@link #writeItems}; {@code items} resolves LegacyCraft item names, unknown ones are dropped. */
    public static Chest read(NbtTag.NbtList list, Function<String, Item> items) {
        Chest chest = new Chest();
        if (list == null || items == null) return chest;
        for (NbtTag element : list.values()) {
            if (!(element instanceof NbtTag.Compound tag) || !(tag.get("id") instanceof NbtTag.Str id)) continue;
            int slot = tag.getByte("Slot") & 255;
            if (slot >= SIZE) continue;
            String name = id.value();
            Item item = items.apply(name.startsWith("minecraft:") ? name.substring(10) : name);
            int count = tag.getByte("Count");
            if (item == null || count < 1) continue;
            int damage = tag.has("Damage") ? tag.getShort("Damage") : 0;
            ItemStack stack = new ItemStack(item, Math.min(count, item.maxStackSize()), Math.max(0, Math.min(damage, item.maxDamage())));
            if (tag.get("tag") instanceof NbtTag.Compound extra) stack.readTag(extra);
            chest.slots[slot] = stack;
        }
        return chest;
    }
}

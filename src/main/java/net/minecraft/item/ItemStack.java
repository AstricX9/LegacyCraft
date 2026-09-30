package net.minecraft.item;

import net.minecraft.nbt.NbtTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable stack value with validated counts, splitting, merging and durability,
 * plus the parts of 1.8.9's item NBT that play uses: enchantments
 * ({@code ench}), an enchanted book's stored enchantments
 * ({@code StoredEnchantments}), the anvil's prior-work {@code RepairCost}
 * and a custom name ({@code display.Name}). Enchantments are kept as legacy
 * id to level, in the order they were added. Stacks with different tags
 * don't merge (ItemStack.areItemStackTagsEqual).
 */
public final class ItemStack {
    private final Item item;
    private int count;
    private int damage;
    private LinkedHashMap<Integer, Integer> enchantments;
    private LinkedHashMap<Integer, Integer> storedEnchantments;
    private int repairCost;
    private String customName;

    public ItemStack(Item item) { this(item, 1, 0); }
    public ItemStack(Item item, int count) { this(item, count, 0); }
    public ItemStack(Item item, int count, int damage) {
        this.item = Objects.requireNonNull(item, "item");
        if (count < 1 || count > item.maxStackSize())
            throw new IllegalArgumentException("Invalid stack count " + count + " for " + item);
        if (damage < 0 || damage > item.maxDamage())
            throw new IllegalArgumentException("Invalid damage " + damage + " for " + item);
        this.count = count;
        this.damage = damage;
    }

    public Item item() { return item; }
    public int count() { return count; }
    public int damage() { return damage; }
    public int remainingCapacity() { return item.maxStackSize() - count; }

    /** Same item, damage and tags (so the two may stack). */
    public boolean canMerge(ItemStack other) {
        return other != null && item == other.item && damage == other.damage && tagsEqual(other);
    }

    /** Whether the enchantments, stored enchantments, repair cost and name all match (areItemStackTagsEqual). */
    public boolean tagsEqual(ItemStack other) {
        return Objects.equals(orEmpty(enchantments), orEmpty(other.enchantments))
            && Objects.equals(orEmpty(storedEnchantments), orEmpty(other.storedEnchantments))
            && repairCost == other.repairCost && Objects.equals(customName, other.customName);
    }

    private static Map<Integer, Integer> orEmpty(Map<Integer, Integer> map) { return map == null ? Map.of() : map; }

    public ItemStack split(int amount) {
        if (amount < 1 || amount > count) throw new IllegalArgumentException("Invalid split amount " + amount);
        count -= amount;
        ItemStack part = new ItemStack(item, amount, damage);
        part.copyTagsFrom(this);
        return part;
    }
    public int mergeFrom(ItemStack other) {
        if (!canMerge(other)) return 0;
        int moved = Math.min(remainingCapacity(), other.count);
        count += moved;
        other.count -= moved;
        return moved;
    }
    /** @return true when the final item in the stack breaks. */
    public boolean damage(int amount) {
        if (!item.isDamageable() || amount <= 0) return false;
        damage += amount;
        if (damage <= item.maxDamage()) return false;
        count--;
        damage = 0;
        return count == 0;
    }
    public boolean isEmpty() { return count == 0; }
    public ItemStack copy() {
        ItemStack copy = new ItemStack(item, count, damage);
        copy.copyTagsFrom(this);
        return copy;
    }

    /** A copy of this stack with another count (the same item, damage and tags). */
    public ItemStack withCount(int newCount) {
        ItemStack copy = new ItemStack(item, newCount, damage);
        copy.copyTagsFrom(this);
        return copy;
    }

    /** This stack's tags on another item (an enchanted book made from a book). */
    public ItemStack asItem(Item other) {
        ItemStack copy = new ItemStack(other, count, 0);
        copy.copyTagsFrom(this);
        return copy;
    }

    private void copyTagsFrom(ItemStack other) {
        enchantments = other.enchantments == null ? null : new LinkedHashMap<>(other.enchantments);
        storedEnchantments = other.storedEnchantments == null ? null : new LinkedHashMap<>(other.storedEnchantments);
        repairCost = other.repairCost;
        customName = other.customName;
    }

    /** Sets the damage directly (anvil repairs); clamped to the item's durability. */
    public void setDamage(int value) { damage = Math.max(0, Math.min(item.maxDamage(), value)); }

    // ---- enchantments -----------------------------------------------------------------------

    /** Enchantment id to level, in the order added; empty when none (a live view: callers must not change it). */
    public Map<Integer, Integer> enchantments() { return enchantments == null ? Map.of() : enchantments; }
    /** An enchanted book's stored enchantments, in the order added; empty when none. */
    public Map<Integer, Integer> storedEnchantments() { return storedEnchantments == null ? Map.of() : storedEnchantments; }

    /** Whether it carries enchantments (isItemEnchanted); a book's stored ones don't count. */
    public boolean isEnchanted() { return enchantments != null && !enchantments.isEmpty(); }

    /** Adds an enchantment (ItemStack.addEnchantment). */
    public void addEnchantment(int id, int level) {
        if (enchantments == null) enchantments = new LinkedHashMap<>();
        enchantments.put(id, level);
    }

    /** Stores an enchantment on a book, keeping the higher level if it is already there (ItemEnchantedBook.addEnchantment). */
    public void addStoredEnchantment(int id, int level) {
        if (storedEnchantments == null) storedEnchantments = new LinkedHashMap<>();
        Integer have = storedEnchantments.get(id);
        if (have == null || have < level) storedEnchantments.put(id, level);
    }

    /** Replaces the enchantments; an empty map removes them. */
    public void setEnchantments(Map<Integer, Integer> map) {
        enchantments = map == null || map.isEmpty() ? null : new LinkedHashMap<>(map);
    }

    public int level(int enchantmentId) {
        Integer level = enchantments == null ? null : enchantments.get(enchantmentId);
        return level == null ? 0 : level;
    }

    // ---- anvil data -------------------------------------------------------------------------

    /** Levels of prior work (RepairCost), 0 when never worked on. */
    public int repairCost() { return repairCost; }
    public void setRepairCost(int value) { repairCost = value; }

    /** The anvil-given name, or null. */
    public String customName() { return customName; }
    public boolean hasCustomName() { return customName != null; }
    public void setCustomName(String name) { customName = name; }
    public void clearCustomName() { customName = null; }

    // ---- NBT --------------------------------------------------------------------------------

    /** The stack's {@code tag} compound as 1.8.9 writes it, or null when it has no tags. */
    public NbtTag.Compound writeTag() {
        if (enchantments == null && storedEnchantments == null && repairCost == 0 && customName == null) return null;
        NbtTag.Compound tag = new NbtTag.Compound();
        if (enchantments != null) tag.put("ench", enchantmentList(enchantments));
        if (storedEnchantments != null) tag.put("StoredEnchantments", enchantmentList(storedEnchantments));
        if (repairCost != 0) tag.put("RepairCost", new NbtTag.Int(repairCost));
        if (customName != null) {
            NbtTag.Compound display = new NbtTag.Compound();
            display.put("Name", new NbtTag.Str(customName));
            tag.put("display", display);
        }
        return tag;
    }

    /** Reads what {@link #writeTag} wrote (and the same parts of a vanilla tag); null does nothing. */
    public void readTag(NbtTag.Compound tag) {
        if (tag == null) return;
        enchantments = readEnchantments(tag.get("ench"));
        storedEnchantments = readEnchantments(tag.get("StoredEnchantments"));
        repairCost = tag.get("RepairCost") instanceof NbtTag.Int cost ? cost.value() : 0;
        customName = tag.get("display") instanceof NbtTag.Compound display && display.get("Name") instanceof NbtTag.Str name
            ? name.value() : null;
    }

    private static NbtTag.NbtList enchantmentList(Map<Integer, Integer> map) {
        List<NbtTag> list = new ArrayList<>();
        for (var entry : map.entrySet()) {
            NbtTag.Compound each = new NbtTag.Compound();
            each.put("id", new NbtTag.Short((short) (int) entry.getKey()));
            each.put("lvl", new NbtTag.Short((short) (int) entry.getValue()));
            list.add(each);
        }
        return new NbtTag.NbtList(list.isEmpty() ? NbtTag.END : NbtTag.COMPOUND, list);
    }

    private static LinkedHashMap<Integer, Integer> readEnchantments(NbtTag raw) {
        if (!(raw instanceof NbtTag.NbtList list) || list.size() == 0) return null;
        LinkedHashMap<Integer, Integer> map = new LinkedHashMap<>();
        for (NbtTag element : list.values()) {
            if (!(element instanceof NbtTag.Compound each)) continue;
            int id = number(each.get("id")), level = number(each.get("lvl"));
            map.put(id, level);
        }
        return map.isEmpty() ? null : map;
    }

    private static int number(NbtTag tag) {
        if (tag instanceof NbtTag.Short value) return value.value();
        if (tag instanceof NbtTag.Int value) return value.value();
        if (tag instanceof NbtTag.Byte value) return value.value();
        return 0;
    }
}

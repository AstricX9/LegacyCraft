package net.minecraft.item;

import java.util.Objects;

/** Mutable stack value with validated counts, splitting, merging and durability. */
public final class ItemStack {
    private final Item item;
    private int count;
    private int damage;

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
    public boolean canMerge(ItemStack other) {
        return other != null && item == other.item && damage == other.damage;
    }
    public ItemStack split(int amount) {
        if (amount < 1 || amount > count) throw new IllegalArgumentException("Invalid split amount " + amount);
        count -= amount;
        return new ItemStack(item, amount, damage);
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
    public ItemStack copy() { return new ItemStack(item, count, damage); }
}

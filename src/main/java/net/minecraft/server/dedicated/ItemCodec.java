package net.minecraft.server.dedicated;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.VanillaItems;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTag;
import net.minecraft.network.v47.Slot;
import net.minecraft.util.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * LegacyCraft item stacks to and from the wire. LegacyCraft has one item per
 * variant (red wool, red dye) tied to a vanilla id and data value; the wire
 * carries the numeric id (blocks below 256, items from 256) and the data
 * value, or the durability used for damageable items. Enchantments, names
 * and repair cost travel as the stack's NBT tag.
 */
public final class ItemCodec {
    private final BlockRegistry blocks;
    private final ItemRegistry items;

    public ItemCodec(BlockRegistry blocks, ItemRegistry items) {
        this.blocks = blocks;
        this.items = items;
    }

    public Slot toSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Slot.EMPTY;
        Item item = stack.item();
        int id = numericId(item.vanillaId());
        if (id < 0) return Slot.EMPTY;
        int damage = item.isDamageable() ? stack.damage() : item.vanillaData();
        NbtTag.Compound tag = stack.writeTag();
        return new Slot(id, stack.count(), damage, tag == null ? null : encode(tag));
    }

    /** The stack a wire slot stands for, or null for an empty slot or an item LegacyCraft doesn't have. */
    public ItemStack fromSlot(Slot slot) {
        if (slot == null || slot.isEmpty() || slot.count() <= 0) return null;
        ResourceLocation name = vanillaName(slot.itemId());
        if (name == null) return null;
        Item item = items.fromVanilla(name, slot.damage());
        if (item == null) return null;
        ItemStack stack = new ItemStack(item, Math.min(slot.count(), 64), item.isDamageable() ? slot.damage() : 0);
        if (slot.nbt() != null) stack.readTag(decode(slot.nbt()));
        return stack;
    }

    /** The numeric id for a vanilla name: the block id for blocks, the item table otherwise; -1 if unknown. */
    public int numericId(ResourceLocation vanilla) {
        int itemId = VanillaItems.itemId(vanilla.path());
        if (itemId >= 0) return itemId;
        Block block = blocks.get(vanilla);
        return block == null ? -1 : block.registryId();
    }

    public ResourceLocation vanillaName(int id) {
        if (id < 0) return null;
        if (id < 256) {
            if (id >= blocks.size()) return null;
            Block block = blocks.byId(id);
            return block.isAir() ? null : block.name();
        }
        String name = VanillaItems.itemName(id);
        return name == null ? null : new ResourceLocation("minecraft", name);
    }

    private static byte[] encode(NbtTag.Compound tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.write(tag, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static NbtTag.Compound decode(byte[] nbt) {
        try {
            return NbtIo.read(new ByteArrayInputStream(nbt));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}

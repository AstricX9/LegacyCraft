package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.ResourceLocation;

/**
 * Item definition loaded from its own generated JSON document (one per
 * 1.8.9 item model), plus the variants made from one at load time (spawn
 * eggs, potions, banners, the enchanted golden apple). Each knows the
 * vanilla item ID and data value it stands for ({@link VanillaItems}).
 */
public final class Item {
    public enum UseAction { NONE, EAT, DRINK, BLOCK, BOW }

    /** Items whose data value changes what they are beyond their model. */
    public enum Kind { NORMAL, SPAWN_EGG, POTION, ENCHANTED_APPLE, BANNER }

    private final int registryId;
    private final ResourceLocation name;
    private final int maxStackSize;
    private final int maxDamage;
    private final UseAction useAction;
    private final int food;
    private final float saturation;
    private final ResourceLocation model;
    private final Block placedBlock;
    private final int placedMetadata;
    private ResourceLocation vanillaId;
    private int vanillaData;
    private Kind kind = Kind.NORMAL;

    Item(int registryId, ResourceLocation name, int maxStackSize, int maxDamage,
         UseAction useAction, int food, float saturation, ResourceLocation model,
         Block placedBlock, int placedMetadata) {
        this.registryId = registryId;
        this.name = name;
        this.maxStackSize = maxStackSize;
        this.maxDamage = maxDamage;
        this.useAction = useAction;
        this.food = food;
        this.saturation = saturation;
        this.model = model;
        this.placedBlock = placedBlock;
        this.placedMetadata = placedMetadata;
    }

    public int registryId() { return registryId; }
    public ResourceLocation name() { return name; }
    public int maxStackSize() { return maxStackSize; }
    public int maxDamage() { return maxDamage; }
    public boolean isDamageable() { return maxDamage > 0; }
    public UseAction useAction() { return useAction; }
    public boolean isFood() { return food > 0; }
    public int food() { return food; }
    public float saturation() { return saturation; }
    public ResourceLocation model() { return model; }
    public Block placedBlock() { return placedBlock; }
    /** Metadata the item places before orientation rules apply (the item's variant, e.g. birch planks = 2). */
    public int placedMetadata() { return placedMetadata; }
    public BlockState placedState() { return placedBlock == null ? null : BlockState.of(placedBlock, placedMetadata); }

    /** The vanilla item ID this stands for ("minecraft:dye" for red dye). */
    public ResourceLocation vanillaId() { return vanillaId == null ? name : vanillaId; }
    /** Its vanilla data value (1 for red dye; for damageable items durability is kept on the stack instead). */
    public int vanillaData() { return vanillaData; }
    public Kind kind() { return kind; }
    /** Whether this came from another item's model (a spawn egg, potion, banner colour or the enchanted apple). */
    public boolean isVariant() { return kind != Kind.NORMAL; }

    void setVanilla(ResourceLocation id, int data) {
        vanillaId = id;
        vanillaData = data;
    }

    /** A variant drawn with this item's model: its own registry slot, name, kind and vanilla data value. */
    Item variant(int registryId, ResourceLocation name, Kind kind, int data, UseAction action, int stackSize) {
        Item copy = new Item(registryId, name, stackSize, maxDamage, action, food, saturation, model,
            kind == Kind.BANNER ? placedBlock : null, placedMetadata);
        copy.kind = kind;
        copy.vanillaId = vanillaId();
        copy.vanillaData = data;
        return copy;
    }

    @Override public String toString() { return name.toString(); }
}

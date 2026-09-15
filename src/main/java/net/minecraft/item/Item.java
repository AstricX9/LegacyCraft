package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

/** Immutable item definition loaded from its own generated JSON document. */
public final class Item {
    public enum UseAction { NONE, EAT, DRINK, BLOCK, BOW }

    private final int registryId;
    private final ResourceLocation name;
    private final int maxStackSize;
    private final int maxDamage;
    private final UseAction useAction;
    private final int food;
    private final float saturation;
    private final ResourceLocation model;
    private final Block placedBlock;

    Item(int registryId, ResourceLocation name, int maxStackSize, int maxDamage,
         UseAction useAction, int food, float saturation, ResourceLocation model,
         Block placedBlock) {
        this.registryId = registryId;
        this.name = name;
        this.maxStackSize = maxStackSize;
        this.maxDamage = maxDamage;
        this.useAction = useAction;
        this.food = food;
        this.saturation = saturation;
        this.model = model;
        this.placedBlock = placedBlock;
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

    @Override public String toString() { return name.toString(); }
}

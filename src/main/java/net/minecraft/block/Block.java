package net.minecraft.block;

import net.minecraft.util.ResourceLocation;

/** Immutable registry entry. State properties will layer on top of this block definition. */
public final class Block {
    public enum RenderType { MODEL, INVISIBLE }

    private final int registryId;
    private final ResourceLocation name;
    private final String material;
    private final boolean solid;
    private final boolean opaque;
    private final boolean translucent;
    private final float hardness;
    private final RenderType renderType;
    private final int tintColor;
    private final ResourceLocation modelOverride;

    Block(int registryId, ResourceLocation name, String material, boolean solid, boolean opaque,
          boolean translucent, float hardness, RenderType renderType, int tintColor,
          ResourceLocation modelOverride) {
        this.registryId = registryId;
        this.name = name;
        this.material = material;
        this.solid = solid;
        this.opaque = opaque;
        this.translucent = translucent;
        this.hardness = hardness;
        this.renderType = renderType;
        this.tintColor = tintColor;
        this.modelOverride = modelOverride;
    }

    public int registryId() { return registryId; }
    public ResourceLocation name() { return name; }
    public String material() { return material; }
    public boolean isSolid() { return solid; }
    public boolean isOpaque() { return opaque; }
    public boolean isTranslucent() { return translucent; }
    public boolean isAir() { return renderType == RenderType.INVISIBLE; }
    public float hardness() { return hardness; }
    public RenderType renderType() { return renderType; }
    public int tintColor() { return tintColor; }
    public ResourceLocation modelOverride() { return modelOverride; }

    @Override public String toString() { return name.toString(); }
}

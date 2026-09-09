package net.minecraft.client.renderer.block.model;

import net.minecraft.util.ResourceLocation;

import java.util.List;

public record BakedModel(List<BakedQuad> quads, ResourceLocation particleTexture) {
    public BakedModel {
        quads = List.copyOf(quads);
    }
}

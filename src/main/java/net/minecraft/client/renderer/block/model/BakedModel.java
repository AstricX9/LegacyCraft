package net.minecraft.client.renderer.block.model;

import net.minecraft.util.ResourceLocation;

import java.util.List;

/** @param ambientOcclusion the model's {@code ambientocclusion} flag (false for torches, flowers and other cross models) */
public record BakedModel(List<BakedQuad> quads, ResourceLocation particleTexture, boolean ambientOcclusion) {
    public BakedModel {
        quads = List.copyOf(quads);
    }
}

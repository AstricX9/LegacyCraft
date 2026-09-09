package net.minecraft.client.renderer.block.model;

import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;

public record BakedQuad(
    float fromX, float fromY, float fromZ,
    float toX, float toY, float toZ,
    Direction face, Direction cullFace,
    ResourceLocation texture, int tintIndex,
    float u0, float v0, float u1, float v1,
    int rotationX, int rotationY
) {}

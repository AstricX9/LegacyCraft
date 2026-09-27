package net.minecraft.client.renderer.block.model;

import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;

/**
 * One fully transformed model face: four block-space vertices (0-1 per axis,
 * element and variant rotation already applied) with their sprite-space UVs
 * (0-1 across the texture), in the vertex order used for {@code GL_QUADS}.
 */
public record BakedQuad(float[] positions, float[] uvs, Direction face, Direction cullFace,
                        ResourceLocation texture, int tintIndex, boolean shade) {
    public BakedQuad {
        if (positions.length != 12 || uvs.length != 8) throw new IllegalArgumentException("A quad has four vertices");
        positions = positions.clone();
        uvs = uvs.clone();
    }

    public float x(int vertex) { return positions[vertex * 3]; }
    public float y(int vertex) { return positions[vertex * 3 + 1]; }
    public float z(int vertex) { return positions[vertex * 3 + 2]; }
    public float u(int vertex) { return uvs[vertex * 2]; }
    public float v(int vertex) { return uvs[vertex * 2 + 1]; }

    public float minX() { return Math.min(Math.min(x(0), x(1)), Math.min(x(2), x(3))); }
    public float maxX() { return Math.max(Math.max(x(0), x(1)), Math.max(x(2), x(3))); }
    public float minY() { return Math.min(Math.min(y(0), y(1)), Math.min(y(2), y(3))); }
    public float maxY() { return Math.max(Math.max(y(0), y(1)), Math.max(y(2), y(3))); }
    public float minZ() { return Math.min(Math.min(z(0), z(1)), Math.min(z(2), z(3))); }
    public float maxZ() { return Math.max(Math.max(z(0), z(1)), Math.max(z(2), z(3))); }
}

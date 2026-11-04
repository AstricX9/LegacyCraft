package net.minecraft.client.additions.api;

import net.minecraft.world.Dimension;

/** Optional rendering callbacks. The default implementation preserves native rendering. */
public interface GraphicsProfile {
    GraphicsProfile NONE = new GraphicsProfile() {};
    record Fog(float start, float end, float density) {}
    default boolean enabled() { return false; }
    default boolean replacesSky() { return false; }
    default boolean replacesShaderPacks() { return false; }
    default boolean qualityFog() { return false; }
    default double skyFarPlane(float distance) { return distance * 2.0; }
    default float visibility(float blindness) { return 1f; }
    default float sunriseOpacity(float rain) { return 1f; }
    default float celestialOpacity(float rain, float visibility) { return 1f; }
    default float[] atmosphere(float[] colour, float angle, float rain, float thunder) { return colour; }
    default float[] lightmap(float red, float green, float blue, float[] original, float sky, float sun, float gamma, Dimension dimension) { return original; }
    default float sunBrightness(float original, float rain, float thunder, Dimension dimension) { return original; }
    default Fog fog(Fog original, float distance, float fov, float aspect, boolean radial, Dimension dimension, String liquid, float blindness) { return original; }
    default void drawSkyBackground(float[] sky, float[] fog, boolean submerged) {}
}

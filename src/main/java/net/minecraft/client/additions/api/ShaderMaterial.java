package net.minecraft.client.additions.api;

import java.util.function.Function;

/** Block IDs supplied by an optional rendering backend to the generic terrain mesher. */
public interface ShaderMaterial {
    default String property(String key) { return null; }
    default boolean cloudsEnabled() { return true; }
    default boolean underwaterOverlayEnabled() { return true; }
    int blockId(String block, Function<String, String> property);
}

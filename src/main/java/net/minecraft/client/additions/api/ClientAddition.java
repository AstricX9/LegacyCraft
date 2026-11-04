package net.minecraft.client.additions.api;

import net.minecraft.client.entity.Player;
import net.minecraft.client.gui.GuiContext;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shaders.Shaders;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Java entry point for a trusted client module loaded from additions/. */
public interface ClientAddition extends AutoCloseable {
    record Hud(GuiContext gui, Player player, int width, int height, int fps) {}
    record ShaderSelection(Path pack, boolean iris) {}
    record SettingsAction(String title, boolean enabled, Runnable action) {}
    /** Input is delivered even while disabled, so modules can discard queued key presses. */
    default Player.Input input(AdditionContext context, Player.Input input, GameSettings settings) { return input; }
    default void reset() {}
    default float nightVision(AdditionContext context, float original) { return original; }
    default float fovMultiplier(AdditionContext context, float original) { return original; }
    default boolean hurtTilt(AdditionContext context, boolean original) { return original; }
    default void hud(AdditionContext context, Hud hud) {}
    default boolean crosshair(AdditionContext context, GuiContext gui, int width, int height) { return false; }
    default GraphicsProfile graphics(AdditionContext context) { return GraphicsProfile.NONE; }
    default ShaderSelection shaderSelection(AdditionContext context) { return null; }
    default Shaders.Backend shaderBackend() { return null; }
    default void afterWorld(AdditionContext context, int width, int height, Consumer<String> report) {}
    default String settingsNote(AdditionContext context) { return ""; }
    default List<SettingsAction> settingsActions(AdditionContext context, GuiContext gui, GuiScreen parent) { return List.of(); }
    @Override default void close() {}
}

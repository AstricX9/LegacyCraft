package net.legacycraft.additions.steadyFov;
import net.minecraft.client.additions.api.*;
import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.gui.*;
import static org.lwjgl.opengl.GL11.*;
public final class SteadyFovAddition implements ClientAddition {
    @Override public float fovMultiplier(AdditionContext context,float original) { return 1f; }
}

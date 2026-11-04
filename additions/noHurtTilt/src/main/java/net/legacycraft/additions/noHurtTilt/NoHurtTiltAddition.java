package net.legacycraft.additions.noHurtTilt;
import net.minecraft.client.additions.api.*;
import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.gui.*;
import static org.lwjgl.opengl.GL11.*;
public final class NoHurtTiltAddition implements ClientAddition {
    @Override public boolean hurtTilt(AdditionContext context,boolean original) { return false; }
}

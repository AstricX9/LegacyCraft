package net.legacycraft.additions.fullBright;
import net.minecraft.client.additions.api.*;
import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.gui.*;
import static org.lwjgl.opengl.GL11.*;
public final class FullBrightAddition implements ClientAddition {
    @Override public float nightVision(AdditionContext context,float original) {
        return Math.max(original,(float)context.number("brightness")/100f);
    }
}

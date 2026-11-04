package net.legacycraft.additions.fpsCounter;
import net.minecraft.client.additions.api.*;
import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.gui.*;
import static org.lwjgl.opengl.GL11.*;
public final class FpsCounterAddition implements ClientAddition {
    @Override public void hud(AdditionContext context,Hud hud) {
        String text=hud.fps()+" fps";
        hud.gui().font().drawStringWithShadow(text,hud.width()-2-hud.gui().font().getStringWidth(text),2,0xFFFFFF);
    }
}

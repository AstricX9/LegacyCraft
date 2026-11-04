package net.legacycraft.additions.coordinates;
import net.minecraft.client.additions.api.*;
import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.gui.*;
import static org.lwjgl.opengl.GL11.*;
public final class CoordinatesAddition implements ClientAddition {
    @Override public void hud(AdditionContext context,Hud hud) {
        Player p=hud.player();
        String[] facings={"south","west","north","east"};
        String facing=facings[Math.floorMod((int)Math.floor((p.yaw+180f)*4f/360f+.5f),4)];
        hud.gui().font().drawStringWithShadow(String.format(java.util.Locale.ROOT,"XYZ: %.1f / %.1f / %.1f",p.x,p.y,p.z),2,2,0xFFFFFF);
        if(context.settingOn("showFacing")) hud.gui().font().drawStringWithShadow("Facing: "+facing,2,12,0xFFFFFF);
    }
}

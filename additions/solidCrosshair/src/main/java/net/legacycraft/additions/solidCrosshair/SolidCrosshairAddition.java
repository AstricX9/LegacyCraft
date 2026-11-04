package net.legacycraft.additions.solidCrosshair;
import net.minecraft.client.additions.api.*;
import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.gui.*;
import static org.lwjgl.opengl.GL11.*;
public final class SolidCrosshairAddition implements ClientAddition {
    @Override public boolean crosshair(AdditionContext context,GuiContext gui,int width,int height) {
        gui.textures().bind(GuiTextures.ICONS);
        glColor4f(.1f,.1f,.1f,1f);
        for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}})
            Gui.drawTexturedModalRect(width/2-7+d[0],height/2-7+d[1],0,0,16,16);
        int rgb=colour(context.setting("color"));
        glColor4f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,1f);
        Gui.drawTexturedModalRect(width/2-7,height/2-7,0,0,16,16);
        glColor4f(1,1,1,1); return true;
    }
    public static int colour(String name) {
        if(name==null) return 0xFFFFFF;
        return switch(name) { case "Green"->0x55FF55; case "Yellow"->0xFFFF55; case "Cyan"->0x55FFFF; case "Red"->0xFF5555; default->0xFFFFFF; };
    }
}

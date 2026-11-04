package net.legacycraft.additions.toggleSneak;
import net.minecraft.client.additions.api.*;
import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.gui.*;
import static org.lwjgl.opengl.GL11.*;
public final class ToggleSneakAddition implements ClientAddition {
    private boolean latched;
    @Override public Player.Input input(AdditionContext context,Player.Input input,GameSettings settings) {
        int presses=0; while(settings.keyBindSneak.isPressed()) presses++;
        latched=context.enabled() && ((presses & 1)==1 ? !latched : latched);
        if(!latched) return input;
        return new Player.Input(input.forward(),input.back(),input.left(),input.right(),input.jump(),input.sneak() || latched, input.sprint());
    }
    @Override public void reset() { latched=false; }
}

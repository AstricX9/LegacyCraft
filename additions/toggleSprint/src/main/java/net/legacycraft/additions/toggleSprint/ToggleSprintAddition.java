package net.legacycraft.additions.toggleSprint;
import net.minecraft.client.additions.api.*;
import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.gui.*;
import static org.lwjgl.opengl.GL11.*;
public final class ToggleSprintAddition implements ClientAddition {
    private boolean latched;
    @Override public Player.Input input(AdditionContext context,Player.Input input,GameSettings settings) {
        int presses=0; while(settings.keyBindSprint.isPressed()) presses++;
        latched=context.enabled() && ((presses & 1)==1 ? !latched : latched);
        if(!latched) return input;
        return new Player.Input(input.forward(),input.back(),input.left(),input.right(),input.jump(),input.sneak(), input.sprint() || latched);
    }
    @Override public void reset() { latched=false; }
}

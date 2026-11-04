package example;

import net.minecraft.client.additions.api.AdditionContext;
import net.minecraft.client.additions.api.ClientAddition;

/** Copy this module and its manifest, then implement the hooks your mod needs. */
public final class MyAddition implements ClientAddition {
    @Override public void hud(AdditionContext context, Hud hud) {
        hud.gui().font().drawStringWithShadow(context.definition().name(), 2, 24, 0xFFFFFF);
    }
}

package net.minecraft.client.additions;

import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.KeyBinding;

/**
 * The Toggle Sprint and Toggle Sneak additions: a press of the key latches it
 * on and the next press lets go. Presses are drained every tick even while
 * the addition is off, so turning it on doesn't replay old presses.
 */
public final class ToggleKeys {
    private boolean sprint, sneak;

    /** This tick's movement keys with any latched sprint or sneak added. */
    public Player.Input apply(Player.Input input, KeyBinding sprintKey, KeyBinding sneakKey, boolean toggleSprint, boolean toggleSneak) {
        sprint = toggle(sprint, sprintKey, toggleSprint);
        sneak = toggle(sneak, sneakKey, toggleSneak);
        if (!sprint && !sneak) return input;
        return new Player.Input(input.forward(), input.back(), input.left(), input.right(), input.jump(),
            input.sneak() || sneak, input.sprint() || sprint);
    }

    public void reset() { sprint = sneak = false; }

    private static boolean toggle(boolean latched, KeyBinding key, boolean enabled) {
        int presses = 0;
        while (key.isPressed()) presses++;
        if (!enabled) return false;
        return (presses & 1) == 1 ? !latched : latched;
    }
}

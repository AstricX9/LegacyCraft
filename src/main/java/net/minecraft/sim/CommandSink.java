package net.minecraft.sim;

/** Receives what the player asked to do. The simulation side implements it (directly in single-player, via the network in multiplayer). */
@FunctionalInterface
public interface CommandSink {
    void submit(PlayerCommand command);
}

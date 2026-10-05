package net.minecraft.sim;

/** Receives what happened. The viewing side implements it (a renderer in single-player, a connection in multiplayer). */
@FunctionalInterface
public interface EventSink {
    void emit(GameEvent event);
}

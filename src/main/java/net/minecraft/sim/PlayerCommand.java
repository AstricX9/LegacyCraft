package net.minecraft.sim;

/**
 * Something the player asked to do, client to simulation. Commands are
 * requests, never facts: the simulation checks every one (reach, line of
 * sight, held item, mining time, game mode) and may ignore or correct it.
 * Immutable values with no references into either side's live objects, so
 * they can be queued, sent over a socket, or replayed in a test.
 *
 * <p>Faces are {@link net.minecraft.util.Direction} ordinals (DOWN, UP, NORTH,
 * SOUTH, WEST, EAST). Entities are the runtime ids from the EntityManager.
 */
public sealed interface PlayerCommand {

    /** Where the client says the player is and what they are doing; the simulation validates and may answer with {@link GameEvent.PlayerPosition}. */
    record Move(double x, double y, double z, float yaw, float pitch, boolean onGround, boolean sneaking, boolean sprinting)
        implements PlayerCommand {}

    /** Mining a block: START when the button goes down on it, ABORT if it is released or aimed away, FINISH when the client's progress completes. */
    record Dig(Stage stage, int x, int y, int z, int face) implements PlayerCommand {
        public enum Stage { START, ABORT, FINISH }
    }

    /** Right click on a block face: place the held block, or activate what is there. hit* is the point on the face, 0-1. */
    record UseOnBlock(int x, int y, int z, int face, float hitX, float hitY, float hitZ) implements PlayerCommand {}

    /** Right click on nothing (or something out of reach): use the held item in the air. */
    record UseItem() implements PlayerCommand {}

    /** The use key was released after holding an item for {@code heldTicks} (bow draw, eating, blocking). */
    record ReleaseUse(int heldTicks) implements PlayerCommand {}

    /** Left or right click on an entity. */
    record UseEntity(int entityId, Kind kind) implements PlayerCommand {
        public enum Kind { INTERACT, ATTACK }
    }

    /** Hotbar slot 0-8 selected. */
    record SelectSlot(int slot) implements PlayerCommand {}

    /** Middle click on a block (Creative: put its item in the hand). */
    record PickBlock(int x, int y, int z) implements PlayerCommand {}

    /** Steering input while riding (a boat): forward -1..1 and the way the player faces. */
    record Steer(float forward, float yaw) implements PlayerCommand {}

    /** Leave whatever the player is riding. */
    record Dismount() implements PlayerCommand {}

    /** Clicked "respawn" on the death screen. */
    record Respawn() implements PlayerCommand {}

    /** Chat or a slash command, as typed. */
    record Chat(String text) implements PlayerCommand {
        public static final int MAX_LENGTH = 256;
        public Chat { if (text.length() > MAX_LENGTH) throw new IllegalArgumentException("Chat too long"); }
    }
}

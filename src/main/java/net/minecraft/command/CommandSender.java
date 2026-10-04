package net.minecraft.command;

import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.LevelInfo;

/**
 * Who runs a command and what commands may do to the game. In
 * singleplayer this is the local player on the integrated server; the
 * client implements it, tests supply a fake.
 */
public interface CommandSender {
    String name();

    /** Shows a line in this sender's chat ({@code playerChat} lines are hidden by "Commands Only" chat). */
    void addChatMessage(String message, boolean playerChat);

    /** Whether commands (cheats) are allowed for this sender. */
    boolean commandsAllowed();

    LevelInfo level();

    /** Position and facing in vanilla's convention (yaw 0 = south). */
    double x();
    double y();
    double z();
    float yaw();
    float pitch();

    void teleport(double x, double y, double z, float yaw, float pitch);
    WorldSettings.GameType gameType();
    void setGameType(WorldSettings.GameType type);
    void setDifficulty(int difficulty);
    void kill();
    /** Empties the inventory (and held cursor stack); returns how many items were removed. */
    int clearInventory();

    /** The item in the sender's hand, or null (getCurrentEquippedItem); changes to it stick. */
    default net.minecraft.item.ItemStack heldItem() { return null; }
    /** Adds experience points (EntityPlayer.addExperience). */
    default void addExperience(int points) {}
    /** Adds (or with a negative count takes) whole levels (EntityPlayer.addExperienceLevel). */
    default void addExperienceLevels(int levels) {}

    /**
     * Gives the sender {@code count} of the item with this vanilla name and
     * data value (CommandGive): what doesn't fit is dropped at their feet.
     * Returns the item's display name, or null for an unknown item.
     */
    default String give(String id, int count, int data) { return null; }

    /** Creates an entity by its saved id at a position (CommandSummon); false for an unknown id. */
    default boolean summon(String id, double x, double y, double z) { return false; }
    /** Gives the sender an effect (CommandEffect); the potion id, ticks and amplifier. */
    default void addEffect(int potion, int ticks, int amplifier, boolean showParticles) {}
    /** Removes one effect, or every effect for -1; returns whether any was removed. */
    default boolean removeEffect(int potion) { return false; }

    /** The sender's achievements, or null when it has none. */
    default net.minecraft.stats.StatFile achievements() { return null; }
    /** Awards an achievement if its parent is earned, announcing it (EntityPlayerMP.triggerAchievement). */
    default void awardAchievement(net.minecraft.stats.Achievement achievement) {}
    /** Takes an achievement away, announcing it in chat. */
    default void revokeAchievement(net.minecraft.stats.Achievement achievement) {}
}

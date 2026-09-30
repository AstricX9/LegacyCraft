package net.minecraft.entity;

/**
 * Where damage comes from (1.8.9 DamageSource; HYPOTHESIS: recalled flags).
 * Unblockable damage ignores armour points (but not protection
 * enchantments); fire damage is what Fire Protection reduces; out-of-world
 * damage also hurts Creative players and ignores Protection. The type names
 * pick the death message ("death.attack.fall").
 */
public enum DamageSource {
    IN_FIRE("inFire", false, true, false),
    ON_FIRE("onFire", true, true, false),
    LAVA("lava", false, true, false),
    IN_WALL("inWall", true, false, false),
    DROWN("drown", true, false, false),
    CACTUS("cactus", false, false, false),
    FALL("fall", true, false, false),
    OUT_OF_WORLD("outOfWorld", true, false, true),
    GENERIC("generic", true, false, false);

    public final String type;
    private final boolean unblockable, fire, harmsCreative;

    DamageSource(String type, boolean unblockable, boolean fire, boolean harmsCreative) {
        this.type = type;
        this.unblockable = unblockable;
        this.fire = fire;
        this.harmsCreative = harmsCreative;
    }

    /** Armour points don't reduce it and armour doesn't wear (isUnblockable). */
    public boolean isUnblockable() { return unblockable; }
    public boolean isFire() { return fire; }
    /** Hurts even Creative players (canHarmInCreative); Protection ignores it. */
    public boolean canHarmInCreative() { return harmsCreative; }
    /** No explosions or projectiles exist yet. */
    public boolean isExplosion() { return false; }
    public boolean isProjectile() { return false; }

    /** The death message key ("death.attack.inFire"). */
    public String deathMessageKey() { return "death.attack." + type; }
}

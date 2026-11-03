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
    GENERIC("generic", true, false, false),
    /** Starving: bypasses armour and is absolute (no Resistance, no Protection). */
    STARVE("starve", true, false, false, true, false),
    /** Poison and harming: bypasses armour, magic. */
    MAGIC("magic", true, false, false, false, true),
    WITHER("wither", true, false, false),
    /** A player's hit (EntityDamageSource "player"). */
    PLAYER("player", false, false, false),
    /** A thrown snowball or egg (EntityDamageSourceIndirect "thrown"), blamed on the thrower. */
    THROWN("thrown", false, false, false),
    /** An arrow (EntityDamageSourceIndirect "arrow"): "was shot by". */
    ARROW("arrow", false, false, false),
    /** A ghast's or blaze's fireball ("fireball"): fire damage, "was fireballed by". */
    FIREBALL("fireball", false, true, false),
    /** A mob's hit (EntityDamageSource "mob"): scaled by difficulty when it hits a player. */
    MOB("mob", false, false, false),
    /** An explosion nobody set off ("explosion"), scaled by difficulty. */
    EXPLOSION("explosion", false, false, false),
    /** An explosion set off by someone ("explosion.player": "was blown up by"), scaled by difficulty. */
    EXPLOSION_PLAYER("explosion.player", false, false, false);

    public final String type;
    private final boolean unblockable, fire, harmsCreative, absolute, magic;

    DamageSource(String type, boolean unblockable, boolean fire, boolean harmsCreative) {
        this(type, unblockable, fire, harmsCreative, false, false);
    }

    DamageSource(String type, boolean unblockable, boolean fire, boolean harmsCreative, boolean absolute, boolean magic) {
        this.type = type;
        this.unblockable = unblockable;
        this.fire = fire;
        this.harmsCreative = harmsCreative;
        this.absolute = absolute;
        this.magic = magic;
    }

    /** Neither Resistance nor Protection reduce it (isDamageAbsolute). */
    public boolean isAbsolute() { return absolute; }
    public boolean isMagic() { return magic; }
    /** Exhaustion a hit adds (getHungerDamage): 0.3, or 0 for damage that bypasses armour. */
    public float hungerDamage() { return unblockable ? 0f : 0.3f; }

    /** Armour points don't reduce it and armour doesn't wear (isUnblockable). */
    public boolean isUnblockable() { return unblockable; }
    public boolean isFire() { return fire; }
    /** Hurts even Creative players (canHarmInCreative); Protection ignores it. */
    public boolean canHarmInCreative() { return harmsCreative; }
    public boolean isExplosion() { return this == EXPLOSION || this == EXPLOSION_PLAYER; }
    /** Easy halves it (+1), Hard adds half, Peaceful cancels it when it hits a player (isDifficultyScaled). */
    public boolean isDifficultyScaled() { return this == MOB || this == ARROW || this == FIREBALL || isExplosion(); }
    /** Whether the death message names whoever caused it (the "%2$s" of the message). */
    public boolean hasAttacker() { return this == MOB || this == EXPLOSION_PLAYER || this == PLAYER || this == ARROW || this == FIREBALL; }
    public boolean isProjectile() { return this == ARROW || this == FIREBALL || this == THROWN; }

    /** The death message key ("death.attack.inFire"). */
    public String deathMessageKey() { return "death.attack." + type; }
}

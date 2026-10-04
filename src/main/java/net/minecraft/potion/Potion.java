package net.minecraft.potion;

/**
 * The 1.8.9 potion types (Potion.potionTypes): id, translation key, liquid
 * colour, whether it is harmful, and its icon on inventory.png (index x + y x 8,
 * drawn from (0, 198)). HYPOTHESIS: recalled values. Only the effects food
 * and milk can cause do anything yet ({@link #REGENERATION}, {@link #POISON},
 * {@link #HUNGER}, {@link #ABSORPTION}, {@link #RESISTANCE},
 * {@link #FIRE_RESISTANCE}, {@link #CONFUSION}); the others are listed so
 * saved effects survive a round trip and show in the inventory.
 */
public final class Potion {
    private static final Potion[] BY_ID = new Potion[32];

    public static final Potion MOVE_SPEED = new Potion(1, "moveSpeed", 8171462, false, 0, 0);
    public static final Potion MOVE_SLOWDOWN = new Potion(2, "moveSlowdown", 5926017, true, 1, 0);
    public static final Potion DIG_SPEED = new Potion(3, "digSpeed", 14270531, false, 2, 0);
    public static final Potion DIG_SLOWDOWN = new Potion(4, "digSlowDown", 4866583, true, 3, 0);
    public static final Potion DAMAGE_BOOST = new Potion(5, "damageBoost", 9643043, false, 4, 0);
    public static final Potion HEAL = new Potion(6, "heal", 16262179, false, -1, -1);
    public static final Potion HARM = new Potion(7, "harm", 4393481, true, -1, -1);
    public static final Potion JUMP = new Potion(8, "jump", 2293580, false, 2, 1);
    public static final Potion CONFUSION = new Potion(9, "confusion", 5578058, true, 3, 1);
    public static final Potion REGENERATION = new Potion(10, "regeneration", 13458603, false, 7, 0);
    public static final Potion RESISTANCE = new Potion(11, "resistance", 10044730, false, 6, 1);
    public static final Potion FIRE_RESISTANCE = new Potion(12, "fireResistance", 14981690, false, 7, 1);
    public static final Potion WATER_BREATHING = new Potion(13, "waterBreathing", 3035801, false, 0, 2);
    public static final Potion INVISIBILITY = new Potion(14, "invisibility", 8356754, false, 0, 1);
    public static final Potion BLINDNESS = new Potion(15, "blindness", 2039587, true, 5, 1);
    public static final Potion NIGHT_VISION = new Potion(16, "nightVision", 2039713, false, 4, 1);
    public static final Potion HUNGER = new Potion(17, "hunger", 5797459, true, 1, 1);
    public static final Potion WEAKNESS = new Potion(18, "weakness", 4738376, true, 5, 0);
    public static final Potion POISON = new Potion(19, "poison", 5149489, true, 6, 0);
    public static final Potion WITHER = new Potion(20, "wither", 3484199, true, 1, 2);
    public static final Potion HEALTH_BOOST = new Potion(21, "healthBoost", 16284963, false, 7, 2);
    public static final Potion ABSORPTION = new Potion(22, "absorption", 2445989, false, 2, 2);
    public static final Potion SATURATION = new Potion(23, "saturation", 16262179, false, -1, -1);

    public final int id;
    /** The translation key, e.g. {@code potion.regeneration}. */
    public final String name;
    public final int liquidColor;
    public final boolean bad;
    private final int iconIndex;

    private Potion(int id, String name, int liquidColor, boolean bad, int iconX, int iconY) {
        this.id = id;
        this.name = "potion." + name;
        this.liquidColor = liquidColor;
        this.bad = bad;
        this.iconIndex = iconX < 0 ? -1 : iconX + iconY * 8;
        BY_ID[id] = this;
    }

    public static Potion byId(int id) { return id >= 0 && id < BY_ID.length ? BY_ID[id] : null; }

    public boolean hasIcon() { return iconIndex >= 0; }
    public int iconIndex() { return iconIndex; }

    /** Instant effects act once and never sit in the active list (Potion.isInstant). */
    public boolean isInstant() { return this == HEAL || this == HARM || this == SATURATION; }

    /**
     * Whether the effect acts this tick (Potion.isReady): regeneration every
     * 50 >> amplifier ticks, wither 40 >> amplifier, poison 25 >> amplifier
     * (each at least every tick), hunger every tick.
     */
    public boolean isReady(int duration, int amplifier) {
        int interval;
        if (this == REGENERATION) interval = 50 >> amplifier;
        else if (this == POISON) interval = 25 >> amplifier;
        else if (this == WITHER) interval = 40 >> amplifier;
        else return this == HUNGER;
        return interval <= 0 || duration % interval == 0;
    }

    /** "m:ss" as Potion.getDurationString shows it, or "**:**" for an endless (32767+ tick) effect. */
    public static String durationString(PotionEffect effect) {
        if (effect.isMaxDuration()) return "**:**";
        int seconds = effect.duration() / 20, minutes = seconds / 60;
        seconds %= 60;
        return seconds < 10 ? minutes + ":0" + seconds : minutes + ":" + seconds;
    }

    @Override public String toString() { return name; }
}

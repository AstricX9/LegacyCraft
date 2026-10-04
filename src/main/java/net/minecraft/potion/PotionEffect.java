package net.minecraft.potion;

/**
 * One active effect (1.8.9 PotionEffect): its type, ticks left, amplifier
 * (0 = level I), whether it came from a beacon (ambient) and whether it
 * shows particles. Saved in the player's {@code ActiveEffects} list as
 * Id, Amplifier, Duration, Ambient and ShowParticles.
 */
public final class PotionEffect {
    private final Potion potion;
    private int duration, amplifier;
    private boolean ambient, showParticles;

    public PotionEffect(Potion potion, int duration, int amplifier) { this(potion, duration, amplifier, false, true); }

    public PotionEffect(Potion potion, int duration, int amplifier, boolean ambient, boolean showParticles) {
        this.potion = potion;
        this.duration = duration;
        this.amplifier = amplifier;
        this.ambient = ambient;
        this.showParticles = showParticles;
    }

    public PotionEffect copy() { return new PotionEffect(potion, duration, amplifier, ambient, showParticles); }

    public Potion potion() { return potion; }
    public int duration() { return duration; }
    public int amplifier() { return amplifier; }
    public boolean ambient() { return ambient; }
    public boolean showParticles() { return showParticles; }
    public boolean isMaxDuration() { return duration >= 32767; }

    /**
     * Merges a new effect of the same type (PotionEffect.combine): a stronger
     * one replaces the amplifier and duration; an equal one only extends the
     * duration if longer; a weaker one changes nothing. Ambient follows a
     * replacement or extension from a non-ambient source.
     */
    public void combine(PotionEffect other) {
        if (other.amplifier > amplifier) {
            amplifier = other.amplifier;
            duration = other.duration;
        } else if (other.amplifier == amplifier && duration < other.duration) {
            duration = other.duration;
        } else if (!other.ambient && ambient) {
            ambient = other.ambient;
        }
        showParticles = other.showParticles;
    }

    /**
     * One tick (PotionEffect.onUpdate): acts on the target if the type is ready
     * this tick, then counts down. Returns whether any time is left.
     */
    public boolean tick(Target target) {
        if (duration > 0) {
            if (potion.isReady(duration, amplifier)) perform(target);
            duration--;
        }
        return duration > 0;
    }

    /**
     * Potion.performEffect: regeneration heals 1 (if hurt), poison deals 1
     * magic damage unless at 1 health or less, wither deals 1, hunger adds
     * 0.025 x (amplifier + 1) exhaustion.
     */
    private void perform(Target target) {
        if (potion == Potion.REGENERATION) {
            if (target.health() < target.maxHealth()) target.heal(1f);
        } else if (potion == Potion.POISON) {
            if (target.health() > 1f) target.magicDamage(1f);
        } else if (potion == Potion.WITHER) {
            target.witherDamage(1f);
        } else if (potion == Potion.HUNGER) {
            target.addExhaustion(0.025f * (amplifier + 1));
        }
    }

    /** What an effect acts on. */
    public interface Target {
        float health();
        float maxHealth();
        void heal(float amount);
        void magicDamage(float amount);
        void witherDamage(float amount);
        void addExhaustion(float amount);
    }

    @Override public String toString() {
        return potion.name + " x " + (amplifier + 1) + ", Duration: " + duration;
    }
}

package net.minecraft.client.entity;

import net.minecraft.block.Block;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.FoodStats;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The local player's movement, ticked 20 times a second exactly like 1.8.9's
 * EntityPlayerSP/EntityPlayer/EntityLivingBase (HYPOTHESIS: recalled
 * constants), and drawn between ticks from {@link #prevX}/{@link #x}:
 * <ul>
 *   <li><b>Walking</b>: the movement keys push with 0.1 x 0.16277 / f³ a tick
 *       on the ground (f = 0.91 x the block's slipperiness: 0.6 normally,
 *       0.8 slime, 0.98 ice), 0.02 in the air (0.026 sprinting), and motion
 *       keeps f of itself each tick horizontally, 0.98 vertically after
 *       0.08 gravity. That makes 4.3 m/s walking, 5.6 sprinting, 1.3
 *       sneaking and about 7.1 sprint-jumping, the fastest way on foot:
 *       every jump (0.42 up) adds a 0.2 push forward while sprinting.</li>
 *   <li><b>Sprinting</b>: hold sprint or double-tap forward within 7 ticks
 *       while moving forward at full speed with food above 6 (or in
 *       Creative); it stops when you slow, sneak or hit a wall.</li>
 *   <li><b>Sneaking</b>: 0.3 x the input and never stepping off an edge.</li>
 *   <li><b>Water and lava</b>: 0.02 push, keeping 0.8 (water) or 0.5 (lava)
 *       of the motion, 0.02 gravity; holding jump rises 0.04 a tick
 *       ("treading water"), and swimming into a wall with room above hops
 *       out at 0.3.</li>
 *   <li><b>Ladders and vines</b>: at most 0.15 sideways and 0.15 down,
 *       climbing at 0.2 when pushing into them, holding on while sneaking.</li>
 *   <li><b>Flying</b>: double-tap jump in Creative; 0.05 push (0.1
 *       sprinting) with 0.91 drag, jump/sneak adding 0.15 up/down with 0.6
 *       drag; landing ends it. Spectators always fly through blocks.</li>
 *   <li>Soul sand keeps 0.4 of horizontal motion, cobwebs a quarter (a
 *       twentieth vertically), and slime bounces a fall back up.</li>
 * </ul>
 */
public final class Player {
    public static final double WIDTH = 0.6, HEIGHT = 1.8;
    /** 1.8.9 player step height: ledges up to this are walked up without jumping. */
    public static final double STEP_HEIGHT = 0.6;
    /** Below this y the player dies (vanilla's void damage starts at y &lt; -64). */
    public static final double VOID_DEATH_Y = -64;

    public double x, y, z;
    /** Where the player was at the start of the tick (drawing interpolates from here). */
    public double prevX, prevY, prevZ;
    /** Blocks per tick. */
    public double motionX, motionY, motionZ;
    public float yaw = 25.0f;
    public float pitch = 8.0f;
    /** The stack in the player's hand, for mobs that notice it (tempting food); set by the game. */
    public java.util.function.Supplier<net.minecraft.item.ItemStack> heldItem = () -> null;
    /** Sets the player alight for at least this many seconds (Entity.setFire); the game keeps the player's fire. */
    public java.util.function.IntConsumer fireListener = seconds -> {};
    public void setFire(int seconds) { fireListener.accept(seconds); }
    public boolean onGround, collidedHorizontally, collidedVertically;
    /** Set when the player falls out of the world; the client shows the death screen until {@link #respawn()}. */
    public boolean dead;
    /** Sneaking (eyes 0.08 lower) and sprinting, as the camera and model read them. */
    public boolean sneaking, sprinting;
    /** Whether the player counts as in water this tick, and whether they just went in (the client splashes). */
    public boolean inWater, enteredWater;
    /** Touched a portal block this tick (BlockPortal.onEntityCollidedWithBlock); the client clears it once used. */
    public boolean inPortal;
    /** How far the portal's purple haze has built up, 0-1 (EntityPlayerSP.timeInPortal), and last tick's value. */
    public float timeInPortal, prevTimeInPortal;
    public float fallDistance;

    // Player abilities (level.dat "abilities"): Creative may fly; Spectator always flies through blocks.
    public boolean allowFlying, isFlying, noClip;
    /** Health (0-20); experience comes only from furnaces (DEV-028). */
    public float health = 20f;
    public int experienceLevel;
    /** The hunger bar (EntityPlayer.foodStats). */
    public final FoodStats foodStats = new FoodStats();
    /** Extra golden hearts from Absorption (EntityPlayer.getAbsorptionAmount), used up before health. */
    public float absorptionAmount;
    /** Active effects by potion id, in the order they were added (EntityLivingBase.activePotionsMap). */
    private final java.util.LinkedHashMap<Integer, PotionEffect> activeEffects = new java.util.LinkedHashMap<>();

    public int foodLevel() { return foodStats.getFoodLevel(); }
    /** The seed the enchanting table offers from (EntityPlayer.xpSeed), rerolled whenever levels are spent on it. */
    public int xpSeed;
    /** Progress to the next level (0-1), and every point ever gained (EntityPlayer.experience / experienceTotal). */
    public float experience;
    public int experienceTotal;

    /**
     * Adds experience points (EntityPlayer.addExperience): the bar fills by
     * points over the level's cap and each full bar is a level. The cap is
     * 7 + 2 x level below 15, 37 + 5 x (level - 15) below 30, then
     * 112 + 9 x (level - 30).
     */
    public void addExperience(int amount) {
        amount = Math.min(amount, Integer.MAX_VALUE - experienceTotal);
        experience += (float) amount / xpBarCap();
        experienceTotal += amount;
        while (experience >= 1f) {
            experience = (experience - 1f) * xpBarCap();
            experienceLevel++;
            experience /= xpBarCap();
        }
    }

    /**
     * Adds (or with a negative count removes) whole levels
     * (EntityPlayer.addExperienceLevel); going below 0 empties the bar.
     */
    public void addExperienceLevel(int levels) {
        experienceLevel += levels;
        if (experienceLevel < 0) {
            experienceLevel = 0;
            experience = 0;
            experienceTotal = 0;
        }
    }

    /** Spends levels on the enchanting table (EntityPlayer.removeExperienceLevel): the table's seed is rerolled too. */
    public void removeExperienceLevel(int levels) {
        addExperienceLevel(-levels);
        xpSeed = random.nextInt();
    }

    // ---- health and damage (EntityLivingBase / EntityPlayer, HYPOTHESIS: recalled) ---------------

    /** Ticks left of the red hurt flash and the camera shake (hurtTime, starting at 10). */
    public int hurtTime;
    public final int maxHurtTime = 10;
    /** Ticks of protection after being hurt (hurtResistantTime, 20): only bigger hits get through in the first 10. */
    public int hurtResistantTime;
    /** The damage of the hit that started the protection (lastDamage). */
    public float lastDamage;
    /** Ticks since death (the camera tips over for 20). */
    public int deathTime;
    /** Air left under water, 300 when full (Entity.getAir); at -20 the player takes drowning damage. */
    public int air = 300;
    /** The worn armour (0 boots .. 3 helmet): the inventory's armour slots once the client links them. */
    public ItemStack[] armor = new ItemStack[4];
    /** Creative and Spectator take no damage but falling out of the world (capabilities.disableDamage). */
    public boolean disableDamage;
    /** The difficulty (0 peaceful .. 3 hard) and the naturalRegeneration game rule, copied in by the client. */
    public int difficulty = 2;
    public boolean naturalRegeneration = true;
    private int ticksExisted;
    /** Ticks this player has lived (drives the armour glint scroll). */
    public int ticksExisted() { return ticksExisted; }

    /** What the client does when the player is hurt or dies (sounds, the death message, armour breaking). */
    public interface DamageListener {
        /** Hurt by {@code amount} (after armour); {@code fresh} is false when only the excess over a recent hit counted. */
        default void hurt(DamageSource source, float amount, boolean fresh) {}
        default void died(DamageSource source) {}
        /** A worn piece in armour slot {@code slot} broke from the wear. */
        default void armorBroke(int slot) {}
    }
    public DamageListener damageListener = new DamageListener() {};

    /** The armour points worn (1.8.9 armour values), as the HUD and damage reduction use them. */
    public int armorValue() {
        int total = 0;
        for (int slot = 0; slot < armor.length; slot++)
            if (armor[slot] != null) total += net.minecraft.inventory.ArmorValues.points(armor[slot].item(), slot);
        return total;
    }

    /**
     * Takes damage (EntityLivingBase.attackEntityFrom): nothing while
     * damage is disabled (Creative) unless it is out-of-world damage, or when
     * already dead. Within the first 10 of the 20 protected ticks after a hit
     * only the excess over that hit counts; otherwise the whole amount does,
     * the protection restarts and the player flashes red. Returns whether it hurt.
     */
    public boolean attack(DamageSource source, float amount) {
        if (disableDamage && !source.canHarmInCreative()) return false;
        if (dead || health <= 0) return false;
        // EntityPlayer.attackEntityFrom: mobs and explosions hit harder on Hard, softer on Easy, not at all on Peaceful.
        if (source.isDifficultyScaled()) {
            if (difficulty == 0) amount = 0f;
            else if (difficulty == 1) amount = amount / 2f + 1f;
            else if (difficulty == 3) amount = amount * 3f / 2f;
        }
        if (amount == 0f) return false;
        if (source.isFire() && isPotionActive(Potion.FIRE_RESISTANCE)) return false;
        boolean fresh = true;
        if (hurtResistantTime > 20 / 2.0f) {
            if (amount <= lastDamage) return false;
            damageEntity(source, amount - lastDamage);
            lastDamage = amount;
            fresh = false;
        } else {
            lastDamage = amount;
            hurtResistantTime = 20;
            damageEntity(source, amount);
            hurtTime = maxHurtTime;
        }
        if (health <= 0) {
            dead = true;
            clearItemInUse();
            sounds.play("game.player.die", 1f, (random.nextFloat() - random.nextFloat()) * 0.2f + 1f);
            damageListener.died(source);
        } else if (fresh) {
            sounds.play("game.player.hurt", 1f, (random.nextFloat() - random.nextFloat()) * 0.2f + 1f);
        }
        damageListener.hurt(source, amount, fresh);
        return true;
    }

    /**
     * EntityPlayer.damageEntity: blocking with a sword halves blockable damage
     * ((1 + d) / 2); armour points take {@code d x (25 - armour) / 25} off
     * blockable damage and wear each piece by d/4 (at least 1); then, unless
     * the damage is absolute (starving), Resistance takes 20% per level off
     * anything but void damage and protection enchantments up to 20/25 more.
     * Absorption hearts soak up what is left first; a hit that still gets
     * through adds its exhaustion (0.3 unless it bypasses armour).
     */
    private void damageEntity(DamageSource source, float damage) {
        if (!source.isUnblockable() && isBlocking() && damage > 0) damage = (1f + damage) * 0.5f;
        if (!source.isUnblockable()) {
            damageArmor(damage);
            damage = damage * (25 - armorValue()) / 25f;
        }
        damage = applyPotionDamageCalculations(source, damage);
        float before = damage;
        damage = Math.max(damage - absorptionAmount, 0f);
        absorptionAmount = Math.max(0f, absorptionAmount - (before - damage));
        if (damage != 0) {
            addExhaustion(source.hungerDamage());
            health = Math.max(0f, health - damage);
        }
    }

    /** EntityLivingBase.applyPotionDamageCalculations: Resistance, then protection enchantments. */
    private float applyPotionDamageCalculations(DamageSource source, float damage) {
        if (source.isAbsolute()) return damage;
        PotionEffect resistance = activeEffects.get(Potion.RESISTANCE.id);
        if (resistance != null && source != DamageSource.OUT_OF_WORLD) {
            int reduction = (resistance.amplifier() + 1) * 5;
            damage = damage * (25 - reduction) / 25f;
        }
        if (damage <= 0) return 0f;
        int protection = EnchantmentHelper.protectionPoints(armor, source, random);
        if (protection > 20) protection = 20;
        if (protection > 0) damage = damage * (25 - protection) / 25f;
        return damage;
    }

    // ---- hunger (EntityPlayer, HYPOTHESIS: recalled) -----------------------------------------

    /** Adds exhaustion (EntityPlayer.addExhaustion); nothing while damage is disabled (Creative). */
    public void addExhaustion(float amount) {
        if (!disableDamage) foodStats.addExhaustion(amount);
    }

    /** Whether eating is allowed (EntityPlayer.canEat): hungry or {@code ignoreHunger}, and not in Creative. */
    public boolean canEat(boolean ignoreHunger) {
        return (ignoreHunger || foodStats.needFood()) && !disableDamage;
    }

    private final FoodStats.Owner foodOwner = new FoodStats.Owner() {
        @Override public int difficulty() { return difficulty; }
        @Override public boolean naturalRegeneration() { return naturalRegeneration; }
        @Override public boolean shouldHeal() { return health > 0 && health < 20f; }
        @Override public float health() { return health; }
        @Override public void heal(float amount) { Player.this.heal(amount); }
        @Override public void starve(float amount) { attack(DamageSource.STARVE, amount); }
    };

    /**
     * EntityPlayer.addMovementStat: moving costs exhaustion per metre, 0.015
     * swimming (through the water when the eyes are under, across it
     * otherwise), 0.1 sprinting and 0.01 walking or sneaking on the ground;
     * climbing and moving through the air cost nothing.
     */
    private void addMovementExhaustion(double dx, double dy, double dz) {
        if (headInWater()) {
            int cm = Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 100f);
            if (cm > 0) addExhaustion(0.015f * cm * 0.01f);
        } else if (inWater) {
            int cm = Math.round((float) Math.sqrt(dx * dx + dz * dz) * 100f);
            if (cm > 0) addExhaustion(0.015f * cm * 0.01f);
        } else if (onLadder()) {
            // climbing: a statistic only
        } else if (onGround) {
            int cm = Math.round((float) Math.sqrt(dx * dx + dz * dz) * 100f);
            if (cm > 0) addExhaustion((sprinting ? 0.099999994f : 0.01f) * cm * 0.01f);
        }
    }

    // ---- potion effects (EntityLivingBase, HYPOTHESIS: recalled) -------------------------------

    /** What the client does when effects change (nothing needs it yet beyond redrawing). */
    public interface EffectListener {
        default void added(PotionEffect effect) {}
        default void finished(PotionEffect effect) {}
    }
    public EffectListener effectListener = new EffectListener() {};

    public boolean isPotionActive(Potion potion) { return activeEffects.containsKey(potion.id); }
    public PotionEffect getActivePotionEffect(Potion potion) { return activeEffects.get(potion.id); }
    public java.util.Collection<PotionEffect> getActivePotionEffects() { return java.util.Collections.unmodifiableCollection(activeEffects.values()); }

    /**
     * Adds an effect (EntityLivingBase.addPotionEffect): merged into an active
     * one of the same type (and its attribute part reapplied, which refills
     * Absorption), otherwise started.
     */
    public void addPotionEffect(PotionEffect effect) {
        if (effect.potion().isInstant()) return;
        PotionEffect active = activeEffects.get(effect.potion().id);
        if (active != null) {
            active.combine(effect);
            removeModifiers(active);
            applyModifiers(active);
        } else {
            PotionEffect copy = effect.copy();
            activeEffects.put(effect.potion().id, copy);
            applyModifiers(copy);
            effectListener.added(copy);
        }
    }

    /** Removes one effect (EntityLivingBase.removePotionEffect); false if it wasn't active. */
    public boolean removePotionEffect(Potion potion) {
        PotionEffect effect = activeEffects.remove(potion.id);
        if (effect == null) return false;
        finishEffect(effect);
        return true;
    }

    /** Removes every effect (EntityLivingBase.clearActivePotions), as drinking milk does. */
    public void clearActivePotions() {
        for (java.util.Iterator<PotionEffect> it = activeEffects.values().iterator(); it.hasNext(); ) {
            PotionEffect effect = it.next();
            it.remove();
            finishEffect(effect);
        }
    }

    private void finishEffect(PotionEffect effect) {
        removeModifiers(effect);
        effectListener.finished(effect);
    }

    /** Potion.applyAttributesModifiersToEntity: Absorption adds 4 per level. */
    private void applyModifiers(PotionEffect effect) {
        if (effect.potion() == Potion.ABSORPTION) absorptionAmount += 4 * (effect.amplifier() + 1);
    }

    private void removeModifiers(PotionEffect effect) {
        if (effect.potion() == Potion.ABSORPTION) absorptionAmount = Math.max(0f, absorptionAmount - 4 * (effect.amplifier() + 1));
    }

    private final PotionEffect.Target effectTarget = new PotionEffect.Target() {
        @Override public float health() { return health; }
        @Override public float maxHealth() { return 20f; }
        @Override public void heal(float amount) { Player.this.heal(amount); }
        @Override public void magicDamage(float amount) { attack(DamageSource.MAGIC, amount); }
        @Override public void witherDamage(float amount) { attack(DamageSource.WITHER, amount); }
        @Override public void addExhaustion(float amount) { Player.this.addExhaustion(amount); }
    };

    /** EntityLivingBase.updatePotionEffects: each effect acts and counts down; spent ones finish. */
    private void updatePotionEffects() {
        if (activeEffects.isEmpty()) return;
        for (Integer id : new ArrayList<>(activeEffects.keySet())) {
            PotionEffect effect = activeEffects.get(id);
            if (effect == null) continue;
            if (!effect.tick(effectTarget) && activeEffects.remove(id) != null) finishEffect(effect);
        }
    }

    /** Restores saved effects without the start-up side effects (absorption is loaded on its own). */
    public void loadPotionEffect(PotionEffect effect) {
        if (effect.potion().isInstant()) return;
        activeEffects.put(effect.potion().id, effect.copy());
    }

    /** InventoryPlayer.damageArmor: every worn piece loses d/4 durability (at least 1), Unbreaking allowing. */
    private void damageArmor(float damage) {
        damage /= 4f;
        if (damage < 1f) damage = 1f;
        for (int slot = 0; slot < armor.length; slot++) {
            ItemStack piece = armor[slot];
            if (piece == null || !net.minecraft.inventory.ArmorValues.isArmorPiece(piece.item())) continue;
            if (EnchantmentHelper.damageItem(piece, (int) damage, random) || piece.isEmpty()) {
                armor[slot] = null;
                damageListener.armorBroke(slot);
            }
        }
    }

    /** Who the last hit with an attacker came from (a translation key or name), for the death message. */
    public String lastAttacker;
    /** Ticks before the next experience orb can be picked up (EntityPlayer.xpCooldown). */
    public int xpCooldown;

    /**
     * A mob's hit (EntityLivingBase.attackEntityFrom with an attacker): the
     * damage, and on a fresh hit the knockback away from the mob at (fromX, fromZ).
     */
    public boolean attackByMob(float amount, double fromX, double fromZ, String attacker) {
        return attackBy(DamageSource.MOB, amount, fromX, fromZ, attacker);
    }

    /** A hit from someone at (fromX, fromZ) with this source (an arrow, a fireball): knocked back away from them. */
    public boolean attackBy(DamageSource source, float amount, double fromX, double fromZ, String attacker) {
        int before = hurtResistantTime;
        lastAttacker = attacker;
        boolean hurt = attack(source, amount);
        if (hurt && before <= 10) {
            double dx = fromX - x, dz = fromZ - z;
            while (dx * dx + dz * dz < 1.0E-4) {
                dx = (Math.random() - Math.random()) * 0.01;
                dz = (Math.random() - Math.random()) * 0.01;
            }
            knockBack(dx, dz);
        }
        return hurt;
    }

    /** An explosion's damage: "blew up", or "was blown up by" when someone set it off. */
    public boolean attackByExplosion(float amount, String attacker) {
        lastAttacker = attacker;
        return attack(attacker == null ? DamageSource.EXPLOSION : DamageSource.EXPLOSION_PLAYER, amount);
    }

    /** EntityLivingBase.knockBack: halve the motion, push 0.4 away from (dx, dz) and 0.4 up. */
    public void knockBack(double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        motionX /= 2.0;
        motionY /= 2.0;
        motionZ /= 2.0;
        motionX -= dx / length * 0.4;
        motionY += 0.4;
        motionZ -= dz / length * 0.4;
        if (motionY > 0.4) motionY = 0.4;
    }

    /** Heals, up to 20 (EntityLivingBase.heal). */
    public void heal(float amount) { if (health > 0) health = Math.min(20f, health + amount); }

    /**
     * Landing (EntityPlayer/EntityLivingBase.fall): with flight allowed
     * nothing; otherwise {@code ceil((distance - 3) x multiplier)} damage with
     * the big (above 4) or small fall sound, and the landing block's step
     * sound at half volume and 3/4 pitch.
     */
    private void fall(float distance, float multiplier) {
        if (allowFlying || noClip) return;
        // EntityLivingBase.fall: Jump Boost lets you fall one block further per level unhurt.
        PotionEffect jumpBoost = activeEffects.get(Potion.JUMP.id);
        float safe = 3f + (jumpBoost == null ? 0f : jumpBoost.amplifier() + 1);
        int damage = (int) Math.ceil((distance - safe) * multiplier);
        if (damage <= 0) return;
        sounds.play(damage > 4 ? "game.player.hurt.fall.big" : "game.player.hurt.fall.small", 1f, 1f);
        attack(DamageSource.FALL, damage);
        int bx = floor(x), by = floor(y - 0.2), bz = floor(z);
        Block block = by >= 0 && by < World.HEIGHT ? world.get(bx, by, bz) : null;
        if (block != null && !block.isAir()) {
            net.minecraft.block.BlockSoundType type = net.minecraft.block.BlockSoundType.of(block);
            sounds.play(type.stepSound(), type.volume() * 0.5f, type.frequency() * 0.75f);
        }
    }

    /**
     * Once per tick while alive (EntityLivingBase.onEntityUpdate and
     * EntityPlayer.onLivingUpdate): suffocation in a solid block (1), air
     * running out under water (Respiration may skip a tick's loss; 2 damage
     * each time it reaches -20), falling out of the world (4), the hurt and
     * protection timers, and healing: 1 every 80 ticks with food at 18 or
     * more, or 1 a second in Peaceful.
     */
    private void livingUpdate() {
        ticksExisted++;
        if (!noClip && insideOpaqueBlock()) attack(DamageSource.IN_WALL, 1f);
        if (headInWater() && !noClip) {
            if (!disableDamage) {
                int respiration = EnchantmentHelper.maxLevel(Enchantment.RESPIRATION, armor);
                // Water Breathing stops the air running out (EntityLivingBase.onEntityUpdate).
                if (!isPotionActive(Potion.WATER_BREATHING) && !(respiration > 0 && random.nextInt(respiration + 1) > 0)) air--;
                if (air == -20) {
                    air = 0;
                    attack(DamageSource.DROWN, 2f);
                }
            }
        } else {
            air = 300;
        }
        if (y < -64) attack(DamageSource.OUT_OF_WORLD, 4f);
        if (hurtTime > 0) hurtTime--;
        if (hurtResistantTime > 0) hurtResistantTime--;
        if (xpCooldown > 0) xpCooldown--;
        if (dead) return;
        updatePotionEffects();
        if (dead) return;
        // EntityPlayer.onLivingUpdate: Peaceful heals 1 a second and refills food 1 every 10 ticks.
        if (difficulty == 0 && naturalRegeneration) {
            if (health < 20f && ticksExisted % 20 == 0) heal(1f);
            if (foodStats.needFood() && ticksExisted % 10 == 0) foodStats.setFoodLevel(foodStats.getFoodLevel() + 1);
        }
        foodStats.tick(foodOwner);
    }

    /** Eyes under a water surface (Entity.isInsideOfMaterial(water)): the surface sits a ninth under the liquid's height. */
    public boolean headInWater() {
        double eye = y + eyeHeight();
        int bx = floor(x), by = floor(eye), bz = floor(z);
        if (by < 0 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null) return false;
        net.minecraft.block.BlockState state = world.getState(bx, by, bz);
        if (!state.block().material().equals("water")) return false;
        float surface = by + 1 - (net.minecraft.block.Liquids.heightPercent(state.metadata()) - 0.11111111f);
        return eye < surface;
    }

    /** Any of eight points around the eyes inside a solid full block (isEntityInsideOpaqueBlock). */
    private boolean insideOpaqueBlock() {
        for (int i = 0; i < 8; i++) {
            double px = x + ((i % 2) - 0.5f) * WIDTH * 0.8f;
            double py = y + (((i >> 1) % 2) - 0.5f) * 0.1f + eyeHeight();
            double pz = z + (((i >> 2) % 2) - 0.5f) * WIDTH * 0.8f;
            int bx = floor(px), by = floor(py), bz = floor(pz);
            if (by < 0 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null) continue;
            if (world.get(bx, by, bz).isNormalCube()) return true;
        }
        return false;
    }

    /** Points in the current level's bar (EntityPlayer.xpBarCap). */
    public int xpBarCap() {
        return experienceLevel >= 30 ? 112 + (experienceLevel - 30) * 9
            : experienceLevel >= 15 ? 37 + (experienceLevel - 15) * 5 : 7 + experienceLevel * 2;
    }

    /**
     * The stack being used (a sword held up to block, later food or a bow) and
     * the ticks left (EntityPlayer.itemInUse / itemInUseCount); null when not.
     */
    private net.minecraft.item.ItemStack itemInUse;
    private int itemInUseCount;

    /** Starts using a stack for up to {@code duration} ticks (EntityPlayer.setItemInUse). */
    public void setItemInUse(net.minecraft.item.ItemStack stack, int duration) {
        if (stack == null || dead) return;
        itemInUse = stack;
        itemInUseCount = duration;
    }

    /** What happens as an item is used up (EntityPlayer.updateItemUse / onItemUseFinish), done by the client. */
    public interface ItemUseListener {
        /** Eating crumbs and sound or the drinking sound, {@code particles} crumbs for eating. */
        default void update(ItemStack stack, int particles) {}
        /** The use ran its full time: the client applies the item and replaces the held stack. */
        default void finish(ItemStack stack) {}
    }
    public ItemUseListener itemUseListener = new ItemUseListener() {};

    public void clearItemInUse() {
        itemInUse = null;
        itemInUseCount = 0;
    }

    public boolean isUsingItem() { return itemInUse != null; }
    public net.minecraft.item.ItemStack itemInUse() { return itemInUse; }
    public int itemInUseCount() { return itemInUseCount; }

    /** Holding a sword up (EntityPlayer.isBlocking): using an item whose use is BLOCK. */
    public boolean isBlocking() {
        return itemInUse != null && itemInUse.item().useAction() == net.minecraft.item.Item.UseAction.BLOCK;
    }

    /** Where the player's own sounds go (played at their feet). */
    public interface Sounds { void play(String event, float volume, float pitch); }
    public Sounds sounds = (event, volume, pitch) -> {};
    private final java.util.Random random = new java.util.Random();
    private float distanceWalkedOnStep;
    private int nextStepDistance = 1;

    private World world;

    /** Moves the player into another dimension's world (Entity.travelToDimension; the caller places them). */
    public void setWorld(World world) { this.world = world; }
    public World world() { return world; }
    private final double spawnX, spawnZ;
    private int jumpTicks, flyToggleTimer, sprintToggleTimer;
    private boolean jumpWasDown, sneakWasDown, inWeb, firstUpdate = true;
    private float lastForward;

    /** The movement keys for one tick. */
    public record Input(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
        public static Input of(GameSettings settings) {
            return new Input(settings.keyBindForward.isKeyDown(), settings.keyBindBack.isKeyDown(), settings.keyBindLeft.isKeyDown(),
                settings.keyBindRight.isKeyDown(), settings.keyBindJump.isKeyDown(), settings.keyBindSneak.isKeyDown(),
                settings.keyBindSprint.isKeyDown());
        }

        public static final Input NONE = new Input(false, false, false, false, false, false, false);
    }

    public Player(World world) { this(world, 0.5, 0.5); }

    public Player(World world, double spawnX, double spawnZ) {
        this.world = world;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        x = spawnX;
        z = spawnZ;
        y = world.surfaceHeight((int) Math.floor(x), (int) Math.floor(z)) + 1.01;
        prevX = x; prevY = y; prevZ = z;
        xpSeed = random.nextInt();
    }

    /** One 20 Hz tick of movement from these keys. */
    public void tick(Input input) {
        prevX = x; prevY = y; prevZ = z;
        enteredWater = false;
        if (dead) {
            clearItemInUse();
            deathTime++;
            return;
        }
        // EntityPlayer.onUpdate: crumbs and sounds every 4 ticks over the last 25, then the item is used up.
        if (itemInUse != null) {
            ItemStack using = itemInUse;
            if (itemInUseCount <= 25 && itemInUseCount % 4 == 0) itemUseListener.update(using, 5);
            if (--itemInUseCount <= 0) {
                clearItemInUse();
                itemUseListener.update(using, 16);
                itemUseListener.finish(using);
            }
        }
        updateWater();

        // EntityPlayerSP: the input, sprinting and the flight toggle.
        if (sprintToggleTimer > 0) sprintToggleTimer--;
        boolean wasJumping = jumpWasDown, wasSneaking = sneakWasDown, wasForward = lastForward >= 0.8f;
        float strafe = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
        float forward = (input.forward() ? 1 : 0) - (input.back() ? 1 : 0);
        sneaking = input.sneak() && !noClip;
        if (input.sneak()) {
            strafe *= 0.3f;
            forward *= 0.3f;
        }
        // Using an item (blocking with a sword) slows the input to a fifth and cancels a pending double tap.
        if (isUsingItem()) {
            strafe *= 0.2f;
            forward *= 0.2f;
            sprintToggleTimer = 0;
        }
        boolean canSprint = foodStats.getFoodLevel() > 6 || allowFlying;
        if (onGround && !wasSneaking && !wasForward && forward >= 0.8f && !sprinting && canSprint && !isUsingItem()) {
            if (sprintToggleTimer <= 0 && !input.sprint()) sprintToggleTimer = 7;
            else sprinting = true;
        }
        if (!sprinting && forward >= 0.8f && canSprint && !isUsingItem() && input.sprint()) sprinting = true;
        if (sprinting && (forward < 0.8f || collidedHorizontally || !canSprint)) sprinting = false;
        if (allowFlying) {
            if (noClip) {
                isFlying = true;
            } else if (!wasJumping && input.jump()) {
                if (flyToggleTimer == 0) {
                    flyToggleTimer = 7;
                } else {
                    isFlying = !isFlying;
                    flyToggleTimer = 0;
                }
            }
        }
        if (isFlying) {
            if (input.sneak()) motionY -= 0.15;
            if (input.jump()) motionY += 0.15;
        }
        jumpWasDown = input.jump();
        sneakWasDown = input.sneak();
        lastForward = forward;

        // EntityPlayer / EntityLivingBase.onLivingUpdate.
        if (flyToggleTimer > 0) flyToggleTimer--;
        if (jumpTicks > 0) jumpTicks--;
        if (Math.abs(motionX) < 0.005) motionX = 0;
        if (Math.abs(motionY) < 0.005) motionY = 0;
        if (Math.abs(motionZ) < 0.005) motionZ = 0;
        if (input.jump()) {
            if (inWater || inLava()) motionY += 0.04;
            else if (onGround && jumpTicks == 0) {
                jump();
                jumpTicks = 10;
            }
        } else {
            jumpTicks = 0;
        }
        strafe *= 0.98f;
        forward *= 0.98f;
        float airSpeed = 0.02f + (sprinting ? 0.02f * 0.3f : 0f);
        double beforeX = x, beforeY = y, beforeZ = z;
        moveWithHeading(strafe, forward, airSpeed);
        addMovementExhaustion(x - beforeX, y - beforeY, z - beforeZ);
        if (onGround && isFlying && !noClip) isFlying = false;
        livingUpdate();
        firstUpdate = false;
    }

    /** EntityLivingBase.jump plus EntityPlayer's cost: 0.8 exhaustion sprinting, 0.2 otherwise. */
    private void jump() {
        // EntityLivingBase.jump: Jump Boost adds 0.1 per level.
        PotionEffect jumpBoost = activeEffects.get(Potion.JUMP.id);
        motionY = 0.42 + (jumpBoost == null ? 0 : (jumpBoost.amplifier() + 1) * 0.1f);
        if (sprinting) {
            double angle = Math.toRadians(yaw + 180f);
            motionX -= Math.sin(angle) * 0.2;
            motionZ += Math.cos(angle) * 0.2;
        }
        addExhaustion(sprinting ? 0.8f : 0.2f);
    }

    private void moveWithHeading(float strafe, float forward, float airSpeed) {
        double flyingMotionY = motionY;
        if (isFlying) airSpeed = 0.05f * (sprinting ? 2f : 1f);
        if (inWater && !isFlying) {
            double startY = y;
            // Depth Strider (EntityLivingBase.moveEntityWithHeading): up to level 3, halved off the ground,
            // moves the water drag towards 0.546 and the push towards the walking speed.
            float drag = 0.8f, push = 0.02f;
            float strider = Math.min(3f, net.minecraft.enchantment.EnchantmentHelper.maxLevel(
                net.minecraft.enchantment.Enchantment.DEPTH_STRIDER, armor));
            if (!onGround) strider *= 0.5f;
            if (strider > 0f) {
                drag += (0.54600006f - drag) * strider / 3f;
                push += (moveSpeed() - push) * strider / 3f;
            }
            moveFlying(strafe, forward, push);
            move(motionX, motionY, motionZ);
            motionX *= drag; motionY *= 0.8; motionZ *= drag;
            motionY -= 0.02;
            if (collidedHorizontally && offsetIsFree(motionX, motionY + 0.6 - y + startY, motionZ)) motionY = 0.3;
        } else if (inLava() && !isFlying) {
            double startY = y;
            moveFlying(strafe, forward, 0.02f);
            move(motionX, motionY, motionZ);
            motionX *= 0.5; motionY *= 0.5; motionZ *= 0.5;
            motionY -= 0.02;
            if (collidedHorizontally && offsetIsFree(motionX, motionY + 0.6 - y + startY, motionZ)) motionY = 0.3;
        } else {
            float friction = onGround ? slipperinessBelow() * 0.91f : 0.91f;
            float push = onGround ? moveSpeed() * (0.16277136f / (friction * friction * friction)) : airSpeed;
            moveFlying(strafe, forward, push);
            friction = onGround ? slipperinessBelow() * 0.91f : 0.91f;
            if (onLadder()) {
                motionX = clamp(motionX, -0.15, 0.15);
                motionZ = clamp(motionZ, -0.15, 0.15);
                fallDistance = 0;
                if (motionY < -0.15) motionY = -0.15;
                if (sneaking && motionY < 0) motionY = 0;
            }
            move(motionX, motionY, motionZ);
            if (collidedHorizontally && onLadder()) motionY = 0.2;
            motionY -= 0.08;
            motionY *= 0.98;
            motionX *= friction;
            motionZ *= friction;
        }
        if (isFlying) motionY = flyingMotionY * 0.6;
    }

    /**
     * Walking speed (the movement speed attribute): 0.1, times 1.3 while
     * sprinting, 1 + 0.2 per level of Speed and 1 - 0.15 per level of
     * Slowness (all "multiply total" modifiers).
     */
    public float moveSpeed() {
        float speed = sprinting ? 0.13f : 0.1f;
        PotionEffect fast = activeEffects.get(Potion.MOVE_SPEED.id);
        if (fast != null) speed *= 1f + 0.2f * (fast.amplifier() + 1);
        PotionEffect slow = activeEffects.get(Potion.MOVE_SLOWDOWN.id);
        if (slow != null) speed *= Math.max(0f, 1f - 0.15f * (slow.amplifier() + 1));
        return speed;
    }

    /**
     * The attack damage attribute for a weapon's {@code base} damage
     * (PotionAttackDamage, 1.8.9): Weakness takes 0.5 per level off, then
     * Strength multiplies by 1 + 1.3 per level.
     */
    public float attackDamage(float base) {
        PotionEffect weakness = activeEffects.get(Potion.WEAKNESS.id);
        if (weakness != null) base -= 0.5f * (weakness.amplifier() + 1);
        PotionEffect strength = activeEffects.get(Potion.DAMAGE_BOOST.id);
        if (strength != null) base *= 1f + 1.3f * (strength.amplifier() + 1);
        return Math.max(0f, base);
    }

    /** EntityRenderer.getNightVisionBrightness: 1, flickering in the last 10 seconds; 0 without the effect. */
    public float nightVisionBrightness(float partialTicks) {
        PotionEffect effect = activeEffects.get(Potion.NIGHT_VISION.id);
        if (effect == null) return 0f;
        int ticks = effect.duration();
        return ticks > 200 ? 1f : 0.7f + (float) Math.sin((ticks - partialTicks) * Math.PI * 0.2) * 0.3f;
    }

    /** Turns input into motion along the facing (Entity.moveFlying). */
    private void moveFlying(float strafe, float forward, float amount) {
        float length = strafe * strafe + forward * forward;
        if (length < 1.0E-4f) return;
        length = (float) Math.sqrt(length);
        if (length < 1f) length = 1f;
        length = amount / length;
        strafe *= length;
        forward *= length;
        double angle = Math.toRadians(yaw + 180f);
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        motionX += strafe * cos - forward * sin;
        motionZ += forward * cos + strafe * sin;
    }

    /**
     * In water this tick (Entity.handleWaterMovement): water in the box
     * shrunk 0.4 top and bottom. Going in stops any fall.
     */
    private void updateWater() {
        AxisAlignedBB box = boundingBox();
        AxisAlignedBB waterBox = new AxisAlignedBB(box.minX() + 0.001, box.minY() + 0.4 + 0.001, box.minZ() + 0.001,
            box.maxX() - 0.001, box.maxY() - 0.4 - 0.001, box.maxZ() - 0.001);
        double[] current = noClip ? null : world.liquidCurrent(waterBox, "water");
        if (current != null) {
            // Currents push anyone not flying (EntityPlayer.isPushedByWater).
            double[] push = net.minecraft.block.Liquids.normalize(current[0], current[1], current[2]);
            if (!isFlying) {
                motionX += push[0] * 0.014;
                motionY += push[1] * 0.014;
                motionZ += push[2] * 0.014;
            }
            if (!inWater && !firstUpdate) {
                enteredWater = true;
                float volume = (float) Math.min(1.0, Math.sqrt(motionX * motionX * 0.2 + motionY * motionY + motionZ * motionZ * 0.2) * 0.2);
                sounds.play("game.player.swim.splash", volume, 1f + (random.nextFloat() - random.nextFloat()) * 0.4f);
            }
            fallDistance = 0;
            inWater = true;
        } else {
            inWater = false;
        }
    }

    /** Lava in the box shrunk 0.1 sideways and 0.4 top and bottom (Entity.isInLava). */
    public boolean inLava() {
        if (noClip) return false;
        AxisAlignedBB box = boundingBox();
        return world.isMaterialInBox(new AxisAlignedBB(box.minX() + 0.1, box.minY() + 0.4, box.minZ() + 0.1,
            box.maxX() - 0.1, box.maxY() - 0.4, box.maxZ() - 0.1), "lava");
    }

    /** Whether the box moved this far would touch no block and no liquid (the hop out of water onto a ledge). */
    private boolean offsetIsFree(double dx, double dy, double dz) {
        AxisAlignedBB moved = boundingBox().offset(dx, dy, dz);
        for (AxisAlignedBB block : collisionBoxes(moved)) if (block.intersects(moved)) return false;
        return !world.isMaterialInBox(moved, "water") && !world.isMaterialInBox(moved, "lava");
    }

    public boolean onLadder() {
        String name = blockName(floor(x), floor(boundingBox().minY()), floor(z));
        return name.equals("ladder") || name.equals("vine");
    }

    /** Slipperiness of the block under the feet (0.6; ice and packed ice 0.98; slime 0.8). */
    private float slipperinessBelow() {
        return switch (blockName(floor(x), floor(boundingBox().minY()) - 1, floor(z))) {
            case "ice", "packed_ice" -> 0.98f;
            case "slime" -> 0.8f;
            default -> 0.6f;
        };
    }

    private String blockName(int bx, int by, int bz) {
        if (by < 0 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null) return "air";
        return world.get(bx, by, bz).name().path();
    }

    /** Applies a game mode's abilities: Creative may fly, Spectator flies through blocks, others walk. */
    public void setAbilities(boolean creative, boolean spectator) {
        allowFlying = creative || spectator;
        noClip = spectator;
        if (spectator) isFlying = true;
        else if (!allowFlying) isFlying = false;
    }

    public AxisAlignedBB boundingBox() {
        return new AxisAlignedBB(x - WIDTH / 2, y, z - WIDTH / 2, x + WIDTH / 2, y + HEIGHT, z + WIDTH / 2);
    }

    /**
     * Moves by clipping against block collision boxes one axis at a time
     * (Y, then X, then Z, as 1.8 entities do), after cobwebs slow the move
     * and, while sneaking on the ground, cutting back any horizontal move
     * that would leave nothing below within a block. When a horizontal move
     * is blocked while on the ground, the move is retried lifted by up to
     * {@link #STEP_HEIGHT} and kept if it gets further. Afterwards the
     * blocks touched act (soul sand, cobweb, slime) and falls are tracked.
     */
    public void move(double dx, double dy, double dz) {
        if (noClip) {
            x += dx; y += dy; z += dz;
            onGround = false;
            return;
        }
        double startX = x, startY = y, startZ = z;
        boolean sneakingOnGround = onGround && sneaking;
        if (inWeb) {
            inWeb = false;
            dx *= 0.25; dy *= 0.05; dz *= 0.25;
            motionX = motionY = motionZ = 0;
        }
        AxisAlignedBB start = boundingBox();
        if (onGround && sneaking) {
            final double step = 0.05;
            while (dx != 0 && isEmpty(start.offset(dx, -1, 0))) dx = towardZero(dx, step);
            while (dz != 0 && isEmpty(start.offset(0, -1, dz))) dz = towardZero(dz, step);
            while (dx != 0 && dz != 0 && isEmpty(start.offset(dx, -1, dz))) {
                dx = towardZero(dx, step);
                dz = towardZero(dz, step);
            }
        }
        double wantedX = dx, wantedY = dy, wantedZ = dz;
        List<AxisAlignedBB> boxes = collisionBoxes(start.addCoord(dx, dy, dz));
        AxisAlignedBB box = start;
        for (AxisAlignedBB block : boxes) dy = block.clipY(box, dy);
        box = box.offset(0, dy, 0);
        for (AxisAlignedBB block : boxes) dx = block.clipX(box, dx);
        box = box.offset(dx, 0, 0);
        for (AxisAlignedBB block : boxes) dz = block.clipZ(box, dz);
        box = box.offset(0, 0, dz);

        boolean landed = wantedY != dy && wantedY < 0;
        if ((onGround || landed) && (wantedX != dx || wantedZ != dz)) {
            double stepX = wantedX, stepY = STEP_HEIGHT, stepZ = wantedZ;
            List<AxisAlignedBB> stepBoxes = collisionBoxes(start.addCoord(wantedX, STEP_HEIGHT, wantedZ));
            AxisAlignedBB stepped = start;
            for (AxisAlignedBB block : stepBoxes) stepY = block.clipY(stepped, stepY);
            stepped = stepped.offset(0, stepY, 0);
            for (AxisAlignedBB block : stepBoxes) stepX = block.clipX(stepped, stepX);
            stepped = stepped.offset(stepX, 0, 0);
            for (AxisAlignedBB block : stepBoxes) stepZ = block.clipZ(stepped, stepZ);
            stepped = stepped.offset(0, 0, stepZ);
            double down = -stepY;
            for (AxisAlignedBB block : stepBoxes) down = block.clipY(stepped, down);
            stepped = stepped.offset(0, down, 0);
            if (stepX * stepX + stepZ * stepZ > dx * dx + dz * dz) {
                box = stepped;
                dx = stepX; dz = stepZ; dy = stepY + down;
            }
        }

        x = (box.minX() + box.maxX()) / 2;
        y = box.minY();
        z = (box.minZ() + box.maxZ()) / 2;
        collidedHorizontally = wantedX != dx || wantedZ != dz;
        collidedVertically = wantedY != dy;
        onGround = collidedVertically && wantedY < 0;
        if (onGround) {
            // Entity.updateFallState / Block.onFallenUpon: slime cancels the damage unless sneaking.
            if (fallDistance > 0) {
                boolean slime = blockName(floor(x), floor(y - 0.2), floor(z)).equals("slime");
                fall(fallDistance, slime && !sneaking ? 0f : 1f);
            }
            fallDistance = 0;
        } else if (dy < 0) fallDistance -= (float) dy;
        if (wantedX != dx) motionX = 0;
        if (wantedZ != dz) motionZ = 0;
        if (collidedVertically) {
            boolean bouncy = blockName(floor(x), floor(y - 0.2), floor(z)).equals("slime");
            if (bouncy && !sneaking && motionY < 0) motionY = -motionY;
            else motionY = 0;
        }
        touchBlocks();
        if (!isFlying && !sneakingOnGround) stepSounds(startX, startY, startZ);
    }

    /**
     * Footsteps (Entity.moveEntity/playStepSound, HYPOTHESIS): every block of
     * distance x 0.6 walked (climbing counts only on ladders) over something
     * that isn't air plays the step sound of the block 0.2 below the feet (a
     * fence, wall or gate under an air block counts; snow lying on top wins)
     * at 0.15 x its volume; liquids stay quiet but add the swim sound.
     */
    private void stepSounds(double startX, double startY, double startZ) {
        int bx = floor(x), by = floor(y - 0.2), bz = floor(z);
        net.minecraft.block.Block block = world.get(bx, by, bz);
        if (block.isAir() && by > 0) {
            net.minecraft.block.Block below = world.get(bx, by - 1, bz);
            if (below instanceof net.minecraft.block.BlockFence || below instanceof net.minecraft.block.BlockWall
                || below instanceof net.minecraft.block.BlockFenceGate) {
                block = below;
                by--;
            }
        }
        double mx = x - startX, my = block.name().path().equals("ladder") ? y - startY : 0, mz = z - startZ;
        distanceWalkedOnStep += (float) (Math.sqrt(mx * mx + my * my + mz * mz) * 0.6);
        if (distanceWalkedOnStep <= nextStepDistance || block.isAir()) return;
        nextStepDistance = (int) distanceWalkedOnStep + 1;
        if (inWater) {
            float volume = (float) Math.min(1.0, Math.sqrt(motionX * motionX * 0.2 + motionY * motionY + motionZ * motionZ * 0.2) * 0.35);
            sounds.play("game.player.swim", volume, 1f + (random.nextFloat() - random.nextFloat()) * 0.4f);
        }
        net.minecraft.block.Block above = world.get(bx, by + 1, bz);
        if (above.name().path().equals("snow_layer")) block = above;
        else if (net.minecraft.block.Liquids.isLiquid(block)) return;
        net.minecraft.block.BlockSoundType type = net.minecraft.block.BlockSoundType.of(block);
        sounds.play(type.stepSound(), type.volume() * 0.15f, type.frequency());
    }

    /** What the blocks the player is inside do to them (Entity.doBlockCollisions). */
    private void touchBlocks() {
        AxisAlignedBB box = boundingBox();
        int minX = floor(box.minX() + 0.001), maxX = floor(box.maxX() - 0.001);
        int minY = floor(box.minY() + 0.001), maxY = floor(box.maxY() - 0.001);
        int minZ = floor(box.minZ() + 0.001), maxZ = floor(box.maxZ() - 0.001);
        for (int bx = minX; bx <= maxX; bx++)
            for (int by = minY; by <= maxY; by++)
                for (int bz = minZ; bz <= maxZ; bz++) {
                    switch (blockName(bx, by, bz)) {
                        case "soul_sand" -> { motionX *= 0.4; motionZ *= 0.4; }
                        case "web" -> inWeb = true;
                        case "portal" -> inPortal = true;
                        case "cactus" -> attack(DamageSource.CACTUS, 1f);
                        case "slime" -> {
                            if (Math.abs(motionY) < 0.1 && !sneaking) {
                                double factor = 0.4 + Math.abs(motionY) * 0.2;
                                motionX *= factor;
                                motionZ *= factor;
                            }
                        }
                        default -> { }
                    }
                }
    }

    private static double towardZero(double value, double step) {
        if (value < step && value >= -step) return 0;
        return value > 0 ? value - step : value + step;
    }

    private boolean isEmpty(AxisAlignedBB area) {
        for (AxisAlignedBB block : collisionBoxes(area)) if (block.intersects(area)) return false;
        return true;
    }

    private List<AxisAlignedBB> collisionBoxes(AxisAlignedBB area) {
        List<AxisAlignedBB> result = new ArrayList<>();
        int minX = floor(area.minX()), maxX = floor(area.maxX());
        int minY = floor(area.minY()) - 1, maxY = floor(area.maxY());
        int minZ = floor(area.minZ()), maxZ = floor(area.maxZ());
        for (int by = minY; by <= maxY; by++)
            for (int bz = minZ; bz <= maxZ; bz++)
                for (int bx = minX; bx <= maxX; bx++)
                    for (AxisAlignedBB box : Block.collisionBoxesAt(world, bx, by, bz))
                        if (box.intersects(area)) result.add(box);
        return result;
    }

    /** Whether any of these world-space boxes overlaps the player (used to refuse placements). */
    public boolean intersects(List<AxisAlignedBB> boxes) {
        AxisAlignedBB self = boundingBox();
        for (AxisAlignedBB box : boxes) if (box.intersects(self)) return true;
        return false;
    }

    public void turn(double mouseDx, double mouseDy) { turn(mouseDx, mouseDy, 0.5f, false); }

    /**
     * Mouse look with 1.8.9's sensitivity curve: {@code f = s * 0.6 + 0.2},
     * degrees per pixel {@code f^3 * 8 * 0.15}, so the default 0.5 gives the
     * 0.15 this client always used. HYPOTHESIS until measured.
     */
    public void turn(double mouseDx, double mouseDy, float sensitivity, boolean invert) {
        double f = sensitivity * 0.6 + 0.2;
        double scale = f * f * f * 8.0 * 0.15;
        yaw += (float) (mouseDx * scale);
        pitch += (float) (mouseDy * scale * (invert ? -1 : 1));
        pitch = Math.max(-90, Math.min(90, pitch));
    }

    /** Places the player (loading a saved position, teleporting): no motion, nothing to interpolate from. */
    public void setPositionAndRotation(double x, double y, double z, float yaw, float pitch) {
        this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        prevX = x; prevY = y; prevZ = z;
        motionX = motionY = motionZ = 0;
        fallDistance = 0;
    }

    /** The position drawn this frame, between the last two ticks. */
    public double renderX(float partialTicks) { return prevX + (x - prevX) * partialTicks; }
    public double renderY(float partialTicks) { return prevY + (y - prevY) * partialTicks; }
    public double renderZ(float partialTicks) { return prevZ + (z - prevZ) * partialTicks; }

    public double eyeY() { return y + eyeHeight(); }
    public double eyeHeight() { return sneaking ? 1.54 : 1.62; }
    public double dirX() { return Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }
    public double dirY() { return -Math.sin(Math.toRadians(pitch)); }
    public double dirZ() { return -Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }

    public void respawn() {
        dead = false;
        x = spawnX;
        z = spawnZ;
        y = world.surfaceHeight((int) Math.floor(x), (int) Math.floor(z)) + 2;
        prevX = x; prevY = y; prevZ = z;
        motionX = motionY = motionZ = 0;
        fallDistance = 0;
        if (!noClip) isFlying = false;
        health = 20f;
        foodStats.reset();
        activeEffects.clear();
        absorptionAmount = 0f;
        air = 300;
        hurtTime = hurtResistantTime = deathTime = 0;
        lastDamage = 0;
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static int floor(double n) { return (int) Math.floor(n); }
}

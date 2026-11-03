package net.minecraft.entity;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.entity.ai.MoveHelper;
import net.minecraft.entity.ai.PathNavigate;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.Random;

/**
 * A mob (1.8.9 EntityLivingBase + EntityLiving, HYPOTHESIS: recalled
 * rules): health, the 10-tick hurt flash and 20-tick protection window,
 * knockback, burning, drowning, suffocating and falling damage, death
 * (tipping over for 20 ticks, then a puff of smoke, its drops and, when the
 * player killed it, experience orbs), the AI task lists with the path
 * navigator and move, look and jump helpers, the body turning after the
 * head, pushing and being pushed, ambient sounds and despawning.
 */
public abstract class EntityLiving extends MovingEntity {
    public float health;
    public int hurtTime, deathTime;
    public final int maxHurtTime = 10;
    public int hurtResistantTime;
    protected float lastDamage;
    /** Ticks left during which a kill counts as the player's (recentlyHit). */
    protected int recentlyHit;
    private int lootingLevel;
    /** Ticks since the mob last saw the player close (entityAge): drives wandering and despawning. */
    protected int entityAge;
    public boolean persistenceRequired;
    /** What the mob holds (equipment slot 0): drawn in its hand, adds its attack damage, sometimes dropped. */
    public ItemStack heldItem;
    /** EntityLiving.equipmentDropChances[0]: 8.5% unless set otherwise. */
    protected float heldDropChance = 0.085f;
    public int air = 300;

    public float renderYawOffset, prevRenderYawOffset, rotationYawHead, prevRotationYawHead;
    public float limbSwing, limbSwingAmount, prevLimbSwingAmount;
    public float moveForward, moveStrafing;
    private float landMovementFactor;
    private boolean isJumping;
    private int jumpTicks;
    private int livingSoundTime;
    private int bodyTicks;
    private float prevBodyHead;

    private Player attackTarget, revengeTarget;
    private int revengeTimer;

    protected final EntityAI.Tasks tasks = new EntityAI.Tasks(), targetTasks = new EntityAI.Tasks();
    private final PathNavigate navigator;
    private final MoveHelper moveHelper;
    private final MoveHelper.Look lookHelper;

    protected EntityLiving(World world, double x, double y, double z) {
        super(world, x, y, z);
        stepHeight = 0.6f;
        navigator = new PathNavigate(this, world);
        moveHelper = new MoveHelper(this);
        lookHelper = new MoveHelper.Look(this);
        health = maxHealth();
        rotationYaw = (float) (RANDOM.nextDouble() * 360.0);
        renderYawOffset = rotationYawHead = rotationYaw;
    }

    // ---- attributes and sounds ----------------------------------------------------------------

    public abstract float maxHealth();
    /** The movement speed attribute (0.25 for most mobs). */
    public abstract double movementSpeed();
    /** How far it notices and paths to targets (the follow range attribute, 16). */
    public float followRange() { return 16f; }
    protected float attackDamage() { return 2f; }
    protected String livingSound() { return null; }

    /** EntityLiving.playLivingSound: the ambient sound once (a hatched mob calls out). */
    public void playLivingSound() {
        if (livingSound() != null) playSound(livingSound(), soundVolume(), soundPitch());
    }
    protected String hurtSound() { return "game.neutral.hurt"; }
    protected String deathSound() { return "game.neutral.die"; }
    protected String fallSound(int damage) { return damage > 4 ? "game.neutral.hurt.fall.big" : "game.neutral.hurt.fall.small"; }
    protected float soundVolume() { return 1f; }
    protected float soundPitch() { return (rand.nextFloat() - rand.nextFloat()) * 0.2f + 1f; }
    protected int talkInterval() { return 80; }
    /** The items dropped on death (dropFewItems); {@code byPlayer} when the player killed it recently. */
    protected void dropFewItems(boolean byPlayer, int looting) {}
    protected int experiencePoints() { return 0; }
    protected boolean canDespawn() { return true; }
    /** EntityLivingBase.canDropLoot: babies leave no items or experience. */
    protected boolean canDropLoot() { return true; }
    /**
     * EntityLiving.onInitialSpawn: set up a freshly spawned mob (natural
     * spawning, world generation, spawn eggs, /summon without data), such as
     * a sheep's random fleece.
     */
    public void onInitialSpawn() {}
    /** EntityCreature.getBlockPathWeight: how much the mob likes a spot (wandering targets, spawning). */
    public float blockPathWeight(int x, int y, int z) { return 0f; }

    public Random getRNG() { return rand; }
    public int idleAge() { return entityAge; }
    public PathNavigate navigator() { return navigator; }
    public MoveHelper moveHelper() { return moveHelper; }
    public MoveHelper.Look lookHelper() { return lookHelper; }
    public Player attackTarget() { return attackTarget; }
    public void setAttackTarget(Player target) { attackTarget = target; }
    public Player revengeTarget() { return revengeTarget; }
    /** EntityLivingBase.setRevengeTarget: as if this player had just hit it. */
    public void setRevengeTarget(Player player) {
        revengeTarget = player;
        revengeTimer = ticksExisted;
    }
    public int revengeTimer() { return revengeTimer; }
    /** EntityJumpHelper.setJumping: jump on this tick's movement. */
    public void setJumping(boolean jumping) { jumpRequested = jumping; }
    public void setAIMoveSpeed(float speed) {
        landMovementFactor = speed;
        moveForward = speed;
    }

    /** EntityLivingBase.heal: up to the maximum, never once dead. */
    public void heal(float amount) { if (health > 0) health = Math.min(maxHealth(), health + amount); }

    public boolean isAlive() { return !isRemoved() && health > 0; }

    /** The furthest drop a path may take: 3, more with a target and health to spare (EntityLiving.getMaxFallHeight). */
    public int maxFallHeight() {
        if (attackTarget == null) return 3;
        int i = (int) (health - maxHealth() * 0.33f);
        i -= (3 - (host == null ? 2 : host.difficulty())) * 4;
        if (i < 0) i = 0;
        return i + 3;
    }

    // ---- ticking -------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (frozen()) return;
        prevX = x; prevY = y; prevZ = z;
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
        prevRenderYawOffset = renderYawOffset;
        prevRotationYawHead = rotationYawHead;
        ticksExisted++;
        entityUpdate();
        if (isRemoved()) return;
        livingUpdate();
        // EntityLivingBase.onUpdate: the body turns after the head and the direction walked.
        updateBodyRotation();
        firstUpdate = false;
    }

    /** Entity/EntityLivingBase/EntityLiving.onEntityUpdate. */
    protected void entityUpdate() {
        handleWaterMovement();
        if (fire > 0) {
            if (isImmuneToFire()) {
                fire = Math.max(0, fire - 4);
            } else {
                if (fire % 20 == 0) attackEntityFrom(DamageSource.ON_FIRE, 1f, null);
                fire--;
            }
        }
        if (isInLava() && !isImmuneToFire()) {
            attackEntityFrom(DamageSource.LAVA, 4f, null);
            setFire(15);
            fallDistance *= 0.5f;
        }
        if (y < -64) attackEntityFrom(DamageSource.OUT_OF_WORLD, 4f, null);
        if (isAlive() && insideOpaqueBlock()) attackEntityFrom(DamageSource.IN_WALL, 1f, null);
        if (isAlive() && headInWater()) {
            if (--air == -20) {
                air = 0;
                if (host != null)
                    for (int i = 0; i < 8; i++) {
                        double dx = rand.nextFloat() - rand.nextFloat(), dy = rand.nextFloat() - rand.nextFloat(), dz = rand.nextFloat() - rand.nextFloat();
                        host.particle("bubble", x + dx, y + dy, z + dz, motionX, motionY, motionZ);
                    }
                attackEntityFrom(DamageSource.DROWN, 2f, null);
            }
            extinguish();
        } else {
            air = 300;
        }
        if (hurtTime > 0) hurtTime--;
        if (hurtResistantTime > 0) hurtResistantTime--;
        if (health <= 0) onDeathUpdate();
        if (recentlyHit > 0) recentlyHit--;
        if (revengeTarget != null && (revengeTarget.dead || ticksExisted - revengeTimer > 100)) revengeTarget = null;
        if (isAlive() && livingSound() != null && rand.nextInt(1000) < livingSoundTime++) {
            livingSoundTime = -talkInterval();
            playSound(livingSound(), soundVolume(), soundPitch());
        }
    }

    /** EntityLivingBase.onLivingUpdate. */
    protected void livingUpdate() {
        if (jumpTicks > 0) jumpTicks--;
        if (Math.abs(motionX) < 0.005) motionX = 0;
        if (Math.abs(motionY) < 0.005) motionY = 0;
        if (Math.abs(motionZ) < 0.005) motionZ = 0;
        if (health <= 0) {
            isJumping = jumpRequested = false;
            moveStrafing = moveForward = 0;
        } else {
            updateEntityActionState();
        }
        if (isJumping) {
            if (isInWater()) motionY += 0.03999999910593033;
            else if (isInLava()) jumpInLava();
            else if (onGround && jumpTicks == 0) {
                jump();
                jumpTicks = 10;
            }
        } else {
            jumpTicks = 0;
        }
        moveStrafing *= 0.98f;
        moveForward *= 0.98f;
        moveEntityWithHeading(moveStrafing, moveForward);
        collideWithNearbyEntities();
    }

    /** EntityLiving.updateEntityActionState: despawning, then the AI. */
    protected void updateEntityActionState() {
        entityAge++;
        despawnEntity();
        targetTasks.tick();
        tasks.tick();
        updateMovement();
        lookHelper.tick();
        // EntityJumpHelper.doJump: a jump asked for this tick holds for this tick only.
        isJumping = jumpRequested;
        jumpRequested = false;
    }
    private boolean jumpRequested;

    /** The path follower and move helper (slimes and magma cubes hop with their own). */
    protected void updateMovement() {
        navigator.tick();
        moveHelper.tick();
    }

    /** EntityLivingBase.handleJumpLava: swimming up through lava. */
    protected void jumpInLava() { motionY += 0.03999999910593033; }

    protected void jump() {
        motionY = 0.42;
        isAirBorne = true;
    }

    /**
     * EntityLivingBase.moveEntityWithHeading: water (0.8 drag, 0.02 push,
     * hopping out at walls), lava (0.5), otherwise ground friction 0.91 x
     * slipperiness with a push of moveSpeed x 0.16277 / f^3 (0.02 in the
     * air), 0.08 gravity and 0.98 drag; then the limbs swing with the
     * distance moved.
     */
    protected void moveEntityWithHeading(float strafe, float forward) {
        if (isInWater()) {
            double startY = y;
            moveFlying(strafe, forward, 0.02f);
            move(motionX, motionY, motionZ);
            motionX *= 0.8; motionY *= 0.8; motionZ *= 0.8;
            motionY -= 0.02;
            if (collidedHorizontally && offsetIsFree(motionX, motionY + 0.6 - y + startY, motionZ)) motionY = 0.3;
        } else if (isInLava()) {
            double startY = y;
            moveFlying(strafe, forward, 0.02f);
            move(motionX, motionY, motionZ);
            motionX *= 0.5; motionY *= 0.5; motionZ *= 0.5;
            motionY -= 0.02;
            if (collidedHorizontally && offsetIsFree(motionX, motionY + 0.6 - y + startY, motionZ)) motionY = 0.3;
        } else {
            float friction = onGround ? slipperinessBelow() * 0.91f : 0.91f;
            float push = onGround ? landMovementFactor * (0.16277136f / (friction * friction * friction)) : 0.02f;
            moveFlying(strafe, forward, push);
            friction = onGround ? slipperinessBelow() * 0.91f : 0.91f;
            boolean ladder = onLadder();
            if (ladder) {
                motionX = Math.max(-0.15, Math.min(0.15, motionX));
                motionZ = Math.max(-0.15, Math.min(0.15, motionZ));
                fallDistance = 0;
                if (motionY < -0.15) motionY = -0.15;
            }
            move(motionX, motionY, motionZ);
            if (collidedHorizontally && onLadder()) motionY = 0.2;
            if (world.generatedChunk(floor(x) >> 4, floor(z) >> 4) != null) motionY -= 0.08;
            else motionY = y > 0 ? -0.1 : 0;
            motionY *= 0.98;
            motionX *= friction;
            motionZ *= friction;
        }
        prevLimbSwingAmount = limbSwingAmount;
        double dx = x - prevX, dz = z - prevZ;
        float moved = (float) Math.sqrt(dx * dx + dz * dz) * 4f;
        if (moved > 1f) moved = 1f;
        limbSwingAmount += (moved - limbSwingAmount) * 0.4f;
        limbSwing += limbSwingAmount;
    }

    /** Entity.moveFlying: input along the body's facing. */
    protected void moveFlying(float strafe, float forward, float amount) {
        float length = strafe * strafe + forward * forward;
        if (length < 1.0E-4f) return;
        length = (float) Math.sqrt(length);
        if (length < 1f) length = 1f;
        length = amount / length;
        strafe *= length;
        forward *= length;
        float sin = (float) Math.sin(Math.toRadians(rotationYaw)), cos = (float) Math.cos(Math.toRadians(rotationYaw));
        motionX += strafe * cos - forward * sin;
        motionZ += forward * cos + strafe * sin;
    }

    private float slipperinessBelow() {
        int bx = floor(x), by = floor(boundingBox().minY()) - 1, bz = floor(z);
        if (by < 0) return 0.6f;
        return switch (world.get(bx, by, bz).name().path()) {
            case "ice", "packed_ice" -> 0.98f;
            case "slime" -> 0.8f;
            default -> 0.6f;
        };
    }

    protected boolean onLadder() {
        int by = floor(boundingBox().minY());
        if (by < 0 || by >= World.HEIGHT) return false;
        String name = world.get(floor(x), by, floor(z)).name().path();
        return name.equals("ladder") || name.equals("vine");
    }

    @Override
    protected void touchBlocks() {
        AxisAlignedBB box = boundingBox();
        int minX = floor(box.minX() + 0.001), maxX = floor(box.maxX() - 0.001);
        int minY = floor(box.minY() + 0.001), maxY = floor(box.maxY() - 0.001);
        int minZ = floor(box.minZ() + 0.001), maxZ = floor(box.maxZ() - 0.001);
        for (int bx = minX; bx <= maxX; bx++)
            for (int by = Math.max(0, minY); by <= Math.min(World.HEIGHT - 1, maxY); by++)
                for (int bz = minZ; bz <= maxZ; bz++) {
                    switch (world.get(bx, by, bz).name().path()) {
                        case "soul_sand" -> { motionX *= 0.4; motionZ *= 0.4; }
                        case "web" -> { motionX *= 0.25; motionY *= 0.05; motionZ *= 0.25; fallDistance = 0; }
                        case "cactus" -> attackEntityFrom(DamageSource.CACTUS, 1f, null);
                        default -> { }
                    }
                }
        // Entity.moveEntity: fire and lava blocks burn; water puts it out with a fizz.
        if (touchesFire()) {
            attackEntityFrom(DamageSource.IN_FIRE, 1f, null);
            if (!inWater) {
                fire++;
                if (fire == 0) setFire(8);
            }
        } else if (fire <= 0) {
            fire = -1;
        }
        if (inWater && fire > 0) {
            playSound("random.fizz", 0.7f, 1.6f + (rand.nextFloat() - rand.nextFloat()) * 0.4f);
            fire = -1;
        }
    }

    @Override
    protected void fall(float distance, float multiplier) {
        int damage = (int) Math.ceil((distance - 3f) * multiplier);
        if (damage <= 0) return;
        playSound(fallSound(damage), 1f, 1f);
        attackEntityFrom(DamageSource.FALL, damage, null);
        int bx = floor(x), by = floor(y - 0.2), bz = floor(z);
        if (by >= 0 && by < World.HEIGHT && !world.get(bx, by, bz).isAir() && host != null) {
            net.minecraft.block.BlockSoundType type = net.minecraft.block.BlockSoundType.of(world.get(bx, by, bz));
            host.playSound(type.stepSound(), x, y, z, type.volume() * 0.5f, type.frequency() * 0.75f);
        }
    }

    private boolean headInWater() {
        double eye = y + eyeHeight();
        int bx = floor(x), by = floor(eye), bz = floor(z);
        if (by < 0 || by >= World.HEIGHT) return false;
        net.minecraft.block.BlockState state = world.getState(bx, by, bz);
        if (!state.block().material().equals("water")) return false;
        float surface = by + 1 - (net.minecraft.block.Liquids.heightPercent(state.metadata()) - 0.11111111f);
        return eye < surface;
    }

    private boolean insideOpaqueBlock() {
        for (int i = 0; i < 8; i++) {
            double px = x + ((i % 2) - 0.5f) * width * 0.8f;
            double py = y + (((i >> 1) % 2) - 0.5f) * 0.1f + eyeHeight();
            double pz = z + (((i >> 2) % 2) - 0.5f) * width * 0.8f;
            int bx = floor(px), by = floor(py), bz = floor(pz);
            if (by < 0 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null) continue;
            if (world.get(bx, by, bz).isNormalCube()) return true;
        }
        return false;
    }

    /**
     * EntityBodyHelper.updateRenderAngles: walking turns the body to the
     * walking direction (the head kept within 75 degrees); standing, the
     * body follows the head, more and more freely after 10 still ticks.
     */
    private void updateBodyRotation() {
        double dx = x - prevX, dz = z - prevZ;
        if (dx * dx + dz * dz > 2.500000277905201E-7) {
            renderYawOffset = rotationYaw;
            rotationYawHead = bound(renderYawOffset, rotationYawHead, 75f);
            prevBodyHead = rotationYawHead;
            bodyTicks = 0;
            return;
        }
        float limit = 75f;
        if (Math.abs(rotationYawHead - prevBodyHead) > 15f) {
            bodyTicks = 0;
            prevBodyHead = rotationYawHead;
        } else {
            bodyTicks++;
            if (bodyTicks > 10) limit = Math.max(1f - (bodyTicks - 10) / 10f, 0f) * 75f;
        }
        renderYawOffset = bound(rotationYawHead, renderYawOffset, limit);
    }

    private static float bound(float a, float b, float max) {
        float diff = wrapDegrees(a - b);
        if (diff < -max) diff = -max;
        if (diff >= max) diff = max;
        return a - diff;
    }

    /** EntityLivingBase.collideWithNearbyEntities: mobs and the player push each other apart. */
    private void collideWithNearbyEntities() {
        if (host == null) return;
        AxisAlignedBB box = boundingBox();
        AxisAlignedBB near = new AxisAlignedBB(box.minX() - 0.2, box.minY(), box.minZ() - 0.2, box.maxX() + 0.2, box.maxY(), box.maxZ() + 0.2);
        for (Entity entity : host.entities()) {
            if (entity == this || !(entity instanceof EntityLiving other) || other.isRemoved()) continue;
            if (other.boundingBox().intersects(near)) push(other.x, other.z, other);
        }
        for (Player player : host.players())
            if (!player.dead && !player.noClip && player.boundingBox().intersects(near)) push(player.x, player.z, player);
    }

    /** Entity.applyEntityCollision: each is pushed 0.05 x (closeness) away from the other. */
    private void push(double ox, double oz, Object other) {
        double dx = ox - x, dz = oz - z;
        double max = Math.max(Math.abs(dx), Math.abs(dz));
        if (max < 0.01) return;
        max = Math.sqrt(max);
        dx /= max;
        dz /= max;
        double scale = Math.min(1.0, 1.0 / max);
        dx *= scale * 0.05;
        dz *= scale * 0.05;
        addVelocity(-dx, 0, -dz);
        if (other instanceof EntityLiving living) living.addVelocity(dx, 0, dz);
        else if (other instanceof Player player) {
            player.motionX += dx;
            player.motionZ += dz;
        }
    }

    /**
     * EntityLiving.despawnEntity: unless persistent, a mob more than 128
     * blocks from the nearest player goes at once; one idle 600+ ticks and more than
     * 32 blocks away goes 1 time in 800; within 32 blocks it stays awake.
     */
    private void despawnEntity() {
        if (persistenceRequired) {
            entityAge = 0;
            return;
        }
        Player player = host == null ? null : host.nearestPlayer(x, y, z);
        if (player == null) return;
        double distance = distanceSqTo(player.x, player.y, player.z);
        if (canDespawn() && distance > 16384.0) remove();
        if (entityAge > 600 && rand.nextInt(800) == 0 && distance > 1024.0 && canDespawn()) remove();
        else if (distance < 1024.0) entityAge = 0;
    }

    // ---- sight ---------------------------------------------------------------------------------

    /** EntitySenses.canSee / canEntityBeSeen: no block between the two pairs of eyes. */
    public boolean canSee(Player player) {
        return !net.minecraft.world.Explosion.blocked(world, x, y + eyeHeight(), z, player.x, player.eyeY(), player.z);
    }

    // ---- damage --------------------------------------------------------------------------------

    /**
     * EntityLivingBase.attackEntityFrom. Within the first 10 protected
     * ticks only damage above the last hit counts; otherwise the whole hit
     * restarts the protection, flashes red and knocks the mob away from
     * {@code attacker} (when there is one). Returns whether it was hurt.
     */
    public boolean attackEntityFrom(DamageSource source, float amount, Player attacker) {
        if (isRemoved() || health <= 0) return false;
        if (source.isFire() && isImmuneToFire()) return false;
        entityAge = 0;
        limbSwingAmount = 1.5f;
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
        if (attacker != null) {
            revengeTarget = attacker;
            revengeTimer = ticksExisted;
            recentlyHit = 100;
        }
        if (fresh && attacker != null) {
            double dx = attacker.x - x, dz = attacker.z - z;
            while (dx * dx + dz * dz < 1.0E-4) {
                dx = (Math.random() - Math.random()) * 0.01;
                dz = (Math.random() - Math.random()) * 0.01;
            }
            knockBack(dx, dz);
        }
        if (health <= 0) {
            playSound(deathSound(), soundVolume(), soundPitch());
            // EntityPlayer.onKillEntity and EntitySkeleton.onDeath: Monster Hunter, and Sniper Duel from 50 blocks.
            if (attacker != null && host != null && this instanceof net.minecraft.entity.monster.IMob) {
                host.achievement("killEnemy");
                double dx = attacker.x - x, dz = attacker.z - z;
                if (this instanceof net.minecraft.entity.monster.EntitySkeleton && source == DamageSource.ARROW && dx * dx + dz * dz >= 2500.0)
                    host.achievement("snipeSkeleton");
            }
            onDeath(source);
        } else if (fresh) {
            playSound(hurtSound(), soundVolume(), soundPitch());
        }
        return true;
    }

    /** Hit by the player's weapon: looting is remembered for the drops. */
    public boolean attackFromPlayer(Player player, float amount, int looting) {
        lootingLevel = looting;
        return attackEntityFrom(DamageSource.PLAYER, amount, player);
    }

    @Override
    public boolean attackFromExplosion(float damage, String attacker, MovingEntity exploder) {
        return attackEntityFrom(DamageSource.EXPLOSION, damage, null);
    }

    /** EntityLivingBase.damageEntity (no armour on these mobs). */
    protected void damageEntity(DamageSource source, float amount) {
        if (amount <= 0) return;
        health = Math.max(0f, health - amount);
    }

    /** EntityLivingBase.knockBack: halve the motion, push 0.4 away and 0.4 up (capped at 0.4). */
    public void knockBack(double dx, double dz) {
        isAirBorne = true;
        double length = Math.sqrt(dx * dx + dz * dz);
        motionX /= 2.0;
        motionY /= 2.0;
        motionZ /= 2.0;
        motionX -= dx / length * 0.4;
        motionY += 0.4;
        motionZ -= dz / length * 0.4;
        if (motionY > 0.4) motionY = 0.4;
    }

    /**
     * EntityMob.attackEntityAsMob: the attack damage attribute, which counts
     * the held weapon (difficulty-scaled by the player).
     */
    public boolean attackEntityAsMob(Player target) {
        return target.attackByMob(totalAttackDamage(), x, z, displayName());
    }

    /** The attack damage attribute with the held weapon's modifier. */
    public float totalAttackDamage() {
        float weapon = heldItem == null ? 0f : net.minecraft.item.ToolValues.attackDamage(heldItem.item()) - 1f;
        return attackDamage() + weapon;
    }

    /** Babies (zombie pigmen, animals) are half size; LegacyCraft's other mobs never are. */
    public boolean isChild() { return false; }

    /** Drawn at full brightness whatever the light (blazes, magma cubes: getBrightnessForRender 15728880). */
    public boolean fullBright() { return false; }

    /** EntityLiving.addRareDrop: the 2.5% bonus item when the player killed it ({@code bonus} 1 with looting luck). */
    protected void addRareDrop(int bonus) {}

    /**
     * EntityLiving.dropEquipment: the held item, 8.5% of the time (plus 1%
     * per looting level) when the player killed it, worn to a random
     * durability.
     */
    protected void dropEquipment(boolean byPlayer, int looting) {
        if (heldItem == null || !byPlayer && heldDropChance <= 1f) return;
        if (rand.nextFloat() - looting * 0.01f >= heldDropChance) return;
        ItemStack stack = heldItem.copy();
        int max = stack.item().maxDamage();
        if (heldDropChance <= 1f && max > 0) {
            int k = Math.max(max - 25, 1);
            int l = max - rand.nextInt(rand.nextInt(k) + 1);
            if (l > k) l = k;
            if (l < 1) l = 1;
            stack = new ItemStack(stack.item(), stack.count(), l);
        }
        if (host == null) return;
        EntityItem drop = new EntityItem(world, x, y, z, stack);
        drop.pickupDelay = 10;
        host.spawn(drop);
    }

    /** The name in death messages (entity.Creeper.name's translation key). */
    public String displayName() { return "entity." + entityId() + ".name"; }

    /** EntityLivingBase.onDeath: the drops, if the mobLoot rule allows. */
    protected void onDeath(DamageSource source) {
        if (host == null) return;
        int looting = recentlyHit > 0 ? lootingLevel : 0;
        if (canDropLoot() && host.gameRule("doMobLoot")) {
            dropFewItems(recentlyHit > 0, looting);
            dropEquipment(recentlyHit > 0, looting);
            if (recentlyHit > 0) {
                int roll = rand.nextInt(200) - looting;
                if (roll < 5) addRareDrop(roll <= 0 ? 1 : 0);
            }
        }
    }

    /**
     * EntityLivingBase.onDeathUpdate: after 20 ticks the body vanishes in 20
     * puffs of smoke, leaving experience orbs if the player killed it.
     */
    protected void onDeathUpdate() {
        deathTime++;
        if (deathTime != 20) return;
        if (host != null) {
            if (recentlyHit > 0 && canDropLoot() && host.gameRule("doMobLoot")) {
                int xp = experiencePoints();
                while (xp > 0) {
                    int split = EntityXPOrb.split(xp);
                    xp -= split;
                    EntityXPOrb orb = new EntityXPOrb(world, x, y, z, split);
                    host.spawn(orb);
                }
            }
            for (int i = 0; i < 20; i++) {
                double vx = rand.nextGaussian() * 0.02, vy = rand.nextGaussian() * 0.02, vz = rand.nextGaussian() * 0.02;
                host.particle("explode", x + rand.nextFloat() * width * 2f - width, y + rand.nextFloat() * height,
                    z + rand.nextFloat() * width * 2f - width, vx, vy, vz);
            }
        }
        remove();
    }

    /** Entity.dropItem: an item stack at the feet (pickup after 10 ticks). */
    protected void dropItem(String name, int count) { dropItem(name, count, 0f); }

    /** Entity.entityDropItem: an item stack {@code offsetY} above the feet (pickup after 10 ticks); the drop, or null. */
    protected EntityItem dropItem(String name, int count, float offsetY) {
        if (host == null || count <= 0) return null;
        java.util.function.Function<String, Item> items = world.itemLookup();
        Item item = items == null ? null : items.apply(name);
        if (item == null) return null;
        EntityItem drop = new EntityItem(world, x, y + offsetY, z, new ItemStack(item, count));
        drop.pickupDelay = 10;
        host.spawn(drop);
        return drop;
    }

    protected void playSound(String event, float volume, float pitch) {
        if (host != null && event != null) host.playSound(event, x, y, z, volume, pitch);
    }

    // ---- saving -------------------------------------------------------------------------------

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("HealF", new net.minecraft.nbt.NbtTag.Float(health));
        tag.put("Health", new net.minecraft.nbt.NbtTag.Short((short) Math.ceil(health)));
        tag.put("HurtTime", new net.minecraft.nbt.NbtTag.Short((short) hurtTime));
        tag.put("DeathTime", new net.minecraft.nbt.NbtTag.Short((short) deathTime));
        tag.put("Air", new net.minecraft.nbt.NbtTag.Short((short) air));
        tag.put("PersistenceRequired", new net.minecraft.nbt.NbtTag.Byte((byte) (persistenceRequired ? 1 : 0)));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("HealF") instanceof net.minecraft.nbt.NbtTag.Float heal) health = heal.value();
        else if (tag.get("Health") instanceof net.minecraft.nbt.NbtTag.Short shortHealth) health = shortHealth.value();
        if (tag.get("HurtTime") instanceof net.minecraft.nbt.NbtTag.Short hurt) hurtTime = hurt.value();
        if (tag.get("DeathTime") instanceof net.minecraft.nbt.NbtTag.Short death) deathTime = death.value();
        if (tag.get("Air") instanceof net.minecraft.nbt.NbtTag.Short breath) air = breath.value();
        if (tag.get("PersistenceRequired") instanceof net.minecraft.nbt.NbtTag.Byte persist) persistenceRequired = persist.value() != 0;
        renderYawOffset = prevRenderYawOffset = rotationYawHead = prevRotationYawHead = rotationYaw;
    }
}

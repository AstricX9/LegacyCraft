package net.minecraft.entity.monster;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.world.World;

/**
 * The magma cube (1.8.9 EntityMagmaCube + EntitySlime, HYPOTHESIS:
 * recalled): size 1, 2 or 4 (0.51 per size wide and tall, size^2 health,
 * 3 armour per size, speed 0.2 + 0.1 x size), always lit, never burns or
 * takes fall damage. It hops (0.42 + 0.1 x size high, every 40-116 ticks,
 * three times as often when chasing) toward the player it notices within
 * 16 blocks, otherwise in a random direction, swims up through lava, and
 * hurts the player it touches for size + 2. Landing squishes it and throws
 * flames. Bigger cubes split into 2-4 of half the size when they die and
 * drop magma cream (rand(4) - 2, plus looting); experience equals the size.
 */
public final class EntityMagmaCube extends EntityLiving implements IMob {
    private int size = 1;
    public float squishAmount, squishFactor, prevSquishFactor;
    private boolean wasOnGround;
    private float yRot;
    private int jumpDelay;
    private boolean moveUpdate, aggressive;
    private double moveSpeed;

    public EntityMagmaCube(World world, double x, double y, double z) {
        super(world, x, y, z);
        setSize(1);
        navigator().setCanSwim(true);
        tasks.add(1, new SwimTask(this));
        tasks.add(2, new Attack(this));
        tasks.add(3, new FaceRandom(this));
        tasks.add(5, new Hop(this));
        targetTasks.add(1, new FindPlayer(this));
    }

    @Override public String entityId() { return "LavaSlime"; }
    @Override public float maxHealth() { return size * size; }
    @Override public double movementSpeed() { return 0.2 + 0.1 * size; }
    @Override public boolean isImmuneToFire() { return true; }
    @Override public boolean fullBright() { return true; }
    @Override protected void fall(float distance, float multiplier) {}
    @Override protected boolean canTriggerWalking() { return false; }
    @Override protected String hurtSound() { return "mob.slime." + (size > 1 ? "big" : "small"); }
    @Override protected String deathSound() { return hurtSound(); }
    @Override protected float soundVolume() { return 0.4f * size; }
    @Override protected int experiencePoints() { return size; }
    @Override public float eyeHeight() { return 0.625f * height; }
    public int size() { return size; }

    /** EntitySlime.setSlimeSize. */
    public void setSize(int size) {
        this.size = size;
        width = height = 0.51000005f * size;
        health = maxHealth();
    }

    /** EntitySlime.onInitialSpawn: size 1, 2 or 4, larger more often on harder difficulties. */
    @Override
    public void onInitialSpawn() {
        int i = rand.nextInt(3);
        float difficulty = host == null ? 0.5f : host.difficulty() / 3f;
        if (i < 2 && rand.nextFloat() < 0.5f * difficulty) i++;
        setSize(1 << i);
    }

    private String jumpSound() { return size > 1 ? "mob.magmacube.big" : "mob.magmacube.small"; }

    /** EntityMagmaCube.getJumpDelay: four times the slime's 10-29 ticks. */
    private int jumpDelay() { return (rand.nextInt(20) + 10) * 4; }

    /** EntityMagmaCube.jump: higher for bigger cubes. */
    @Override
    protected void jump() {
        motionY = 0.42f + size * 0.1f;
        isAirBorne = true;
    }

    /** EntityMagmaCube.handleJumpLava. */
    @Override
    protected void jumpInLava() {
        motionY = 0.22f + size * 0.05f;
        isAirBorne = true;
    }

    /** EntitySlime.onUpdate: the squish and the landing's flames and sound; gone in Peaceful. */
    @Override
    public void tick() {
        if (host != null && host.difficulty() == 0) {
            remove();
            return;
        }
        squishFactor += (squishAmount - squishFactor) * 0.5f;
        prevSquishFactor = squishFactor;
        super.tick();
        if (isRemoved()) return;
        if (onGround && !wasOnGround) {
            if (host != null)
                for (int j = 0; j < size * 8; j++) {
                    float angle = rand.nextFloat() * (float) Math.PI * 2f;
                    float spread = rand.nextFloat() * 0.5f + 0.5f;
                    host.particle("flame", x + Math.sin(angle) * size * 0.5 * spread, boundingBox().minY(),
                        z + Math.cos(angle) * size * 0.5 * spread, 0, 0, 0);
                }
            playSound(jumpSound(), soundVolume(), ((rand.nextFloat() - rand.nextFloat()) * 0.2f + 1f) / 0.8f);
            squishAmount = -0.5f;
        } else if (!onGround && wasOnGround) {
            squishAmount = 1f;
        }
        wasOnGround = onGround;
        squishAmount *= 0.9f;
        touchPlayer();
    }

    /** EntitySlime.onCollideWithPlayer: size + 2 damage to a player in reach and in sight. */
    private void touchPlayer() {
        if (host == null || !isAlive()) return;
        Player player = host.player();
        if (player == null || player.dead || !player.boundingBox().intersects(boundingBox())) return;
        double reach = 0.6 * size;
        if (canSee(player) && distanceSqTo(player.x, player.y, player.z) < reach * reach
            && player.attackByMob(size + 2f, x, z, displayName()))
            playSound("mob.attack", 1f, (rand.nextFloat() - rand.nextFloat()) * 0.2f + 1f);
    }

    /** EntitySlime.SlimeMoveHelper: turn toward the chosen direction and hop when the delay runs out. */
    @Override
    protected void updateMovement() {
        rotationYaw = limitAngle(rotationYaw, yRot, 30f);
        rotationYawHead = renderYawOffset = rotationYaw;
        if (!moveUpdate) {
            moveForward = 0;
            return;
        }
        moveUpdate = false;
        if (onGround) {
            setAIMoveSpeed((float) (moveSpeed * movementSpeed()));
            if (jumpDelay-- <= 0) {
                jumpDelay = jumpDelay();
                if (aggressive) jumpDelay /= 3;
                setJumping(true);
                playSound(jumpSound(), soundVolume(), ((rand.nextFloat() - rand.nextFloat()) * 0.2f + 1f) * 0.8f);
            } else {
                moveStrafing = moveForward = 0;
                setAIMoveSpeed(0f);
            }
        } else {
            setAIMoveSpeed((float) (moveSpeed * movementSpeed()));
        }
    }

    private static float limitAngle(float current, float target, float max) {
        float change = (target - current) % 360f;
        if (change >= 180f) change -= 360f;
        if (change < -180f) change += 360f;
        if (change > max) change = max;
        if (change < -max) change = -max;
        return current + change;
    }

    void setDirection(float yaw, boolean aggressive) {
        this.yRot = yaw;
        this.aggressive = aggressive;
    }

    void setMoveSpeed(double speed) {
        this.moveSpeed = speed;
        this.moveUpdate = true;
    }

    /** Magma cubes wear 3 armour points per size (getTotalArmorValue). */
    @Override
    protected void damageEntity(DamageSource source, float amount) {
        if (!source.isUnblockable()) amount = amount * Math.max(0, 25 - size * 3) / 25f;
        super.damageEntity(source, amount);
    }

    /** EntitySlime.setDead: a big cube breaks into 2-4 half-size ones. */
    @Override
    protected void onDeathUpdate() {
        boolean vanishing = deathTime == 19;
        super.onDeathUpdate();
        if (vanishing && isRemoved() && size > 1 && host != null) {
            int count = 2 + rand.nextInt(3);
            for (int k = 0; k < count; k++) {
                float fx = ((k % 2) - 0.5f) * size / 4f, fz = ((k / 2) - 0.5f) * size / 4f;
                EntityMagmaCube child = new EntityMagmaCube(world, x + fx, y + 0.5, z + fz);
                child.setSize(size / 2);
                child.setPositionAndRotation(x + fx, y + 0.5, z + fz, rand.nextFloat() * 360f, 0f);
                child.persistenceRequired = persistenceRequired;
                host.spawn(child);
            }
        }
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        if (size <= 1) return;
        int count = rand.nextInt(4) - 2;
        if (looting > 0) count += rand.nextInt(looting + 1);
        for (int i = 0; i < count; i++) dropItem("magma_cream", 1);
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("Size", new net.minecraft.nbt.NbtTag.Int(size - 1));
        tag.put("wasOnGround", new net.minecraft.nbt.NbtTag.Byte((byte) (wasOnGround ? 1 : 0)));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        float saved = health;
        super.readNbt(tag);
        if (tag.get("Size") instanceof net.minecraft.nbt.NbtTag.Int s) {
            float loaded = health;
            setSize(Math.max(1, s.value() + 1));
            health = loaded;
        }
        if (tag.get("wasOnGround") instanceof net.minecraft.nbt.NbtTag.Byte b) wasOnGround = b.value() != 0;
    }

    /** EntitySlime.AISlimeFloat: swim up in lava or water. */
    private static final class SwimTask extends EntityAI {
        private final EntityMagmaCube cube;
        SwimTask(EntityMagmaCube cube) {
            this.cube = cube;
            mutexBits = 5;
        }
        @Override public boolean shouldExecute() { return cube.isInWater() || cube.isInLava(); }
        @Override public void updateTask() {
            if (cube.getRNG().nextFloat() < 0.8f) cube.setJumping(true);
            cube.setMoveSpeed(1.2);
        }
    }

    /** EntitySlime.AISlimeAttack: face the target and hop at it for up to 15 seconds at a time. */
    private static final class Attack extends EntityAI {
        private final EntityMagmaCube cube;
        private int timer;
        Attack(EntityMagmaCube cube) {
            this.cube = cube;
            mutexBits = 2;
        }
        @Override public boolean shouldExecute() {
            Player target = cube.attackTarget();
            return target != null && !target.dead && !target.disableDamage;
        }
        @Override public void startExecuting() { timer = 300; }
        @Override public boolean continueExecuting() { return shouldExecute() && --timer > 0; }
        @Override public void updateTask() {
            Player target = cube.attackTarget();
            if (target == null) return;
            double dx = target.x - cube.x(), dz = target.z - cube.z();
            float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
            cube.rotationYaw = limitAngle(cube.rotationYaw, yaw, 10f);
            cube.setDirection(cube.rotationYaw, true);
        }
    }

    /** EntitySlime.AISlimeFaceRandom: with no target, pick a new direction every 2-5 seconds. */
    private static final class FaceRandom extends EntityAI {
        private final EntityMagmaCube cube;
        private float chosen;
        private int next;
        FaceRandom(EntityMagmaCube cube) {
            this.cube = cube;
            mutexBits = 2;
        }
        @Override public boolean shouldExecute() {
            return cube.attackTarget() == null && (cube.onGround || cube.isInWater() || cube.isInLava());
        }
        @Override public void updateTask() {
            if (--next <= 0) {
                next = 40 + cube.getRNG().nextInt(60);
                chosen = cube.getRNG().nextInt(360);
            }
            cube.setDirection(chosen, false);
        }
    }

    /** EntitySlime.AISlimeHop: keep hopping. */
    private static final class Hop extends EntityAI {
        private final EntityMagmaCube cube;
        Hop(EntityMagmaCube cube) {
            this.cube = cube;
            mutexBits = 5;
        }
        @Override public boolean shouldExecute() { return true; }
        @Override public void updateTask() { cube.setMoveSpeed(1.0); }
    }

    /** EntityAIFindEntityNearestPlayer (follow range 16, 4 up or down). */
    private static final class FindPlayer extends EntityAI {
        private final EntityMagmaCube cube;
        FindPlayer(EntityMagmaCube cube) {
            this.cube = cube;
            mutexBits = 1;
        }
        @Override public boolean shouldExecute() {
            Player player = cube.host() == null ? null : cube.host().player();
            if (player == null || player.dead || player.disableDamage || player.noClip) return false;
            double range = cube.followRange() * (player.sneaking ? 0.8 : 1.0);
            if (!player.boundingBox().intersects(cube.boundingBox().grow(range, 4.0, range))) return false;
            if (cube.distanceSqTo(player.x, player.y, player.z) > range * range) return false;
            cube.setAttackTarget(player);
            return true;
        }
        @Override public boolean continueExecuting() {
            Player target = cube.attackTarget();
            if (target == null || target.dead || target.disableDamage || target.noClip) return false;
            double range = cube.followRange();
            return cube.distanceSqTo(target.x, target.y, target.z) <= range * range;
        }
        @Override public void resetTask() { cube.setAttackTarget(null); }
    }
}

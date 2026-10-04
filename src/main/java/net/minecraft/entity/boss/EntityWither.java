package net.minecraft.entity.boss;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityItem;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityWitherSkull;
import net.minecraft.entity.MovingEntity;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The wither (1.8.9 EntityWither, HYPOTHESIS: recalled): 300 health, speed
 * 0.6, 4 armour, follows from 40 blocks, 0.9 x 3.5, never burns, always
 * lit, never despawns, falls slowly and climbs toward its target, staying
 * 5 blocks above it until armoured. Built, it swells for 11 seconds
 * (invulnerable, healing 10 every half second from a third of its health),
 * then explodes with strength 7 and the spawn roar is heard everywhere.
 * The middle head shoots the attack target every 2 seconds within 20
 * blocks; each side head picks any living thing that isn't undead within
 * 20 blocks (8 up or down) and shoots it every 2-3 seconds within 30, and
 * on Normal and Hard fires a blue skull at a random spot after 16 idle
 * looks. Hurt, it breaks the blocks around it a second later (if
 * mobGriefing allows). Below half health it is armoured: arrows bounce off.
 * It heals 1 a second, drops a nether star and 50 experience.
 */
public final class EntityWither extends EntityMob {
    /** The swelling after it is built (getInvulTime): 220 ticks. */
    public int invulTime;
    /** The side heads' pitch and yaw (field_82220_d / field_82221_e) and their previous values. */
    public final float[] headPitch = new float[2], headYaw = new float[2], prevHeadPitch = new float[2], prevHeadYaw = new float[2];
    private final int[] nextHeadUpdate = new int[2], idleHeadUpdates = new int[2];
    /** The side heads' targets (the watched target ids 1 and 2): a Player or an EntityLiving, or null. */
    private final Object[] headTargets = new Object[2];
    private int blockBreakCounter;

    public EntityWither(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = 0.9f;
        height = 3.5f;
        tasks.add(0, new EntityAI.Swimming(this));
        tasks.add(2, new SkullAttack(this));
        tasks.add(5, new EntityAI.Wander(this, 1.0));
        tasks.add(6, new EntityAI.WatchClosest(this, 8f));
        tasks.add(7, new EntityAI.LookIdle(this));
        targetTasks.add(1, new EntityAI.HurtByTarget(this));
        targetTasks.add(2, new EntityAI.NearestAttackableTarget(this));
    }

    @Override public String entityId() { return "WitherBoss"; }
    @Override public float maxHealth() { return 300f; }
    @Override public double movementSpeed() { return 0.6; }
    @Override public float followRange() { return 40f; }
    @Override public boolean isImmuneToFire() { return true; }
    @Override public boolean fullBright() { return true; }
    @Override public float eyeHeight() { return 2.975f; }
    @Override protected String livingSound() { return "mob.wither.idle"; }
    @Override protected String hurtSound() { return "mob.wither.hurt"; }
    @Override protected String deathSound() { return "mob.wither.death"; }
    @Override protected int experiencePoints() { return 50; }
    @Override protected boolean canDespawn() { return false; }
    @Override protected void fall(float distance, float multiplier) {}

    /** Below half health: the armour aura, and arrows bounce off (isArmored). */
    public boolean isArmored() { return health <= maxHealth() / 2f; }

    /** func_82206_m: freshly built, invulnerable for 220 ticks at a third of its health. */
    public void startSpawning() {
        invulTime = 220;
        health = maxHealth() / 3f;
    }

    /** EntityWither.onLivingUpdate: hovering after the middle head's target, then the heads turn and smoke. */
    @Override
    protected void livingUpdate() {
        motionY *= 0.6000000238418579;
        Player target = attackTarget();
        if (target != null && !target.dead) {
            if (y < target.y || !isArmored() && y < target.y + 5.0) {
                if (motionY < 0) motionY = 0;
                motionY += (0.5 - motionY) * 0.6000000238418579;
            }
            double dx = target.x - x, dz = target.z - z;
            double d = dx * dx + dz * dz;
            if (d > 9.0) {
                double length = Math.sqrt(d);
                motionX += (dx / length * 0.5 - motionX) * 0.6000000238418579;
                motionZ += (dz / length * 0.5 - motionZ) * 0.6000000238418579;
            }
        }
        if (motionX * motionX + motionZ * motionZ > 0.05000000074505806)
            rotationYaw = (float) Math.atan2(motionZ, motionX) * (180f / (float) Math.PI) - 90f;
        super.livingUpdate();
        for (int i = 0; i < 2; i++) {
            prevHeadYaw[i] = headYaw[i];
            prevHeadPitch[i] = headPitch[i];
        }
        for (int j = 0; j < 2; j++) {
            Object t = headTargets[j];
            if (t != null) {
                double hx = headX(j + 1), hy = headY(j + 1), hz = headZ(j + 1);
                double dx = tx(t) - hx, dy = ty(t) + teye(t) - hy, dz = tz(t) - hz;
                double distance = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
                float pitch = (float) -(Math.atan2(dy, distance) * 180.0 / Math.PI);
                headPitch[j] = turn(headPitch[j], pitch, 40f);
                headYaw[j] = turn(headYaw[j], yaw, 10f);
            } else {
                headYaw[j] = turn(headYaw[j], renderYawOffset, 10f);
            }
        }
        if (host != null) {
            boolean armored = isArmored();
            for (int l = 0; l < 3; l++) {
                double hx = headX(l), hy = headY(l), hz = headZ(l);
                host.particle("smoke", hx + rand.nextGaussian() * 0.3, hy + rand.nextGaussian() * 0.3, hz + rand.nextGaussian() * 0.3, 0, 0, 0);
                if (armored && rand.nextInt(4) == 0)
                    host.particle("mobSpell", hx + rand.nextGaussian() * 0.3, hy + rand.nextGaussian() * 0.3, hz + rand.nextGaussian() * 0.3,
                        0.7, 0.7, 0.5);
            }
            if (invulTime > 0)
                for (int i = 0; i < 3; i++)
                    host.particle("mobSpell", x + rand.nextGaussian(), y + rand.nextFloat() * 3.3f, z + rand.nextGaussian(), 0.7, 0.7, 0.9);
        }
    }

    /** EntityWither.updateAITasks. */
    @Override
    protected void updateEntityActionState() {
        if (invulTime > 0) {
            int left = invulTime - 1;
            if (left <= 0 && host != null) {
                host.explode(this, x, y + eyeHeight(), z, 7f, false, host.gameRule("mobGriefing"), null);
                host.globalSound("mob.wither.spawn", x, y, z);
            }
            invulTime = left;
            if (ticksExisted % 10 == 0) heal(10f);
            return;
        }
        super.updateEntityActionState();
        for (int i = 1; i < 3; i++) {
            if (ticksExisted < nextHeadUpdate[i - 1]) continue;
            nextHeadUpdate[i - 1] = ticksExisted + 10 + rand.nextInt(10);
            int difficulty = host == null ? 2 : host.difficulty();
            if (difficulty == 2 || difficulty == 3) {
                int idle = idleHeadUpdates[i - 1]++;
                if (idle > 15) {
                    double sx = x - 10 + rand.nextDouble() * 20, sy = y - 5 + rand.nextDouble() * 10, sz = z - 10 + rand.nextDouble() * 20;
                    launchSkull(i + 1, sx, sy, sz, true);
                    idleHeadUpdates[i - 1] = 0;
                }
            }
            Object t = headTargets[i - 1];
            if (t != null) {
                if (alive(t) && distanceSqTo(tx(t), ty(t), tz(t)) <= 900.0 && sees(t)) {
                    if (t instanceof Player p && p.disableDamage) {
                        headTargets[i - 1] = null;
                    } else {
                        launchSkull(i + 1, tx(t), ty(t) + teye(t) * 0.5, tz(t), false);
                        nextHeadUpdate[i - 1] = ticksExisted + 40 + rand.nextInt(20);
                        idleHeadUpdates[i - 1] = 0;
                    }
                } else {
                    headTargets[i - 1] = null;
                }
            } else {
                List<Object> candidates = nearbyTargets();
                for (int tries = 0; tries < 10 && !candidates.isEmpty(); tries++) {
                    Object c = candidates.get(rand.nextInt(candidates.size()));
                    if (c != this && alive(c) && sees(c)) {
                        if (!(c instanceof Player p) || !p.disableDamage) headTargets[i - 1] = c;
                        break;
                    }
                    candidates.remove(c);
                }
            }
        }
        if (blockBreakCounter > 0 && --blockBreakCounter == 0 && host != null && host.gameRule("mobGriefing")) {
            int by = floor(y), bx = floor(x), bz = floor(z);
            boolean broke = false;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int dy = 0; dy <= 3; dy++) {
                        int px = bx + dx, py = by + dy, pz = bz + dz;
                        if (py < 0 || py >= World.HEIGHT) continue;
                        var block = world.get(px, py, pz);
                        if (block.isAir() || !canDestroy(block.name().path())) continue;
                        host.breakBlock(px, py, pz, true);
                        broke = true;
                    }
            // World.playAuxSFX 1012: the zombie's door-breaking crash.
            if (broke) host.playSound("mob.zombie.woodbreak", x, y, z, 2f, (rand.nextFloat() - rand.nextFloat()) * 0.2f + 1f);
        }
        if (ticksExisted % 20 == 0) heal(1f);
    }

    /** EntityWither.canDestroyBlock: everything but bedrock, end portals and their frames, command blocks and barriers. */
    public static boolean canDestroy(String block) {
        return !block.equals("bedrock") && !block.equals("end_portal") && !block.equals("end_portal_frame")
            && !block.equals("command_block") && !block.equals("barrier");
    }

    /** The living things a side head may pick: within 20 blocks (8 up or down), not undead (attackEntitySelector). */
    private List<Object> nearbyTargets() {
        List<Object> result = new ArrayList<>();
        if (host == null) return result;
        AxisAlignedBB area = boundingBox().grow(20.0, 8.0, 20.0);
        for (Entity entity : host.entities())
            if (entity instanceof EntityLiving living && living.isAlive() && !isUndead(living) && living.boundingBox().intersects(area))
                result.add(living);
        Player player = host.player();
        if (player != null && !player.dead && !player.noClip && player.boundingBox().intersects(area)) result.add(player);
        return result;
    }

    /** EnumCreatureAttribute.UNDEAD: skeletons, zombie pigmen and withers. */
    public static boolean isUndead(EntityLiving living) {
        return living instanceof EntityWither || living instanceof net.minecraft.entity.monster.EntitySkeleton
            || living instanceof net.minecraft.entity.monster.EntityPigZombie;
    }

    /** EntityWither.launchWitherSkullToCoords: the shoot sound and a skull from the head toward the spot. */
    void launchSkull(int head, double tx, double ty, double tz, boolean blue) {
        if (host == null) return;
        host.playSound("mob.wither.shoot", x, y, z, 2f, (rand.nextFloat() - rand.nextFloat()) * 0.2f + 1f);
        double hx = headX(head), hy = headY(head), hz = headZ(head);
        EntityWitherSkull skull = EntityWitherSkull.shotBy(world, this, tx - hx, ty - hy, tz - hz, rand);
        skull.invulnerable = blue;
        skull.setPosition(hx, hy, hz);
        skull.prevX = hx;
        skull.prevY = hy;
        skull.prevZ = hz;
        host.spawn(skull);
    }

    /** func_82214_u / func_82208_v / func_82213_w: where head 0 (middle), 1 or 2 is. */
    public double headX(int head) {
        if (head <= 0) return x;
        double a = (renderYawOffset + 180f * (head - 1)) / 180.0 * Math.PI;
        return x + Math.cos(a) * 1.3;
    }
    public double headY(int head) { return head <= 0 ? y + 3.0 : y + 2.2; }
    public double headZ(int head) {
        if (head <= 0) return z;
        double a = (renderYawOffset + 180f * (head - 1)) / 180.0 * Math.PI;
        return z + Math.sin(a) * 1.3;
    }

    /** func_82204_b: turn toward {@code target} by at most {@code max} degrees. */
    private static float turn(float current, float target, float max) {
        float change = target - current;
        while (change < -180f) change += 360f;
        while (change >= 180f) change -= 360f;
        if (change > max) change = max;
        if (change < -max) change = -max;
        return current + change;
    }

    private static double tx(Object t) { return t instanceof Player p ? p.x : ((MovingEntity) t).x(); }
    private static double ty(Object t) { return t instanceof Player p ? p.y : ((MovingEntity) t).y(); }
    private static double tz(Object t) { return t instanceof Player p ? p.z : ((MovingEntity) t).z(); }
    private static double teye(Object t) { return t instanceof Player p ? p.eyeHeight() : ((MovingEntity) t).eyeHeight(); }
    private static boolean alive(Object t) { return t instanceof Player p ? !p.dead && p.health > 0 : ((EntityLiving) t).isAlive(); }

    private boolean sees(Object t) {
        return !net.minecraft.world.Explosion.blocked(world, x, y + eyeHeight(), z, tx(t), ty(t) + teye(t), tz(t));
    }

    /** EntityWither.attackEntityFrom: nothing hurts it while it swells; armoured, arrows bounce off; a hit sets it breaking blocks. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount, Player attacker) {
        if (source == DamageSource.DROWN) return false;
        if (invulTime > 0 && source != DamageSource.OUT_OF_WORLD) return false;
        if (isArmored() && source == DamageSource.ARROW) return false;
        if (blockBreakCounter <= 0) blockBreakCounter = 20;
        for (int i = 0; i < 2; i++) idleHeadUpdates[i] += 3;
        return super.attackEntityFrom(source, amount, attacker);
    }

    /** 4 armour points take damage down by 16%. */
    @Override
    protected void damageEntity(DamageSource source, float amount) {
        if (!source.isUnblockable()) amount = amount * (25 - 4) / 25f;
        super.damageEntity(source, amount);
    }

    /** EntityWither.dropFewItems: a nether star that lasts twice as long, and Beatdown for the player. */
    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        EntityItem star = dropItem("nether_star", 1, 0f);
        if (star != null) star.age = -EntityItem.LIFESPAN;
        if (host != null && host.player() != null && host.player().boundingBox().intersects(boundingBox().grow(50.0, 100.0, 50.0)))
            host.achievement("killWither");
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("Invul", new net.minecraft.nbt.NbtTag.Int(invulTime));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("Invul") instanceof net.minecraft.nbt.NbtTag.Int invul) invulTime = invul.value();
    }

    /** EntityAIArrowAttack(1.0, 40, 20): close in until the target has been in sight a second within 20 blocks, a skull every 2 seconds. */
    private static final class SkullAttack extends EntityAI {
        private final EntityWither wither;
        private Player target;
        private int rangedAttackTime = -1, seeTime;
        SkullAttack(EntityWither wither) {
            this.wither = wither;
            mutexBits = 3;
        }
        @Override public boolean shouldExecute() {
            Player t = wither.attackTarget();
            if (t == null || t.dead) return false;
            target = t;
            return true;
        }
        @Override public boolean continueExecuting() { return shouldExecute() || !wither.navigator().noPath(); }
        @Override public void resetTask() {
            target = null;
            seeTime = 0;
            rangedAttackTime = -1;
        }
        @Override public void updateTask() {
            if (target == null) return;
            double distance = wither.distanceSqTo(target.x, target.boundingBox().minY(), target.z);
            boolean sees = wither.canSee(target);
            if (sees) seeTime++;
            else seeTime = 0;
            if (distance <= 400.0 && seeTime >= 20) wither.navigator().clearPath();
            else wither.navigator().tryMoveTo(target.x, target.y, target.z, 1.0);
            wither.lookHelper().setLookPosition(target.x, target.y + target.eyeHeight(), target.z, 30f, 30f);
            if (--rangedAttackTime == 0) {
                if (distance > 400.0 || !sees) return;
                // EntityWither.launchWitherSkullToEntity(0, target): rarely (1 in 1000) a blue one.
                wither.launchSkull(0, target.x, target.y + target.eyeHeight() * 0.5, target.z, wither.getRNG().nextFloat() < 0.001f);
                rangedAttackTime = 40;
            } else if (rangedAttackTime < 0) {
                rangedAttackTime = 40;
            }
        }
    }
}

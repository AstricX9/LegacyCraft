package net.minecraft.entity.boss;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAreaEffectCloud;
import net.minecraft.entity.EntityDragonFireball;
import net.minecraft.entity.EntityEnderCrystal;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.Set;

/**
 * The ender dragon (1.9 EntityDragon and its PhaseManager, HYPOTHESIS:
 * recalled): 200 health, 16 x 8, flying through blocks. Its body follows a
 * 64-tick ring buffer of yaw and height that the model and its parts trail
 * behind. It circles the island between the pillars (the outer ring of
 * path nodes while crystals stand, the middle one after), now and then
 * strafing the player with fireballs or landing on the exit portal, where it
 * looks for the player, roars and breathes dragon's breath three or four
 * times before taking off again. Crystals within 32 blocks heal it 1 every
 * half second; breaking the one healing it costs it 10. Hits to anything
 * but the head do a quarter (+1). Wings knock back and hurt for 5, the head
 * for 10, and it breaks every block it flies through except the End's own
 * stone, obsidian, bedrock, iron bars and portals. Dying, it flies to the
 * portal and comes apart in 10 seconds of light and experience.
 * shortcut: flies straight between path nodes (vanilla runs A* over them); add the graph when it matters.
 */
public final class EntityDragon extends EntityLiving implements IMob {
    public enum Phase { HOLDING_PATTERN, STRAFE_PLAYER, LANDING_APPROACH, LANDING, TAKEOFF, SITTING_SCANNING, SITTING_ATTACKING,
        SITTING_FLAMING, CHARGING_PLAYER, DYING, HOVER;
        public boolean stationary() { return this == SITTING_SCANNING || this == SITTING_ATTACKING || this == SITTING_FLAMING; }
    }

    /** Blocks the dragon can't break (EntityDragon.destroyBlocksInAABB). */
    private static final Set<String> UNBREAKABLE = Set.of("barrier", "obsidian", "end_stone", "bedrock", "end_portal",
        "end_portal_frame", "command_block", "repeating_command_block", "chain_command_block", "iron_bars", "end_gateway");

    /** Body parts: head, neck, body, three tail pieces, two wings (EntityDragonPart). */
    public static final int HEAD = 0, NECK = 1, BODY = 2, TAIL1 = 3, WING_RIGHT = 6, WING_LEFT = 7;
    private static final float[][] PART_SIZES = {{6, 6}, {6, 6}, {8, 8}, {4, 4}, {4, 4}, {4, 4}, {4, 4}, {4, 4}};
    /** Part centres (x, y bottom, z). */
    public final double[][] parts = new double[8][3];

    public final double[][] ringBuffer = new double[64][3];
    public int ringBufferIndex = -1;
    public float prevAnimTime, animTime;
    public float randomYawVelocity;
    public boolean slowed;
    public int deathTicks;
    public EntityEnderCrystal healingCrystal;

    public Phase phase = Phase.HOLDING_PATTERN;
    private Phase previousPhase;
    private int phaseTicks, fireballCharge, flameCount;
    private float sittingDamage;
    private boolean clockwise = true;
    /** Where the current phase is flying to, or null to hover in place. */
    public double[] target;
    private Player strafeTarget;
    /** The part the player's last hit landed on (set by the game's aim), or -1. */
    public int hitPart = -1;

    /** The fight it belongs to (the End's), or null. */
    public net.minecraft.world.DragonFight fight;
    /** Told once, when the death animation ends. */
    public Runnable onDeath = () -> {};
    /** The y of the exit portal's top (where it lands); set by the game. */
    public int podiumTop = 64;

    public EntityDragon(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = 16f;
        height = 8f;
        health = maxHealth();
        persistenceRequired = true;
    }

    @Override public String entityId() { return "EnderDragon"; }
    @Override public float maxHealth() { return 200f; }
    @Override public double movementSpeed() { return 0; }
    @Override public boolean isImmuneToFire() { return true; }
    @Override public boolean fullBright() { return true; }
    @Override protected String livingSound() { return "mob.enderdragon.growl"; }
    @Override protected String hurtSound() { return "mob.enderdragon.hit"; }
    @Override protected String deathSound() { return null; }
    @Override protected float soundVolume() { return 5f; }
    @Override protected boolean canDespawn() { return false; }
    @Override protected boolean canTriggerWalking() { return false; }
    @Override public boolean isImmuneToExplosions() { return false; }

    public void setPhase(Phase next) {
        if (phase == Phase.DYING && next != Phase.DYING) return;
        previousPhase = phase;
        phase = next;
        phaseTicks = 0;
        target = null;
        switch (next) {
            case SITTING_FLAMING -> flameCount++;
            case LANDING -> flameCount = 0;
            case SITTING_SCANNING, SITTING_ATTACKING -> { motionX = motionY = motionZ = 0; }
            default -> {}
        }
        if (next == Phase.SITTING_ATTACKING && host != null) host.playSound("mob.enderdragon.growl", x, y, z, 2.5f, 0.8f + rand.nextFloat() * 0.3f);
        if (next.stationary()) sittingDamage = 0;
    }

    // ---- the ring buffer (getMovementOffsets) -------------------------------------------------

    /** The yaw and height {@code offset} ticks ago, between ticks by {@code partialTicks}. */
    public double[] movementOffsets(int offset, float partialTicks) {
        if (health <= 0) partialTicks = 0;
        partialTicks = 1f - partialTicks;
        int i = ringBufferIndex - offset & 63, j = ringBufferIndex - offset - 1 & 63;
        double yaw = ringBuffer[i][0], dyaw = wrap(ringBuffer[j][0] - yaw);
        double y0 = ringBuffer[i][1], dy = ringBuffer[j][1] - y0;
        return new double[]{yaw + dyaw * partialTicks, y0 + dy * partialTicks, ringBuffer[i][2] + (ringBuffer[j][2] - ringBuffer[i][2]) * partialTicks};
    }

    private static double wrap(double angle) {
        angle %= 360.0;
        if (angle >= 180.0) angle -= 360.0;
        if (angle < -180.0) angle += 360.0;
        return angle;
    }

    // ---- ticking ------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (frozen()) return;
        prevX = x; prevY = y; prevZ = z;
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
        prevRenderYawOffset = renderYawOffset;
        prevRotationYawHead = rotationYawHead;
        ticksExisted++;
        if (hurtTime > 0) hurtTime--;
        if (hurtResistantTime > 0) hurtResistantTime--;
        prevAnimTime = animTime;
        if (health <= 0) {
            deathUpdate();
            return;
        }
        if (host != null) {
            float f = (float) Math.cos(animTime * Math.PI * 2), f1 = (float) Math.cos(prevAnimTime * Math.PI * 2);
            if (f1 <= -0.3f && f >= -0.3f) host.playSound("mob.enderdragon.wings", x, y, z, 5f, 0.8f + rand.nextFloat() * 0.3f);
            if (rand.nextInt(1000) < 5 && phase != Phase.DYING) playSound("mob.enderdragon.growl", 5f, soundPitch());
        }
        updateCrystal();
        float speed = 0.2f / ((float) Math.sqrt(motionX * motionX + motionZ * motionZ) * 10f + 1f);
        speed *= (float) Math.pow(2.0, motionY);
        animTime += phase.stationary() ? 0.1f : slowed ? speed * 0.5f : speed;
        rotationYaw = (float) wrap(rotationYaw);
        if (ringBufferIndex < 0)
            for (double[] entry : ringBuffer) { entry[0] = rotationYaw; entry[1] = y; }
        if (++ringBufferIndex == 64) ringBufferIndex = 0;
        ringBuffer[ringBufferIndex][0] = rotationYaw;
        ringBuffer[ringBufferIndex][1] = y;
        if (host != null) phaseUpdate();
        if (target != null) fly();
        renderYawOffset = rotationYaw;
        rotationYawHead = rotationYaw;
        updateParts();
        if (host != null) {
            if (hurtTime == 0) {
                knockBack(partBox(WING_RIGHT).grow(4, 2, 4).offset(0, -2, 0));
                knockBack(partBox(WING_LEFT).grow(4, 2, 4).offset(0, -2, 0));
                bite(partBox(HEAD).grow(1, 1, 1));
                bite(partBox(NECK).grow(1, 1, 1));
            }
            slowed = breakBlocks(partBox(HEAD)) | breakBlocks(partBox(NECK)) | breakBlocks(partBox(BODY));
        }
    }

    /** The flight step toward {@link #target} (EntityDragon.onLivingUpdate). */
    private void fly() {
        double dx = target[0] - x, dy = target[1] - y, dz = target[2] - z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        float maxRise = maxRiseOrFall();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double rise = horizontal > 0 ? Math.max(-maxRise, Math.min(maxRise, dy / horizontal)) : Math.signum(dy) * maxRise;
        motionY += rise * 0.1;
        rotationYaw = (float) wrap(rotationYaw);
        double turn = Math.max(-50, Math.min(50, wrap(180.0 - Math.toDegrees(Math.atan2(dx, dz)) - rotationYaw)));
        double length = Math.sqrt(distanceSq);
        double tx = length > 0 ? dx / length : 0, ty = length > 0 ? dy / length : 0, tz = length > 0 ? dz / length : 0;
        double fx = Math.sin(Math.toRadians(rotationYaw)), fy = motionY, fz = -Math.cos(Math.toRadians(rotationYaw));
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        float facing = Math.max(((float) (fx * tx + fy * ty + fz * tz) + 0.5f) / 1.5f, 0f);
        randomYawVelocity *= 0.8f;
        randomYawVelocity = (float) (randomYawVelocity + turn * yawFactor());
        rotationYaw += randomYawVelocity * 0.1f;
        float near = (float) (2.0 / (distanceSq + 1.0));
        moveFlying(0f, -1f, 0.06f * (facing * near + (1f - near)));
        double scale = slowed ? 0.8 : 1.0;
        setPosition(x + motionX * scale, y + motionY * scale, z + motionZ * scale);
        double ml = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        float along = ml > 0 ? ((float) ((motionX * fx + motionY * fy + motionZ * fz) / ml) + 1f) / 2f : 0.5f;
        along = 0.8f + 0.15f * along;
        motionX *= along;
        motionZ *= along;
        motionY *= 0.91;
    }

    private float maxRiseOrFall() {
        return switch (phase) {
            case LANDING -> 1.5f;
            case CHARGING_PLAYER, DYING -> 3f;
            default -> 0.6f;
        };
    }

    private float yawFactor() {
        float f = (float) Math.sqrt(motionX * motionX + motionZ * motionZ) + 1f, f1 = Math.min(f, 40f);
        return phase == Phase.LANDING ? f1 / f : 0.7f / f1 / f;
    }

    // ---- phases -------------------------------------------------------------------------------

    /** The 24 path nodes (EntityDragon.initPathPoints): 12 at radius 60, 8 at 40, 4 at 20, 10 to 20 above the ground. */
    public double[] node(int i) {
        int nx, nz, up = 5;
        if (i < 12) {
            nx = (int) Math.floor(60.0 * Math.cos(2.0 * (-Math.PI + 0.2617994 * i)));
            nz = (int) Math.floor(60.0 * Math.sin(2.0 * (-Math.PI + 0.2617994 * i)));
        } else if (i < 20) {
            int j = i - 12;
            nx = (int) Math.floor(40.0 * Math.cos(2.0 * (-Math.PI + 0.3926991 * j)));
            nz = (int) Math.floor(40.0 * Math.sin(2.0 * (-Math.PI + 0.3926991 * j)));
            up += 10;
        } else {
            int j = i - 20;
            nx = (int) Math.floor(20.0 * Math.cos(2.0 * (-Math.PI + 0.7853982 * j)));
            nz = (int) Math.floor(20.0 * Math.sin(2.0 * (-Math.PI + 0.7853982 * j)));
        }
        int top = world.generatedChunk(nx >> 4, nz >> 4) == null ? 0 : world.surfaceHeight(nx, nz) + 1;
        return new double[]{nx, Math.max(73, top + up), nz};
    }

    private int nearestNode() {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        int crystals = aliveCrystals();
        for (int i = crystals == 0 ? 12 : 0; i < 24; i++) {
            double[] n = node(i);
            double d = (n[0] - x) * (n[0] - x) + (n[1] - y) * (n[1] - y) + (n[2] - z) * (n[2] - z);
            if (d < bestDistance) { bestDistance = d; best = i; }
        }
        return best;
    }

    private double distanceSqToTarget() {
        if (target == null) return 0;
        double dx = target[0] - x, dy = target[1] - y, dz = target[2] - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void phaseUpdate() {
        phaseTicks++;
        Player player = host.player();
        switch (phase) {
            case HOLDING_PATTERN -> {
                double d = distanceSqToTarget();
                if (target == null || d < 100.0 || d > 22500.0 || collidedHorizontally || collidedVertically) holdingNewTarget(player);
            }
            case STRAFE_PLAYER -> strafe();
            case LANDING_APPROACH -> {
                if (target == null) target = new double[]{0.5, podiumTop + 10, 0.5};
                if (distanceSqToTarget() < 100.0) setPhase(Phase.LANDING);
            }
            case LANDING -> {
                if (target == null) target = new double[]{0.5, podiumTop, 0.5};
                if (distanceSqToTarget() < 1.0) setPhase(Phase.SITTING_SCANNING);
            }
            case TAKEOFF -> {
                if (target == null) {
                    double fx = Math.sin(Math.toRadians(rotationYaw)), fz = -Math.cos(Math.toRadians(rotationYaw));
                    target = new double[]{fx * 40, 105, fz * 40};
                }
                double dx = x - 0.5, dy = y - podiumTop, dz = z - 0.5;
                if (dx * dx + dy * dy + dz * dz > 100.0) setPhase(Phase.HOLDING_PATTERN);
            }
            case SITTING_SCANNING -> {
                Player near = nearPlayer(player, 20.0, 10.0);
                if (near != null) {
                    if (phaseTicks > 25) setPhase(Phase.SITTING_ATTACKING);
                    else turnToward(near.x, near.z);
                } else if (phaseTicks >= 100) {
                    Player far = nearPlayer(player, 150.0, 150.0);
                    setPhase(Phase.TAKEOFF);
                    if (far != null) {
                        setPhase(Phase.CHARGING_PLAYER);
                        target = new double[]{far.x, far.y, far.z};
                    }
                }
            }
            case SITTING_ATTACKING -> { if (phaseTicks >= 40) setPhase(Phase.SITTING_FLAMING); }
            case SITTING_FLAMING -> {
                if (phaseTicks < 10 && phaseTicks % 2 == 0) breathParticles();
                if (phaseTicks == 10) breathe();
                if (phaseTicks >= 200) setPhase(flameCount >= 4 ? Phase.TAKEOFF : Phase.SITTING_SCANNING);
            }
            case CHARGING_PLAYER -> {
                if (target == null) setPhase(Phase.HOLDING_PATTERN);
                else {
                    double d = distanceSqToTarget();
                    if (d < 100.0 || d > 22500.0 || collidedHorizontally || collidedVertically || phaseTicks > 200) setPhase(Phase.HOLDING_PATTERN);
                }
            }
            case DYING -> {
                if (target == null) target = new double[]{0.5, podiumTop, 0.5};
                double d = distanceSqToTarget();
                health = d >= 100.0 && d <= 22500.0 && !collidedHorizontally && !collidedVertically ? 1f : 0f;
                if (host != null && phaseTicks % 10 == 0)
                    host.particle("largeexplode", x + (rand.nextFloat() - 0.5) * 8, y + 2 + (rand.nextFloat() - 0.5) * 4,
                        z + (rand.nextFloat() - 0.5) * 8, 0, 0, 0);
            }
            case HOVER -> target = new double[]{x, y, z};
        }
    }

    /** PhaseHoldingPattern.findNewTarget: maybe land or strafe, else on to the next node around the ring. */
    private void holdingNewTarget(Player player) {
        int crystals = aliveCrystals();
        if (target != null) {
            if (rand.nextInt(crystals + 3) == 0) {
                setPhase(Phase.LANDING_APPROACH);
                return;
            }
            Player near = nearPlayer(player, 128.0, 128.0);
            if (near != null) {
                double d = ((near.x - 0.5) * (near.x - 0.5) + (near.y - podiumTop) * (near.y - podiumTop) + (near.z - 0.5) * (near.z - 0.5)) / 512.0;
                if (rand.nextInt(Math.abs((int) d) + 2) == 0 || rand.nextInt(crystals + 2) == 0) {
                    strafeAt(near);
                    return;
                }
            }
        }
        int from = nearestNode(), to = from;
        if (rand.nextInt(8) == 0) {
            clockwise = !clockwise;
            to = from + 6;
        }
        to += clockwise ? 1 : -1;
        if (crystals > 0) to = Math.floorMod(to, 12);
        else to = ((to - 12) & 7) + 12;
        double[] n = node(to);
        target = new double[]{n[0], n[1] + rand.nextFloat() * 20f, n[2]};
    }

    private void strafeAt(Player player) {
        setPhase(Phase.STRAFE_PLAYER);
        strafeTarget = player;
        fireballCharge = 0;
    }

    /** PhaseStrafePlayer: close in on the player and, once lined up within 10 degrees for 5 ticks in sight, shoot. */
    private void strafe() {
        Player t = strafeTarget;
        if (t == null || t.dead) {
            setPhase(Phase.HOLDING_PATTERN);
            return;
        }
        double dx = t.x - x, dz = t.z - z, horizontal = Math.sqrt(dx * dx + dz * dz);
        double above = Math.min(0.4 + horizontal / 80.0 - 1.0, 10.0);
        target = new double[]{t.x, t.y + above, t.z};
        double d = (t.x - x) * (t.x - x) + (t.y - y) * (t.y - y) + (t.z - z) * (t.z - z);
        if (d < 4096.0 && sees(t)) {
            fireballCharge++;
            double len = Math.max(1e-6, horizontal);
            double fx = Math.sin(Math.toRadians(rotationYaw)), fz = -Math.cos(Math.toRadians(rotationYaw));
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (fx * dx + fz * dz) / len)))) + 0.5;
            if (fireballCharge >= 5 && angle >= 0 && angle < 10) {
                shootFireball(t);
                fireballCharge = 0;
                setPhase(Phase.HOLDING_PATTERN);
            }
        } else if (fireballCharge > 0) {
            fireballCharge--;
        }
        if (phaseTicks > 400) setPhase(Phase.HOLDING_PATTERN);
    }

    private void shootFireball(Player t) {
        double fx = Math.sin(Math.toRadians(rotationYaw)), fz = -Math.cos(Math.toRadians(rotationYaw));
        double[] head = parts[HEAD];
        double sx = head[0] + fx, sy = head[1] + PART_SIZES[HEAD][1] / 2 + 0.5, sz = head[2] + fz;
        EntityDragonFireball fireball = EntityDragonFireball.shotBy(world, this, t.x - sx, t.y + 0.9 - (sy + PART_SIZES[HEAD][1] / 2), t.z - sz, rand);
        fireball.setPosition(sx, sy, sz);
        fireball.prevX = sx;
        fireball.prevY = sy;
        fireball.prevZ = sz;
        host.playSound("entity.enderdragon.shoot", x, y, z, 5f, 0.8f + rand.nextFloat() * 0.3f);
        host.spawn(fireball);
    }

    /** PhaseSittingFlaming at tick 10: a 5-block cloud of dragon's breath on the ground in front of the head for 10 seconds. */
    private void breathe() {
        double[] head = parts[HEAD];
        double lx = head[0] - x, lz = head[2] - z, l = Math.max(1e-6, Math.sqrt(lx * lx + lz * lz));
        double cx = head[0] + lx / l * 2.5, cz = head[2] + lz / l * 2.5;
        double cy = head[1] + PART_SIZES[HEAD][1] / 2;
        int bx = floor(cx), bz = floor(cz);
        int by = floor(cy);
        while (by > 0 && world.get(bx, by - 1, bz).isAir() && by > floor(cy) - 15) by--;
        EntityAreaEffectCloud cloud = new EntityAreaEffectCloud(world, cx, by, cz);
        cloud.owner = this;
        cloud.dragonBreath = true;
        cloud.radius = 5f;
        cloud.duration = 200;
        cloud.waitTime = 0;
        cloud.effects.add(new PotionEffect(Potion.HARM, 1, 0));
        host.spawn(cloud);
    }

    private void breathParticles() {
        double[] head = parts[HEAD];
        double lx = head[0] - x, lz = head[2] - z, l = Math.max(1e-6, Math.sqrt(lx * lx + lz * lz));
        for (int i = 0; i < 8; i++)
            host.particle("dragonbreath", head[0] + lx / l * 3, head[1] + 2, head[2] + lz / l * 3,
                lx / l * 0.3 + rand.nextGaussian() * 0.05, -rand.nextFloat() * 0.3, lz / l * 0.3 + rand.nextGaussian() * 0.05);
    }

    /** PhaseSittingScanning: turn toward the player when not facing them within 10 degrees. */
    private void turnToward(double px, double pz) {
        double dx = px - parts[HEAD][0], dz = pz - parts[HEAD][2];
        double turn = Math.max(-100, Math.min(100, wrap(180.0 - Math.toDegrees(Math.atan2(dx, dz)) - rotationYaw)));
        randomYawVelocity *= 0.8f;
        float f = (float) Math.sqrt(dx * dx + dz * dz) + 1f, f1 = Math.min(f, 40f);
        randomYawVelocity = (float) (randomYawVelocity + turn * (0.7 / f1 / f));
        rotationYaw += randomYawVelocity;
    }

    private Player nearPlayer(Player player, double horizontal, double vertical) {
        if (player == null || player.dead || player.noClip || player.disableDamage) return null;
        double dx = player.x - x, dz = player.z - z;
        return dx * dx + dz * dz <= horizontal * horizontal && Math.abs(player.y - y) <= vertical ? player : null;
    }

    private boolean sees(Player player) {
        return !net.minecraft.world.Explosion.blocked(world, parts[HEAD][0], parts[HEAD][1] + 3, parts[HEAD][2], player.x, player.y + player.eyeHeight(), player.z);
    }

    // ---- crystals ------------------------------------------------------------------------------

    private int aliveCrystals() {
        if (host == null) return 0;
        int count = 0;
        for (Entity entity : host.entities())
            if (entity instanceof EntityEnderCrystal crystal && !crystal.isRemoved() && crystal.showBottom) count++;
        return count;
    }

    /** EntityDragon.updateDragonEnderCrystal: heal from the nearest crystal within 32, re-picked one tick in ten. */
    private void updateCrystal() {
        if (healingCrystal != null) {
            if (healingCrystal.isRemoved()) healingCrystal = null;
            else if (ticksExisted % 10 == 0 && health < maxHealth()) health = Math.min(maxHealth(), health + 1f);
        }
        if (host == null || rand.nextInt(10) != 0) return;
        AxisAlignedBB area = boundingBox().grow(32, 32, 32);
        EntityEnderCrystal nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : host.entities()) {
            if (!(entity instanceof EntityEnderCrystal crystal) || crystal.isRemoved() || !crystal.boundingBox().intersects(area)) continue;
            double d = crystal.distanceSqTo(x, y, z);
            if (d < best) { best = d; nearest = crystal; }
        }
        healingCrystal = nearest;
    }

    /** EntityDragon.onCrystalDestroyed: losing the crystal that heals it hurts, and a flying dragon turns on the player. */
    public void crystalDestroyed(EntityEnderCrystal crystal) {
        if (crystal == healingCrystal) {
            healingCrystal = null;
            damagePart(HEAD, DamageSource.EXPLOSION, 10f, null);
        }
        Player player = host == null ? null : host.player();
        if (phase == Phase.HOLDING_PATTERN && player != null && !player.dead) strafeAt(player);
    }

    // ---- parts and contact ------------------------------------------------------------------

    private void updateParts() {
        double[] a5 = movementOffsets(5, 1f), a10 = movementOffsets(10, 1f);
        float tilt = (float) (a5[1] - a10[1]) * 10f * (float) Math.PI / 180f;
        float cosTilt = (float) Math.cos(tilt), sinTilt = (float) Math.sin(tilt);
        float yaw = rotationYaw * (float) Math.PI / 180f, sinYaw = (float) Math.sin(yaw), cosYaw = (float) Math.cos(yaw);
        set(BODY, x + sinYaw * 0.5, y, z - cosYaw * 0.5);
        set(WING_RIGHT, x + cosYaw * 4.5, y + 2, z + sinYaw * 4.5);
        set(WING_LEFT, x - cosYaw * 4.5, y + 2, z - sinYaw * 4.5);
        float headYaw = rotationYaw * (float) Math.PI / 180f - randomYawVelocity * 0.01f;
        float hs = (float) Math.sin(headYaw), hc = (float) Math.cos(headYaw);
        double headY = phase.stationary() ? -1.0 : a5[1] - movementOffsets(0, 1f)[1];
        set(HEAD, x + hs * 6.5 * cosTilt, y + headY + sinTilt * 6.5, z - hc * 6.5 * cosTilt);
        set(NECK, x + hs * 5.5 * cosTilt, y + headY + sinTilt * 5.5, z - hc * 5.5 * cosTilt);
        for (int k = 0; k < 3; k++) {
            double[] tail = movementOffsets(12 + k * 2, 1f);
            float ty = rotationYaw * (float) Math.PI / 180f + (float) wrap(tail[0] - a5[0]) * (float) Math.PI / 180f;
            float ts = (float) Math.sin(ty), tc = (float) Math.cos(ty);
            float near = 1.5f, along = (k + 1) * 2f;
            set(TAIL1 + k, x - (sinYaw * near + ts * along) * cosTilt, y + (tail[1] - a5[1]) - (along + near) * sinTilt + 1.5,
                z + (cosYaw * near + tc * along) * cosTilt);
        }
    }

    private void set(int part, double px, double py, double pz) {
        parts[part][0] = px;
        parts[part][1] = py;
        parts[part][2] = pz;
    }

    public AxisAlignedBB partBox(int part) {
        double hw = PART_SIZES[part][0] / 2.0;
        return new AxisAlignedBB(parts[part][0] - hw, parts[part][1], parts[part][2] - hw,
            parts[part][0] + hw, parts[part][1] + PART_SIZES[part][1], parts[part][2] + hw);
    }

    /** The boxes the player can hit (one per part, head first). */
    public AxisAlignedBB[] partBoxes() {
        AxisAlignedBB[] boxes = new AxisAlignedBB[8];
        for (int i = 0; i < 8; i++) boxes[i] = partBox(i);
        return boxes;
    }

    /** EntityDragon.collideWithEntities: wings fling things away from the body, hurting them for 5 when flying. */
    private void knockBack(AxisAlignedBB area) {
        double cx = (partBox(BODY).minX() + partBox(BODY).maxX()) / 2, cz = (partBox(BODY).minZ() + partBox(BODY).maxZ()) / 2;
        for (Entity entity : host.entities()) {
            if (!(entity instanceof EntityLiving living) || living == this || !living.isAlive() || !living.boundingBox().intersects(area)) continue;
            double dx = living.x() - cx, dz = living.z() - cz, d = Math.max(1e-4, dx * dx + dz * dz);
            living.addVelocity(dx / d * 4, 0.2, dz / d * 4);
            if (!phase.stationary()) living.attackEntityFrom(DamageSource.MOB, 5f, null);
        }
        Player player = host.player();
        if (player != null && !player.dead && !player.noClip && player.boundingBox().intersects(area)) {
            double dx = player.x - cx, dz = player.z - cz, d = Math.max(1e-4, dx * dx + dz * dz);
            player.motionX += dx / d * 4;
            player.motionY += 0.2;
            player.motionZ += dz / d * 4;
            if (!phase.stationary()) player.attackByMob(5f, x, z, displayName());
        }
    }

    /** EntityDragon.attackEntitiesInList: the head and neck hurt for 10. */
    private void bite(AxisAlignedBB area) {
        for (Entity entity : host.entities())
            if (entity instanceof EntityLiving living && living != this && living.isAlive() && living.boundingBox().intersects(area))
                living.attackEntityFrom(DamageSource.MOB, 10f, null);
        Player player = host.player();
        if (player != null && !player.dead && !player.noClip && player.boundingBox().intersects(area))
            player.attackByMob(10f, x, z, displayName());
    }

    /** EntityDragon.destroyBlocksInAABB: breaks what it flies through (if mobGriefing); returns whether something stopped it. */
    private boolean breakBlocks(AxisAlignedBB box) {
        int x0 = floor(box.minX()), y0 = floor(box.minY()), z0 = floor(box.minZ());
        int x1 = floor(box.maxX()), y1 = floor(box.maxY()), z1 = floor(box.maxZ());
        boolean stopped = false, broke = false, griefing = host.gameRule("mobGriefing");
        for (int bx = x0; bx <= x1; bx++)
            for (int by = Math.max(0, y0); by <= Math.min(World.HEIGHT - 1, y1); by++)
                for (int bz = z0; bz <= z1; bz++) {
                    if (world.generatedChunk(bx >> 4, bz >> 4) == null) continue;
                    var block = world.get(bx, by, bz);
                    if (block.isAir() || block.material().equals("fire")) continue;
                    if (!griefing || UNBREAKABLE.contains(block.name().path())) stopped = true;
                    else {
                        host.setBlock(bx, by, bz, "air", 0);
                        broke = true;
                    }
                }
        if (broke)
            host.particle("largeexplode", box.minX() + (box.maxX() - box.minX()) * rand.nextFloat(),
                box.minY() + (box.maxY() - box.minY()) * rand.nextFloat(), box.minZ() + (box.maxZ() - box.minZ()) * rand.nextFloat(), 0, 0, 0);
        return stopped;
    }

    // ---- damage and death ----------------------------------------------------------------------

    /** Hits land on the part the player aimed at (or the body); the dragon itself is only hurt through its parts. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount, Player attacker) {
        int part = hitPart >= 0 ? hitPart : BODY;
        hitPart = -1;
        if (source == DamageSource.PLAYER || source == DamageSource.ARROW || source.isExplosion() || source == DamageSource.OUT_OF_WORLD)
            return damagePart(part, source, amount, attacker);
        return false;
    }

    @Override
    public boolean attackFromExplosion(float damage, String attacker, net.minecraft.entity.MovingEntity exploder) {
        if (exploder == this) return false;
        return damagePart(BODY, DamageSource.EXPLOSION, damage, null);
    }

    /** EntityDragon.attackEntityFromPart. */
    public boolean damagePart(int part, DamageSource source, float amount, Player attacker) {
        if (phase == Phase.DYING || health <= 0) return false;
        if (phase.stationary() && source == DamageSource.ARROW) return false;
        if (part != HEAD) amount = amount / 4f + Math.min(amount, 1f);
        if (amount < 0.01f) return false;
        float before = health;
        boolean hurt = super.attackEntityFrom(source, amount, attacker);
        if (health <= 0 && !phase.stationary()) {
            health = 1f;
            setPhase(Phase.DYING);
        }
        if (phase.stationary()) {
            sittingDamage += before - health;
            if (sittingDamage > 0.25f * maxHealth()) {
                sittingDamage = 0;
                setPhase(Phase.TAKEOFF);
            }
        }
        return hurt;
    }

    /** Hit knockback doesn't move the dragon. */
    @Override public void knockBack(double dx, double dz) {}

    /** No loot table: the egg and experience come from the death animation. */
    @Override protected void onDeath(DamageSource source) {}

    /** EntityDragon.onDeathUpdate: 10 seconds rising and spinning, bursts of light from 9 s, experience from 7.5 s. */
    private void deathUpdate() {
        deathTicks++;
        deathTime = deathTicks;
        if (host == null) return;
        if (deathTicks >= 180 && deathTicks <= 200)
            host.particle("hugeexplosion", x + rand.nextFloat() * 8 - 4, y + 2 + rand.nextFloat() * 4 - 2, z + rand.nextFloat() * 8 - 4, 0, 0, 0);
        boolean loot = host.gameRule("doMobLoot");
        int xp = fight != null && !fight.previouslyKilled ? 12000 : 500;
        if (deathTicks > 150 && deathTicks % 5 == 0 && loot) dropExperience((int) Math.floor(xp * 0.08f));
        if (deathTicks == 1) host.globalSound("mob.enderdragon.end", x, y, z);
        setPosition(x, y + 0.1, z);
        rotationYaw += 20f;
        renderYawOffset = rotationYaw;
        if (deathTicks == 200) {
            if (loot) dropExperience((int) Math.floor(xp * 0.2f));
            onDeath.run();
            remove();
        }
    }

    private void dropExperience(int xp) {
        while (xp > 0) {
            int split = EntityXPOrb.split(xp);
            xp -= split;
            host.spawn(new EntityXPOrb(world, x, y, z, split));
        }
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("DragonPhase", new net.minecraft.nbt.NbtTag.Int(phase.ordinal()));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("DragonPhase") instanceof net.minecraft.nbt.NbtTag.Int p && p.value() >= 0 && p.value() < Phase.values().length)
            phase = Phase.values()[p.value()];
        if (phase.stationary() || phase == Phase.DYING) phase = Phase.HOLDING_PATTERN;
    }
}

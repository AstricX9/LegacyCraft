package net.minecraft.entity.boss;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityEnderCrystal;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.Set;

/**
 * The ender dragon (1.8.9 EntityDragon, HYPOTHESIS: recalled): 200 health,
 * 16 x 8, flying through blocks. Its body follows a 64-tick ring buffer of
 * yaw and height that the model and its parts trail behind. It flies toward
 * a target: half the time the player (swooping at them), otherwise a random
 * spot over the island, picking a new one when it gets within 10 blocks,
 * strays past 150, or is hit. The nearest crystal within 32 heals it 1 every
 * half second; losing that crystal costs it 10. Hits to anything but the head
 * do a quarter (+1). Its wings fling things away, its head hurts for 10, and
 * it breaks every block it flies through except end stone, obsidian, bedrock
 * and barriers. Dying, it rises for 10 seconds amid explosions and
 * experience, then leaves the exit portal with the dragon egg below it.
 */
public final class EntityDragon extends EntityLiving implements IMob {
    /** Blocks the dragon can't break (EntityDragon.destroyBlocksInAABB). */
    private static final Set<String> UNBREAKABLE = Set.of("barrier", "obsidian", "end_stone", "bedrock", "command_block");

    /** Body parts (EntityDragonPart): head, body, three tail pieces, two wings. */
    public static final int HEAD = 0, BODY = 1, TAIL1 = 2, WING1 = 5, WING2 = 6;
    /** Part widths and heights as onLivingUpdate resizes them. */
    private static final float[][] PART_SIZES = {{3, 3}, {5, 3}, {2, 2}, {2, 2}, {2, 2}, {4, 2}, {4, 3}};
    /** Part feet (x, y bottom, z). */
    public final double[][] parts = new double[7][3];

    public final double[][] ringBuffer = new double[64][3];
    public int ringBufferIndex = -1;
    public float prevAnimTime, animTime;
    public float randomYawVelocity;
    public boolean slowed;
    public int deathTicks;
    public EntityEnderCrystal healingCrystal;

    /** Where it is flying (targetX/Y/Z), and the player it's after, if any. */
    public double targetX, targetY = 100, targetZ;
    public Player target;
    private boolean forceNewTarget;
    /** The part the player's last hit landed on (set by the game's aim), or -1. */
    public int hitPart = -1;
    private int growlTime;

    /** Told once, when the death animation ends and the exit portal is built. */
    public Runnable onDeath = () -> {};

    public EntityDragon(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = 16f;
        height = 8f;
        health = maxHealth();
        persistenceRequired = true;
        targetX = x;
        targetZ = z;
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

    // ---- ticking (onLivingUpdate) ------------------------------------------------------------

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
            if (host != null)
                host.particle("largeexplode", x + (rand.nextFloat() - 0.5) * 8, y + 2 + (rand.nextFloat() - 0.5) * 4,
                    z + (rand.nextFloat() - 0.5) * 8, 0, 0, 0);
            deathUpdate();
            return;
        }
        if (host != null) {
            float f = (float) Math.cos(animTime * Math.PI * 2), f1 = (float) Math.cos(prevAnimTime * Math.PI * 2);
            if (f1 <= -0.3f && f >= -0.3f) host.playSound("mob.enderdragon.wings", x, y, z, 5f, 0.8f + rand.nextFloat() * 0.3f);
            if (rand.nextInt(1000) < growlTime++) {
                growlTime = -talkInterval();
                playLivingSound();
            }
        }
        updateCrystal();
        float speed = 0.2f / ((float) Math.sqrt(motionX * motionX + motionZ * motionZ) * 10f + 1f);
        speed *= (float) Math.pow(2.0, motionY);
        animTime += slowed ? speed * 0.5f : speed;
        rotationYaw = (float) wrap(rotationYaw);
        if (ringBufferIndex < 0)
            for (double[] entry : ringBuffer) { entry[0] = rotationYaw; entry[1] = y; }
        if (++ringBufferIndex == 64) ringBufferIndex = 0;
        ringBuffer[ringBufferIndex][0] = rotationYaw;
        ringBuffer[ringBufferIndex][1] = y;
        if (host != null) fly();
        renderYawOffset = rotationYaw;
        rotationYawHead = rotationYaw;
        updateParts();
        if (host != null) {
            if (hurtTime == 0) {
                knockBack(partBox(WING1).grow(4, 2, 4).offset(0, -2, 0));
                knockBack(partBox(WING2).grow(4, 2, 4).offset(0, -2, 0));
                bite(partBox(HEAD).grow(1, 1, 1));
            }
            slowed = breakBlocks(partBox(HEAD)) | breakBlocks(partBox(BODY));
        }
    }

    /** The flight step of onLivingUpdate: chase the player or drift toward the target, then turn and fly forward. */
    private void fly() {
        double dx = targetX - x, dy = targetY - y, dz = targetZ - z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (target != null) {
            targetX = target.x;
            targetZ = target.z;
            double tx = targetX - x, tz = targetZ - z;
            targetY = target.boundingBox().minY() + Math.min(10.0, 0.4 + Math.sqrt(tx * tx + tz * tz) / 80.0 - 1.0);
        } else {
            targetX += rand.nextGaussian() * 2.0;
            targetZ += rand.nextGaussian() * 2.0;
        }
        if (forceNewTarget || distanceSq < 100.0 || distanceSq > 22500.0 || collidedHorizontally || collidedVertically) setNewTarget();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double rise = horizontal > 0 ? Math.max(-0.6, Math.min(0.6, dy / horizontal)) : Math.signum(dy) * 0.6;
        motionY += rise * 0.1;
        rotationYaw = (float) wrap(rotationYaw);
        double turn = Math.max(-50, Math.min(50, wrap(180.0 - Math.toDegrees(Math.atan2(dx, dz)) - rotationYaw)));
        double tx = targetX - x, ty = targetY - y, tz = targetZ - z, tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tl > 0) { tx /= tl; ty /= tl; tz /= tl; }
        double fx = Math.sin(Math.toRadians(rotationYaw)), fy = motionY, fz = -Math.cos(Math.toRadians(rotationYaw));
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        float facing = Math.max(((float) (fx * tx + fy * ty + fz * tz) + 0.5f) / 1.5f, 0f);
        randomYawVelocity *= 0.8f;
        float f4 = (float) Math.sqrt(motionX * motionX + motionZ * motionZ) + 1f;
        double d11 = Math.min(40.0, Math.sqrt(motionX * motionX + motionZ * motionZ) + 1.0);
        randomYawVelocity = (float) (randomYawVelocity + turn * (0.7 / d11 / f4));
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

    /** EntityDragon.setNewTarget: half the time a player, otherwise a spot 70-120 high within 60 of the centre, over 10 away. */
    void setNewTarget() {
        forceNewTarget = false;
        Player player = host == null ? null : host.player();
        boolean targetable = player != null && !player.dead && !player.noClip;
        if (rand.nextInt(2) == 0 && targetable) {
            target = player;
            return;
        }
        do {
            targetX = rand.nextFloat() * 120f - 60f;
            targetY = 70f + rand.nextFloat() * 50f;
            targetZ = rand.nextFloat() * 120f - 60f;
        } while ((x - targetX) * (x - targetX) + (y - targetY) * (y - targetY) + (z - targetZ) * (z - targetZ) <= 100.0);
        target = null;
    }

    // ---- crystals ------------------------------------------------------------------------------

    /** EntityDragon.updateDragonEnderCrystal: heal from the nearest crystal within 32, re-picked one tick in ten; lose 10 when it breaks. */
    private void updateCrystal() {
        if (healingCrystal != null) {
            if (healingCrystal.isRemoved()) {
                healingCrystal = null;
                damagePart(HEAD, DamageSource.EXPLOSION, 10f, null);
            } else if (ticksExisted % 10 == 0 && health < maxHealth()) {
                health = Math.min(maxHealth(), health + 1f);
            }
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

    // ---- parts and contact ------------------------------------------------------------------

    private void updateParts() {
        double[] a5 = movementOffsets(5, 1f), a10 = movementOffsets(10, 1f), a0 = movementOffsets(0, 1f);
        float tilt = (float) (a5[1] - a10[1]) * 10f * (float) Math.PI / 180f;
        float cosTilt = (float) Math.cos(tilt), sinTilt = (float) Math.sin(tilt);
        float yaw = rotationYaw * (float) Math.PI / 180f, sinYaw = (float) Math.sin(yaw), cosYaw = (float) Math.cos(yaw);
        set(BODY, x + sinYaw * 0.5, y, z - cosYaw * 0.5);
        set(WING1, x + cosYaw * 4.5, y + 2, z + sinYaw * 4.5);
        set(WING2, x - cosYaw * 4.5, y + 2, z - sinYaw * 4.5);
        float headYaw = yaw - randomYawVelocity * 0.01f;
        float hs = (float) Math.sin(headYaw), hc = (float) Math.cos(headYaw);
        set(HEAD, x + hs * 5.5 * cosTilt, y + (a0[1] - a5[1]) + sinTilt * 5.5, z - hc * 5.5 * cosTilt);
        for (int k = 0; k < 3; k++) {
            double[] tail = movementOffsets(12 + k * 2, 1f);
            float ty = yaw + (float) wrap(tail[0] - a5[0]) * (float) Math.PI / 180f;
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
        AxisAlignedBB[] boxes = new AxisAlignedBB[parts.length];
        for (int i = 0; i < boxes.length; i++) boxes[i] = partBox(i);
        return boxes;
    }

    /** EntityDragon.collideWithEntities: the wings fling living things away from the body. */
    private void knockBack(AxisAlignedBB area) {
        AxisAlignedBB body = partBox(BODY);
        double cx = (body.minX() + body.maxX()) / 2, cz = (body.minZ() + body.maxZ()) / 2;
        for (Entity entity : host.entities()) {
            if (!(entity instanceof EntityLiving living) || living == this || !living.isAlive() || !living.boundingBox().intersects(area)) continue;
            double dx = living.x() - cx, dz = living.z() - cz, d = Math.max(1e-4, dx * dx + dz * dz);
            living.addVelocity(dx / d * 4, 0.2, dz / d * 4);
        }
        Player player = host.player();
        if (player != null && !player.dead && !player.noClip && player.boundingBox().intersects(area)) {
            double dx = player.x - cx, dz = player.z - cz, d = Math.max(1e-4, dx * dx + dz * dz);
            player.motionX += dx / d * 4;
            player.motionY += 0.2;
            player.motionZ += dz / d * 4;
        }
    }

    /** EntityDragon.attackEntitiesInList: the head hurts for 10. */
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
                    if (block.isAir()) continue;
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
        return damagePart(part, source, amount, attacker);
    }

    @Override
    public boolean attackFromExplosion(float damage, String attacker, net.minecraft.entity.MovingEntity exploder) {
        if (exploder == this) return false;
        return damagePart(BODY, DamageSource.EXPLOSION, damage, null);
    }

    /**
     * EntityDragon.attackEntityFromPart: a quarter (+1) away from the head; it
     * breaks off toward a spot just ahead of itself; only players and
     * explosions hurt it.
     */
    public boolean damagePart(int part, DamageSource source, float amount, Player attacker) {
        if (health <= 0) return false;
        if (part != HEAD) amount = amount / 4f + 1f;
        float yaw = rotationYaw * (float) Math.PI / 180f;
        targetX = x + Math.sin(yaw) * 5 + (rand.nextFloat() - 0.5f) * 2f;
        targetY = y + rand.nextFloat() * 3f + 1f;
        targetZ = z - Math.cos(yaw) * 5 + (rand.nextFloat() - 0.5f) * 2f;
        target = null;
        if (source == DamageSource.PLAYER || source == DamageSource.ARROW || source.isExplosion() || source == DamageSource.OUT_OF_WORLD)
            super.attackEntityFrom(source, amount, attacker);
        return true;
    }

    /** Hit knockback doesn't move the dragon. */
    @Override public void knockBack(double dx, double dz) {}

    /** No drops: the egg and experience come from the death animation. */
    @Override protected void onDeath(DamageSource source) {}

    /** EntityDragon.onDeathUpdate: 10 seconds rising, bursts of light from 9 s, experience from 7.5 s, then the portal. */
    private void deathUpdate() {
        deathTicks++;
        deathTime = deathTicks;
        if (host == null) return;
        if (deathTicks >= 180 && deathTicks <= 200)
            host.particle("hugeexplosion", x + (rand.nextFloat() - 0.5) * 8, y + 2 + (rand.nextFloat() - 0.5) * 4,
                z + (rand.nextFloat() - 0.5) * 8, 0, 0, 0);
        boolean loot = host.gameRule("doMobLoot");
        if (deathTicks > 150 && deathTicks % 5 == 0 && loot) dropExperience(1000);
        if (deathTicks == 1) host.globalSound("mob.enderdragon.end", x, y, z);
        setPosition(x, y + 0.1, z);
        rotationYaw += 20f;
        renderYawOffset = rotationYaw;
        if (deathTicks == 200) {
            if (loot) dropExperience(2000);
            net.minecraft.world.EndPodium.generatePortal(world, floor(x), 64, floor(z));
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
}

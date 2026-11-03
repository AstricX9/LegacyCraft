package net.minecraft.entity.monster;

import net.minecraft.block.BlockState;
import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.world.World;

import java.util.Set;

/**
 * The enderman (1.8.9 EntityEnderman, HYPOTHESIS: recalled): 40 health,
 * speed 0.3, 7 attack, 64 follow range, 0.6 x 2.9. Neutral until hurt or
 * stared at: a player within 64 looking at its head (without a pumpkin on)
 * makes it scream and come for them, and once angry it teleports away when
 * looked at up close and toward its target when it falls 16 behind. Water
 * and daylight under the open sky make it teleport off; arrows never land,
 * it teleports instead. It takes up natural blocks now and then and puts
 * them down again, and drops an ender pearl.
 */
public final class EntityEnderman extends EntityMob {
    /** EntityEnderman.carriableBlocks. */
    private static final Set<String> CARRIABLE = Set.of("grass", "dirt", "sand", "gravel", "yellow_flower", "red_flower",
        "brown_mushroom", "red_mushroom", "tnt", "cactus", "clay", "pumpkin", "melon_block", "mycelium");

    /** The block in its hands, or null. */
    public BlockState carried;
    /** Mouth open and shaking (EntityEnderman.isScreaming). */
    public boolean screaming;
    private boolean aggressive;
    private int stareTicks, teleportDelay;

    public EntityEnderman(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = 0.6f;
        height = 2.9f;
        stepHeight = 1f;
        tasks.add(0, new EntityAI.Swimming(this));
        tasks.add(2, new EntityAI.AttackOnCollide(this, 1.0));
        tasks.add(7, new EntityAI.Wander(this, 1.0));
        tasks.add(8, new EntityAI.WatchClosest(this, 8f));
        tasks.add(8, new EntityAI.LookIdle(this));
        tasks.add(10, new PlaceBlock());
        tasks.add(11, new TakeBlock());
        targetTasks.add(1, new EntityAI.HurtByTarget(this));
        targetTasks.add(2, new FindPlayer());
    }

    @Override public String entityId() { return "Enderman"; }
    @Override public float maxHealth() { return 40f; }
    @Override public double movementSpeed() { return 0.3; }
    @Override public float followRange() { return 64f; }
    @Override protected float attackDamage() { return 7f; }
    @Override public float eyeHeight() { return 2.55f; }
    @Override protected String livingSound() { return screaming ? "mob.endermen.scream" : "mob.endermen.idle"; }
    @Override protected String hurtSound() { return "mob.endermen.hit"; }
    @Override protected String deathSound() { return "mob.endermen.death"; }
    @Override protected int experiencePoints() { return 5; }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        int count = rand.nextInt(2 + looting);
        for (int i = 0; i < count; i++) dropItem("ender_pearl", 1);
    }

    /** EntityEnderman.onLivingUpdate (portal particles) and updateAITasks (water, daylight). */
    @Override
    protected void livingUpdate() {
        if (host != null)
            for (int i = 0; i < 2; i++)
                host.particle("portal", x + (rand.nextDouble() - 0.5) * width, y + rand.nextDouble() * height - 0.25,
                    z + (rand.nextDouble() - 0.5) * width, (rand.nextDouble() - 0.5) * 2, -rand.nextDouble(), (rand.nextDouble() - 0.5) * 2);
        if (host != null && health > 0) {
            if (isInWater() || isRainedOn()) attackEntityFrom(DamageSource.DROWN, 1f, null);
            if (screaming && !aggressive && rand.nextInt(100) == 0) screaming = false;
            if (net.minecraft.world.MobSpawner.skylightSubtracted(world, host.worldTime()) < 4 && !world.isEnd() && !world.isNether()) {
                float brightness = brightness();
                int bx = floor(x), by = floor(y + eyeHeight()), bz = floor(z);
                if (brightness > 0.5f && by >= 0 && by < World.HEIGHT && world.getSkyLight(bx, by, bz) >= 15
                    && rand.nextFloat() * 30f < (brightness - 0.4f) * 2f) {
                    setAttackTarget(null);
                    screaming = aggressive = false;
                    teleportRandomly();
                }
            }
        }
        super.livingUpdate();
    }

    /** World.canLightningStrike at its head: rain falling on it. shortcut: any rain under open sky, ignoring the biome. */
    private boolean isRainedOn() {
        return world.isRaining() && world.getSkyLight(floor(x), Math.min(World.HEIGHT - 1, floor(y + height)), floor(z)) >= 15;
    }

    /** EntityEnderman.attackEntityFrom: angered and screaming; arrows (and other thrown things) make it teleport instead. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount, Player attacker) {
        if (isRemoved() || health <= 0) return false;
        screaming = true;
        if (attacker != null && !attacker.disableDamage) aggressive = true;
        if (source.isProjectile()) {
            aggressive = false;
            for (int i = 0; i < 64; i++) if (teleportRandomly()) return false;
            return false;
        }
        return super.attackEntityFrom(source, amount, attacker);
    }

    /** EntityEnderman.shouldAttackPlayer: no pumpkin on, looking within a narrowing cone at its eyes, with nothing between. */
    public boolean isStaredAtBy(Player player) {
        if (player.armor[3] != null && player.armor[3].item().name().path().equals("pumpkin")) return false;
        double lx = player.dirX(), ly = player.dirY(), lz = player.dirZ();
        double dx = x - player.x, dy = y + eyeHeight() - player.eyeY(), dz = z - player.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d == 0) return false;
        double dot = (lx * dx + ly * dy + lz * dz) / d / Math.sqrt(lx * lx + ly * ly + lz * lz);
        return dot > 1.0 - 0.025 / d && canSee(player);
    }

    /** EntityEnderman.teleportRandomly: anywhere within 32 sideways and 32 up or down. */
    public boolean teleportRandomly() {
        return teleportTo(x + (rand.nextDouble() - 0.5) * 64.0, y + (rand.nextInt(64) - 32), z + (rand.nextDouble() - 0.5) * 64.0);
    }

    /** EntityEnderman.teleportToEntity: to a spot about 16 nearer the player. */
    private boolean teleportToward(Player player) {
        double dx = x - player.x, dy = y + height / 2.0 - player.eyeY(), dz = z - player.z;
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l == 0) return false;
        dx /= l; dy /= l; dz /= l;
        return teleportTo(x + (rand.nextDouble() - 0.5) * 8.0 - dx * 16.0, y + (rand.nextInt(16) - 8) - dy * 16.0,
            z + (rand.nextDouble() - 0.5) * 8.0 - dz * 16.0);
    }

    /**
     * EntityEnderman.teleportTo: drop to the ground under the spot, and stay
     * only if it's clear of blocks and liquid; 128 portal particles mark the
     * way and the portal sound plays at both ends.
     */
    boolean teleportTo(double tx, double ty, double tz) {
        double ox = x, oy = y, oz = z;
        int bx = floor(tx), bz = floor(tz);
        int by = floor(ty);
        if (by < 1 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null) return false;
        setPosition(tx, ty, tz);
        boolean ground = false;
        while (!ground && by > 0) {
            if (net.minecraft.block.Liquids.blocksMovement(world.get(bx, by - 1, bz))) ground = true;
            else {
                setPosition(x, y - 1, z);
                by--;
            }
        }
        if (!ground || !offsetIsFree(0, 0, 0)) {
            setPosition(ox, oy, oz);
            return false;
        }
        if (host != null) {
            for (int i = 0; i < 128; i++) {
                double f = i / 127.0;
                host.particle("portal", ox + (x - ox) * f + (rand.nextDouble() - 0.5) * width * 2,
                    oy + (y - oy) * f + rand.nextDouble() * height, oz + (z - oz) * f + (rand.nextDouble() - 0.5) * width * 2,
                    (rand.nextFloat() - 0.5f) * 0.2f, (rand.nextFloat() - 0.5f) * 0.2f, (rand.nextFloat() - 0.5f) * 0.2f);
            }
            host.playSound("mob.endermen.portal", ox, oy, oz, 1f, 1f);
            playSound("mob.endermen.portal", 1f, 1f);
        }
        prevX = x; prevY = y; prevZ = z;
        navigator().clearPath();
        return true;
    }

    /**
     * EntityEnderman.AIFindPlayer: a player who stares for 5 ticks becomes
     * the target (with the stare sound); while angry, being stared at within
     * 4 blocks sends it away, and falling more than 16 behind brings it near
     * again (at most every 30 ticks).
     */
    private final class FindPlayer extends EntityAI {
        private Player staring;
        FindPlayer() { mutexBits = 1; }

        @Override public boolean shouldExecute() {
            if (host == null) return false;
            Player player = host.player();
            if (player == null || player.dead || player.disableDamage || player.noClip) return false;
            if (distanceSqTo(player.x, player.y, player.z) > 64.0 * 64.0 || !isStaredAtBy(player)) return false;
            staring = player;
            return true;
        }

        @Override public void startExecuting() {
            stareTicks = 5;
            teleportDelay = 0;
        }

        @Override public boolean continueExecuting() {
            if (staring != null) {
                if (!isStaredAtBy(staring)) return false;
                aggressive = true;
                lookHelper().setLookPosition(staring.x, staring.eyeY(), staring.z, 10f, 30f);
                return true;
            }
            Player target = attackTarget();
            return target != null && !target.dead && !target.disableDamage && !target.noClip
                && distanceSqTo(target.x, target.y, target.z) <= 64.0 * 64.0;
        }

        @Override public void resetTask() {
            staring = null;
            screaming = false;
            setAttackTarget(null);
        }

        @Override public void updateTask() {
            if (staring != null) {
                if (--stareTicks <= 0) {
                    setAttackTarget(staring);
                    staring = null;
                    screaming = true;
                    playSound("mob.endermen.stare", 1f, 1f);
                }
                return;
            }
            Player target = attackTarget();
            if (target == null) return;
            if (isStaredAtBy(target)) {
                if (distanceSqTo(target.x, target.y, target.z) < 16.0) teleportRandomly();
                teleportDelay = 0;
            } else if (distanceSqTo(target.x, target.y, target.z) > 256.0 && teleportDelay++ >= 30 && teleportToward(target)) {
                teleportDelay = 0;
            }
        }
    }

    /** EntityEnderman.AITakeBlock: one tick in 20, a carriable block within 2 sideways and 0-3 up. */
    private final class TakeBlock extends EntityAI {
        @Override public boolean shouldExecute() {
            return carried == null && host != null && host.gameRule("mobGriefing") && rand.nextInt(20) == 0;
        }
        @Override public void updateTask() {
            int bx = floor(x - 2 + rand.nextDouble() * 4), by = floor(y + rand.nextDouble() * 3), bz = floor(z - 2 + rand.nextDouble() * 4);
            if (by < 0 || by >= World.HEIGHT) return;
            BlockState state = world.getState(bx, by, bz);
            if (!CARRIABLE.contains(state.block().name().path())) return;
            carried = state;
            host.setBlock(bx, by, bz, "air", 0);
        }
    }

    /** EntityEnderman.AIPlaceBlock: one tick in 2000, into air above a full block within 1 sideways and 0-2 up. */
    private final class PlaceBlock extends EntityAI {
        @Override public boolean shouldExecute() {
            return carried != null && host != null && host.gameRule("mobGriefing") && rand.nextInt(2000) == 0;
        }
        @Override public void updateTask() {
            int bx = floor(x - 1 + rand.nextDouble() * 2), by = floor(y + rand.nextDouble() * 2), bz = floor(z - 1 + rand.nextDouble() * 2);
            if (by < 1 || by >= World.HEIGHT) return;
            if (!world.get(bx, by, bz).isAir() || world.get(bx, by - 1, bz).isAir() || !world.get(bx, by - 1, bz).isFullCube()) return;
            host.setBlock(bx, by, bz, carried.block().name().path(), carried.metadata());
            carried = null;
        }
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        if (carried != null) {
            tag.put("carried", new net.minecraft.nbt.NbtTag.Short((short) carried.block().registryId()));
            tag.put("carriedData", new net.minecraft.nbt.NbtTag.Short((short) carried.metadata()));
        }
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("carried") instanceof net.minecraft.nbt.NbtTag.Short id && id.value() > 0) {
            var block = world.registry().byId(id.value());
            int meta = tag.get("carriedData") instanceof net.minecraft.nbt.NbtTag.Short data ? data.value() : 0;
            if (block != null && !block.isAir()) carried = BlockState.of(block, meta);
        }
    }
}

package net.minecraft.entity.monster;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.world.World;

/**
 * The creeper (1.8.9 EntityCreeper + EntityMob, HYPOTHESIS: recalled):
 * 20 health, speed 0.25, silent until hurt. It hunts a visible player
 * within 16 blocks and, once within 3 blocks with the player in sight,
 * hisses and swells for 30 ticks, stopping and shrinking back if the player
 * gets more than 7 blocks away or out of sight; at the end of the fuse it
 * explodes with strength 3 (breaking blocks if the mobGriefing rule allows)
 * and is gone. Falling shortens the fuse, flint and steel lights it for
 * good. It drops 0-2 gunpowder (plus 0-looting) and 5 experience, and like
 * every hostile mob it disappears in Peaceful and despawns when far away.
 */
public final class EntityCreeper extends EntityMob {
    private int lastActiveTime, timeSinceIgnited;
    private int fuseTime = 30;
    private int explosionRadius = 3;
    /** -1 shrinking/idle, 1 swelling (getCreeperState). */
    private int state = -1;
    private boolean ignited;

    public EntityCreeper(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = 0.6f;
        height = 1.7f;
        tasks.add(1, new EntityAI.Swimming(this));
        tasks.add(2, new Swell(this));
        tasks.add(4, new EntityAI.AttackOnCollide(this, 1.0));
        tasks.add(5, new EntityAI.Wander(this, 0.8));
        tasks.add(6, new EntityAI.WatchClosest(this, 8f));
        tasks.add(6, new EntityAI.LookIdle(this));
        targetTasks.add(1, new EntityAI.NearestAttackableTarget(this));
        targetTasks.add(2, new EntityAI.HurtByTarget(this));
    }

    @Override public String entityId() { return "Creeper"; }
    @Override public float maxHealth() { return 20f; }
    @Override public double movementSpeed() { return 0.25; }
    @Override protected String hurtSound() { return "mob.creeper.say"; }
    @Override protected String deathSound() { return "mob.creeper.death"; }
    @Override protected int experiencePoints() { return 5; }

    public int creeperState() { return state; }
    public void setCreeperState(int state) { this.state = state; }

    /** How far through its fuse it is, 0-1 (getCreeperFlashIntensity), for the swelling and flashing. */
    public float flashIntensity(float partialTicks) {
        return (lastActiveTime + (timeSinceIgnited - lastActiveTime) * partialTicks) / (fuseTime - 2);
    }

    /** EntityCreeper.onUpdate: the fuse runs while swelling and runs back down otherwise. */
    @Override
    public void tick() {
        if (isAlive()) {
            lastActiveTime = timeSinceIgnited;
            if (ignited) state = 1;
            if (state > 0 && timeSinceIgnited == 0) playSound("creeper.primed", 1f, 0.5f);
            timeSinceIgnited += state;
            if (timeSinceIgnited < 0) timeSinceIgnited = 0;
            if (timeSinceIgnited >= fuseTime) {
                timeSinceIgnited = fuseTime;
                explode();
                return;
            }
        }
        super.tick();
    }

    /** EntityCreeper.explode: a strength-3 explosion where it stands. */
    private void explode() {
        if (host == null) return;
        boolean griefing = host.gameRule("mobGriefing");
        remove();
        host.explode(this, x, y, z, explosionRadius, griefing, displayName());
    }

    /** A long fall shortens the fuse (EntityCreeper.fall), to at most 5 ticks before going off. */
    @Override
    protected void fall(float distance, float multiplier) {
        super.fall(distance, multiplier);
        timeSinceIgnited = (int) (timeSinceIgnited + distance * 1.5f);
        if (timeSinceIgnited > fuseTime - 5) timeSinceIgnited = fuseTime - 5;
    }

    /** Right-clicked with flint and steel (EntityCreeper.interact): lit, it can't be stopped. */
    public void ignite() {
        playSound("fire.ignite", 1f, rand.nextFloat() * 0.4f + 0.8f);
        ignited = true;
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        int count = rand.nextInt(3);
        if (looting > 0) count += rand.nextInt(looting + 1);
        for (int i = 0; i < count; i++) dropItem("gunpowder", 1);
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("Fuse", new net.minecraft.nbt.NbtTag.Short((short) fuseTime));
        tag.put("ExplosionRadius", new net.minecraft.nbt.NbtTag.Byte((byte) explosionRadius));
        tag.put("ignited", new net.minecraft.nbt.NbtTag.Byte((byte) (ignited ? 1 : 0)));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("Fuse") instanceof net.minecraft.nbt.NbtTag.Short f) fuseTime = f.value();
        if (tag.get("ExplosionRadius") instanceof net.minecraft.nbt.NbtTag.Byte r) explosionRadius = r.value();
        if (tag.get("ignited") instanceof net.minecraft.nbt.NbtTag.Byte i) ignited = i.value() != 0;
    }

    /**
     * EntityAICreeperSwell: while swelling or with the target within 3
     * blocks, stand still and swell when the target is within 7 blocks and
     * in sight, otherwise shrink.
     */
    private static final class Swell extends EntityAI {
        private final EntityCreeper creeper;
        private Player target;
        Swell(EntityCreeper creeper) {
            this.creeper = creeper;
            mutexBits = 1;
        }
        @Override public boolean shouldExecute() {
            Player t = creeper.attackTarget();
            return creeper.state > 0 || t != null && creeper.distanceSqTo(t.x, t.y, t.z) < 9.0;
        }
        @Override public void startExecuting() {
            creeper.navigator().clearPath();
            target = creeper.attackTarget();
        }
        @Override public void resetTask() { target = null; }
        @Override public void updateTask() {
            if (target == null) creeper.state = -1;
            else if (creeper.distanceSqTo(target.x, target.y, target.z) > 49.0) creeper.state = -1;
            else if (!creeper.canSee(target)) creeper.state = -1;
            else creeper.state = 1;
        }
    }
}

package net.minecraft.entity.monster;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.world.World;

/**
 * The zombie pigman (1.8.9 EntityPigZombie + EntityZombie, HYPOTHESIS:
 * recalled): 20 health, 2 armour, speed 0.23, 0.6 x 1.95, carries a golden
 * sword (5 + 4 attack), never burns and ignores the player until hurt. A
 * hit makes it and every pigman within 35 blocks (10 up or down) angry for
 * 20-40 seconds, faster (+0.05) and grunting; angry pigmen chase and hit
 * the player. One in 20 is a baby (half size, faster). Drops 0-1 rotten
 * flesh and 0-1 gold nuggets (plus looting), rarely a gold ingot, and
 * sometimes its sword.
 */
public final class EntityPigZombie extends EntityMob {
    /** Ticks of anger left (angerLevel). */
    public int angerLevel;
    private int randomSoundDelay;
    private boolean child;

    public EntityPigZombie(World world, double x, double y, double z) {
        super(world, x, y, z);
        width = 0.6f;
        height = 1.95f;
        tasks.add(0, new EntityAI.Swimming(this));
        tasks.add(2, new EntityAI.AttackOnCollide(this, 1.0));
        tasks.add(7, new EntityAI.Wander(this, 1.0));
        tasks.add(8, new EntityAI.WatchClosest(this, 8f));
        tasks.add(8, new EntityAI.LookIdle(this));
        targetTasks.add(1, new EntityAI.HurtByTarget(this));
        targetTasks.add(2, new TargetAggressor(this));
    }

    @Override public String entityId() { return "PigZombie"; }
    @Override public float maxHealth() { return 20f; }
    @Override public double movementSpeed() {
        double speed = 0.23;
        if (child) speed *= 1.5;
        if (isAngry() && !child) speed += 0.05;
        return speed;
    }
    @Override public float followRange() { return 35f; }
    @Override protected float attackDamage() { return 5f; }
    @Override public boolean isImmuneToFire() { return true; }
    @Override protected String livingSound() { return "mob.zombiepig.zpig"; }
    @Override protected String hurtSound() { return "mob.zombiepig.zpighurt"; }
    @Override protected String deathSound() { return "mob.zombiepig.zpigdeath"; }
    @Override protected int experiencePoints() {
        int xp = 5 + (heldItem != null ? 1 + rand.nextInt(3) : 0);
        return child ? (int) (xp * 2.5f) : xp;
    }
    @Override public boolean isChild() { return child; }
    @Override public float eyeHeight() { return child ? 0.93f : 1.74f; }
    @Override protected float soundPitch() { return (rand.nextFloat() - rand.nextFloat()) * 0.2f + (child ? 1.5f : 1f); }

    public boolean isAngry() { return angerLevel > 0; }

    @Override
    protected void playStepSound(int bx, int by, int bz, net.minecraft.block.Block block) {
        playSound("mob.zombie.step", 0.15f, 1f);
    }

    /** EntityPigZombie.onInitialSpawn: the golden sword, and one in 20 a baby. */
    @Override
    public void onInitialSpawn() {
        java.util.function.Function<String, net.minecraft.item.Item> items = world.itemLookup();
        net.minecraft.item.Item sword = items == null ? null : items.apply("golden_sword");
        if (sword != null) heldItem = new net.minecraft.item.ItemStack(sword);
        if (rand.nextFloat() < 0.05f) setChild(true);
    }

    public void setChild(boolean child) {
        this.child = child;
        width = child ? 0.3f : 0.6f;
        height = child ? 0.975f : 1.95f;
    }

    /** EntityPigZombie.updateAITasks: the anger runs down, with an angry grunt now and then. */
    @Override
    protected void updateEntityActionState() {
        if (isAngry()) angerLevel--;
        if (randomSoundDelay > 0 && --randomSoundDelay == 0)
            playSound("mob.zombiepig.zpigangry", soundVolume() * 2f, ((rand.nextFloat() - rand.nextFloat()) * 0.2f + 1f) * 1.8f);
        super.updateEntityActionState();
    }

    /** EntityPigZombie.attackEntityFrom: the player's hit angers it and (AIHurtByAggressor) the pigmen around. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount, Player attacker) {
        if (attacker != null && isAlive() && !attacker.disableDamage) {
            becomeAngry();
            if (host != null) {
                var area = boundingBox().grow(followRange(), 10.0, followRange());
                for (Entity entity : host.entities())
                    if (entity instanceof EntityPigZombie other && other != this && other.isAlive() && other.boundingBox().intersects(area)) {
                        other.becomeAngry();
                        other.setRevengeTarget(attacker);
                    }
            }
        }
        return super.attackEntityFrom(source, amount, attacker);
    }

    /** EntityPigZombie.becomeAngryAt: 400-799 ticks of anger and a grunt within 2 seconds. */
    public void becomeAngry() {
        angerLevel = 400 + rand.nextInt(400);
        randomSoundDelay = rand.nextInt(40);
    }

    @Override
    protected void dropFewItems(boolean byPlayer, int looting) {
        int flesh = rand.nextInt(2 + looting);
        for (int i = 0; i < flesh; i++) dropItem("rotten_flesh", 1);
        int nuggets = rand.nextInt(2 + looting);
        for (int i = 0; i < nuggets; i++) dropItem("gold_nugget", 1);
    }

    @Override protected void addRareDrop(int bonus) { dropItem("gold_ingot", 1); }

    /** Zombies wear 2 armour points (the armour attribute), which takes damage down by 8%. */
    @Override
    protected void damageEntity(DamageSource source, float amount) {
        if (!source.isUnblockable()) amount = amount * (25 - 2) / 25f;
        super.damageEntity(source, amount);
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("Anger", new net.minecraft.nbt.NbtTag.Short((short) angerLevel));
        tag.put("IsBaby", new net.minecraft.nbt.NbtTag.Byte((byte) (child ? 1 : 0)));
        tag.put("HasSword", new net.minecraft.nbt.NbtTag.Byte((byte) (heldItem != null ? 1 : 0)));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("Anger") instanceof net.minecraft.nbt.NbtTag.Short anger) angerLevel = anger.value();
        if (tag.get("IsBaby") instanceof net.minecraft.nbt.NbtTag.Byte baby) setChild(baby.value() != 0);
        if (tag.get("HasSword") instanceof net.minecraft.nbt.NbtTag.Byte sword && sword.value() != 0) {
            var items = world.itemLookup();
            var item = items == null ? null : items.apply("golden_sword");
            if (item != null) heldItem = new net.minecraft.item.ItemStack(item);
        }
    }

    /** EntityPigZombie.AITargetAggressor: only an angry pigman goes after the player. */
    private static final class TargetAggressor extends EntityAI {
        private final EntityPigZombie pigman;
        private final EntityAI nearest;
        TargetAggressor(EntityPigZombie pigman) {
            this.pigman = pigman;
            this.nearest = new EntityAI.NearestAttackableTarget(pigman);
            mutexBits = 1;
        }
        @Override public boolean shouldExecute() { return pigman.isAngry() && nearest.shouldExecute(); }
        @Override public boolean continueExecuting() { return nearest.continueExecuting(); }
        @Override public void startExecuting() { nearest.startExecuting(); }
        @Override public void resetTask() { nearest.resetTask(); }
    }
}

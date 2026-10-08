package net.minecraft.entity.passive;

import net.minecraft.block.Block;
import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityXPOrb;
import net.minecraft.entity.ai.EntityAI;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.Set;

/**
 * A farm animal (1.8.9 EntityAgeable + EntityAnimal, HYPOTHESIS: recalled).
 * Babies start at age -24000 and grow up a tick at a time (feeding them
 * their food takes a tenth off what is left); adults that have just bred
 * wait 6000 ticks. Feeding an adult its food puts it in love for 600 ticks
 * (hearts), and two in love find each other within 8 blocks and, after 60
 * ticks close together, have a baby, with 1-7 experience. Animals never
 * despawn, like grass (10) over other blocks, panic when hurt or burning,
 * follow a player holding their food and babies follow an adult. Babies are
 * half size and drop nothing. Adults killed by the player give 1-3
 * experience.
 */
public abstract class EntityAnimal extends EntityLiving {
    /** Negative while a baby, positive while it can't breed again (EntityAgeable.growingAge). */
    protected int growingAge;
    /** Ticks left in love (EntityAnimal.inLove). */
    public int inLove;
    /** Ticks left showing growth sparkles after being fed as a baby (EntityAgeable's forced-growth counter). */
    private int growthSparkles;
    private final float adultWidth, adultHeight;

    protected EntityAnimal(World world, double x, double y, double z, float width, float height) {
        super(world, x, y, z);
        this.adultWidth = width;
        this.adultHeight = height;
        this.width = width;
        this.height = height;
    }

    /** A new animal of the same kind at this position (EntityAgeable.createChild). */
    protected abstract EntityAnimal createChild(double x, double y, double z);
    /** The items that make it breed (EntityAnimal.isBreedingItem). */
    protected abstract Set<String> breedingItems();

    public boolean isChild() { return growingAge < 0; }
    public int growingAge() { return growingAge; }

    /** EntityAgeable.setGrowingAge: babies are half size (setScaleForAge). */
    public void setGrowingAge(int age) {
        growingAge = age;
        float scale = age < 0 ? 0.5f : 1f;
        width = adultWidth * scale;
        height = adultHeight * scale;
    }

    /** EntityAgeable.ageUp: {@code seconds} closer to growing up (fed babies sparkle). */
    public void ageUp(int seconds, boolean fed) {
        int age = growingAge + seconds * 20;
        if (growingAge < 0 && age > 0) age = 0;
        setGrowingAge(age);
        if (fed && growthSparkles == 0) growthSparkles = 40;
    }

    public boolean isInLove() { return inLove > 0; }

    @Override protected boolean canDespawn() { return false; }
    @Override protected boolean canDropLoot() { return !isChild(); }
    @Override protected int talkInterval() { return 120; }
    /** EntityLivingBase.getSoundPitch: babies sound half an octave higher. */
    @Override protected float soundPitch() { return (rand.nextFloat() - rand.nextFloat()) * 0.2f + (isChild() ? 1.5f : 1f); }
    @Override protected int experiencePoints() { return 1 + rand.nextInt(3); }

    /** EntityAnimal.getBlockPathWeight: grass is best, otherwise the brighter the better. */
    @Override
    public float blockPathWeight(int bx, int by, int bz) {
        if (by - 1 >= 0 && by - 1 < World.HEIGHT && world.get(bx, by - 1, bz).name().path().equals("grass")) return 10f;
        return net.minecraft.world.MobSpawner.brightness(world, host == null ? 6000 : host.worldTime(), bx, by, bz) - 0.5f;
    }

    @Override
    protected void livingUpdate() {
        if (growingAge != 0) inLove = 0;
        super.livingUpdate();
        // EntityAgeable.onLivingUpdate: growing up, or the breeding wait running out.
        if (growthSparkles > 0) {
            if (growthSparkles % 4 == 0 && host != null)
                host.particle("happyVillager", x + rand.nextFloat() * width * 2f - width, y + 0.5 + rand.nextFloat() * height,
                    z + rand.nextFloat() * width * 2f - width, 0, 0, 0);
            growthSparkles--;
        }
        if (growingAge < 0) setGrowingAge(growingAge + 1);
        else if (growingAge > 0) setGrowingAge(growingAge - 1);
        if (inLove > 0) {
            inLove--;
            if (inLove % 10 == 0) hearts(1);
        }
    }

    /** Heart particles around the body (EntityAnimal's love particles and status 18). */
    protected void hearts(int count) {
        if (host == null) return;
        for (int i = 0; i < count; i++)
            host.particle("heart", x + rand.nextFloat() * width * 2f - width, y + 0.5 + rand.nextFloat() * height,
                z + rand.nextFloat() * width * 2f - width, rand.nextGaussian() * 0.02, rand.nextGaussian() * 0.02, rand.nextGaussian() * 0.02);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount, Player attacker) {
        inLove = 0;
        return super.attackEntityFrom(source, amount, attacker);
    }

    // ---- interaction --------------------------------------------------------------------------

    /** What right-clicking an animal can do to the player's hand (the game implements it). */
    public interface Interactor {
        /** The held stack, or null. */
        ItemStack held();
        boolean creative();
        /** Uses one of the held stack up (not in Creative: EntityAnimal.consumeItemFromStack). */
        void consumeHeld();
        /** One of the held stack becomes {@code result} (a bucket of milk): it replaces a single item or goes into the inventory. */
        void exchangeHeld(ItemStack result);
        /** Wears the held tool by {@code amount} (not in Creative). */
        void damageHeld(int amount);
        /** A new stack of an item by name, or null. */
        ItemStack stack(String name);
        void awardAchievement(String id);
    }

    /**
     * EntityAnimal.interact and EntityAgeable.interact: its food breeds an
     * adult that is ready (or speeds a baby up by 10% of what is left); a
     * spawn egg of its own kind hatches a baby next to it. Returns whether
     * anything happened (the arm swings).
     */
    public boolean interact(Interactor game) {
        ItemStack held = game.held();
        if (held == null) return false;
        String name = held.item().name().path();
        if (breedingItems().contains(name)) {
            if (growingAge == 0 && inLove <= 0) {
                game.consumeHeld();
                inLove = 600;
                hearts(7);
                return true;
            }
            if (isChild()) {
                game.consumeHeld();
                ageUp((int) ((-growingAge / 20) * 0.1f), true);
                return true;
            }
        }
        if (held.item().kind() == net.minecraft.item.Item.Kind.SPAWN_EGG) {
            net.minecraft.item.VanillaItems.Egg egg = net.minecraft.item.VanillaItems.egg(held.item().vanillaData());
            if (egg != null && egg.entity().equals(entityId()) && host != null) {
                EntityAnimal baby = createChild(x, y, z);
                baby.setGrowingAge(-24000);
                baby.setPositionAndRotation(x, y, z, 0f, 0f);
                host.spawn(baby);
                game.consumeHeld();
                return true;
            }
        }
        return false;
    }

    // ---- saving -------------------------------------------------------------------------------

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("Age", new net.minecraft.nbt.NbtTag.Int(growingAge));
        tag.put("ForcedAge", new net.minecraft.nbt.NbtTag.Int(0));
        tag.put("InLove", new net.minecraft.nbt.NbtTag.Int(inLove));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("Age") instanceof net.minecraft.nbt.NbtTag.Int age) setGrowingAge(age.value());
        if (tag.get("InLove") instanceof net.minecraft.nbt.NbtTag.Int love) inLove = love.value();
    }

    // ---- shared goals ------------------------------------------------------------------------

    /** The nearest other entity of this exact class within a box grown by these amounts, or null. */
    @SuppressWarnings("unchecked")
    protected <T extends EntityAnimal> T nearest(Class<T> type, double grow, double growY, java.util.function.Predicate<T> filter) {
        if (host == null) return null;
        var area = boundingBox().grow(grow, growY, grow);
        T best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : host.entities()) {
            if (entity == this || entity.getClass() != type || entity.isRemoved()) continue;
            T other = (T) entity;
            if (!other.isAlive() || !other.boundingBox().intersects(area) || !filter.test(other)) continue;
            double distance = distanceSqTo(other.x(), other.y(), other.z());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    /** EntityAIPanic: hurt by someone or burning, run to a random spot within 5 across and 4 up or down. */
    public static final class Panic extends EntityAI {
        private final EntityLiving entity;
        private final double speed;
        private double tx, ty, tz;
        public Panic(EntityLiving entity, double speed) {
            this.entity = entity;
            this.speed = speed;
            mutexBits = 1;
        }
        @Override public boolean shouldExecute() {
            if (entity.revengeTarget() == null && !entity.isBurning()) return false;
            double[] target = randomTarget(entity, 5, 4);
            if (target == null) return false;
            tx = target[0]; ty = target[1]; tz = target[2];
            return true;
        }
        @Override public void startExecuting() { entity.navigator().tryMoveTo(tx, ty, tz, speed); }
        @Override public boolean continueExecuting() { return !entity.navigator().noPath(); }
    }

    /**
     * EntityAITempt: follow the nearest player within 10 blocks holding one
     * of {@code items}, stopping 2.5 blocks short, then wait 100 ticks
     * before being tempted again.
     */
    public static final class Tempt extends EntityAI {
        private final EntityLiving entity;
        private final double speed;
        private final Set<String> items;
        private Player player;
        private int delay;
        public Tempt(EntityLiving entity, double speed, Set<String> items) {
            this.entity = entity;
            this.speed = speed;
            this.items = items;
            mutexBits = 3;
        }
        @Override public boolean shouldExecute() {
            if (delay > 0) {
                delay--;
                return false;
            }
            Player p = entity.host() == null ? null : entity.host().nearestPlayer(entity.x(), entity.y(), entity.z());
            if (p == null || p.dead || entity.distanceSqTo(p.x, p.y, p.z) > 100.0) return false;
            ItemStack held = p.heldItem == null ? null : p.heldItem.get();
            if (held == null || !items.contains(held.item().name().path())) return false;
            player = p;
            return true;
        }
        @Override public void resetTask() {
            player = null;
            entity.navigator().clearPath();
            delay = 100;
        }
        @Override public void updateTask() {
            entity.lookHelper().setLookPosition(player.x, player.y + player.eyeHeight(), player.z, 30f, 40f);
            if (entity.distanceSqTo(player.x, player.y, player.z) < 6.25) entity.navigator().clearPath();
            else entity.navigator().tryMoveTo(player.x, player.y, player.z, speed);
        }
    }

    /**
     * EntityAIMate: in love, walk to the nearest other in love within 8
     * blocks; after 60 ticks within 3 blocks of each other, a baby (age
     * -24000) appears with seven hearts and 1-7 experience, and both parents
     * wait 6000 ticks.
     */
    public static final class Mate extends EntityAI {
        private final EntityAnimal animal;
        private final double speed;
        private EntityAnimal mate;
        private int delay;
        public Mate(EntityAnimal animal, double speed) {
            this.animal = animal;
            this.speed = speed;
            mutexBits = 3;
        }
        @Override public boolean shouldExecute() {
            if (!animal.isInLove()) return false;
            mate = animal.nearest(animal.getClass(), 8, 8, other -> other.isInLove());
            return mate != null;
        }
        @Override public boolean continueExecuting() { return mate.isAlive() && mate.isInLove() && delay < 60; }
        @Override public void resetTask() {
            mate = null;
            delay = 0;
        }
        @Override public void updateTask() {
            animal.lookHelper().setLookPosition(mate.x(), mate.y() + mate.eyeHeight(), mate.z(), 10f, 40f);
            animal.navigator().tryMoveTo(mate.x(), mate.y(), mate.z(), speed);
            delay++;
            if (delay >= 60 && animal.distanceSqTo(mate.x(), mate.y(), mate.z()) < 9.0) spawnBaby();
        }
        private void spawnBaby() {
            if (animal.host() == null) return;
            EntityAnimal child = animal.createChild(animal.x(), animal.y(), animal.z());
            if (child instanceof EntitySheep lamb && animal instanceof EntitySheep a && mate instanceof EntitySheep b)
                lamb.fleeceColor = EntitySheep.mixedColor(a.fleeceColor, b.fleeceColor, animal.getRNG());
            animal.setGrowingAge(6000);
            mate.setGrowingAge(6000);
            animal.inLove = 0;
            mate.inLove = 0;
            child.setGrowingAge(-24000);
            child.setPositionAndRotation(animal.x(), animal.y(), animal.z(), 0f, 0f);
            animal.host().spawn(child);
            animal.hearts(7);
            animal.host().achievement("breedCow");
            if (animal.host().gameRule("doMobLoot"))
                animal.host().spawn(new EntityXPOrb(animal.world(), animal.x(), animal.y(), animal.z(), animal.getRNG().nextInt(7) + 1));
        }
    }

    /** EntityAIFollowParent: a baby walks to the nearest adult of its kind within 8 blocks once more than 3 away (up to 16). */
    public static final class FollowParent extends EntityAI {
        private final EntityAnimal child;
        private final double speed;
        private EntityAnimal parent;
        private int delay;
        public FollowParent(EntityAnimal child, double speed) {
            this.child = child;
            this.speed = speed;
        }
        @Override public boolean shouldExecute() {
            if (child.growingAge >= 0) return false;
            EntityAnimal adult = child.nearest(child.getClass(), 8, 4, other -> other.growingAge >= 0);
            if (adult == null || child.distanceSqTo(adult.x(), adult.y(), adult.z()) < 9.0) return false;
            parent = adult;
            return true;
        }
        @Override public boolean continueExecuting() {
            if (child.growingAge >= 0 || parent == null || !parent.isAlive()) return false;
            double distance = child.distanceSqTo(parent.x(), parent.y(), parent.z());
            return distance >= 9.0 && distance <= 256.0;
        }
        @Override public void startExecuting() { delay = 0; }
        @Override public void resetTask() { parent = null; }
        @Override public void updateTask() {
            if (--delay > 0) return;
            delay = 10;
            child.navigator().tryMoveTo(parent.x(), parent.y(), parent.z(), speed);
        }
    }

    /** The step sound animals make instead of the block's (Entity.playStepSound overrides), at 0.15. */
    protected abstract String stepSound();

    @Override
    protected void playStepSound(int bx, int by, int bz, Block block) {
        playSound(stepSound(), 0.15f, 1f);
    }

    /** The drop name for a meat that cooks when the animal dies burning. */
    protected String meat(String raw, String cooked) { return isBurning() ? cooked : raw; }

    protected static double[] randomTarget(EntityLiving entity, int horizontal, int vertical) {
        return EntityAI.randomTarget(entity, horizontal, vertical);
    }
}

package net.minecraft.entity.monster;

import net.minecraft.entity.EntityLiving;
import net.minecraft.world.World;

/**
 * A hostile mob (1.8.9 EntityMob, HYPOTHESIS: recalled): hostile hurt,
 * death, splash and fall sounds; it ages twice as fast toward despawning
 * while it stands in the light, is removed in Peaceful, and prefers dark
 * places ({@code 0.5 - brightness}).
 */
public abstract class EntityMob extends EntityLiving implements IMob {
    protected EntityMob(World world, double x, double y, double z) { super(world, x, y, z); }

    @Override protected String hurtSound() { return "game.hostile.hurt"; }
    @Override protected String deathSound() { return "game.hostile.die"; }
    @Override protected String splashSound() { return "game.hostile.swim.splash"; }
    @Override protected String fallSound(int damage) { return damage > 4 ? "game.hostile.hurt.fall.big" : "game.hostile.hurt.fall.small"; }

    @Override
    public void tick() {
        if (host != null && host.difficulty() == 0) {
            remove();
            return;
        }
        super.tick();
    }

    @Override
    protected void livingUpdate() {
        if (brightness() > 0.5f) entityAge += 2;
        super.livingUpdate();
    }

    @Override
    public float blockPathWeight(int x, int y, int z) { return 0.5f - brightness(x, y, z); }

    /** Entity.getBrightness: the light at the eyes through the brightness curve. */
    public float brightness() {
        return brightness(floor(x), floor(y + eyeHeight()), floor(z));
    }

    /** World.getLightBrightness: the light (sky dimmed by the time of day) through 1.8.9's brightness curve. */
    public float brightness(int bx, int by, int bz) {
        return net.minecraft.world.MobSpawner.brightness(world, host == null ? 6000 : host.worldTime(), bx, by, bz);
    }
}

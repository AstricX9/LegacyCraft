package net.minecraft.entity;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

/**
 * The dragon's fireball (1.9 EntityDragonFireball, HYPOTHESIS: recalled):
 * it can't be batted back, trails dragon's breath, and where it lands it
 * leaves a cloud of dragon's breath (radius 3 growing to 7 over 30
 * seconds, Instant Damage II), moved onto the nearest living thing within 4.
 */
public final class EntityDragonFireball extends EntityFireball {
    public EntityDragonFireball(World world, double x, double y, double z) { super(world, x, y, z, 1f); }

    public static EntityDragonFireball shotBy(World world, EntityLiving shooter, double ax, double ay, double az, java.util.Random random) {
        EntityDragonFireball fireball = new EntityDragonFireball(world, shooter.x(), shooter.y(), shooter.z());
        fireball.aim(shooter, ax, ay, az, random);
        return fireball;
    }

    @Override public String entityId() { return "DragonFireball"; }
    @Override public float renderScale() { return 1f; }
    @Override public boolean deflectable() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (!isRemoved() && host != null) host.particle("dragonbreath", x, y, z, 0, 0, 0);
    }

    @Override
    protected void onImpact(Object hit, WorldRenderer.Hit block) {
        if (hit == shooter) return;
        if (host != null) {
            EntityAreaEffectCloud cloud = new EntityAreaEffectCloud(world, x, y, z);
            cloud.owner = shooter;
            cloud.dragonBreath = true;
            cloud.radius = 3f;
            cloud.duration = 600;
            cloud.radiusPerTick = (7f - cloud.radius) / cloud.duration;
            cloud.effects.add(new PotionEffect(Potion.HARM, 1, 1));
            double best = 16.0;
            for (Entity entity : host.entities())
                if (entity instanceof EntityLiving living && living != shooter && living.isAlive()) {
                    double d = living.distanceSqTo(x, y, z);
                    if (d < best) { best = d; cloud.setPosition(living.x(), living.y(), living.z()); }
                }
            var player = host.player();
            if (player != null && !player.dead) {
                double dx = player.x - x, dy = player.y - y, dz = player.z - z, d = dx * dx + dy * dy + dz * dz;
                if (d < best) cloud.setPosition(player.x, player.y, player.z);
            }
            host.playSound("entity.enderdragon_fireball.explode", x, y, z, 1f, rand.nextFloat() * 0.1f + 0.9f);
            host.spawn(cloud);
        }
        remove();
    }
}

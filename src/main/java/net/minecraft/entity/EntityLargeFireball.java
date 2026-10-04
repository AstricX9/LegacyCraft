package net.minecraft.entity;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.world.World;

/**
 * A ghast's fireball (1.8.9 EntityLargeFireball, HYPOTHESIS: recalled): 6
 * damage to what it hits, then an explosion of strength 1 that breaks
 * blocks and starts fires (if mobGriefing allows). The player can bat it
 * back with a hit.
 */
public final class EntityLargeFireball extends EntityFireball {
    public int explosionPower = 1;

    public EntityLargeFireball(World world, double x, double y, double z) { super(world, x, y, z, 1f); }

    public static EntityLargeFireball shotBy(World world, EntityLiving shooter, double ax, double ay, double az, java.util.Random random) {
        EntityLargeFireball fireball = new EntityLargeFireball(world, shooter.x(), shooter.y(), shooter.z());
        fireball.aim(shooter, ax, ay, az, random);
        return fireball;
    }

    @Override public String entityId() { return "Fireball"; }
    @Override public float renderScale() { return 2f; }

    @Override
    protected void onImpact(Object hit, WorldRenderer.Hit block) {
        if (hit != null) damage(hit, 6f);
        if (host != null) {
            boolean griefing = host.gameRule("mobGriefing");
            remove();
            host.explode(null, x, y, z, explosionPower, griefing, griefing, null);
        } else {
            remove();
        }
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.writeNbt(tag);
        tag.put("ExplosionPower", new net.minecraft.nbt.NbtTag.Int(explosionPower));
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtTag.Compound tag) {
        super.readNbt(tag);
        if (tag.get("ExplosionPower") instanceof net.minecraft.nbt.NbtTag.Int power) explosionPower = power.value();
    }
}

package net.minecraft.entity;

import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.world.World;

/**
 * Entities by their saved id (1.8.9 EntityList): what /summon and the saved
 * entity list create. Only the entities LegacyCraft has are here.
 */
public final class EntityList {
    private EntityList() {}

    /** A new entity of this id at the origin, or null if there is no such entity. */
    public static MovingEntity create(String id, World world, double x, double y, double z) {
        return switch (id) {
            case "Creeper" -> new EntityCreeper(world, x, y, z);
            case "PrimedTnt" -> new EntityTNTPrimed(world, x, y, z, null);
            case "XPOrb" -> new EntityXPOrb(world, x, y, z, 1);
            case "ThrownPotion" -> new EntityPotion(world, x, y, z, 0);
            case "Snowball" -> new EntitySnowball(world, x, y, z);
            case "ThrownEgg" -> new EntityEgg(world, x, y, z);
            case "Pig" -> new net.minecraft.entity.passive.EntityPig(world, x, y, z);
            case "PigZombie" -> new net.minecraft.entity.monster.EntityPigZombie(world, x, y, z);
            case "Skeleton" -> new net.minecraft.entity.monster.EntitySkeleton(world, x, y, z);
            case "Blaze" -> new net.minecraft.entity.monster.EntityBlaze(world, x, y, z);
            case "Ghast" -> new net.minecraft.entity.monster.EntityGhast(world, x, y, z);
            case "LavaSlime" -> new net.minecraft.entity.monster.EntityMagmaCube(world, x, y, z);
            case "Arrow" -> new EntityArrow(world, x, y, z);
            case "Fireball" -> new EntityLargeFireball(world, x, y, z);
            case "SmallFireball" -> new EntitySmallFireball(world, x, y, z);
            case "WitherBoss" -> new net.minecraft.entity.boss.EntityWither(world, x, y, z);
            case "WitherSkull" -> new EntityWitherSkull(world, x, y, z);
            case "Cow" -> new net.minecraft.entity.passive.EntityCow(world, x, y, z);
            case "Sheep" -> new net.minecraft.entity.passive.EntitySheep(world, x, y, z);
            case "Chicken" -> new net.minecraft.entity.passive.EntityChicken(world, x, y, z);
            case "Boat" -> new EntityBoat(world, x, y, z);
            case "VillagerGolem" -> new net.minecraft.entity.passive.EntityIronGolem(world, x, y, z);
            default -> null;
        };
    }

    /** Entity.readEntityFromNBT through the id: a loaded entity, or null for unknown ids. */
    public static MovingEntity fromNbt(net.minecraft.nbt.NbtTag.Compound tag, World world) {
        if (!(tag.get("id") instanceof net.minecraft.nbt.NbtTag.Str id)) return null;
        MovingEntity entity = create(id.value(), world, 0, 0, 0);
        if (entity == null) return null;
        entity.readNbt(tag);
        return entity;
    }
}

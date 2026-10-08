package net.minecraft.server.dedicated;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.entity.Player;
import net.minecraft.entity.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHost;
import net.minecraft.entity.EntityItem;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityXPOrb;
import net.minecraft.entity.MovingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolValues;
import net.minecraft.network.v47.Clientbound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.Explosion;
import net.minecraft.world.MobSpawner;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mobs on the server: natural spawning around every player (the shared
 * {@link MobSpawner}), the mobs' own AI and the world they ask things of
 * ({@link EntityHost}), fights in both directions and creeper explosions.
 *
 * <p>The mob code aims at and hurts a {@link Player}; each connected player
 * has one here as their body, copied from the {@link ServerPlayer} before
 * the mobs tick. What the mobs do to a body (damage after armour, knockback)
 * goes back to the server's player and their client.
 */
final class ServerMobs implements EntityHost {
    /** Sounds carry 16 blocks, further when louder (vanilla's sendToAllNear). */
    private static final double SOUND_RANGE = 16;
    /** Players this far from an explosion get its packet (64 blocks). */
    private static final double EXPLOSION_RANGE_SQ = 4096;
    /** Furthest a player can reach to hit a mob (NetHandlerPlayServer allows 6). */
    private static final double ATTACK_REACH_SQ = 36;

    private final DedicatedServer server;
    private final Map<ServerPlayer, Player> bodies = new LinkedHashMap<>();
    private List<Player> bodyList = List.of();
    /** Each mob's hurt time and whether it was alive when last looked at, to tell clients when it is hit or dies. */
    private final Map<EntityLiving, int[]> lastSeen = new HashMap<>();

    ServerMobs(DedicatedServer server) { this.server = server; }

    // ---- bodies -----------------------------------------------------------------------------

    Player body(ServerPlayer player) {
        return bodies.computeIfAbsent(player, p -> {
            Player body = new Player(server.world, p.x, p.z);
            body.damageListener = new Player.DamageListener() {
                @Override public void hurt(DamageSource source, float amount, boolean fresh) {
                    server.life.hurtThroughBody(p, body.health, source, body.lastAttacker);
                }
                @Override public void armorBroke(int slot) {
                    server.sendToTrackers(p, Clientbound.SoundEffect.at("random.break", p.x, p.y, p.z, 0.8f, 0.9f));
                    p.connection.send(Clientbound.SoundEffect.at("random.break", p.x, p.y, p.z, 0.8f, 0.9f));
                }
            };
            bodyList = null;
            return body;
        });
    }

    /** A player left: mobs chasing them give up. */
    void removed(ServerPlayer player) {
        Player body = bodies.remove(player);
        if (body != null) body.dead = true;
        bodyList = null;
    }

    /** Copies each player's position, state and armour onto their body. */
    private void syncBodies() {
        for (ServerPlayer player : server.players()) {
            Player body = body(player);
            body.prevX = body.x; body.prevY = body.y; body.prevZ = body.z;
            body.x = player.x; body.y = player.y; body.z = player.z;
            body.yaw = player.yaw;
            body.pitch = player.pitch;
            body.onGround = player.onGround;
            body.fallDistance = player.fallDistance;
            body.sneaking = player.sneaking;
            body.sprinting = player.sprinting;
            body.dead = player.dead;
            body.health = player.dead ? 0 : player.health;
            body.disableDamage = !player.isSurvivalLike();
            body.noClip = player.gameMode == 3;
            body.difficulty = server.properties().difficulty;
            body.armor = player.inventory.armorInventory;
            body.heldItem = player::heldStack;
            body.motionX = body.motionY = body.motionZ = 0;
            if (body.hurtResistantTime > 0) body.hurtResistantTime--;
            if (body.hurtTime > 0) body.hurtTime--;
            if (player.dead) body.hurtResistantTime = 0;
        }
    }

    // ---- ticking ----------------------------------------------------------------------------

    /** Before the entities tick: the bodies catch up with their players, then new mobs spawn around them. */
    void beforeTick() {
        syncBodies();
        server.world.setDifficulty(server.properties().difficulty);
        if (server.properties().spawnMonsters)
            MobSpawner.spawnMonsters(server.world, this, server.random, server.spawnX, server.spawnY, server.spawnZ);
        if (server.properties().spawnAnimals)
            MobSpawner.spawnCreatures(server.world, this, server.random, server.tick(), server.spawnX, server.spawnY, server.spawnZ);
    }

    /**
     * After the entities tick: a player knocked back is sent the push, and
     * whoever can see a mob is told when it was hurt or died.
     */
    void afterTick() {
        for (Map.Entry<ServerPlayer, Player> entry : bodies.entrySet()) {
            Player body = entry.getValue();
            ServerPlayer player = entry.getKey();
            if (body.motionX == 0 && body.motionY == 0 && body.motionZ == 0) continue;
            player.connection.send(new Clientbound.EntityVelocity(player.entityId, Clientbound.SpawnObject.velocity(body.motionX),
                Clientbound.SpawnObject.velocity(body.motionY), Clientbound.SpawnObject.velocity(body.motionZ)));
            body.motionX = body.motionY = body.motionZ = 0;
        }
        lastSeen.keySet().removeIf(Entity::isRemoved);
        for (Entity entity : server.entities.values()) {
            if (!(entity instanceof EntityLiving mob) || mob.isRemoved()) continue;
            int[] seen = lastSeen.computeIfAbsent(mob, m -> new int[]{m.hurtTime, m.health > 0 ? 1 : 0});
            int id = server.entities.idOf(mob.uuid());
            if (mob.health <= 0 && seen[1] == 1) server.sendToViewers(id, new Clientbound.EntityStatus(id, Clientbound.EntityStatus.DEAD));
            else if (mob.hurtTime > seen[0]) server.sendToViewers(id, new Clientbound.EntityStatus(id, Clientbound.EntityStatus.HURT));
            seen[0] = mob.hurtTime;
            seen[1] = mob.health > 0 ? 1 : 0;
        }
    }

    // ---- players hitting mobs -----------------------------------------------------------------

    /**
     * A left click on a mob (Use Entity, attack): the held item's damage
     * (half as much again for a falling critical hit) plus Sharpness, extra
     * knockback from Knockback or sprinting, Fire Aspect, tool wear and the
     * swing's exhaustion, as in singleplayer.
     */
    void attack(ServerPlayer player, int targetId) {
        if (player.dead || player.gameMode == 3) return;
        if (!(server.entities.get(targetId) instanceof EntityLiving mob) || !mob.isAlive()) return;
        if (mob.distanceSqTo(player.x, player.eyeY(), player.z) > ATTACK_REACH_SQ) return;
        Player body = body(player);
        ItemStack held = player.heldStack();
        float damage = body.attackDamage(ToolValues.attackDamage(held == null ? null : held.item()));
        float bonus = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.SHARPNESS, held) * 1.25f;
        int knockback = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.KNOCKBACK, held)
            + (player.sprinting ? 1 : 0);
        if (damage <= 0 && bonus <= 0) return;
        boolean crit = player.fallDistance > 0 && !player.onGround;
        if (crit && damage > 0) damage *= 1.5f;
        damage += bonus;
        int fireAspect = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.FIRE_ASPECT, held);
        boolean lit = false;
        if (fireAspect > 0 && !mob.isBurning()) {
            lit = true;
            mob.setFire(1);
        }
        int looting = net.minecraft.enchantment.EnchantmentHelper.level(net.minecraft.enchantment.Enchantment.LOOTING, held);
        if (!mob.attackFromPlayer(body, damage, looting)) {
            if (lit) mob.fire = 0;
            return;
        }
        int id = server.entities.idOf(mob.uuid());
        if (knockback > 0) {
            double yaw = Math.toRadians(player.yaw);
            mob.addVelocity(-Math.sin(yaw) * knockback * 0.5, 0.1, Math.cos(yaw) * knockback * 0.5);
        }
        if (crit) server.sendToViewers(id, new Clientbound.Animation(id, 4));
        if (bonus > 0) server.sendToViewers(id, new Clientbound.Animation(id, 5));
        if (held != null && player.isSurvivalLike()) {
            ToolValues.Type type = ToolValues.type(held.item());
            int wear = type == ToolValues.Type.SWORD ? 1
                : type == ToolValues.Type.AXE || type == ToolValues.Type.PICKAXE || type == ToolValues.Type.SHOVEL ? 2 : 0;
            if (wear > 0 && net.minecraft.enchantment.EnchantmentHelper.damageItem(held, wear, server.random)) {
                player.inventory.mainInventory[player.inventory.currentItem] = null;
                playSound("random.break", player.x, player.y, player.z, 0.8f, 0.8f + server.random.nextFloat() * 0.4f);
            }
        }
        if (fireAspect > 0) mob.setFire(fireAspect * 4);
        server.life.addExhaustion(player, 0.3f);
    }

    // ---- what mobs may ask of the server ------------------------------------------------------

    /** There is no single player on a server; mobs use {@link #players()} and {@link #nearestPlayer}. */
    @Override public Player player() { return null; }

    @Override
    public List<Player> players() {
        if (bodyList == null) bodyList = List.copyOf(bodies.values());
        return bodyList;
    }

    @Override public List<Entity> entities() { return server.entities.values(); }

    @Override
    public void spawn(Entity entity) {
        // Experience isn't kept on the server yet, so orbs aren't made.
        if (entity instanceof EntityXPOrb) return;
        if (entity instanceof EntityItem item) { server.spawnItem(item); return; }
        if (entity instanceof MovingEntity moving) moving.setHost(this);
        server.entities.add(entity);
    }

    @Override
    public void playSound(String event, double x, double y, double z, float volume, float pitch) {
        double range = volume > 1f ? SOUND_RANGE * volume : SOUND_RANGE;
        Clientbound.SoundEffect packet = null;
        for (ServerPlayer player : server.players()) {
            double dx = player.x - x, dy = player.y - y, dz = player.z - z;
            if (dx * dx + dy * dy + dz * dz > range * range) continue;
            if (packet == null) packet = Clientbound.SoundEffect.at(event, x, y, z, volume, pitch);
            player.connection.send(packet);
        }
    }

    /** Clients make their own particles (death smoke, crits, bubbles) from the entity state they're sent. */
    @Override public void particle(String kind, double x, double y, double z, double vx, double vy, double vz) {}

    @Override public int difficulty() { return server.properties().difficulty; }
    @Override public boolean gameRule(String name) { return true; }
    @Override public long worldTime() { return server.timeOfDay; }

    @Override
    public void setBlock(int x, int y, int z, String block, int metadata) {
        Block type = server.blocks.get("minecraft:" + block);
        if (type != null && y >= 0 && y < World.HEIGHT) server.world.setStateAndUpdate(x, y, z, BlockState.of(type, metadata));
    }

    @Override
    public void breakBlock(int x, int y, int z, boolean drop) {
        if (y < 0 || y >= World.HEIGHT) return;
        World world = server.world;
        BlockState previous = world.getState(x, y, z);
        if (previous.block().isAir()) return;
        blockBreakEffects(x, y, z, previous);
        world.setStateAndUpdate(x, y, z, BlockState.of(server.blocks.air(), 0));
        if (drop) server.dropBlock(x, y, z, previous);
    }

    @Override
    public void blockBreakEffects(int x, int y, int z, BlockState state) {
        Clientbound.Effect effect = new Clientbound.Effect(Clientbound.Effect.BLOCK_BREAK, x, y, z, state.packed() >> 4 | (state.packed() & 15) << 12, false);
        for (ServerPlayer player : server.players())
            if (player.loadedChunks.contains(World.chunkKey(x >> 4, z >> 4))) player.connection.send(effect);
    }

    @Override
    public void explode(Entity exploder, double x, double y, double z, float size, boolean smoking, String attacker) {
        explode(exploder, x, y, z, size, false, smoking, attacker);
    }

    /**
     * World.newExplosion on the server: hurts and pushes mobs, items and
     * players near it, breaks blocks when {@code smoking} (dropping some),
     * and tells every client within 64 blocks, with their own push.
     */
    @Override
    public void explode(Entity exploder, double x, double y, double z, float size, boolean flaming, boolean smoking, String attacker) {
        World world = server.world;
        Explosion explosion = new Explosion(x, y, z, size);
        explosion.computeAffectedBlocks(world, server.random);
        float reach = size * 2f;
        AxisAlignedBB area = new AxisAlignedBB(x - reach - 1, y - reach - 1, z - reach - 1, x + reach + 1, y + reach + 1, z + reach + 1);
        for (Entity entity : server.entities.values()) {
            if (entity == exploder || entity.isRemoved()) continue;
            AxisAlignedBB box;
            double eyeHeight;
            if (entity instanceof MovingEntity moving) {
                if (moving.isImmuneToExplosions()) continue;
                box = moving.boundingBox();
                eyeHeight = moving.eyeHeight();
            } else if (entity instanceof EntityItem item) {
                box = item.boundingBox();
                eyeHeight = EntityItem.SIZE * 0.85;
            } else {
                continue;
            }
            if (!box.intersects(area)) continue;
            double[] push = push(world, x, y, z, reach, entity.x(), entity.y(), entity.z(), eyeHeight, box);
            if (push == null) continue;
            if (entity instanceof MovingEntity moving) {
                moving.attackFromExplosion((float) push[3], attacker, exploder instanceof MovingEntity m ? m : null);
                moving.addVelocity(push[0], push[1], push[2]);
            } else {
                EntityItem item = (EntityItem) entity;
                item.explosionDamage((float) push[3]);
                item.motionX += push[0];
                item.motionY += push[1];
                item.motionZ += push[2];
            }
        }
        Map<ServerPlayer, double[]> pushed = new HashMap<>();
        for (Map.Entry<ServerPlayer, Player> entry : bodies.entrySet()) {
            Player body = entry.getValue();
            if (body.dead || !body.boundingBox().intersects(area)) continue;
            double[] push = push(world, x, y, z, reach, body.x, body.y, body.z, body.eyeHeight(), body.boundingBox());
            if (push == null) continue;
            body.attackByExplosion((float) push[3], attacker);
            // The push travels in the explosion packet, not as a velocity.
            body.motionX = body.motionY = body.motionZ = 0;
            if (entry.getKey().gameMode != 3 && !(entry.getKey().isCreative())) pushed.put(entry.getKey(), push);
        }
        List<int[]> blocks = smoking ? explosion.affectedBlocks() : List.of();
        if (smoking) destroyExploded(blocks, size);
        if (flaming) {
            Block fire = server.blocks.get("minecraft:fire");
            for (int[] pos : explosion.affectedBlocks()) {
                int bx = pos[0], by = pos[1], bz = pos[2];
                if (by < 1 || by >= World.HEIGHT || !world.get(bx, by, bz).isAir() || !world.get(bx, by - 1, bz).isOpaque()) continue;
                if (server.random.nextInt(3) == 0) world.setStateAndUpdate(bx, by, bz, BlockState.defaultOf(fire));
            }
        }
        byte[] offsets = new byte[blocks.size() * 3];
        int cx = (int) x, cy = (int) y, cz = (int) z, n = 0;
        for (int[] pos : blocks) {
            offsets[n++] = (byte) (pos[0] - cx);
            offsets[n++] = (byte) (pos[1] - cy);
            offsets[n++] = (byte) (pos[2] - cz);
        }
        for (ServerPlayer player : server.players()) {
            double dx = player.x - x, dy = player.y - y, dz = player.z - z;
            if (dx * dx + dy * dy + dz * dz >= EXPLOSION_RANGE_SQ) continue;
            double[] push = pushed.getOrDefault(player, new double[4]);
            player.connection.send(new Clientbound.Explosion((float) x, (float) y, (float) z, size, offsets,
                (float) push[0], (float) push[1], (float) push[2]));
        }
    }

    /**
     * The push (x, y, z) and damage an explosion gives something at this spot
     * with this box, or null when it's out of reach: (1 - distance/reach) x
     * exposure, damage {@code (i^2 + i) / 2 x 7 x reach + 1}.
     */
    private static double[] push(World world, double x, double y, double z, float reach, double ex, double ey, double ez,
                                 double eyeHeight, AxisAlignedBB box) {
        double dx = ex - x, dy = ey - y, dz = ez - z;
        double closeness = Math.sqrt(dx * dx + dy * dy + dz * dz) / reach;
        if (closeness > 1.0) return null;
        double px = ex - x, py = ey + eyeHeight - y, pz = ez - z;
        double length = Math.sqrt(px * px + py * py + pz * pz);
        if (length == 0) return null;
        double impact = (1.0 - closeness) * Explosion.exposure(world, x, y, z, box);
        float damage = (int) ((impact * impact + impact) / 2.0 * 7.0 * reach + 1.0);
        return new double[]{px / length * impact, py / length * impact, pz / length * impact, damage};
    }

    /** Explosion.doExplosionB: each blasted block drops 1 in {@code size} of the time and is gone. */
    private void destroyExploded(List<int[]> blocks, float size) {
        World world = server.world;
        for (int[] pos : blocks) {
            int bx = pos[0], by = pos[1], bz = pos[2];
            BlockState state = world.getState(bx, by, bz);
            Block block = state.block();
            if (block.isAir()) continue;
            for (ItemStack drop : server.drops.explosionDrops(block.name().path(), block.material(), state.metadata(), 1f / size, server.random))
                server.spawnItem(EntityItem.fromBlock(world, bx, by, bz, drop, server.random));
            world.setStateAndUpdate(bx, by, bz, BlockState.of(server.blocks.air(), 0));
        }
    }

    // ---- what clients are shown -----------------------------------------------------------------

    /** The 1.8.9 network type of a mob (EntityList ids), or -1 for one clients can't be shown. */
    static int mobType(EntityLiving mob) {
        return switch (mob.entityId()) {
            case "Creeper" -> 50;
            case "Skeleton" -> 51;
            case "Spider" -> 52;
            case "Zombie" -> 54;
            case "Slime" -> 55;
            case "Ghast" -> 56;
            case "PigZombie" -> 57;
            case "Enderman" -> 58;
            case "Blaze" -> 61;
            case "LavaSlime" -> 62;
            case "WitherBoss" -> 64;
            case "Pig" -> 90;
            case "Sheep" -> 91;
            case "Cow" -> 92;
            case "Chicken" -> 93;
            case "VillagerGolem" -> 99;
            default -> -1;
        };
    }

    /**
     * A mob's metadata: 0 flags (burning), 6 health, 12 age for animals and
     * baby zombies, and each kind's own (13 skeleton type, 16 creeper state,
     * fleece, saddle, cube size, ghast attacking, blaze burning).
     */
    static byte[] metadata(EntityLiving mob) {
        net.minecraft.network.v47.Metadata data = new net.minecraft.network.v47.Metadata()
            .putByte(0, mob.isBurning() && !mob.isImmuneToFire() ? 0x01 : 0)
            .putFloat(6, Math.max(0f, mob.health));
        if (mob instanceof net.minecraft.entity.passive.EntityAnimal animal) data.putByte(12, animal.isChild() ? -1 : 0);
        else if (mob.isChild()) data.putByte(12, 1);
        if (mob instanceof net.minecraft.entity.monster.EntitySkeleton skeleton) data.putByte(13, skeleton.skeletonType);
        else if (mob instanceof net.minecraft.entity.monster.EntityCreeper creeper) data.putByte(16, creeper.creeperState());
        else if (mob instanceof net.minecraft.entity.passive.EntitySheep sheep) data.putByte(16, sheep.fleeceColor | (sheep.sheared ? 0x10 : 0));
        else if (mob instanceof net.minecraft.entity.monster.EntityMagmaCube cube) data.putByte(16, cube.size());
        else if (mob instanceof net.minecraft.entity.monster.EntityGhast ghast) data.putByte(16, ghast.attacking ? 1 : 0);
        else if (mob instanceof net.minecraft.entity.monster.EntityBlaze blaze) data.putByte(16, mob.isBurning() ? 1 : 0);
        else if (mob instanceof net.minecraft.entity.monster.EntitySpider spider) data.putByte(16, spider.isBesideClimbableBlock() ? 1 : 0);
        return data.build();
    }
}

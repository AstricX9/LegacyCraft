package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.entity.Player;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHost;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityTNTPrimed;
import net.minecraft.entity.MovingEntity;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Mob spawning (1.8.9 SpawnerAnimals.findChunksForSpawning, HYPOTHESIS:
 * recalled), first written for the MONSTER type. Every tick, in each chunk within 7 of
 * the player (the 17x17 square counts toward the cap, its edge doesn't
 * spawn) a random spot below the chunk's height is tried: three packs of
 * four tries, each step up to 5 blocks sideways, at least 24 blocks from
 * the player and from the world spawn, on a block with a solid top, in
 * air, passing the mob's own check and with nothing in the way; at most 4
 * per chunk per tick. Each pack draws from the monster list of its spot
 * ({@link #monsters}); draws of mobs LegacyCraft doesn't have spawn
 * nothing, and the cap of 70 is cut to the share it does have so those that
 * exist are as common as in vanilla. Animals: {@link #spawnCreatures}.
 */
public final class MobSpawner {
    private static final float[] BRIGHTNESS = new float[16];

    static {
        for (int i = 0; i <= 15; i++) {
            float f = 1f - i / 15f;
            BRIGHTNESS[i] = (1f - f) / (f * 3f + 1f);
        }
    }

    private MobSpawner() {}

    /** World.calculateSkylightSubtracted at this time of day, darker in rain. */
    public static int skylightSubtracted(World world, long worldTime) {
        float angle = net.minecraft.client.renderer.SkyRenderer.celestialAngle(worldTime, 1f);
        float f = 1f - ((float) Math.cos(angle * Math.PI * 2) * 2f + 0.5f);
        f = Math.max(0f, Math.min(1f, f));
        f = 1f - f;
        if (world.isRaining()) f *= 1f - 5f / 16f;
        f = 1f - f;
        return (int) (f * 11f);
    }

    /** World.getLightFromNeighbors: block light or sky light less the night, slabs and stairs taking their brightest neighbour. */
    public static int lightFromNeighbors(World world, long worldTime, int x, int y, int z) {
        int subtracted = skylightSubtracted(world, worldTime);
        if (y < 0) return 0;
        if (y >= World.HEIGHT) return Math.max(0, 15 - subtracted);
        if (world.generatedChunk(x >> 4, z >> 4) == null) return 0;
        Block block = world.get(x, y, z);
        if (block.useNeighborBrightness()) {
            int best = rawLight(world, subtracted, x, y + 1, z);
            best = Math.max(best, rawLight(world, subtracted, x + 1, y, z));
            best = Math.max(best, rawLight(world, subtracted, x - 1, y, z));
            best = Math.max(best, rawLight(world, subtracted, x, y, z + 1));
            best = Math.max(best, rawLight(world, subtracted, x, y, z - 1));
            return best;
        }
        return rawLight(world, subtracted, x, y, z);
    }

    private static int rawLight(World world, int subtracted, int x, int y, int z) {
        if (y < 0) return 0;
        if (y >= World.HEIGHT) return Math.max(0, 15 - subtracted);
        return Math.max(world.getSkyLight(x, y, z) - subtracted, world.getBlockLight(x, y, z));
    }

    /** World.getLightBrightness: the light level through 1.8.9's brightness curve (0-1). */
    public static float brightness(World world, long worldTime, int x, int y, int z) {
        return BRIGHTNESS[Math.max(0, Math.min(15, lightFromNeighbors(world, worldTime, x, y, z)))];
    }

    public static float brightnessOf(int light) { return BRIGHTNESS[Math.max(0, Math.min(15, light))]; }

    /**
     * The monster list at a position (BiomeGenBase.spawnableMonsterList and
     * ChunkProviderHell.getPossibleCreatures, 1.8.9, HYPOTHESIS: recalled):
     * spiders, zombies, skeletons, creepers and slimes (100 each, packs of
     * 4), endermen (10) and witches (5) almost everywhere; none on mushroom
     * islands; in the Nether zombie pigmen (100), ghasts (50) and magma cubes
     * (1), and inside a fortress (or on its nether brick) blazes (10, packs
     * of 2-3), zombie pigmen (5), skeletons (10) and magma cubes (3).
     */
    public static List<SpawnEntry> monsters(World world, int x, int y, int z) {
        if (world.isNether()) {
            var generator = world.netherGenerator();
            boolean onBrick = y >= 1 && y < World.HEIGHT && world.get(x, y - 1, z).name().path().equals("nether_brick");
            if (generator != null && generator.fortresses().isInside(x, y, z, onBrick)) return FORTRESS;
            return HELL;
        }
        int biome = world.biomeAt(x, z).id();
        return biome == 14 || biome == 15 ? List.of() : OVERWORLD_MONSTERS;
    }

    private static final List<SpawnEntry> OVERWORLD_MONSTERS = List.of(new SpawnEntry("Spider", 100, 4, 4),
        new SpawnEntry("Zombie", 100, 4, 4), new SpawnEntry("Skeleton", 100, 4, 4), new SpawnEntry("Creeper", 100, 4, 4),
        new SpawnEntry("Slime", 100, 4, 4), new SpawnEntry("Enderman", 10, 1, 4), new SpawnEntry("Witch", 5, 1, 1));
    private static final List<SpawnEntry> HELL = List.of(new SpawnEntry("PigZombie", 100, 4, 4),
        new SpawnEntry("Ghast", 50, 4, 4), new SpawnEntry("LavaSlime", 1, 4, 4));
    private static final List<SpawnEntry> FORTRESS = List.of(new SpawnEntry("Blaze", 10, 2, 3),
        new SpawnEntry("PigZombie", 5, 4, 4), new SpawnEntry("Skeleton", 10, 4, 4), new SpawnEntry("LavaSlime", 3, 4, 4));

    /** The monsters LegacyCraft has. */
    public static boolean monsterExists(String entity) {
        return switch (entity) {
            case "Creeper", "Skeleton", "PigZombie", "Ghast", "LavaSlime", "Blaze" -> true;
            default -> false;
        };
    }

    /** The share of the dimension's usual monster weight that LegacyCraft can spawn (the cap is cut to it). */
    private static float implementedShare(World world) {
        if (world.isNether()) return 1f;
        int total = 0, implemented = 0;
        for (SpawnEntry entry : OVERWORLD_MONSTERS) {
            total += entry.weight();
            if (monsterExists(entry.entity())) implemented += entry.weight();
        }
        return (float) implemented / total;
    }

    /**
     * One tick of hostile spawning (findChunksForSpawning for MONSTER):
     * each pack draws from the monster list at its first spot, and each mob
     * must pass its own check: creepers and skeletons need darkness, ghasts
     * spawn only one try in 20, and nothing spawns in Peaceful. The cap of
     * 70 is cut to the share of the list LegacyCraft has, so the mobs that
     * exist are as common as in vanilla.
     */
    public static int spawnMonsters(World world, EntityHost host, Random rand, int spawnX, int spawnY, int spawnZ) {
        Player player = host.player();
        if (player == null || host.difficulty() == 0 || !host.gameRule("doMobSpawning")) return 0;
        int pcx = (int) Math.floor(player.x) >> 4, pcz = (int) Math.floor(player.z) >> 4;
        List<int[]> eligible = new ArrayList<>();
        int counted = 0;
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                counted++;
                boolean edge = dx == -8 || dx == 8 || dz == -8 || dz == 8;
                Chunk chunk = world.generatedChunk(pcx + dx, pcz + dz);
                if (!edge && chunk != null && chunk.isGenerated()) eligible.add(new int[] {pcx + dx, pcz + dz});
            }
        int existing = 0;
        for (Entity entity : host.entities()) if (entity instanceof net.minecraft.entity.monster.IMob && !entity.isRemoved()) existing++;
        int cap = (int) (70 * counted / 289 * implementedShare(world));
        if (existing > cap) return 0;
        Collections.shuffle(eligible, rand);
        int total = 0;
        chunks:
        for (int[] c : eligible) {
            Chunk chunk = world.generatedChunk(c[0], c[1]);
            int x = c[0] * 16 + rand.nextInt(16), z = c[1] * 16 + rand.nextInt(16);
            int height = roundUp(height(world, chunk, x & 15, z & 15) + 1, 16);
            int y = rand.nextInt(height > 0 ? height : topSegment(chunk) + 16 - 1);
            if (y < 0 || y >= World.HEIGHT || world.get(x, y, z).isNormalCube()) continue;
            int spawned = 0;
            for (int pack = 0; pack < 3; pack++) {
                int px = x, py = y, pz = z;
                SpawnEntry entry = null;
                for (int attempt = 0; attempt < 4; attempt++) {
                    px += rand.nextInt(6) - rand.nextInt(6);
                    py += rand.nextInt(1) - rand.nextInt(1);
                    pz += rand.nextInt(6) - rand.nextInt(6);
                    float fx = px + 0.5f, fz = pz + 0.5f;
                    double pdx = player.x - fx, pdy = player.y - py, pdz = player.z - fz;
                    if (pdx * pdx + pdy * pdy + pdz * pdz < 576.0) continue;
                    // The world spawn counts in the Nether too (its world info is the overworld's).
                    double sdx = spawnX - fx, sdy = spawnY - py, sdz = spawnZ - fz;
                    if (sdx * sdx + sdy * sdy + sdz * sdz < 576.0) continue;
                    if (entry == null) {
                        List<SpawnEntry> list = monsters(world, px, py, pz);
                        if (list.isEmpty()) break;
                        entry = pick(list, rand);
                    }
                    if (!monsters(world, px, py, pz).contains(entry) || !canSpawnOnGround(world, px, py, pz)) continue;
                    if (!monsterExists(entry.entity())) break;
                    MovingEntity created = net.minecraft.entity.EntityList.create(entry.entity(), world, fx, py, fz);
                    if (!(created instanceof EntityLiving mob)) break;
                    mob.setHost(host);
                    mob.setPositionAndRotation(fx, py, fz, rand.nextFloat() * 360f, 0f);
                    mob.renderYawOffset = mob.rotationYawHead = mob.rotationYaw;
                    if (canMobSpawnHere(world, host, rand, mob) && isNotColliding(world, host, mob)) {
                        mob.onInitialSpawn();
                        spawned++;
                        total++;
                        host.spawn(mob);
                    }
                    int max = mob instanceof net.minecraft.entity.monster.EntityGhast ? 1 : 4;
                    if (spawned >= max) continue chunks;
                }
            }
        }
        return total;
    }

    /** Each monster's getCanSpawnHere. */
    public static boolean canMobSpawnHere(World world, EntityHost host, Random rand, EntityLiving mob) {
        if (host.difficulty() == 0) return false;
        if (mob instanceof net.minecraft.entity.monster.EntityGhast) return rand.nextInt(20) == 0;
        if (mob instanceof net.minecraft.entity.monster.EntityPigZombie || mob instanceof net.minecraft.entity.monster.EntityMagmaCube)
            return true;
        int bx = (int) Math.floor(mob.x()), by = (int) Math.floor(mob.boundingBox().minY()), bz = (int) Math.floor(mob.z());
        if (by < 0 || by >= World.HEIGHT) return false;
        if (!(mob instanceof net.minecraft.entity.monster.EntityBlaze) && !isValidLightLevel(world, host, rand, bx, by, bz)) return false;
        return mob.blockPathWeight(bx, by, bz) >= 0f;
    }

    /** EntityMob.isValidLightLevel: sky light below a random 0-31 and light below a random 0-7. */
    private static boolean isValidLightLevel(World world, EntityHost host, Random rand, int bx, int by, int bz) {
        if (world.getSkyLight(bx, by, bz) > rand.nextInt(32)) return false;
        int light = lightFromNeighbors(world, host.worldTime(), bx, by, bz);
        return light <= rand.nextInt(8);
    }

    // ---- animals --------------------------------------------------------------------------------

    /** One line of a biome's spawn list (SpawnListEntry): entity id, weight and group size. */
    public record SpawnEntry(String entity, int weight, int min, int max) {}

    private static final List<SpawnEntry> FARM = List.of(new SpawnEntry("Sheep", 12, 4, 4), new SpawnEntry("Pig", 10, 4, 4),
        new SpawnEntry("Chicken", 10, 4, 4), new SpawnEntry("Cow", 8, 4, 4));

    /**
     * The biome's creature list (BiomeGenBase.spawnableCreatureList, 1.8.9,
     * HYPOTHESIS: recalled): sheep, pigs, chickens and cows almost
     * everywhere; plains and savannas add horses, forests wolves (flower
     * forests rabbits), taigas wolves and rabbits, jungles more chickens;
     * oceans, rivers, beaches, deserts, snowy plains and mesas have none and
     * mushroom islands only mooshrooms. Entries for animals LegacyCraft
     * doesn't have yet keep their weight but spawn nothing.
     */
    public static List<SpawnEntry> creatures(int biomeId) {
        int base = biomeId >= 128 ? biomeId - 128 : biomeId;
        List<SpawnEntry> list = new ArrayList<>();
        switch (base) {
            case 0, 10, 24, 7, 11, 16, 26, 25, 2, 17, 8, 9, 12, 13, 37, 38, 39 -> { return list; }
            case 14, 15 -> {
                list.add(new SpawnEntry("MushroomCow", 8, 4, 8));
                return list;
            }
            default -> list.addAll(FARM);
        }
        switch (base) {
            case 1 -> list.add(new SpawnEntry("EntityHorse", 5, 2, 6));
            case 35, 36 -> list.add(new SpawnEntry("EntityHorse", 1, 2, 6));
            case 4, 18 -> {
                if (biomeId == 132) list.add(new SpawnEntry("Rabbit", 4, 2, 3));
                else list.add(new SpawnEntry("Wolf", 5, 4, 4));
            }
            case 5, 19, 30, 31, 32, 33 -> {
                list.add(new SpawnEntry("Wolf", 8, 4, 4));
                list.add(new SpawnEntry("Rabbit", 4, 2, 3));
            }
            case 21, 22, 23 -> list.add(new SpawnEntry("Chicken", 10, 4, 4));
            default -> { }
        }
        return list;
    }

    /** The animals LegacyCraft has. */
    public static boolean exists(String entity) {
        return entity.equals("Sheep") || entity.equals("Pig") || entity.equals("Chicken") || entity.equals("Cow");
    }

    /** WeightedRandom.getRandomItem. */
    public static SpawnEntry pick(List<SpawnEntry> list, Random rand) {
        int total = 0;
        for (SpawnEntry entry : list) total += entry.weight();
        int i = rand.nextInt(total);
        for (SpawnEntry entry : list) {
            i -= entry.weight();
            if (i < 0) return entry;
        }
        return list.get(list.size() - 1);
    }

    /**
     * One tick of animal spawning (findChunksForSpawning for CREATURE): only
     * every 400 ticks, with a cap of 10 animals for the 17x17 chunks; like
     * monsters, but each pack draws from the biome's creature list, and an
     * animal needs grass under it and light above 8 (sky light counted at
     * full day, EntityAnimal.getCanSpawnHere). Peaceful doesn't stop it.
     */
    public static int spawnCreatures(World world, EntityHost host, Random rand, long totalTime, int spawnX, int spawnY, int spawnZ) {
        Player player = host.player();
        if (player == null || totalTime % 400L != 0L || !host.gameRule("doMobSpawning")) return 0;
        int pcx = (int) Math.floor(player.x) >> 4, pcz = (int) Math.floor(player.z) >> 4;
        List<int[]> eligible = new ArrayList<>();
        int counted = 0;
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                counted++;
                boolean edge = dx == -8 || dx == 8 || dz == -8 || dz == 8;
                Chunk chunk = world.generatedChunk(pcx + dx, pcz + dz);
                if (!edge && chunk != null && chunk.isGenerated()) eligible.add(new int[] {pcx + dx, pcz + dz});
            }
        int existing = 0;
        for (Entity entity : host.entities())
            if (entity instanceof net.minecraft.entity.passive.EntityAnimal && !entity.isRemoved()) existing++;
        if (existing > 10 * counted / 289) return 0;
        Collections.shuffle(eligible, rand);
        int total = 0;
        chunks:
        for (int[] c : eligible) {
            Chunk chunk = world.generatedChunk(c[0], c[1]);
            int x = c[0] * 16 + rand.nextInt(16), z = c[1] * 16 + rand.nextInt(16);
            int height = roundUp(height(world, chunk, x & 15, z & 15) + 1, 16);
            int y = rand.nextInt(height > 0 ? height : topSegment(chunk) + 16 - 1);
            if (y < 0 || y >= World.HEIGHT || world.get(x, y, z).isNormalCube()) continue;
            int spawned = 0;
            for (int pack = 0; pack < 3; pack++) {
                int px = x, py = y, pz = z;
                SpawnEntry entry = null;
                for (int attempt = 0; attempt < 4; attempt++) {
                    px += rand.nextInt(6) - rand.nextInt(6);
                    py += rand.nextInt(1) - rand.nextInt(1);
                    pz += rand.nextInt(6) - rand.nextInt(6);
                    float fx = px + 0.5f, fz = pz + 0.5f;
                    double pdx = player.x - fx, pdy = player.y - py, pdz = player.z - fz;
                    if (pdx * pdx + pdy * pdy + pdz * pdz < 576.0) continue;
                    double sdx = spawnX - fx, sdy = spawnY - py, sdz = spawnZ - fz;
                    if (sdx * sdx + sdy * sdy + sdz * sdz < 576.0) continue;
                    if (entry == null) {
                        List<SpawnEntry> list = creatures(world.biomeAt(px, pz).id());
                        if (list.isEmpty()) break;
                        entry = pick(list, rand);
                    }
                    if (!creatures(world.biomeAt(px, pz).id()).contains(entry) || !canSpawnOnGround(world, px, py, pz)) continue;
                    if (!exists(entry.entity())) break;
                    MovingEntity created = net.minecraft.entity.EntityList.create(entry.entity(), world, fx, py, fz);
                    if (!(created instanceof EntityLiving mob)) break;
                    mob.setHost(host);
                    mob.setPositionAndRotation(fx, py, fz, rand.nextFloat() * 360f, 0f);
                    mob.renderYawOffset = mob.rotationYawHead = mob.rotationYaw;
                    if (canAnimalSpawnHere(world, mob) && isNotColliding(world, host, mob)) {
                        mob.onInitialSpawn();
                        spawned++;
                        total++;
                        host.spawn(mob);
                    }
                    if (spawned >= 4) continue chunks;
                }
            }
        }
        return total;
    }

    /** EntityAnimal.getCanSpawnHere: grass below, light above 8 (sky light at full day), a spot it likes. */
    private static boolean canAnimalSpawnHere(World world, EntityLiving mob) {
        int bx = (int) Math.floor(mob.x()), by = (int) Math.floor(mob.boundingBox().minY()), bz = (int) Math.floor(mob.z());
        if (by < 1 || by >= World.HEIGHT) return false;
        if (!world.get(bx, by - 1, bz).name().path().equals("grass")) return false;
        if (Math.max(world.getSkyLight(bx, by, bz), world.getBlockLight(bx, by, bz)) <= 8) return false;
        return mob.blockPathWeight(bx, by, bz) >= 0f;
    }

    /** The chunk's height map: one above the highest block that dims light (Chunk.getHeight). */
    private static int height(World world, Chunk chunk, int lx, int lz) {
        for (int s = Chunk.HEIGHT / ChunkSection.SIZE - 1; s >= 0; s--) {
            ChunkSection section = chunk.section(s);
            if (section == null || section.isEmpty()) continue;
            for (int y = s * 16 + 15; y >= s * 16; y--) {
                if (chunk.get(lx, y, lz) == 0) continue;
                Block block = world.get(chunk.chunkX() * 16 + lx, y, chunk.chunkZ() * 16 + lz);
                if (block.lightOpacity() > 0) return y + 1;
            }
        }
        return 0;
    }

    private static int topSegment(Chunk chunk) {
        for (int s = Chunk.HEIGHT / ChunkSection.SIZE - 1; s >= 0; s--) {
            ChunkSection section = chunk.section(s);
            if (section != null && !section.isEmpty()) return s * 16;
        }
        return 0;
    }

    private static int roundUp(int value, int multiple) {
        int remainder = value % multiple;
        return remainder == 0 ? value : value + multiple - remainder;
    }

    /**
     * SpawnerAnimals.canCreatureTypeSpawnAtLocation (on the ground): a solid
     * top below (not bedrock or barrier), and no full cube or liquid here or
     * a full cube above.
     */
    public static boolean canSpawnOnGround(World world, int x, int y, int z) {
        if (y < 1 || y >= World.HEIGHT - 1) return false;
        BlockState below = world.getState(x, y - 1, z);
        if (!Block.hasSolidTop(below)) return false;
        String name = below.block().name().path();
        if (name.equals("bedrock") || name.equals("barrier")) return false;
        Block here = world.get(x, y, z);
        return !here.isNormalCube() && !net.minecraft.block.Liquids.isLiquid(here) && !world.get(x, y + 1, z).isNormalCube();
    }

    /** EntityLiving.isNotColliding: no other mob or lit TNT, block or liquid in its box. */
    public static boolean isNotColliding(World world, EntityHost host, MovingEntity mob) {
        AxisAlignedBB box = mob.boundingBox();
        for (Entity entity : host.entities()) {
            if (entity == mob || entity.isRemoved()) continue;
            if ((entity instanceof EntityLiving || entity instanceof EntityTNTPrimed)
                && ((MovingEntity) entity).boundingBox().intersects(box)) return false;
        }
        Player player = host.player();
        if (player != null && !player.dead && player.boundingBox().intersects(box)) return false;
        return mob.isFreeOfBlocks(box) && !world.isMaterialInBox(box, "water") && !world.isMaterialInBox(box, "lava");
    }
}

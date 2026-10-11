package net.minecraft.benchmark;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.entity.Player;
import net.minecraft.client.renderer.SnapshotProbe;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHost;
import net.minecraft.entity.MovingEntity;
import net.minecraft.util.StateHash;
import net.minecraft.world.MobSpawner;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.chunk.Chunk;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Headless, repeatable engine benchmarks (docs/performance-benchmarks.md).
 * Each scenario runs warm-up rounds, then measured rounds, and prints the
 * median, best and 90th-percentile round time, the median CPU time of the
 * benchmark thread (steadier than wall time on a shared machine), the bytes the thread
 * allocated per round, the collector activity during the measured rounds,
 * and a checksum of the result so a change that alters world data shows up.
 * Run one scenario per JVM: {@code gradlew benchmark -Pbench=worldgen}.
 */
public final class EngineBenchmark {
    private static final long SEED = 12345L;
    private static final ClasspathResourceManager RESOURCES = new ClasspathResourceManager();
    private static final BlockRegistry BLOCKS = BlockRegistry.load(RESOURCES);
    private static final BiomeRegistry BIOMES = BiomeRegistry.load(RESOURCES, BLOCKS);

    private interface Round { long run(int round); }

    public static void main(String[] args) {
        String which = args.length == 0 ? "all" : args[0];
        List<String> names = which.equals("all") ? List.of("worldgen", "snapshot", "blockaccess", "relight", "mobs") : List.of(which.split(","));
        for (String name : names) {
            switch (name) {
                case "worldgen" -> measure(name, 2, 6, EngineBenchmark::worldgen);
                case "snapshot" -> { World world = generated(9); measure(name, 5, 15, round -> snapshots(world, 8)); }
                case "blockaccess" -> { World world = generated(8); measure(name, 5, 15, round -> blockAccess(world, round)); }
                case "relight" -> { World world = generated(6); measure(name, 6, 20, round -> relight(world, round)); }
                case "mobs" -> { World world = generated(12); measure(name, 6, 16, round -> mobs(world, round)); }
                default -> throw new IllegalArgumentException("Unknown benchmark " + name);
            }
        }
    }

    // ---- Scenarios ---------------------------------------------------------------------------------

    /** A fresh world, every chunk within radius 8 of the origin generated (populated and lit) nearest ring first. */
    private static long worldgen(int round) {
        World world = generated(8);
        long hash = 0;
        for (Chunk chunk : sorted(world)) hash = hash * 31 + StateHash.chunkHash(chunk) + Arrays.hashCode(chunk.lightData());
        return hash;
    }

    /** The main-thread copy the renderer takes before meshing, for every non-empty section within radius 8. */
    private static long snapshots(World world, int radius) {
        long sum = 0;
        for (int cz = -radius; cz <= radius; cz++)
            for (int cx = -radius; cx <= radius; cx++) {
                Chunk chunk = world.generatedChunk(cx, cz);
                for (int sy = 0; sy < Chunk.HEIGHT / 16; sy++)
                    if (chunk.section(sy) != null) sum += SnapshotProbe.snapshot(world, cx, sy, cz);
            }
        return sum;
    }

    /** Random block reads and collision-box queries over loaded terrain, as entity physics and ticking do. */
    private static long blockAccess(World world, int round) {
        Random random = new Random(round);
        long sum = 0;
        for (int i = 0; i < 2_000_000; i++) {
            BlockState state = world.getState(random.nextInt(256) - 128, random.nextInt(128), random.nextInt(256) - 128);
            sum += state.packed();
        }
        for (int i = 0; i < 200_000; i++)
            sum += Block.collisionBoxesAt(world, random.nextInt(240) - 120, 50 + random.nextInt(40), random.nextInt(240) - 120).size();
        return sum;
    }

    /** Places then removes glowstone at random spots, relighting both ways; the light field must end where it began. */
    private static long relight(World world, int round) {
        Random random = new Random(round);
        BlockState glowstone = BlockState.defaultOf(BLOCKS.get("minecraft:glowstone"));
        int[] xs = new int[300], ys = new int[300], zs = new int[300];
        BlockState[] old = new BlockState[300];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = random.nextInt(160) - 80;
            ys[i] = 20 + random.nextInt(80);
            zs[i] = random.nextInt(160) - 80;
            old[i] = world.getState(xs[i], ys[i], zs[i]);
            world.setState(xs[i], ys[i], zs[i], glowstone);
        }
        for (int i = xs.length - 1; i >= 0; i--) world.setState(xs[i], ys[i], zs[i], old[i]);
        long hash = 0;
        for (Chunk chunk : sorted(world)) hash = hash * 31 + Arrays.hashCode(chunk.lightData());
        return hash;
    }

    /**
     * Spawns a night's worth of mobs around a player, then ticks every entity 100 times.
     * Mobs use unseeded randomness, so the count varies; the world is generated well past
     * the spawning range so a mob walking off the edge doesn't generate terrain mid-round.
     */
    private static long mobs(World world, int round) {
        Host host = new Host();
        host.player = new Player(world, 8.5, 8.5);
        Random random = new Random(round);
        for (int i = 0; i < 400 && host.entities.size() < 120; i++) {
            MobSpawner.spawnMonsters(world, host, random, 8, 64, 8);
            MobSpawner.spawnCreatures(world, host, random, 400L * i, 8, 64, 8);
        }
        long start = System.nanoTime();
        for (int tick = 0; tick < 100; tick++) {
            for (Entity entity : new ArrayList<>(host.entities)) if (!entity.isRemoved()) entity.tick();
            host.entities.removeIf(Entity::isRemoved);
        }
        lastMobTicks = System.nanoTime() - start;
        return host.entities.size();
    }

    private static long lastMobTicks;

    // ---- Harness -----------------------------------------------------------------------------------

    private static World generated(int radius) {
        World world = new World(BLOCKS, BIOMES, SEED);
        for (int ring = 0; ring <= radius; ring++)
            for (int dz = -ring; dz <= ring; dz++)
                for (int dx = -ring; dx <= ring; dx++)
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) world.getChunk(dx, dz);
        return world;
    }

    private static List<Chunk> sorted(World world) {
        List<Chunk> chunks = new ArrayList<>(world.loadedChunks());
        chunks.sort((a, b) -> a.chunkX() != b.chunkX() ? Integer.compare(a.chunkX(), b.chunkX()) : Integer.compare(a.chunkZ(), b.chunkZ()));
        return chunks;
    }

    private static void measure(String name, int warmup, int rounds, Round body) {
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long checksum = 0;
        for (int i = 0; i < warmup; i++) checksum = body.run(-1 - i);
        long[] times = new long[rounds], cpu = new long[rounds], allocated = new long[rounds], mobTicks = new long[rounds];
        long gcCount = gcCount(), gcTime = gcTime();
        long thread = Thread.currentThread().getId();
        for (int i = 0; i < rounds; i++) {
            long bytes = threads.getThreadAllocatedBytes(thread), cpuStart = threads.getCurrentThreadCpuTime(), start = System.nanoTime();
            checksum = body.run(i);
            times[i] = System.nanoTime() - start;
            cpu[i] = threads.getCurrentThreadCpuTime() - cpuStart;
            allocated[i] = threads.getThreadAllocatedBytes(thread) - bytes;
            mobTicks[i] = lastMobTicks;
        }
        gcCount = gcCount() - gcCount;
        gcTime = gcTime() - gcTime;
        Arrays.sort(times);
        Arrays.sort(mobTicks);
        System.out.printf(Locale.ROOT, "%-12s median %9.2f ms  best %9.2f ms  p90 %9.2f ms  cpu %9.2f ms  alloc %9.1f MB/round  gc %d (%d ms)  checksum %x%s%n",
            name, times[rounds / 2] / 1e6, times[0] / 1e6, times[(int) Math.ceil(rounds * .9) - 1] / 1e6, median(cpu) / 1e6,
            median(allocated) / 1048576.0, gcCount, gcTime, checksum,
            name.equals("mobs") ? String.format(Locale.ROOT, "  ticks %.3f ms/tick", mobTicks[rounds / 2] / 1e6 / 100) : "");
    }

    private static long median(long[] values) {
        long[] copy = values.clone();
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    private static long gcCount() {
        long count = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) count += Math.max(0, gc.getCollectionCount());
        return count;
    }

    private static long gcTime() {
        long time = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) time += Math.max(0, gc.getCollectionTime());
        return time;
    }

    /** The minimum the spawner and mobs need: one player at night, no sounds or particles. */
    private static final class Host implements EntityHost {
        final List<Entity> entities = new ArrayList<>();
        Player player;
        @Override public Player player() { return player; }
        @Override public List<Entity> entities() { return new ArrayList<>(entities); }
        @Override public void spawn(Entity entity) {
            if (entity instanceof MovingEntity moving) moving.setHost(this);
            entities.add(entity);
        }
        @Override public void playSound(String event, double x, double y, double z, float volume, float pitch) {}
        @Override public void particle(String kind, double x, double y, double z, double vx, double vy, double vz) {}
        @Override public void explode(Entity exploder, double x, double y, double z, float size, boolean smoking, String attacker) {}
        @Override public int difficulty() { return 2; }
        @Override public boolean gameRule(String name) { return true; }
        @Override public long worldTime() { return 18000; }
    }
}

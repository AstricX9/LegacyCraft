package net.minecraft.server;

import net.minecraft.entity.EntityManager;
import net.minecraft.world.World;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Authoritative single-player simulation with an explicit 20 TPS contract. */
public final class IntegratedServer implements AutoCloseable {
    public static final int TICKS_PER_SECOND = 20;
    private final World world;
    private final EntityManager entities = new EntityManager();
    private ScheduledExecutorService executor;
    private long tickCount;

    public IntegratedServer(World world) { this.world = Objects.requireNonNull(world, "world"); }
    public World world() { return world; }
    public EntityManager entities() { return entities; }
    public long tickCount() { return tickCount; }
    public synchronized void tick() { entities.tick(); tickCount++; }
    public synchronized void start() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "LegacyCraft Server");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::tick, 0, 1000 / TICKS_PER_SECOND, TimeUnit.MILLISECONDS);
    }
    public synchronized boolean isRunning() { return executor != null; }
    @Override public synchronized void close() {
        if (executor != null) { executor.shutdownNow(); executor = null; }
    }
}

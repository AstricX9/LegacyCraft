package net.minecraft.entity;

import net.minecraft.world.World;

import java.util.Objects;
import java.util.UUID;

/** Server-owned world object with stable identity and lifecycle. */
public abstract class Entity {
    private final UUID uuid;
    protected final World world;
    protected double x, y, z;
    private boolean removed;

    protected Entity(World world, UUID uuid, double x, double y, double z) {
        this.world = Objects.requireNonNull(world, "world");
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        setPosition(x, y, z);
    }
    protected Entity(World world, double x, double y, double z) { this(world, UUID.randomUUID(), x, y, z); }
    public UUID uuid() { return uuid; }
    public World world() { return world; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public void setPosition(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Entity position must be finite");
        this.x = x; this.y = y; this.z = z;
    }
    public boolean isRemoved() { return removed; }
    public void remove() { removed = true; }
    public abstract void tick();
}

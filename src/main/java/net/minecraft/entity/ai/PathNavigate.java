package net.minecraft.entity.ai;

import net.minecraft.entity.EntityLiving;
import net.minecraft.world.World;

/**
 * Path following on the ground (1.8.9 PathNavigateGround, HYPOTHESIS:
 * recalled): finds a path within the entity's follow range, moves to each
 * point through the move helper, skips ahead when the entity is within its
 * own width of a point or can walk straight to a later point at the same
 * height, and gives up when it has moved less than 1.5 blocks in 100 ticks.
 */
public final class PathNavigate {
    private final EntityLiving entity;
    private final World world;
    private PathFinder.Path path;
    private double speed;
    private int totalTicks, ticksAtLastPos;
    private double lastX, lastY, lastZ;
    private boolean avoidsWater, canSwim;

    public PathNavigate(EntityLiving entity, World world) {
        this.entity = entity;
        this.world = world;
    }

    public void setAvoidsWater(boolean avoidsWater) { this.avoidsWater = avoidsWater; }
    public void setCanSwim(boolean canSwim) { this.canSwim = canSwim; }

    public PathFinder.Path pathTo(double x, double y, double z) {
        if (!entity.onGround && !entity.isInWater() && !entity.isInLava()) return null;
        return new PathFinder(world, entity, avoidsWater, canSwim).find(x, y, z, entity.followRange());
    }

    public PathFinder.Path pathToEntity(double x, double minY, double z) { return pathTo(x, minY, z); }

    public boolean tryMoveTo(double x, double y, double z, double speed) { return setPath(pathTo(x, y, z), speed); }

    public boolean setPath(PathFinder.Path newPath, double speed) {
        if (newPath == null) {
            path = null;
            return false;
        }
        if (!newPath.sameDestination(path)) path = newPath;
        if (path.points.isEmpty()) return false;
        this.speed = speed;
        ticksAtLastPos = totalTicks;
        lastX = entity.x(); lastY = pathableY(); lastZ = entity.z();
        return true;
    }

    public PathFinder.Path path() { return path; }
    public boolean noPath() { return path == null || path.isFinished(); }
    public void clearPath() { path = null; }

    /** PathNavigate.onUpdateNavigation: follow the path and aim the move helper at the current point. */
    public void tick() {
        totalTicks++;
        if (noPath()) return;
        if (entity.onGround || entity.isInWater() || entity.isInLava()) pathFollow();
        if (noPath()) return;
        double[] target = path.vector(entity, path.index);
        entity.moveHelper().setMoveTo(target[0], target[1], target[2], speed);
    }

    private double pathableY() {
        if (entity.isInWater() && canSwim) {
            int y = (int) entity.boundingBox().minY();
            int bx = (int) Math.floor(entity.x()), bz = (int) Math.floor(entity.z());
            int guard = 0;
            while (y + 1 < World.HEIGHT && world.get(bx, y, bz).material().equals("water") && guard++ < 16) y++;
            return y;
        }
        return (int) Math.floor(entity.boundingBox().minY() + 0.5);
    }

    private void pathFollow() {
        double ex = entity.x(), ey = pathableY(), ez = entity.z();
        int end = path.points.size();
        for (int i = path.index; i < path.points.size(); i++) {
            if (path.points.get(i).y != (int) ey) {
                end = i;
                break;
            }
        }
        float reach = entity.width * entity.width;
        for (int i = path.index; i < end; i++) {
            double[] v = path.vector(entity, i);
            double dx = v[0] - ex, dy = v[1] - ey, dz = v[2] - ez;
            if (dx * dx + dy * dy + dz * dz < reach) path.index = i + 1;
        }
        for (int i = end - 1; i >= path.index; i--) {
            double[] v = path.vector(entity, i);
            if (directPath(ex, ey, ez, v[0], v[2])) {
                path.index = i;
                break;
            }
        }
        if (totalTicks - ticksAtLastPos > 100) {
            double dx = ex - lastX, dy = ey - lastY, dz = ez - lastZ;
            if (dx * dx + dy * dy + dz * dz < 2.25) clearPath();
            ticksAtLastPos = totalTicks;
            lastX = ex; lastY = ey; lastZ = ez;
        }
    }

    /**
     * PathNavigateGround.isDirectPathBetweenPoints, simplified: every half
     * block along the line has room for the entity at this height and
     * something safe to stand on (no drop, no lava, water only for swimmers).
     */
    private boolean directPath(double x0, double y, double z0, double x1, double z1) {
        double dx = x1 - x0, dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-8) return false;
        int sizeX = (int) Math.ceil(entity.width), sizeY = (int) entity.height + 1;
        int by = (int) y;
        for (double t = 0; t <= length; t += 0.5) {
            double px = x0 + dx * t / length, pz = z0 + dz * t / length;
            int bx = (int) Math.floor(px - sizeX / 2.0 + 0.5), bz = (int) Math.floor(pz - sizeX / 2.0 + 0.5);
            for (int ix = bx; ix < bx + sizeX; ix++)
                for (int iz = bz; iz < bz + sizeX; iz++) {
                    if (world.generatedChunk(ix >> 4, iz >> 4) == null || by < 1 || by + sizeY >= World.HEIGHT) return false;
                    String below = world.get(ix, by - 1, iz).material();
                    if (below.equals("air") || below.equals("lava") || below.equals("water") && !entity.isInWater()) return false;
                    for (int iy = by; iy < by + sizeY; iy++)
                        if (!net.minecraft.block.Block.collisionBoxesAt(world, ix, iy, iz).isEmpty()) return false;
                }
        }
        return true;
    }
}

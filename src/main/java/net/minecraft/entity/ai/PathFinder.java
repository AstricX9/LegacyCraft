package net.minecraft.entity.ai;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Ground pathfinding (1.8.9 PathFinder + WalkNodeProcessor, HYPOTHESIS:
 * recalled rules): A* over block positions for an entity
 * {@code floor(width + 1)} blocks wide and {@code floor(height + 1)} tall,
 * stepping to the four horizontal neighbours, climbing one block at a time
 * and dropping at most {@link EntityLiving#maxFallHeight()} blocks, never
 * into lava, through fences and walls or into water when the entity avoids
 * it. Points are only explored within {@code range} of the target, and the
 * path leads to the point nearest the target when the target itself can't
 * be reached.
 */
public final class PathFinder {
    /** One step of a path, the block the entity's (north-west) feet corner stands in. */
    public static final class Point {
        public final int x, y, z;
        float totalDistance, distanceToNext, distanceToTarget;
        Point previous;
        boolean closed;

        Point(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }

        float distanceTo(Point other) {
            float dx = other.x - x, dy = other.y - y, dz = other.z - z;
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        @Override public String toString() { return x + ", " + y + ", " + z; }
    }

    /** A found path, followed from {@link #index}. */
    public static final class Path {
        public final List<Point> points;
        public int index;

        Path(List<Point> points) { this.points = points; }

        public boolean isFinished() { return index >= points.size(); }
        public Point last() { return points.isEmpty() ? null : points.get(points.size() - 1); }

        /** Where the entity aims for the point: its middle for one-block-wide entities (Path.getVectorFromIndex). */
        public double[] vector(EntityLiving entity, int i) {
            Point point = points.get(i);
            double offset = (int) (entity.width + 1f) * 0.5;
            return new double[] {point.x + offset, point.y, point.z + offset};
        }

        public boolean sameDestination(Path other) {
            if (other == null || other.points.size() != points.size()) return false;
            for (int i = 0; i < points.size(); i++) {
                Point a = points.get(i), b = other.points.get(i);
                if (a.x != b.x || a.y != b.y || a.z != b.z) return false;
            }
            return true;
        }
    }

    /** WalkNodeProcessor's answers: 1 open, 2 open water, 0 solid, -1 water to avoid, -2 lava, -3 fence. */
    private static final int OPEN = 1, SWIM = 2, BLOCKED = 0, WATER = -1, LAVA = -2, FENCE = -3;

    private final World world;
    private final EntityLiving entity;
    private final int sizeX, sizeY, sizeZ;
    private final boolean avoidsWater, canSwim;
    private final Map<Long, Point> points = new HashMap<>();

    public PathFinder(World world, EntityLiving entity, boolean avoidsWater, boolean canSwim) {
        this.world = world;
        this.entity = entity;
        sizeX = (int) Math.floor(entity.width + 1f);
        sizeY = (int) Math.floor(entity.height + 1f);
        sizeZ = sizeX;
        this.avoidsWater = avoidsWater;
        this.canSwim = canSwim;
    }

    /** A path from the entity to (tx, ty, tz) within {@code range}, or null. */
    public Path find(double tx, double ty, double tz, float range) {
        points.clear();
        var box = entity.boundingBox();
        int startY = (int) Math.floor(box.minY() + 0.5);
        if (canSwim && entity.isInWater()) {
            // PathNavigateGround.getPathablePosY: a swimmer starts from the water's surface.
            startY = (int) box.minY();
            int bx = (int) Math.floor(entity.x()), bz = (int) Math.floor(entity.z());
            while (startY + 1 < World.HEIGHT && world.get(bx, startY, bz).material().equals("water")) startY++;
        }
        Point start = point((int) Math.floor(box.minX()), startY, (int) Math.floor(box.minZ()));
        Point target = point((int) Math.floor(tx - entity.width / 2f), (int) Math.floor(ty), (int) Math.floor(tz - entity.width / 2f));
        return search(start, target, range);
    }

    private Path search(Point start, Point target, float range) {
        start.totalDistance = 0;
        start.distanceToNext = start.distanceTo(target);
        start.distanceToTarget = start.distanceToNext;
        PriorityQueue<Point> open = new PriorityQueue<>((a, b) -> Float.compare(a.distanceToTarget, b.distanceToTarget));
        open.add(start);
        Point closest = start;
        int visits = 0;
        while (!open.isEmpty() && visits++ < 2000) {
            Point current = open.poll();
            if (current.closed) continue;
            if (current.x == target.x && current.y == target.y && current.z == target.z) return path(current);
            if (current.distanceTo(target) < closest.distanceTo(target)) closest = current;
            current.closed = true;
            for (Point option : options(current, target, range)) {
                float total = current.totalDistance + current.distanceTo(option);
                if (option.previous != null && total >= option.totalDistance) continue;
                option.previous = current;
                option.totalDistance = total;
                option.distanceToNext = option.distanceTo(target);
                option.distanceToTarget = option.totalDistance + option.distanceToNext;
                open.add(option);
            }
        }
        return closest == start ? null : path(closest);
    }

    private List<Point> options(Point current, Point target, float range) {
        List<Point> out = new ArrayList<>(4);
        int jump = verticalOffset(current.x, current.y + 1, current.z) == OPEN ? 1 : 0;
        int[][] steps = {{0, 1}, {-1, 0}, {1, 0}, {0, -1}};
        for (int[] step : steps) {
            Point next = safePoint(current.x + step[0], current.y, current.z + step[1], jump);
            if (next != null && !next.closed && next.distanceTo(target) < range) out.add(next);
        }
        return out;
    }

    /** WalkNodeProcessor.getSafePoint: stand here, or one up, then fall to the ground. */
    private Point safePoint(int x, int y, int z, int jump) {
        Point found = null;
        int here = verticalOffset(x, y, z);
        if (here == SWIM) return point(x, y, z);
        if (here == OPEN) found = point(x, y, z);
        if (found == null && jump > 0 && here != FENCE && verticalOffset(x, y + jump, z) == OPEN) {
            found = point(x, y + jump, z);
            y += jump;
        }
        if (found == null) return null;
        int fallen = 0, below = 0;
        while (y > 0) {
            below = verticalOffset(x, y - 1, z);
            if (avoidsWater && below == WATER) return null;
            if (below != OPEN) break;
            if (fallen++ >= entity.maxFallHeight()) return null;
            y--;
            if (y <= 0) return null;
            found = point(x, y, z);
        }
        if (below == LAVA) return null;
        return found;
    }

    /** What the entity-sized space at (x, y, z) is: open, solid, water, lava or a fence. */
    private int verticalOffset(int x, int y, int z) {
        boolean water = false;
        for (int bx = x; bx < x + sizeX; bx++)
            for (int by = y; by < y + sizeY; by++)
                for (int bz = z; bz < z + sizeZ; bz++) {
                    if (by < 0) return BLOCKED;
                    if (by >= World.HEIGHT) continue;
                    if (world.generatedChunk(bx >> 4, bz >> 4) == null) return BLOCKED;
                    BlockState state = world.getState(bx, by, bz);
                    Block block = state.block();
                    if (block.isAir()) continue;
                    String material = block.material();
                    if (material.equals("water")) {
                        if (avoidsWater) return WATER;
                        water = true;
                        continue;
                    }
                    if (material.equals("lava")) return LAVA;
                    if (block instanceof net.minecraft.block.BlockFence || block instanceof net.minecraft.block.BlockWall
                        || block instanceof net.minecraft.block.BlockFenceGate && (state.metadata() & 4) == 0) return FENCE;
                    if (!Block.collisionBoxesAt(world, bx, by, bz).isEmpty()) return BLOCKED;
                }
        return water ? SWIM : OPEN;
    }

    private Point point(int x, int y, int z) {
        long key = ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
        return points.computeIfAbsent(key, k -> new Point(x, y, z));
    }

    private static Path path(Point end) {
        List<Point> list = new ArrayList<>();
        for (Point p = end; p != null; p = p.previous) list.add(0, p);
        return new Path(list);
    }
}

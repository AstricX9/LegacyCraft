package net.minecraft.util;

/** Immutable axis-aligned box with the 1.8 collision-clipping helpers. */
public record AxisAlignedBB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public static final AxisAlignedBB FULL = new AxisAlignedBB(0, 0, 0, 1, 1, 1);

    /** A box given in sixteenths of a block, the unit block shapes are authored in. */
    public static AxisAlignedBB sixteenths(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new AxisAlignedBB(minX / 16, minY / 16, minZ / 16, maxX / 16, maxY / 16, maxZ / 16);
    }

    public AxisAlignedBB offset(double x, double y, double z) {
        return new AxisAlignedBB(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }

    /** Grows the box in the direction of a movement vector (for gathering what a move could touch). */
    public AxisAlignedBB addCoord(double x, double y, double z) {
        return new AxisAlignedBB(x < 0 ? minX + x : minX, y < 0 ? minY + y : minY, z < 0 ? minZ + z : minZ,
            x > 0 ? maxX + x : maxX, y > 0 ? maxY + y : maxY, z > 0 ? maxZ + z : maxZ);
    }

    public AxisAlignedBB union(AxisAlignedBB other) {
        return new AxisAlignedBB(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
            Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    public boolean intersects(AxisAlignedBB other) {
        return other.maxX > minX && other.minX < maxX && other.maxY > minY && other.minY < maxY
            && other.maxZ > minZ && other.minZ < maxZ;
    }

    /** How far {@code mover} may travel along X by {@code offset} before touching this box. */
    public double clipX(AxisAlignedBB mover, double offset) {
        if (mover.maxY <= minY || mover.minY >= maxY || mover.maxZ <= minZ || mover.minZ >= maxZ) return offset;
        if (offset > 0 && mover.maxX <= minX) return Math.min(offset, minX - mover.maxX);
        if (offset < 0 && mover.minX >= maxX) return Math.max(offset, maxX - mover.minX);
        return offset;
    }

    public double clipY(AxisAlignedBB mover, double offset) {
        if (mover.maxX <= minX || mover.minX >= maxX || mover.maxZ <= minZ || mover.minZ >= maxZ) return offset;
        if (offset > 0 && mover.maxY <= minY) return Math.min(offset, minY - mover.maxY);
        if (offset < 0 && mover.minY >= maxY) return Math.max(offset, maxY - mover.minY);
        return offset;
    }

    public double clipZ(AxisAlignedBB mover, double offset) {
        if (mover.maxX <= minX || mover.minX >= maxX || mover.maxY <= minY || mover.minY >= maxY) return offset;
        if (offset > 0 && mover.maxZ <= minZ) return Math.min(offset, minZ - mover.maxZ);
        if (offset < 0 && mover.minZ >= maxZ) return Math.max(offset, maxZ - mover.minZ);
        return offset;
    }

    /**
     * Where a ray enters this box: the distance along the (unnormalised)
     * direction and the face it enters through, or {@code null} if it misses
     * or starts inside.
     */
    public RayHit rayEntry(double ox, double oy, double oz, double dx, double dy, double dz) {
        double[] origin = {ox, oy, oz}, direction = {dx, dy, dz};
        double[] min = {minX, minY, minZ}, max = {maxX, maxY, maxZ};
        double enter = Double.NEGATIVE_INFINITY, exit = Double.POSITIVE_INFINITY;
        int axis = -1;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(direction[i]) < 1e-12) {
                if (origin[i] < min[i] || origin[i] > max[i]) return null;
                continue;
            }
            double t0 = (min[i] - origin[i]) / direction[i], t1 = (max[i] - origin[i]) / direction[i];
            double near = Math.min(t0, t1), far = Math.max(t0, t1);
            if (near > enter) { enter = near; axis = i; }
            exit = Math.min(exit, far);
        }
        if (axis < 0 || enter > exit || enter < 0) return null;
        Direction face = switch (axis) {
            case 0 -> dx > 0 ? Direction.WEST : Direction.EAST;
            case 1 -> dy > 0 ? Direction.DOWN : Direction.UP;
            default -> dz > 0 ? Direction.NORTH : Direction.SOUTH;
        };
        return new RayHit(enter, face);
    }

    public record RayHit(double distance, Direction face) {}
}

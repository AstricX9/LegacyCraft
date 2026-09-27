package net.minecraft.util;

import java.util.Locale;

public enum Direction {
    DOWN(0,-1,0), UP(0,1,0), NORTH(0,0,-1), SOUTH(0,0,1), WEST(-1,0,0), EAST(1,0,0);

    /** Horizontal directions in the 1.8 iteration order used by neighbour scans. */
    public static final Direction[] HORIZONTALS = {NORTH, EAST, SOUTH, WEST};

    public final int xOffset, yOffset, zOffset;
    Direction(int xOffset, int yOffset, int zOffset) {
        this.xOffset = xOffset; this.yOffset = yOffset; this.zOffset = zOffset;
    }

    public static Direction fromName(String name) { return valueOf(name.toUpperCase(Locale.ROOT)); }
    public String getName() { return name().toLowerCase(Locale.ROOT); }

    public Direction opposite() {
        return switch (this) {
            case DOWN -> UP; case UP -> DOWN; case NORTH -> SOUTH;
            case SOUTH -> NORTH; case WEST -> EAST; case EAST -> WEST;
        };
    }

    public boolean isHorizontal() { return yOffset == 0; }
    /** 'x', 'y' or 'z'. */
    public char axis() { return xOffset != 0 ? 'x' : yOffset != 0 ? 'y' : 'z'; }

    /** One quarter turn clockwise seen from above (north to east); vertical directions are unchanged. */
    public Direction rotateY() { return rotateY(90); }
    /** One quarter turn counter-clockwise seen from above (north to west). */
    public Direction rotateYCCW() { return rotateY(270); }

    public Direction rotateY(int degrees) {
        int turns = Math.floorMod(degrees / 90, 4);
        Direction result = this;
        for (int i = 0; i < turns; i++) result = switch (result) {
            case NORTH -> EAST; case EAST -> SOUTH; case SOUTH -> WEST; case WEST -> NORTH;
            default -> result;
        };
        return result;
    }

    /** The direction whose unit vector is closest to the given (non-zero) vector. */
    public static Direction nearest(double x, double y, double z) {
        Direction best = NORTH;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Direction direction : values()) {
            double dot = x * direction.xOffset + y * direction.yOffset + z * direction.zOffset;
            if (dot > bestDot) { bestDot = dot; best = direction; }
        }
        return best;
    }
}

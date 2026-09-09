package net.minecraft.util;

public enum Direction {
    DOWN(0,-1,0), UP(0,1,0), NORTH(0,0,-1), SOUTH(0,0,1), WEST(-1,0,0), EAST(1,0,0);

    public final int xOffset, yOffset, zOffset;
    Direction(int xOffset, int yOffset, int zOffset) {
        this.xOffset = xOffset; this.yOffset = yOffset; this.zOffset = zOffset;
    }

    public static Direction fromName(String name) { return valueOf(name.toUpperCase()); }

    public Direction rotateY(int degrees) {
        int turns = Math.floorMod(degrees / 90, 4);
        Direction result = this;
        for (int i = 0; i < turns; i++) result = switch (result) {
            case NORTH -> EAST; case EAST -> SOUTH; case SOUTH -> WEST; case WEST -> NORTH;
            default -> result;
        };
        return result;
    }
}

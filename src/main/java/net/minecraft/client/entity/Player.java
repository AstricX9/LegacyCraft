package net.minecraft.client.entity;

import net.minecraft.world.World;
import static org.lwjgl.glfw.GLFW.*;

public final class Player {
    public double x, y, z;
    public double velocityY;
    public float yaw = 25.0f;
    public float pitch = 8.0f;
    public boolean onGround;
    private final World world;

    public Player(World world) {
        this.world = world;
        x = 0.5;
        z = 0.5;
        y = world.surfaceHeight((int) x, (int) z) + 1.01;
    }

    public void update(long window, double dt) {
        boolean forward = glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS;
        boolean back = glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS;
        boolean left = glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS;
        boolean right = glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS;
        boolean sprint = glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS;
        boolean sneak = glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS;

        double inputForward = (forward ? 1 : 0) - (back ? 1 : 0);
        double inputStrafe = (right ? 1 : 0) - (left ? 1 : 0);
        double magnitude = Math.hypot(inputForward, inputStrafe);
        if (magnitude > 0) { inputForward /= magnitude; inputStrafe /= magnitude; }
        double speed = sneak ? 2.0 : sprint && forward ? 5.6 : 4.3;
        double angle = Math.toRadians(yaw);
        double dx = (Math.sin(angle) * inputForward + Math.cos(angle) * inputStrafe) * speed * dt;
        double dz = (-Math.cos(angle) * inputForward + Math.sin(angle) * inputStrafe) * speed * dt;

        if (onGround && glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
            velocityY = 8.2;
            onGround = false;
        }
        velocityY -= 23.5 * dt;
        if (velocityY < -40) velocityY = -40;

        moveAxis(dx, 0, 0);
        moveAxis(0, 0, dz);
        onGround = false;
        moveAxis(0, velocityY * dt, 0);
        if (y < -10) respawn();
    }

    private void moveAxis(double dx, double dy, double dz) {
        double amount = dx != 0 ? dx : dy != 0 ? dy : dz;
        int steps = Math.max(1, (int) Math.ceil(Math.abs(amount) / 0.05));
        double sx = dx / steps, sy = dy / steps, sz = dz / steps;
        for (int i = 0; i < steps; i++) {
            x += sx; y += sy; z += sz;
            if (collides()) {
                x -= sx; y -= sy; z -= sz;
                if (dy < 0) onGround = true;
                if (dy != 0) velocityY = 0;
                break;
            }
        }
    }

    private boolean collides() {
        int minX = floor(x - 0.299), maxX = floor(x + 0.299);
        int minY = floor(y + 0.001), maxY = floor(y + 1.799);
        int minZ = floor(z - 0.299), maxZ = floor(z + 0.299);
        for (int by = minY; by <= maxY; by++)
            for (int bz = minZ; bz <= maxZ; bz++)
                for (int bx = minX; bx <= maxX; bx++)
                    if (world.get(bx, by, bz).isSolid()) return true;
        return false;
    }

    public boolean intersectsBlock(int bx, int by, int bz) {
        return x + 0.3 > bx && x - 0.3 < bx + 1 && y + 1.8 > by && y < by + 1
            && z + 0.3 > bz && z - 0.3 < bz + 1;
    }

    public void turn(double mouseDx, double mouseDy) {
        yaw += (float) (mouseDx * 0.15);
        pitch += (float) (mouseDy * 0.15);
        pitch = Math.max(-90, Math.min(90, pitch));
    }

    public double eyeY() { return y + 1.62; }
    public double dirX() { return Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }
    public double dirY() { return -Math.sin(Math.toRadians(pitch)); }
    public double dirZ() { return -Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }

    private void respawn() {
        x = 0.5;
        z = 0.5;
        y = world.surfaceHeight((int) x, (int) z) + 2;
        velocityY = 0;
    }
    private static int floor(double n) { return (int) Math.floor(n); }
}

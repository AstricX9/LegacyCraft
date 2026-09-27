package net.minecraft.client.entity;

import net.minecraft.block.Block;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class Player {
    public static final double WIDTH = 0.6, HEIGHT = 1.8;
    /** 1.8.9 player step height: ledges up to this are walked up without jumping. */
    public static final double STEP_HEIGHT = 0.6;
    public double x, y, z;
    public double velocityY;
    public float yaw = 25.0f;
    public float pitch = 8.0f;
    public boolean onGround;
    /** Set when the player falls out of the world; the client shows the death screen until {@link #respawn()}. */
    public boolean dead;

    // Player abilities (level.dat "abilities"): Creative may fly; Spectator always flies through blocks.
    public boolean allowFlying, isFlying, noClip;
    /** Shown by the survival HUD. No damage, hunger or experience sources exist yet (DEV-015). */
    public float health = 20f;
    public int foodLevel = 20, experienceLevel;
    public float experience;
    /** Horizontal velocity while flying (blocks/second); walking moves at the target speed directly. */
    public double velocityX, velocityZ;
    /** Seconds left in which a second jump press toggles flight (vanilla: 7 ticks). */
    private double flyToggleTimer;
    private boolean jumpWasDown;
    /** HYPOTHESIS: 1.8.9 flight reaches ~10.9 m/s (0.05 blocks/tick^2 with 0.91 drag), doubled when sprinting. */
    public static final double FLY_SPEED = 10.92, FLY_SPRINT_SPEED = 21.6, FLY_VERTICAL_SPEED = 7.5;
    public static final double FLY_TOGGLE_WINDOW = 7 / 20.0;
    private final World world;
    private final double spawnX, spawnZ;
    /** Below this y the player dies (vanilla's void damage starts at y &lt; -64). */
    public static final double VOID_DEATH_Y = -64;

    public Player(World world) { this(world, 0.5, 0.5); }

    public Player(World world, double spawnX, double spawnZ) {
        this.world = world;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        x = spawnX;
        z = spawnZ;
        y = world.surfaceHeight((int) Math.floor(x), (int) Math.floor(z)) + 1.01;
    }

    /** One movement step driven by the (rebindable) movement keys. */
    public void update(GameSettings settings, double dt) {
        if (dead) return;
        boolean forward = settings.keyBindForward.isKeyDown();
        boolean back = settings.keyBindBack.isKeyDown();
        boolean left = settings.keyBindLeft.isKeyDown();
        boolean right = settings.keyBindRight.isKeyDown();
        boolean sprint = settings.keyBindSprint.isKeyDown();
        boolean sneak = settings.keyBindSneak.isKeyDown();
        boolean jump = settings.keyBindJump.isKeyDown();

        if (allowFlying && jump && !jumpWasDown) {
            if (flyToggleTimer <= 0) {
                flyToggleTimer = FLY_TOGGLE_WINDOW;
            } else if (!noClip) {
                isFlying = !isFlying;
                flyToggleTimer = 0;
                velocityY = 0;
            }
        }
        jumpWasDown = jump;
        if (flyToggleTimer > 0) flyToggleTimer -= dt;

        double inputForward = (forward ? 1 : 0) - (back ? 1 : 0);
        double inputStrafe = (right ? 1 : 0) - (left ? 1 : 0);
        double magnitude = Math.hypot(inputForward, inputStrafe);
        if (magnitude > 0) { inputForward /= magnitude; inputStrafe /= magnitude; }
        double angle = Math.toRadians(yaw);
        double dirX = Math.sin(angle) * inputForward + Math.cos(angle) * inputStrafe;
        double dirZ = -Math.cos(angle) * inputForward + Math.sin(angle) * inputStrafe;

        if (isFlying || noClip) {
            fly(dirX, dirZ, sprint && forward, jump, sneak, dt);
        } else {
            double speed = sneak ? 2.0 : sprint && forward ? 5.6 : 4.3;
            double dx = dirX * speed * dt, dz = dirZ * speed * dt;
            if (onGround && jump) {
                velocityY = 8.2;
                onGround = false;
            }
            velocityY -= 23.5 * dt;
            if (velocityY < -40) velocityY = -40;
            move(dx, velocityY * dt, dz);
        }
        if (y < VOID_DEATH_Y) dead = true;
    }

    /**
     * Creative flight: velocity eases toward the input direction with vanilla's
     * per-tick drag (0.91 horizontally, 0.6 vertically, applied continuously);
     * jump rises, sneak descends, and touching the ground ends flight.
     */
    private void fly(double dirX, double dirZ, boolean sprinting, boolean up, boolean down, double dt) {
        double ticks = dt * 20.0;
        double horizontal = sprinting ? FLY_SPRINT_SPEED : FLY_SPEED;
        double keepHorizontal = Math.pow(0.91, ticks), keepVertical = Math.pow(0.6, ticks);
        velocityX = dirX * horizontal + (velocityX - dirX * horizontal) * keepHorizontal;
        velocityZ = dirZ * horizontal + (velocityZ - dirZ * horizontal) * keepHorizontal;
        double targetY = ((up ? 1 : 0) - (down ? 1 : 0)) * FLY_VERTICAL_SPEED;
        velocityY = targetY + (velocityY - targetY) * keepVertical;
        if (noClip) {
            x += velocityX * dt; y += velocityY * dt; z += velocityZ * dt;
            onGround = false;
            return;
        }
        move(velocityX * dt, velocityY * dt, velocityZ * dt);
        if (onGround) {
            isFlying = false;
            velocityX = velocityZ = 0;
        }
    }

    /** Applies a game mode's abilities: Creative may fly, Spectator flies through blocks, others walk. */
    public void setAbilities(boolean creative, boolean spectator) {
        allowFlying = creative || spectator;
        noClip = spectator;
        if (spectator) isFlying = true;
        else if (!allowFlying) isFlying = false;
    }

    public AxisAlignedBB boundingBox() {
        return new AxisAlignedBB(x - WIDTH / 2, y, z - WIDTH / 2, x + WIDTH / 2, y + HEIGHT, z + WIDTH / 2);
    }

    /**
     * Moves by clipping against block collision boxes one axis at a time
     * (Y, then X, then Z, as 1.8 entities do). When a horizontal move is
     * blocked while on the ground, the move is retried lifted by up to
     * {@link #STEP_HEIGHT} and kept if it gets further.
     */
    public void move(double dx, double dy, double dz) {
        double wantedX = dx, wantedY = dy, wantedZ = dz;
        AxisAlignedBB start = boundingBox();
        List<AxisAlignedBB> boxes = collisionBoxes(start.addCoord(dx, dy, dz));
        AxisAlignedBB box = start;
        for (AxisAlignedBB block : boxes) dy = block.clipY(box, dy);
        box = box.offset(0, dy, 0);
        for (AxisAlignedBB block : boxes) dx = block.clipX(box, dx);
        box = box.offset(dx, 0, 0);
        for (AxisAlignedBB block : boxes) dz = block.clipZ(box, dz);
        box = box.offset(0, 0, dz);

        boolean landed = wantedY != dy && wantedY < 0;
        boolean steppedUp = false;
        if ((onGround || landed) && (wantedX != dx || wantedZ != dz)) {
            double stepX = wantedX, stepY = STEP_HEIGHT, stepZ = wantedZ;
            List<AxisAlignedBB> stepBoxes = collisionBoxes(start.addCoord(wantedX, STEP_HEIGHT, wantedZ));
            AxisAlignedBB stepped = start;
            for (AxisAlignedBB block : stepBoxes) stepY = block.clipY(stepped, stepY);
            stepped = stepped.offset(0, stepY, 0);
            for (AxisAlignedBB block : stepBoxes) stepX = block.clipX(stepped, stepX);
            stepped = stepped.offset(stepX, 0, 0);
            for (AxisAlignedBB block : stepBoxes) stepZ = block.clipZ(stepped, stepZ);
            stepped = stepped.offset(0, 0, stepZ);
            double down = -stepY;
            for (AxisAlignedBB block : stepBoxes) down = block.clipY(stepped, down);
            stepped = stepped.offset(0, down, 0);
            if (stepX * stepX + stepZ * stepZ > dx * dx + dz * dz) {
                box = stepped;
                dx = stepX; dz = stepZ; dy = stepY + down;
                steppedUp = true;
            }
        }

        x = (box.minX() + box.maxX()) / 2;
        y = box.minY();
        z = (box.minZ() + box.maxZ()) / 2;
        onGround = landed || steppedUp;
        if (wantedY != dy) velocityY = 0;
    }

    private List<AxisAlignedBB> collisionBoxes(AxisAlignedBB area) {
        List<AxisAlignedBB> result = new ArrayList<>();
        int minX = floor(area.minX()), maxX = floor(area.maxX());
        int minY = floor(area.minY()) - 1, maxY = floor(area.maxY());
        int minZ = floor(area.minZ()), maxZ = floor(area.maxZ());
        for (int by = minY; by <= maxY; by++)
            for (int bz = minZ; bz <= maxZ; bz++)
                for (int bx = minX; bx <= maxX; bx++)
                    for (AxisAlignedBB box : Block.collisionBoxesAt(world, bx, by, bz))
                        if (box.intersects(area)) result.add(box);
        return result;
    }

    /** Whether any of these world-space boxes overlaps the player (used to refuse placements). */
    public boolean intersects(List<AxisAlignedBB> boxes) {
        AxisAlignedBB self = boundingBox();
        for (AxisAlignedBB box : boxes) if (box.intersects(self)) return true;
        return false;
    }

    public void turn(double mouseDx, double mouseDy) { turn(mouseDx, mouseDy, 0.5f, false); }

    /**
     * Mouse look with 1.8.9's sensitivity curve: {@code f = s * 0.6 + 0.2},
     * degrees per pixel {@code f^3 * 8 * 0.15}, so the default 0.5 gives the
     * 0.15 this client always used. HYPOTHESIS until measured.
     */
    public void turn(double mouseDx, double mouseDy, float sensitivity, boolean invert) {
        double f = sensitivity * 0.6 + 0.2;
        double scale = f * f * f * 8.0 * 0.15;
        yaw += (float) (mouseDx * scale);
        pitch += (float) (mouseDy * scale * (invert ? -1 : 1));
        pitch = Math.max(-90, Math.min(90, pitch));
    }

    /** Places the player (loading a saved position). */
    public void setPositionAndRotation(double x, double y, double z, float yaw, float pitch) {
        this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        velocityY = 0;
        velocityX = velocityZ = 0;
    }

    public double eyeY() { return y + 1.62; }
    public double dirX() { return Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }
    public double dirY() { return -Math.sin(Math.toRadians(pitch)); }
    public double dirZ() { return -Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }

    public void respawn() {
        dead = false;
        x = spawnX;
        z = spawnZ;
        y = world.surfaceHeight((int) Math.floor(x), (int) Math.floor(z)) + 2;
        velocityY = 0;
        velocityX = velocityZ = 0;
        if (!noClip) isFlying = false;
        health = 20f;
        foodLevel = 20;
    }
    private static int floor(double n) { return (int) Math.floor(n); }
}

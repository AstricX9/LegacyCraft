package net.minecraft.client.entity;

/**
 * The per-tick (20 Hz) state behind the camera effects and the player's
 * animation, updated from how far the player moved during the tick, the
 * way 1.8.9's entity updates do (HYPOTHESIS: recalled constants):
 * <ul>
 *   <li>view bobbing: walked distance (0.6 per block, not while flying) and
 *       a bob strength easing toward the ground speed, capped at 0.1, zero
 *       in the air; plus a camera pitch from falling or rising;</li>
 *   <li>hand sway: the arm's yaw and pitch trail the view by half the gap
 *       each tick;</li>
 *   <li>attack swing: six ticks from a swing start;</li>
 *   <li>legs: limb swing driven by ground speed; the body turns toward the
 *       walking direction and stays within 75° of the head;</li>
 *   <li>field of view: 1.1x flying, 1.15x sprinting, eased halfway per tick.</li>
 * </ul>
 * Angles here are vanilla's (yaw 0 = south); the player's own yaw is
 * vanilla's minus 180.
 */
public final class PlayerAnimation {
    private static final int SWING_TICKS = 6;
    private double lastX, lastY, lastZ;
    private boolean started;

    public int ticksExisted;
    public float distanceWalked, prevDistanceWalked;
    public float cameraYaw, prevCameraYaw, cameraPitch, prevCameraPitch;
    public float armYaw, prevArmYaw, armPitch, prevArmPitch;
    public float bodyYaw, prevBodyYaw;
    public float limbSwing, prevLimbSwing, limbSwingAmount, prevLimbSwingAmount;
    public float swingProgress, prevSwingProgress;
    private int swingTicks;
    private boolean swinging;
    public float fovModifier = 1f, prevFovModifier = 1f;

    /** Starts an attack/use swing unless one is less than half done (EntityLivingBase.swingItem). */
    public void swing() {
        if (!swinging || swingTicks >= SWING_TICKS / 2 || swingTicks < 0) {
            swingTicks = -1;
            swinging = true;
        }
    }

    public static float vanillaYaw(Player player) { return player.yaw + 180f; }

    public void tick(Player player) {
        float yaw = vanillaYaw(player);
        if (!started) {
            started = true;
            lastX = player.x; lastY = player.y; lastZ = player.z;
            armYaw = prevArmYaw = yaw;
            armPitch = prevArmPitch = player.pitch;
            bodyYaw = prevBodyYaw = yaw;
        }
        ticksExisted++;
        double dx = player.x - lastX, dy = player.y - lastY, dz = player.z - lastZ;
        lastX = player.x; lastY = player.y; lastZ = player.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        prevSwingProgress = swingProgress;
        if (swinging) {
            swingTicks++;
            if (swingTicks >= SWING_TICKS) {
                swingTicks = 0;
                swinging = false;
            }
        } else {
            swingTicks = 0;
        }
        swingProgress = swingTicks / (float) SWING_TICKS;

        prevDistanceWalked = distanceWalked;
        if (!player.isFlying) distanceWalked += (float) horizontal * 0.6f;

        prevCameraYaw = cameraYaw;
        prevCameraPitch = cameraPitch;
        boolean alive = !player.dead;
        // The bob follows the speed left after ground friction (0.546 of the step), capped at 0.1.
        float bob = (float) Math.min(0.1, horizontal * 0.546);
        float tilt = (float) (Math.atan(-dy * 0.2) * 15.0);
        if (!player.onGround || !alive) bob = 0;
        if (player.onGround || !alive) tilt = 0;
        cameraYaw += (bob - cameraYaw) * 0.4f;
        cameraPitch += (tilt - cameraPitch) * 0.8f;

        prevArmYaw = armYaw;
        prevArmPitch = armPitch;
        armPitch += (player.pitch - armPitch) * 0.5f;
        armYaw += (yaw - armYaw) * 0.5f;

        prevBodyYaw = bodyYaw;
        float target = bodyYaw;
        if (dx * dx + dz * dz > 0.0025000002) target = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        if (swingProgress > 0) target = yaw;
        bodyYaw += wrap(target - bodyYaw) * 0.3f;
        float headOffset = wrap(yaw - bodyYaw);
        headOffset = Math.max(-75f, Math.min(75f, headOffset));
        bodyYaw = yaw - headOffset;
        if (headOffset * headOffset > 2500f) bodyYaw += headOffset * 0.2f;

        prevLimbSwing = limbSwing;
        prevLimbSwingAmount = limbSwingAmount;
        float speed = (float) Math.min(1.0, horizontal * 4.0);
        limbSwingAmount += (speed - limbSwingAmount) * 0.4f;
        limbSwing += limbSwingAmount;

        prevFovModifier = fovModifier;
        float fov = 1f;
        if (player.isFlying) fov *= 1.1f;
        if (player.sprinting) fov *= 1.15f;
        fovModifier += (fov - fovModifier) * 0.5f;
        fovModifier = Math.max(0.1f, Math.min(1.5f, fovModifier));
    }

    public float swingProgress(float partialTicks) {
        float delta = swingProgress - prevSwingProgress;
        if (delta < 0) delta++;
        return prevSwingProgress + delta * partialTicks;
    }

    public float fov(float partialTicks) { return lerp(prevFovModifier, fovModifier, partialTicks); }

    public float bodyYaw(float partialTicks) { return prevBodyYaw + wrap(bodyYaw - prevBodyYaw) * partialTicks; }

    public static float lerp(float from, float to, float t) { return from + (to - from) * t; }

    public static float wrap(float degrees) {
        degrees %= 360f;
        if (degrees >= 180f) degrees -= 360f;
        if (degrees < -180f) degrees += 360f;
        return degrees;
    }
}

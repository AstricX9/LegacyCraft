package net.minecraft.entity.ai;

import net.minecraft.entity.EntityLiving;

/**
 * EntityMoveHelper and EntityLookHelper (HYPOTHESIS: recalled). The move
 * helper turns the body up to 30 degrees a tick toward its target, walks at
 * {@code speed x movementSpeed} and jumps when the target is higher and less
 * than a block away horizontally. The look helper turns the head toward a
 * point at the given yaw and pitch rates, or back toward the body.
 */
public final class MoveHelper {
    private final EntityLiving entity;
    private double posX, posY, posZ, speed;
    private boolean update;

    public MoveHelper(EntityLiving entity) { this.entity = entity; }

    public void setMoveTo(double x, double y, double z, double speed) {
        posX = x; posY = y; posZ = z;
        this.speed = speed;
        update = true;
    }

    public boolean isUpdating() { return update; }
    public double speed() { return speed; }

    public void tick() {
        entity.moveForward = 0;
        if (!update) return;
        update = false;
        int feet = (int) Math.floor(entity.boundingBox().minY() + 0.5);
        double dx = posX - entity.x(), dz = posZ - entity.z(), dy = posY - feet;
        double distance = dx * dx + dy * dy + dz * dz;
        if (distance < 2.500000277905201E-7) return;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        entity.rotationYaw = limitAngle(entity.rotationYaw, yaw, 30f);
        entity.setAIMoveSpeed((float) (speed * entity.movementSpeed()));
        if (dy > 0 && dx * dx + dz * dz < 1.0) entity.setJumping(true);
    }

    static float limitAngle(float current, float target, float max) {
        float change = wrap(target - current);
        if (change > max) change = max;
        if (change < -max) change = -max;
        float result = current + change;
        if (result < 0f) result += 360f;
        else if (result > 360f) result -= 360f;
        return result;
    }

    static float wrap(float angle) {
        angle %= 360f;
        if (angle >= 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    /** EntityLookHelper. */
    public static final class Look {
        private final EntityLiving entity;
        private float deltaYaw, deltaPitch;
        private boolean looking;
        private double posX, posY, posZ;

        public Look(EntityLiving entity) { this.entity = entity; }

        public void setLookPosition(double x, double y, double z, float deltaYaw, float deltaPitch) {
            posX = x; posY = y; posZ = z;
            this.deltaYaw = deltaYaw;
            this.deltaPitch = deltaPitch;
            looking = true;
        }

        public void tick() {
            entity.rotationPitch = 0f;
            if (looking) {
                looking = false;
                double dx = posX - entity.x(), dy = posY - (entity.y() + entity.eyeHeight()), dz = posZ - entity.z();
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
                float pitch = (float) -(Math.atan2(dy, horizontal) * 180.0 / Math.PI);
                entity.rotationPitch = update(entity.rotationPitch, pitch, deltaPitch);
                entity.rotationYawHead = update(entity.rotationYawHead, yaw, deltaYaw);
            } else {
                entity.rotationYawHead = update(entity.rotationYawHead, entity.renderYawOffset, 10f);
            }
            float turn = wrap(entity.rotationYawHead - entity.renderYawOffset);
            if (!entity.navigator().noPath()) {
                if (turn < -75f) entity.rotationYawHead = entity.renderYawOffset - 75f;
                if (turn > 75f) entity.rotationYawHead = entity.renderYawOffset + 75f;
            }
        }

        private static float update(float current, float target, float max) {
            float change = wrap(target - current);
            if (change > max) change = max;
            if (change < -max) change = -max;
            return current + change;
        }
    }
}

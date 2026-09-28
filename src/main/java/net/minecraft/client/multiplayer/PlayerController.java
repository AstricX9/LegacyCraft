package net.minecraft.client.multiplayer;

/**
 * Block-breaking timing from 1.8.9's PlayerControllerMP (master context §16):
 * <ul>
 *   <li>Creative breaks the clicked block at once, then waits 5 ticks before
 *       the next one while the button stays down.</li>
 *   <li>Survival adds the block's relative hardness (see
 *       {@link net.minecraft.item.ToolValues#breakProgressPerTick}) every
 *       tick the button is held on the same block and breaks it at 1.0,
 *       then waits 5 ticks. Moving to another block restarts at 0;
 *       releasing the button resets the progress.</li>
 * </ul>
 * The caller performs the actual break when a method returns true.
 */
public final class PlayerController {
    public static final int HIT_DELAY = 5;
    private int curX, curY = -1, curZ;
    private float curBlockDamage;
    private boolean isHittingBlock;
    private int blockHitDelay;
    /** Ticks spent mining the current block; every fourth one plays the block's step sound (stepSoundTickCounter). */
    private int stepSoundTicks;
    private boolean hitSoundDue;

    /** The attack button was pressed on a block this tick. Returns whether to break it now. */
    public boolean clickBlock(int x, int y, int z, boolean creative, float progressPerTick) {
        if (creative) {
            blockHitDelay = HIT_DELAY;
            return true;
        }
        if (!isHittingBlock || !isCurrent(x, y, z)) {
            curBlockDamage = 0;
            if (progressPerTick >= 1.0f) {
                isHittingBlock = false;
                return true;
            }
            isHittingBlock = true;
            stepSoundTicks = 0;
            curX = x; curY = y; curZ = z;
        }
        return false;
    }

    /** The attack button is held on a block this tick. Returns whether to break it now. */
    public boolean onPlayerDamageBlock(int x, int y, int z, boolean creative, float progressPerTick) {
        if (blockHitDelay > 0) {
            blockHitDelay--;
            return false;
        }
        if (creative) {
            blockHitDelay = HIT_DELAY;
            return true;
        }
        if (isHittingBlock && isCurrent(x, y, z)) {
            curBlockDamage += progressPerTick;
            if (stepSoundTicks % 4 == 0) hitSoundDue = true;
            stepSoundTicks++;
            if (curBlockDamage >= 1.0f) {
                stepSoundTicks = 0;
                isHittingBlock = false;
                curBlockDamage = 0;
                blockHitDelay = HIT_DELAY;
                return true;
            }
            return false;
        }
        return clickBlock(x, y, z, false, progressPerTick);
    }

    /** Whether this tick's mining should make the hit sound (cleared by the call). */
    public boolean takeHitSound() {
        boolean due = hitSoundDue;
        hitSoundDue = false;
        return due;
    }

    /** The button was released or the crosshair left all blocks. */
    public void resetBlockRemoving() {
        isHittingBlock = false;
        curBlockDamage = 0;
    }

    private boolean isCurrent(int x, int y, int z) { return x == curX && y == curY && z == curZ; }

    public boolean isHittingBlock() { return isHittingBlock; }
    public float curBlockDamage() { return curBlockDamage; }
    public int[] currentBlock() { return isHittingBlock ? new int[]{curX, curY, curZ} : null; }

    /** Crack texture index 0-9 for the block being broken, or -1 (vanilla sends (int)(damage * 10) - 1). */
    public int destroyStage() {
        if (!isHittingBlock) return -1;
        return Math.min(9, (int) (curBlockDamage * 10.0f) - 1);
    }
}

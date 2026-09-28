package net.minecraft.client.entity;

import net.minecraft.block.Block;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The local player's movement, ticked 20 times a second exactly like 1.8.9's
 * EntityPlayerSP/EntityPlayer/EntityLivingBase (HYPOTHESIS: recalled
 * constants), and drawn between ticks from {@link #prevX}/{@link #x}:
 * <ul>
 *   <li><b>Walking</b>: the movement keys push with 0.1 x 0.16277 / f³ a tick
 *       on the ground (f = 0.91 x the block's slipperiness: 0.6 normally,
 *       0.8 slime, 0.98 ice), 0.02 in the air (0.026 sprinting), and motion
 *       keeps f of itself each tick horizontally, 0.98 vertically after
 *       0.08 gravity. That makes 4.3 m/s walking, 5.6 sprinting, 1.3
 *       sneaking and about 7.1 sprint-jumping, the fastest way on foot:
 *       every jump (0.42 up) adds a 0.2 push forward while sprinting.</li>
 *   <li><b>Sprinting</b>: hold sprint or double-tap forward within 7 ticks
 *       while moving forward at full speed with food above 6 (or in
 *       Creative); it stops when you slow, sneak or hit a wall.</li>
 *   <li><b>Sneaking</b>: 0.3 x the input and never stepping off an edge.</li>
 *   <li><b>Water and lava</b>: 0.02 push, keeping 0.8 (water) or 0.5 (lava)
 *       of the motion, 0.02 gravity; holding jump rises 0.04 a tick
 *       ("treading water"), and swimming into a wall with room above hops
 *       out at 0.3.</li>
 *   <li><b>Ladders and vines</b>: at most 0.15 sideways and 0.15 down,
 *       climbing at 0.2 when pushing into them, holding on while sneaking.</li>
 *   <li><b>Flying</b>: double-tap jump in Creative; 0.05 push (0.1
 *       sprinting) with 0.91 drag, jump/sneak adding 0.15 up/down with 0.6
 *       drag; landing ends it. Spectators always fly through blocks.</li>
 *   <li>Soul sand keeps 0.4 of horizontal motion, cobwebs a quarter (a
 *       twentieth vertically), and slime bounces a fall back up.</li>
 * </ul>
 */
public final class Player {
    public static final double WIDTH = 0.6, HEIGHT = 1.8;
    /** 1.8.9 player step height: ledges up to this are walked up without jumping. */
    public static final double STEP_HEIGHT = 0.6;
    /** Below this y the player dies (vanilla's void damage starts at y &lt; -64). */
    public static final double VOID_DEATH_Y = -64;

    public double x, y, z;
    /** Where the player was at the start of the tick (drawing interpolates from here). */
    public double prevX, prevY, prevZ;
    /** Blocks per tick. */
    public double motionX, motionY, motionZ;
    public float yaw = 25.0f;
    public float pitch = 8.0f;
    public boolean onGround, collidedHorizontally, collidedVertically;
    /** Set when the player falls out of the world; the client shows the death screen until {@link #respawn()}. */
    public boolean dead;
    /** Sneaking (eyes 0.08 lower) and sprinting, as the camera and model read them. */
    public boolean sneaking, sprinting;
    /** Whether the player counts as in water this tick, and whether they just went in (the client splashes). */
    public boolean inWater, enteredWater;
    public float fallDistance;

    // Player abilities (level.dat "abilities"): Creative may fly; Spectator always flies through blocks.
    public boolean allowFlying, isFlying, noClip;
    /** Shown by the survival HUD. No damage, hunger or experience sources exist yet (DEV-015). */
    public float health = 20f;
    public int foodLevel = 20, experienceLevel;
    public float experience;

    /**
     * The stack being used (a sword held up to block, later food or a bow) and
     * the ticks left (EntityPlayer.itemInUse / itemInUseCount); null when not.
     */
    private net.minecraft.item.ItemStack itemInUse;
    private int itemInUseCount;

    /** Starts using a stack for up to {@code duration} ticks (EntityPlayer.setItemInUse). */
    public void setItemInUse(net.minecraft.item.ItemStack stack, int duration) {
        if (stack == null || dead) return;
        itemInUse = stack;
        itemInUseCount = duration;
    }

    public void clearItemInUse() {
        itemInUse = null;
        itemInUseCount = 0;
    }

    public boolean isUsingItem() { return itemInUse != null; }
    public net.minecraft.item.ItemStack itemInUse() { return itemInUse; }
    public int itemInUseCount() { return itemInUseCount; }

    /** Holding a sword up (EntityPlayer.isBlocking): using an item whose use is BLOCK. */
    public boolean isBlocking() {
        return itemInUse != null && itemInUse.item().useAction() == net.minecraft.item.Item.UseAction.BLOCK;
    }

    /** Where the player's own sounds go (played at their feet). */
    public interface Sounds { void play(String event, float volume, float pitch); }
    public Sounds sounds = (event, volume, pitch) -> {};
    private final java.util.Random random = new java.util.Random();
    private float distanceWalkedOnStep;
    private int nextStepDistance = 1;

    private final World world;
    private final double spawnX, spawnZ;
    private int jumpTicks, flyToggleTimer, sprintToggleTimer;
    private boolean jumpWasDown, sneakWasDown, inWeb, firstUpdate = true;
    private float lastForward;

    /** The movement keys for one tick. */
    public record Input(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
        public static Input of(GameSettings settings) {
            return new Input(settings.keyBindForward.isKeyDown(), settings.keyBindBack.isKeyDown(), settings.keyBindLeft.isKeyDown(),
                settings.keyBindRight.isKeyDown(), settings.keyBindJump.isKeyDown(), settings.keyBindSneak.isKeyDown(),
                settings.keyBindSprint.isKeyDown());
        }

        public static final Input NONE = new Input(false, false, false, false, false, false, false);
    }

    public Player(World world) { this(world, 0.5, 0.5); }

    public Player(World world, double spawnX, double spawnZ) {
        this.world = world;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        x = spawnX;
        z = spawnZ;
        y = world.surfaceHeight((int) Math.floor(x), (int) Math.floor(z)) + 1.01;
        prevX = x; prevY = y; prevZ = z;
    }

    /** One 20 Hz tick of movement from these keys. */
    public void tick(Input input) {
        prevX = x; prevY = y; prevZ = z;
        enteredWater = false;
        if (dead) {
            clearItemInUse();
            return;
        }
        if (itemInUse != null && --itemInUseCount <= 0) clearItemInUse();
        updateWater();

        // EntityPlayerSP: the input, sprinting and the flight toggle.
        if (sprintToggleTimer > 0) sprintToggleTimer--;
        boolean wasJumping = jumpWasDown, wasSneaking = sneakWasDown, wasForward = lastForward >= 0.8f;
        float strafe = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
        float forward = (input.forward() ? 1 : 0) - (input.back() ? 1 : 0);
        sneaking = input.sneak() && !noClip;
        if (input.sneak()) {
            strafe *= 0.3f;
            forward *= 0.3f;
        }
        // Using an item (blocking with a sword) slows the input to a fifth and cancels a pending double tap.
        if (isUsingItem()) {
            strafe *= 0.2f;
            forward *= 0.2f;
            sprintToggleTimer = 0;
        }
        boolean canSprint = foodLevel > 6 || allowFlying;
        if (onGround && !wasSneaking && !wasForward && forward >= 0.8f && !sprinting && canSprint && !isUsingItem()) {
            if (sprintToggleTimer <= 0 && !input.sprint()) sprintToggleTimer = 7;
            else sprinting = true;
        }
        if (!sprinting && forward >= 0.8f && canSprint && !isUsingItem() && input.sprint()) sprinting = true;
        if (sprinting && (forward < 0.8f || collidedHorizontally || !canSprint)) sprinting = false;
        if (allowFlying) {
            if (noClip) {
                isFlying = true;
            } else if (!wasJumping && input.jump()) {
                if (flyToggleTimer == 0) {
                    flyToggleTimer = 7;
                } else {
                    isFlying = !isFlying;
                    flyToggleTimer = 0;
                }
            }
        }
        if (isFlying) {
            if (input.sneak()) motionY -= 0.15;
            if (input.jump()) motionY += 0.15;
        }
        jumpWasDown = input.jump();
        sneakWasDown = input.sneak();
        lastForward = forward;

        // EntityPlayer / EntityLivingBase.onLivingUpdate.
        if (flyToggleTimer > 0) flyToggleTimer--;
        if (jumpTicks > 0) jumpTicks--;
        if (Math.abs(motionX) < 0.005) motionX = 0;
        if (Math.abs(motionY) < 0.005) motionY = 0;
        if (Math.abs(motionZ) < 0.005) motionZ = 0;
        if (input.jump()) {
            if (inWater || inLava()) motionY += 0.04;
            else if (onGround && jumpTicks == 0) {
                jump();
                jumpTicks = 10;
            }
        } else {
            jumpTicks = 0;
        }
        strafe *= 0.98f;
        forward *= 0.98f;
        float airSpeed = 0.02f + (sprinting ? 0.02f * 0.3f : 0f);
        moveWithHeading(strafe, forward, airSpeed);
        if (onGround && isFlying && !noClip) isFlying = false;
        if (y < VOID_DEATH_Y) dead = true;
        firstUpdate = false;
    }

    private void jump() {
        motionY = 0.42;
        if (sprinting) {
            double angle = Math.toRadians(yaw + 180f);
            motionX -= Math.sin(angle) * 0.2;
            motionZ += Math.cos(angle) * 0.2;
        }
    }

    private void moveWithHeading(float strafe, float forward, float airSpeed) {
        double flyingMotionY = motionY;
        if (isFlying) airSpeed = 0.05f * (sprinting ? 2f : 1f);
        if (inWater && !isFlying) {
            double startY = y;
            moveFlying(strafe, forward, 0.02f);
            move(motionX, motionY, motionZ);
            motionX *= 0.8; motionY *= 0.8; motionZ *= 0.8;
            motionY -= 0.02;
            if (collidedHorizontally && offsetIsFree(motionX, motionY + 0.6 - y + startY, motionZ)) motionY = 0.3;
        } else if (inLava() && !isFlying) {
            double startY = y;
            moveFlying(strafe, forward, 0.02f);
            move(motionX, motionY, motionZ);
            motionX *= 0.5; motionY *= 0.5; motionZ *= 0.5;
            motionY -= 0.02;
            if (collidedHorizontally && offsetIsFree(motionX, motionY + 0.6 - y + startY, motionZ)) motionY = 0.3;
        } else {
            float friction = onGround ? slipperinessBelow() * 0.91f : 0.91f;
            float push = onGround ? moveSpeed() * (0.16277136f / (friction * friction * friction)) : airSpeed;
            moveFlying(strafe, forward, push);
            friction = onGround ? slipperinessBelow() * 0.91f : 0.91f;
            if (onLadder()) {
                motionX = clamp(motionX, -0.15, 0.15);
                motionZ = clamp(motionZ, -0.15, 0.15);
                fallDistance = 0;
                if (motionY < -0.15) motionY = -0.15;
                if (sneaking && motionY < 0) motionY = 0;
            }
            move(motionX, motionY, motionZ);
            if (collidedHorizontally && onLadder()) motionY = 0.2;
            motionY -= 0.08;
            motionY *= 0.98;
            motionX *= friction;
            motionZ *= friction;
        }
        if (isFlying) motionY = flyingMotionY * 0.6;
    }

    /** Walking speed: 0.1, times 1.3 while sprinting (the movement speed attribute). */
    private float moveSpeed() { return sprinting ? 0.13f : 0.1f; }

    /** Turns input into motion along the facing (Entity.moveFlying). */
    private void moveFlying(float strafe, float forward, float amount) {
        float length = strafe * strafe + forward * forward;
        if (length < 1.0E-4f) return;
        length = (float) Math.sqrt(length);
        if (length < 1f) length = 1f;
        length = amount / length;
        strafe *= length;
        forward *= length;
        double angle = Math.toRadians(yaw + 180f);
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        motionX += strafe * cos - forward * sin;
        motionZ += forward * cos + strafe * sin;
    }

    /**
     * In water this tick (Entity.handleWaterMovement): water in the box
     * shrunk 0.4 top and bottom. Going in stops any fall.
     */
    private void updateWater() {
        AxisAlignedBB box = boundingBox();
        AxisAlignedBB waterBox = new AxisAlignedBB(box.minX() + 0.001, box.minY() + 0.4 + 0.001, box.minZ() + 0.001,
            box.maxX() - 0.001, box.maxY() - 0.4 - 0.001, box.maxZ() - 0.001);
        double[] current = noClip ? null : world.liquidCurrent(waterBox, "water");
        if (current != null) {
            // Currents push anyone not flying (EntityPlayer.isPushedByWater).
            double[] push = net.minecraft.block.Liquids.normalize(current[0], current[1], current[2]);
            if (!isFlying) {
                motionX += push[0] * 0.014;
                motionY += push[1] * 0.014;
                motionZ += push[2] * 0.014;
            }
            if (!inWater && !firstUpdate) {
                enteredWater = true;
                float volume = (float) Math.min(1.0, Math.sqrt(motionX * motionX * 0.2 + motionY * motionY + motionZ * motionZ * 0.2) * 0.2);
                sounds.play("game.player.swim.splash", volume, 1f + (random.nextFloat() - random.nextFloat()) * 0.4f);
            }
            fallDistance = 0;
            inWater = true;
        } else {
            inWater = false;
        }
    }

    /** Lava in the box shrunk 0.1 sideways and 0.4 top and bottom (Entity.isInLava). */
    public boolean inLava() {
        if (noClip) return false;
        AxisAlignedBB box = boundingBox();
        return world.isMaterialInBox(new AxisAlignedBB(box.minX() + 0.1, box.minY() + 0.4, box.minZ() + 0.1,
            box.maxX() - 0.1, box.maxY() - 0.4, box.maxZ() - 0.1), "lava");
    }

    /** Whether the box moved this far would touch no block and no liquid (the hop out of water onto a ledge). */
    private boolean offsetIsFree(double dx, double dy, double dz) {
        AxisAlignedBB moved = boundingBox().offset(dx, dy, dz);
        for (AxisAlignedBB block : collisionBoxes(moved)) if (block.intersects(moved)) return false;
        return !world.isMaterialInBox(moved, "water") && !world.isMaterialInBox(moved, "lava");
    }

    private boolean onLadder() {
        String name = blockName(floor(x), floor(boundingBox().minY()), floor(z));
        return name.equals("ladder") || name.equals("vine");
    }

    /** Slipperiness of the block under the feet (0.6; ice and packed ice 0.98; slime 0.8). */
    private float slipperinessBelow() {
        return switch (blockName(floor(x), floor(boundingBox().minY()) - 1, floor(z))) {
            case "ice", "packed_ice" -> 0.98f;
            case "slime" -> 0.8f;
            default -> 0.6f;
        };
    }

    private String blockName(int bx, int by, int bz) {
        if (by < 0 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null) return "air";
        return world.get(bx, by, bz).name().path();
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
     * (Y, then X, then Z, as 1.8 entities do), after cobwebs slow the move
     * and, while sneaking on the ground, cutting back any horizontal move
     * that would leave nothing below within a block. When a horizontal move
     * is blocked while on the ground, the move is retried lifted by up to
     * {@link #STEP_HEIGHT} and kept if it gets further. Afterwards the
     * blocks touched act (soul sand, cobweb, slime) and falls are tracked.
     */
    public void move(double dx, double dy, double dz) {
        if (noClip) {
            x += dx; y += dy; z += dz;
            onGround = false;
            return;
        }
        double startX = x, startY = y, startZ = z;
        boolean sneakingOnGround = onGround && sneaking;
        if (inWeb) {
            inWeb = false;
            dx *= 0.25; dy *= 0.05; dz *= 0.25;
            motionX = motionY = motionZ = 0;
        }
        AxisAlignedBB start = boundingBox();
        if (onGround && sneaking) {
            final double step = 0.05;
            while (dx != 0 && isEmpty(start.offset(dx, -1, 0))) dx = towardZero(dx, step);
            while (dz != 0 && isEmpty(start.offset(0, -1, dz))) dz = towardZero(dz, step);
            while (dx != 0 && dz != 0 && isEmpty(start.offset(dx, -1, dz))) {
                dx = towardZero(dx, step);
                dz = towardZero(dz, step);
            }
        }
        double wantedX = dx, wantedY = dy, wantedZ = dz;
        List<AxisAlignedBB> boxes = collisionBoxes(start.addCoord(dx, dy, dz));
        AxisAlignedBB box = start;
        for (AxisAlignedBB block : boxes) dy = block.clipY(box, dy);
        box = box.offset(0, dy, 0);
        for (AxisAlignedBB block : boxes) dx = block.clipX(box, dx);
        box = box.offset(dx, 0, 0);
        for (AxisAlignedBB block : boxes) dz = block.clipZ(box, dz);
        box = box.offset(0, 0, dz);

        boolean landed = wantedY != dy && wantedY < 0;
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
            }
        }

        x = (box.minX() + box.maxX()) / 2;
        y = box.minY();
        z = (box.minZ() + box.maxZ()) / 2;
        collidedHorizontally = wantedX != dx || wantedZ != dz;
        collidedVertically = wantedY != dy;
        onGround = collidedVertically && wantedY < 0;
        if (onGround) fallDistance = 0;
        else if (dy < 0) fallDistance -= (float) dy;
        if (wantedX != dx) motionX = 0;
        if (wantedZ != dz) motionZ = 0;
        if (collidedVertically) {
            boolean bouncy = blockName(floor(x), floor(y - 0.2), floor(z)).equals("slime");
            if (bouncy && !sneaking && motionY < 0) motionY = -motionY;
            else motionY = 0;
        }
        touchBlocks();
        if (!isFlying && !sneakingOnGround) stepSounds(startX, startY, startZ);
    }

    /**
     * Footsteps (Entity.moveEntity/playStepSound, HYPOTHESIS): every block of
     * distance x 0.6 walked (climbing counts only on ladders) over something
     * that isn't air plays the step sound of the block 0.2 below the feet (a
     * fence, wall or gate under an air block counts; snow lying on top wins)
     * at 0.15 x its volume; liquids stay quiet but add the swim sound.
     */
    private void stepSounds(double startX, double startY, double startZ) {
        int bx = floor(x), by = floor(y - 0.2), bz = floor(z);
        net.minecraft.block.Block block = world.get(bx, by, bz);
        if (block.isAir() && by > 0) {
            net.minecraft.block.Block below = world.get(bx, by - 1, bz);
            if (below instanceof net.minecraft.block.BlockFence || below instanceof net.minecraft.block.BlockWall
                || below instanceof net.minecraft.block.BlockFenceGate) {
                block = below;
                by--;
            }
        }
        double mx = x - startX, my = block.name().path().equals("ladder") ? y - startY : 0, mz = z - startZ;
        distanceWalkedOnStep += (float) (Math.sqrt(mx * mx + my * my + mz * mz) * 0.6);
        if (distanceWalkedOnStep <= nextStepDistance || block.isAir()) return;
        nextStepDistance = (int) distanceWalkedOnStep + 1;
        if (inWater) {
            float volume = (float) Math.min(1.0, Math.sqrt(motionX * motionX * 0.2 + motionY * motionY + motionZ * motionZ * 0.2) * 0.35);
            sounds.play("game.player.swim", volume, 1f + (random.nextFloat() - random.nextFloat()) * 0.4f);
        }
        net.minecraft.block.Block above = world.get(bx, by + 1, bz);
        if (above.name().path().equals("snow_layer")) block = above;
        else if (net.minecraft.block.Liquids.isLiquid(block)) return;
        net.minecraft.block.BlockSoundType type = net.minecraft.block.BlockSoundType.of(block);
        sounds.play(type.stepSound(), type.volume() * 0.15f, type.frequency());
    }

    /** What the blocks the player is inside do to them (Entity.doBlockCollisions). */
    private void touchBlocks() {
        AxisAlignedBB box = boundingBox();
        int minX = floor(box.minX() + 0.001), maxX = floor(box.maxX() - 0.001);
        int minY = floor(box.minY() + 0.001), maxY = floor(box.maxY() - 0.001);
        int minZ = floor(box.minZ() + 0.001), maxZ = floor(box.maxZ() - 0.001);
        for (int bx = minX; bx <= maxX; bx++)
            for (int by = minY; by <= maxY; by++)
                for (int bz = minZ; bz <= maxZ; bz++) {
                    switch (blockName(bx, by, bz)) {
                        case "soul_sand" -> { motionX *= 0.4; motionZ *= 0.4; }
                        case "web" -> inWeb = true;
                        case "slime" -> {
                            if (Math.abs(motionY) < 0.1 && !sneaking) {
                                double factor = 0.4 + Math.abs(motionY) * 0.2;
                                motionX *= factor;
                                motionZ *= factor;
                            }
                        }
                        default -> { }
                    }
                }
    }

    private static double towardZero(double value, double step) {
        if (value < step && value >= -step) return 0;
        return value > 0 ? value - step : value + step;
    }

    private boolean isEmpty(AxisAlignedBB area) {
        for (AxisAlignedBB block : collisionBoxes(area)) if (block.intersects(area)) return false;
        return true;
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

    /** Places the player (loading a saved position, teleporting): no motion, nothing to interpolate from. */
    public void setPositionAndRotation(double x, double y, double z, float yaw, float pitch) {
        this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        prevX = x; prevY = y; prevZ = z;
        motionX = motionY = motionZ = 0;
        fallDistance = 0;
    }

    /** The position drawn this frame, between the last two ticks. */
    public double renderX(float partialTicks) { return prevX + (x - prevX) * partialTicks; }
    public double renderY(float partialTicks) { return prevY + (y - prevY) * partialTicks; }
    public double renderZ(float partialTicks) { return prevZ + (z - prevZ) * partialTicks; }

    public double eyeY() { return y + eyeHeight(); }
    public double eyeHeight() { return sneaking ? 1.54 : 1.62; }
    public double dirX() { return Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }
    public double dirY() { return -Math.sin(Math.toRadians(pitch)); }
    public double dirZ() { return -Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }

    public void respawn() {
        dead = false;
        x = spawnX;
        z = spawnZ;
        y = world.surfaceHeight((int) Math.floor(x), (int) Math.floor(z)) + 2;
        prevX = x; prevY = y; prevZ = z;
        motionX = motionY = motionZ = 0;
        fallDistance = 0;
        if (!noClip) isFlying = false;
        health = 20f;
        foodLevel = 20;
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static int floor(double n) { return (int) Math.floor(n); }
}

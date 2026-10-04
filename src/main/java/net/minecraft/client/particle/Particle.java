package net.minecraft.client.particle;

import net.minecraft.block.Block;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * A particle (1.8.9 EntityFX, HYPOTHESIS: recalled constants): a small box
 * that moves with its velocity each 20 Hz tick, falls with its gravity,
 * slows by 2% a tick (30% more on the ground), collides with blocks unless
 * it passes through them, and dies at its maximum age. It draws as a square
 * facing the camera, cut from {@code particles.png} (16x16 cells of 8
 * pixels) unless a subclass draws from the block atlas.
 */
public class Particle {
    protected final World world;
    protected final Random random;
    public double x, y, z, prevX, prevY, prevZ;
    public double motionX, motionY, motionZ;
    protected float width = 0.2f, height = 0.2f;
    protected boolean onGround, noClip, dead;
    protected float gravity;
    protected int age, maxAge;
    protected float scale;
    protected float red = 1f, green = 1f, blue = 1f, alpha = 1f;
    protected int textureIndex;
    protected final float jitterX, jitterY;

    protected Particle(World world, Random random, double x, double y, double z) {
        this.world = world;
        this.random = random;
        setPosition(x, y, z);
        prevX = x; prevY = y; prevZ = z;
        jitterX = random.nextFloat() * 3f;
        jitterY = random.nextFloat() * 3f;
        scale = (random.nextFloat() * 0.5f + 0.5f) * 2f;
        maxAge = (int) (4f / (random.nextFloat() * 0.9f + 0.1f));
    }

    /**
     * With a starting push: the given velocity plus up to 0.4 of random
     * scatter per axis, rescaled to a random speed of 0.06-0.18 and lifted
     * by 0.1 (EntityFX's second constructor).
     */
    protected Particle(World world, Random random, double x, double y, double z, double vx, double vy, double vz) {
        this(world, random, x, y, z);
        motionX = vx + (Math.random() * 2.0 - 1.0) * 0.4;
        motionY = vy + (Math.random() * 2.0 - 1.0) * 0.4;
        motionZ = vz + (Math.random() * 2.0 - 1.0) * 0.4;
        float speed = (float) (Math.random() + Math.random() + 1.0) * 0.15f;
        float length = (float) Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        motionX = motionX / length * speed * 0.4;
        motionY = motionY / length * speed * 0.4 + 0.1;
        motionZ = motionZ / length * speed * 0.4;
    }

    protected void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    protected void setPosition(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }

    public Particle multiplyVelocity(float factor) {
        motionX *= factor;
        motionY = (motionY - 0.1) * factor + 0.1;
        motionZ *= factor;
        return this;
    }

    public Particle multiplyScale(float factor) {
        setSize(0.2f * factor, 0.2f * factor);
        scale *= factor;
        return this;
    }

    public boolean isDead() { return dead; }

    /** 0: particles.png; 1: the block atlas; 2: an item's own sprite. */
    public int layer() { return 0; }

    public void tick() {
        prevX = x; prevY = y; prevZ = z;
        if (age++ >= maxAge) dead = true;
        motionY -= 0.04 * gravity;
        move(motionX, motionY, motionZ);
        motionX *= 0.98; motionY *= 0.98; motionZ *= 0.98;
        if (onGround) {
            motionX *= 0.7;
            motionZ *= 0.7;
        }
    }

    /** Moves by clipping against block collision boxes, Y first, as entities do. */
    protected void move(double dx, double dy, double dz) {
        if (noClip) {
            x += dx; y += dy; z += dz;
            return;
        }
        double wantedX = dx, wantedY = dy, wantedZ = dz;
        AxisAlignedBB box = new AxisAlignedBB(x - width / 2, y, z - width / 2, x + width / 2, y + height, z + width / 2);
        AxisAlignedBB area = box.addCoord(dx, dy, dz);
        List<AxisAlignedBB> boxes = new java.util.ArrayList<>();
        int minX = floor(area.minX()), maxX = floor(area.maxX());
        int minY = floor(area.minY()) - 1, maxY = floor(area.maxY());
        int minZ = floor(area.minZ()), maxZ = floor(area.maxZ());
        for (int by = Math.max(0, minY); by <= Math.min(World.HEIGHT - 1, maxY); by++)
            for (int bz = minZ; bz <= maxZ; bz++)
                for (int bx = minX; bx <= maxX; bx++) {
                    if (world.generatedChunk(bx >> 4, bz >> 4) == null) continue;
                    for (AxisAlignedBB block : Block.collisionBoxesAt(world, bx, by, bz))
                        if (block.intersects(area)) boxes.add(block);
                }
        for (AxisAlignedBB block : boxes) dy = block.clipY(box, dy);
        box = box.offset(0, dy, 0);
        for (AxisAlignedBB block : boxes) dx = block.clipX(box, dx);
        box = box.offset(dx, 0, 0);
        for (AxisAlignedBB block : boxes) dz = block.clipZ(box, dz);
        box = box.offset(0, 0, dz);
        x = (box.minX() + box.maxX()) / 2;
        y = box.minY();
        z = (box.minZ() + box.maxZ()) / 2;
        onGround = wantedY != dy && wantedY < 0;
        if (wantedX != dx) motionX = 0;
        if (wantedY != dy) motionY = 0;
        if (wantedZ != dz) motionZ = 0;
    }

    protected static int floor(double value) { return (int) Math.floor(value); }

    /** {sky, block} light levels the particle is lit by (the light at 0.85 of its height). */
    public int[] light(float partialTicks) {
        int bx = floor(x), by = floor(y + height * 0.85), bz = floor(z);
        if (by < 0 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null) return new int[]{0, 0};
        return new int[]{world.getSkyLight(bx, by, bz), world.getBlockLight(bx, by, bz)};
    }

    protected float size(float partialTicks) { return 0.1f * scale; }

    /**
     * Emits the camera-facing quad (inside a {@code GL_QUADS} batch).
     * {@code left} and {@code up} are the camera's axes; {@code light} the
     * lightmap colour.
     */
    public void render(float partialTicks, float[] left, float[] up, float[] light) {
        float u0 = (textureIndex % 16) / 16f, u1 = u0 + 0.0624375f;
        float v0 = (textureIndex / 16) / 16f, v1 = v0 + 0.0624375f;
        quad(partialTicks, left, up, light, u1, v1, u1, v0, u0, v0, u0, v1);
    }

    protected void quad(float partialTicks, float[] left, float[] up, float[] light,
                        float ua, float va, float ub, float vb, float uc, float vc, float ud, float vd) {
        float s = size(partialTicks);
        double cx = prevX + (x - prevX) * partialTicks;
        double cy = prevY + (y - prevY) * partialTicks;
        double cz = prevZ + (z - prevZ) * partialTicks;
        glColor4f(red * light[0], green * light[1], blue * light[2], alpha);
        glTexCoord2f(ua, va);
        glVertex3d(cx - left[0] * s - up[0] * s, cy - left[1] * s - up[1] * s, cz - left[2] * s - up[2] * s);
        glTexCoord2f(ub, vb);
        glVertex3d(cx - left[0] * s + up[0] * s, cy - left[1] * s + up[1] * s, cz - left[2] * s + up[2] * s);
        glTexCoord2f(uc, vc);
        glVertex3d(cx + left[0] * s + up[0] * s, cy + left[1] * s + up[1] * s, cz + left[2] * s + up[2] * s);
        glTexCoord2f(ud, vd);
        glVertex3d(cx + left[0] * s - up[0] * s, cy + left[1] * s - up[1] * s, cz + left[2] * s - up[2] * s);
    }
}

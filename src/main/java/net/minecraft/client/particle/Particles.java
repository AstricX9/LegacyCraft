package net.minecraft.client.particle;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.World;

import java.util.Random;

/**
 * The particle kinds LegacyCraft spawns, after 1.8.9's EntityFX subclasses
 * (HYPOTHESIS: recalled textures, lifetimes and motion).
 */
public final class Particles {
    private Particles() {}

    /**
     * A fleck of a block (EntityDiggingFX): a random quarter-by-quarter
     * piece of the block's particle texture, tinted like the block, dimmed
     * to 60%, half the usual size, falling with the block's gravity.
     */
    public static final class Digging extends Particle {
        private final TextureAtlas.Tile tile;

        public Digging(World world, Random random, double x, double y, double z, double vx, double vy, double vz,
                       TextureAtlas.Tile tile, int tint) {
            super(world, random, x, y, z, vx, vy, vz);
            this.tile = tile;
            gravity = 1f;
            red = green = blue = 0.6f;
            red *= (tint >> 16 & 255) / 255f;
            green *= (tint >> 8 & 255) / 255f;
            blue *= (tint & 255) / 255f;
            scale /= 2f;
        }

        @Override public int layer() { return 1; }

        @Override public void render(float partialTicks, float[] left, float[] up, float[] light) {
            if (tile == null) return;
            float width = tile.u1() - tile.u0(), height = tile.v1() - tile.v0();
            float u0 = tile.u0() + width * (jitterX / 4f), u1 = tile.u0() + width * ((jitterX + 1f) / 4f);
            float v0 = tile.v0() + height * (jitterY / 4f), v1 = tile.v0() + height * ((jitterY + 1f) / 4f);
            quad(partialTicks, left, up, light, u0, v1, u0, v0, u1, v0, u1, v1);
        }
    }

    /**
     * Smoke (EntitySmokeFX): grey, rising slowly, drifting sideways when it
     * stops rising, growing in over its first ticks and running through the
     * eight smoke frames. {@code size} is 1 for normal and 2.5 for large smoke.
     */
    public static class Smoke extends Particle {
        private final float baseScale;

        public Smoke(World world, Random random, double x, double y, double z, double vx, double vy, double vz, float size) {
            super(world, random, x, y, z, 0, 0, 0);
            motionX = motionX * 0.1 + vx;
            motionY = motionY * 0.1 + vy;
            motionZ = motionZ * 0.1 + vz;
            red = green = blue = (float) (Math.random() * 0.3);
            scale *= 0.75f * size;
            baseScale = scale;
            maxAge = (int) (8.0 / (Math.random() * 0.8 + 0.2) * size);
        }

        @Override protected float size(float partialTicks) {
            float grown = Math.max(0f, Math.min(1f, (age + partialTicks) / maxAge * 32f));
            return 0.1f * baseScale * grown;
        }

        @Override public void tick() {
            prevX = x; prevY = y; prevZ = z;
            if (age++ >= maxAge) dead = true;
            textureIndex = 7 - age * 8 / maxAge;
            motionY += 0.004;
            move(motionX, motionY, motionZ);
            if (y == prevY) {
                motionX *= 1.1;
                motionZ *= 1.1;
            }
            motionX *= 0.96; motionY *= 0.96; motionZ *= 0.96;
            if (onGround) {
                motionX *= 0.7;
                motionZ *= 0.7;
            }
        }
    }

    /**
     * Redstone dust glow (EntityReddustFX): red smoke-frames that neither
     * rise nor fall.
     */
    public static final class Reddust extends Particle {
        private final float baseScale;

        public Reddust(World world, Random random, double x, double y, double z) {
            super(world, random, x, y, z, 0, 0, 0);
            motionX *= 0.1; motionY *= 0.1; motionZ *= 0.1;
            float shade = (float) Math.random() * 0.4f + 0.6f;
            red = ((float) (Math.random() * 0.2) + 0.8f) * shade;
            green = ((float) (Math.random() * 0.2) + 0.8f) * 0f * shade;
            blue = ((float) (Math.random() * 0.2) + 0.8f) * 0f * shade;
            scale *= 0.75f;
            baseScale = scale;
            maxAge = (int) (8.0 / (Math.random() * 0.8 + 0.2));
        }

        @Override protected float size(float partialTicks) {
            float grown = Math.max(0f, Math.min(1f, (age + partialTicks) / maxAge * 32f));
            return 0.1f * baseScale * grown;
        }

        @Override public void tick() {
            prevX = x; prevY = y; prevZ = z;
            if (age++ >= maxAge) dead = true;
            textureIndex = 7 - age * 8 / maxAge;
            move(motionX, motionY, motionZ);
            if (y == prevY) {
                motionX *= 1.1;
                motionZ *= 1.1;
            }
            motionX *= 0.96; motionY *= 0.96; motionZ *= 0.96;
            if (onGround) {
                motionX *= 0.7;
                motionZ *= 0.7;
            }
        }
    }

    /**
     * A flame (EntityFlameFX): barely moving, passing through blocks,
     * shrinking to half as it ages and brightening to full light.
     */
    public static final class Flame extends Particle {
        private final float baseScale;

        public Flame(World world, Random random, double x, double y, double z, double vx, double vy, double vz) {
            super(world, random, x, y, z, vx, vy, vz);
            motionX = motionX * 0.01 + vx;
            motionY = motionY * 0.01 + vy;
            motionZ = motionZ * 0.01 + vz;
            this.x += (random.nextFloat() - random.nextFloat()) * 0.05f;
            this.y += (random.nextFloat() - random.nextFloat()) * 0.05f;
            this.z += (random.nextFloat() - random.nextFloat()) * 0.05f;
            prevX = this.x; prevY = this.y; prevZ = this.z;
            baseScale = scale;
            maxAge = (int) (8.0 / (Math.random() * 0.8 + 0.2)) + 4;
            noClip = true;
            textureIndex = 48;
        }

        @Override protected float size(float partialTicks) {
            float f = (age + partialTicks) / maxAge;
            return 0.1f * baseScale * (1f - f * f * 0.5f);
        }

        @Override public int[] light(float partialTicks) {
            int[] light = super.light(partialTicks);
            float f = Math.max(0f, Math.min(1f, (age + partialTicks) / maxAge));
            light[1] = Math.min(15, light[1] + (int) (f * 15f));
            return light;
        }

        @Override public void tick() {
            prevX = x; prevY = y; prevZ = z;
            if (age++ >= maxAge) dead = true;
            move(motionX, motionY, motionZ);
            motionX *= 0.96; motionY *= 0.96; motionZ *= 0.96;
            if (onGround) {
                motionX *= 0.7;
                motionZ *= 0.7;
            }
        }
    }

    /**
     * A lava pop (EntityLavaFX): thrown up, falling, full bright, shrinking,
     * and trailing smoke more often as it ages.
     */
    public static final class Lava extends Particle {
        private final float baseScale;
        private final ParticleManager manager;

        public Lava(World world, Random random, double x, double y, double z, ParticleManager manager) {
            super(world, random, x, y, z, 0, 0, 0);
            this.manager = manager;
            motionX *= 0.8; motionZ *= 0.8;
            motionY = random.nextFloat() * 0.4f + 0.05f;
            scale *= random.nextFloat() * 2f + 0.2f;
            baseScale = scale;
            maxAge = (int) (16.0 / (Math.random() * 0.8 + 0.2));
            textureIndex = 49;
        }

        @Override protected float size(float partialTicks) {
            float f = (age + partialTicks) / maxAge;
            return 0.1f * baseScale * (1f - f * f);
        }

        @Override public int[] light(float partialTicks) {
            int[] light = super.light(partialTicks);
            light[1] = 15;
            return light;
        }

        @Override public void tick() {
            prevX = x; prevY = y; prevZ = z;
            if (age++ >= maxAge) dead = true;
            if (random.nextFloat() < (float) age / maxAge)
                manager.add(new Smoke(world, random, x, y, z, motionX, motionY, motionZ, 1f));
            motionY -= 0.03;
            move(motionX, motionY, motionZ);
            motionX *= 0.999; motionY *= 0.999; motionZ *= 0.999;
            if (onGround) {
                motionX *= 0.7;
                motionZ *= 0.7;
            }
        }
    }

    /**
     * A drip under a liquid (EntityDropParticleFX): it hangs for 40 ticks,
     * then falls; water splashes on landing, lava stays as a puddle frame.
     * Lava drips cool from yellow to red while hanging.
     */
    public static final class Drip extends Particle {
        private final boolean lava;
        private int hangTicks = 40;
        private final ParticleManager manager;

        public Drip(World world, Random random, double x, double y, double z, boolean lava, ParticleManager manager) {
            super(world, random, x, y, z, 0, 0, 0);
            this.lava = lava;
            this.manager = manager;
            motionX = motionY = motionZ = 0;
            if (lava) { red = 1f; green = 16f / (40 - hangTicks + 16); blue = 4f / (40 - hangTicks + 8); }
            else { red = 0f; green = 0f; blue = 1f; }
            textureIndex = 113;
            setSize(0.01f, 0.01f);
            gravity = 0.06f;
            maxAge = (int) (64.0 / (Math.random() * 0.8 + 0.2));
        }

        @Override public int[] light(float partialTicks) {
            int[] light = super.light(partialTicks);
            if (lava) light[1] = 15;
            return light;
        }

        @Override public void tick() {
            prevX = x; prevY = y; prevZ = z;
            if (lava) { red = 1f; green = 16f / (40 - hangTicks + 16); blue = 4f / (40 - hangTicks + 8); }
            else { red = 0.2f; green = 0.3f; blue = 1f; }
            motionY -= gravity;
            if (hangTicks-- > 0) {
                motionX *= 0.02; motionY *= 0.02; motionZ *= 0.02;
                textureIndex = 113;
            } else {
                textureIndex = 112;
            }
            move(motionX, motionY, motionZ);
            motionX *= 0.98; motionY *= 0.98; motionZ *= 0.98;
            if (maxAge-- <= 0) dead = true;
            if (onGround) {
                if (!lava) {
                    dead = true;
                    manager.add(new Splash(world, random, x, y, z, 0, 0, 0));
                } else {
                    textureIndex = 114;
                }
                motionX *= 0.7;
                motionZ *= 0.7;
            }
            int bx = floor(x), by = floor(y), bz = floor(z);
            if (by >= 0 && by < World.HEIGHT && world.generatedChunk(bx >> 4, bz >> 4) != null) {
                String material = world.get(bx, by, bz).material();
                if (material.equals("water") || material.equals("lava")) dead = true;
            }
        }
    }

    /** A water splash droplet (EntitySplashFX/EntityRainFX): hops up and falls, half of them vanishing on landing. */
    public static final class Splash extends Particle {
        public Splash(World world, Random random, double x, double y, double z, double vx, double vy, double vz) {
            super(world, random, x, y, z, 0, 0, 0);
            motionX *= 0.3;
            motionY = Math.random() * 0.2 + 0.1;
            motionZ *= 0.3;
            textureIndex = 19 + random.nextInt(4);
            setSize(0.01f, 0.01f);
            gravity = 0.04f;
            maxAge = (int) (8.0 / (Math.random() * 0.8 + 0.2));
            if (vy == 0 && (vx != 0 || vz != 0)) {
                motionX = vx;
                motionY = vy + 0.1;
                motionZ = vz;
            }
        }

        @Override public void tick() {
            prevX = x; prevY = y; prevZ = z;
            motionY -= gravity;
            move(motionX, motionY, motionZ);
            motionX *= 0.98; motionY *= 0.98; motionZ *= 0.98;
            if (maxAge-- <= 0) dead = true;
            if (onGround) {
                if (Math.random() < 0.5) dead = true;
                motionX *= 0.7;
                motionZ *= 0.7;
            }
        }
    }

    /** A mote suspended in still water (EntitySuspendFX): dark, tiny, gone once out of water. */
    public static final class Suspended extends Particle {
        public Suspended(World world, Random random, double x, double y, double z) {
            super(world, random, x, y - 0.125, z, 0, 0, 0);
            red = green = blue = 0.4f;
            textureIndex = 0;
            setSize(0.01f, 0.01f);
            scale *= random.nextFloat() * 0.6f + 0.2f;
            motionX = motionY = motionZ = 0;
            maxAge = (int) (16.0 / (Math.random() * 0.8 + 0.2));
        }

        @Override public void tick() {
            prevX = x; prevY = y; prevZ = z;
            move(motionX, motionY, motionZ);
            int bx = floor(x), by = floor(y), bz = floor(z);
            if (by < 0 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null
                || !world.get(bx, by, bz).material().equals("water")) dead = true;
            if (maxAge-- <= 0) dead = true;
        }
    }

    /** Mycelium spores (EntityAuraFX "townaura"): faint grey specks drifting through blocks. */
    public static final class Aura extends Particle {
        public Aura(World world, Random random, double x, double y, double z) {
            super(world, random, x, y, z, 0, 0, 0);
            float shade = random.nextFloat() * 0.1f + 0.2f;
            red = green = blue = shade;
            textureIndex = 0;
            setSize(0.02f, 0.02f);
            scale *= random.nextFloat() * 0.6f + 0.5f;
            motionX *= 0.02; motionY *= 0.02; motionZ *= 0.02;
            maxAge = (int) (20.0 / (Math.random() * 0.8 + 0.2));
            noClip = true;
        }

        @Override public void tick() {
            prevX = x; prevY = y; prevZ = z;
            move(motionX, motionY, motionZ);
            motionX *= 0.99; motionY *= 0.99; motionZ *= 0.99;
            if (maxAge-- <= 0) dead = true;
        }
    }

    /** An underwater bubble (EntityBubbleFX): rises gently and pops once out of water. */
    public static final class Bubble extends Particle {
        public Bubble(World world, Random random, double x, double y, double z, double vx, double vy, double vz) {
            super(world, random, x, y, z, vx, vy, vz);
            textureIndex = 32;
            setSize(0.02f, 0.02f);
            scale *= random.nextFloat() * 0.6f + 0.2f;
            motionX = vx * 0.2 + (Math.random() * 2.0 - 1.0) * 0.02;
            motionY = vy * 0.2 + (Math.random() * 2.0 - 1.0) * 0.02;
            motionZ = vz * 0.2 + (Math.random() * 2.0 - 1.0) * 0.02;
            maxAge = (int) (8.0 / (Math.random() * 0.8 + 0.2));
        }

        @Override public void tick() {
            prevX = x; prevY = y; prevZ = z;
            motionY += 0.002;
            move(motionX, motionY, motionZ);
            motionX *= 0.85; motionY *= 0.85; motionZ *= 0.85;
            int bx = floor(x), by = floor(y), bz = floor(z);
            if (by < 0 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null
                || !world.get(bx, by, bz).material().equals("water")) dead = true;
            if (maxAge-- <= 0) dead = true;
        }
    }
}

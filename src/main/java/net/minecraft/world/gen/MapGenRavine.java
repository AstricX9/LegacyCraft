package net.minecraft.world.gen;

import net.minecraft.util.MathHelper;
import net.minecraft.world.biome.Biome;

import java.util.Random;

/**
 * Ravines (vanilla MapGenRavine): rare, tall, narrow carvings with ragged
 * walls, started like caves from any chunk within 8 chunks. Only stone, dirt
 * and grass are carved; below y=11 they fill with lava.
 *
 * <p>HYPOTHESIS: recalled from 1.8.9.
 */
public final class MapGenRavine {
    private static final int RANGE = 8;
    private final GenBlocks b;
    private final long seed;
    private final Random rand = new Random();
    /** Squared width multiplier per height: what makes the walls ragged. */
    private final float[] wallProfile = new float[1024];
    private ChunkPrimer primer;
    private Biome[] biomes;

    public MapGenRavine(GenBlocks blocks, long seed) {
        this.b = blocks;
        this.seed = seed;
    }

    public void generate(int chunkX, int chunkZ, ChunkPrimer primer, Biome[] biomes) {
        this.primer = primer;
        this.biomes = biomes;
        rand.setSeed(seed);
        long xMultiplier = rand.nextLong(), zMultiplier = rand.nextLong();
        for (int x = chunkX - RANGE; x <= chunkX + RANGE; x++) {
            for (int z = chunkZ - RANGE; z <= chunkZ + RANGE; z++) {
                rand.setSeed((long) x * xMultiplier ^ (long) z * zMultiplier ^ seed);
                if (rand.nextInt(50) != 0) continue;
                double startX = x * 16 + rand.nextInt(16);
                double startY = rand.nextInt(rand.nextInt(40) + 8) + 20;
                double startZ = z * 16 + rand.nextInt(16);
                float yaw = rand.nextFloat() * ((float) Math.PI * 2F);
                float pitch = (rand.nextFloat() - 0.5F) * 2.0F / 8.0F;
                float width = (rand.nextFloat() * 2.0F + rand.nextFloat()) * 2.0F;
                addTunnel(rand.nextLong(), chunkX, chunkZ, startX, startY, startZ, width, yaw, pitch, 0, 0, 3.0);
            }
        }
        this.primer = null;
        this.biomes = null;
    }

    private void addTunnel(long tunnelSeed, int chunkX, int chunkZ, double x, double y, double z, float width,
                           float yaw, float pitch, int step, int steps, double verticalScale) {
        Random random = new Random(tunnelSeed);
        double centreX = chunkX * 16 + 8, centreZ = chunkZ * 16 + 8;
        float yawChange = 0.0F, pitchChange = 0.0F;
        if (steps <= 0) {
            int max = RANGE * 16 - 16;
            steps = max - random.nextInt(max / 4);
        }
        boolean single = false;
        if (step == -1) {
            step = steps / 2;
            single = true;
        }
        float profile = 1.0F;
        for (int level = 0; level < 256; level++) {
            if (level == 0 || random.nextInt(3) == 0) profile = 1.0F + random.nextFloat() * random.nextFloat() * 1.0F;
            wallProfile[level] = profile * profile;
        }
        for (; step < steps; step++) {
            double radius = 1.5 + MathHelper.sin(step * (float) Math.PI / steps) * width * 1.0F;
            double radiusY = radius * verticalScale;
            radius *= random.nextFloat() * 0.25 + 0.75;
            radiusY *= random.nextFloat() * 0.25 + 0.75;
            float cosPitch = MathHelper.cos(pitch);
            float sinPitch = MathHelper.sin(pitch);
            x += MathHelper.cos(yaw) * cosPitch;
            y += sinPitch;
            z += MathHelper.sin(yaw) * cosPitch;
            pitch *= 0.7F;
            pitch += pitchChange * 0.05F;
            yaw += yawChange * 0.05F;
            pitchChange *= 0.8F;
            yawChange *= 0.5F;
            pitchChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yawChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            if (!single && random.nextInt(4) == 0) continue;

            double dx = x - centreX, dz = z - centreZ;
            double remaining = steps - step;
            double reach = width + 2.0F + 16.0F;
            if (dx * dx + dz * dz - remaining * remaining > reach * reach) return;
            if (x < centreX - 16.0 - radius * 2.0 || z < centreZ - 16.0 - radius * 2.0
                || x > centreX + 16.0 + radius * 2.0 || z > centreZ + 16.0 + radius * 2.0) continue;

            int minX = MathHelper.floor(x - radius) - chunkX * 16 - 1;
            int maxX = MathHelper.floor(x + radius) - chunkX * 16 + 1;
            int minY = MathHelper.floor(y - radiusY) - 1;
            int maxY = MathHelper.floor(y + radiusY) + 1;
            int minZ = MathHelper.floor(z - radius) - chunkZ * 16 - 1;
            int maxZ = MathHelper.floor(z + radius) - chunkZ * 16 + 1;
            if (minX < 0) minX = 0;
            if (maxX > 16) maxX = 16;
            if (minY < 1) minY = 1;
            if (maxY > 248) maxY = 248;
            if (minZ < 0) minZ = 0;
            if (maxZ > 16) maxZ = 16;

            if (MapGenCaves.touchesWater(primer, b, minX, maxX, minY, maxY, minZ, maxZ)) continue;
            for (int localX = minX; localX < maxX; localX++) {
                double nx = (localX + chunkX * 16 + 0.5 - x) / radius;
                for (int localZ = minZ; localZ < maxZ; localZ++) {
                    double nz = (localZ + chunkZ * 16 + 0.5 - z) / radius;
                    boolean foundGrass = false;
                    if (nx * nx + nz * nz >= 1.0) continue;
                    for (int localY = maxY; localY > minY; localY--) {
                        double ny = (localY - 1 + 0.5 - y) / radiusY;
                        if ((nx * nx + nz * nz) * wallProfile[localY - 1] + ny * ny / 6.0 >= 1.0) continue;
                        int id = GenBlocks.id(primer.get(localX, localY, localZ));
                        if (id == b.grassId) foundGrass = true;
                        if (id != b.stoneId && id != b.dirtId && id != b.grassId) continue;
                        if (localY - 1 < 10) {
                            primer.set(localX, localY, localZ, b.FLOWING_LAVA);
                        } else {
                            primer.set(localX, localY, localZ, b.AIR);
                            if (foundGrass && GenBlocks.id(primer.get(localX, localY - 1, localZ)) == b.dirtId)
                                primer.set(localX, localY - 1, localZ, biomes[localZ * 16 + localX].topBlock().packed());
                        }
                    }
                }
            }
            if (single) break;
        }
    }
}

package net.minecraft.world.gen;

import net.minecraft.util.MathHelper;

import java.util.Random;

/**
 * The Nether's caves (1.8.9 MapGenCavesHell, HYPOTHESIS: recalled): the
 * overworld's tunnel walk with fewer systems (rand(rand(rand(10)+1)+1),
 * only one chunk in five), any start height up to 128, tunnels twice as
 * wide, carving netherrack (and stray dirt or grass) to air between y=1 and
 * 120, and stopping short of lava instead of water.
 */
final class MapGenCavesHell {
    private static final int RANGE = 8;
    private final GenBlocks b;
    private final long seed;
    private final Random rand = new Random();
    private ChunkPrimer primer;

    MapGenCavesHell(GenBlocks blocks, long seed) {
        this.b = blocks;
        this.seed = seed;
    }

    void generate(int chunkX, int chunkZ, ChunkPrimer primer) {
        this.primer = primer;
        rand.setSeed(seed);
        long xMultiplier = rand.nextLong(), zMultiplier = rand.nextLong();
        for (int x = chunkX - RANGE; x <= chunkX + RANGE; x++)
            for (int z = chunkZ - RANGE; z <= chunkZ + RANGE; z++) {
                rand.setSeed((long) x * xMultiplier ^ (long) z * zMultiplier ^ seed);
                startCaves(x, z, chunkX, chunkZ);
            }
        this.primer = null;
    }

    private void startCaves(int sourceX, int sourceZ, int chunkX, int chunkZ) {
        int count = rand.nextInt(rand.nextInt(rand.nextInt(10) + 1) + 1);
        if (rand.nextInt(5) != 0) count = 0;
        for (int i = 0; i < count; i++) {
            double x = sourceX * 16 + rand.nextInt(16);
            double y = rand.nextInt(128);
            double z = sourceZ * 16 + rand.nextInt(16);
            int tunnels = 1;
            if (rand.nextInt(4) == 0) {
                addTunnel(rand.nextLong(), chunkX, chunkZ, x, y, z, 1.0F + rand.nextFloat() * 6.0F, 0.0F, 0.0F, -1, -1, 0.5);
                tunnels += rand.nextInt(4);
            }
            for (int j = 0; j < tunnels; j++) {
                float yaw = rand.nextFloat() * ((float) Math.PI * 2F);
                float pitch = (rand.nextFloat() - 0.5F) * 2.0F / 8.0F;
                float width = rand.nextFloat() * 2.0F + rand.nextFloat();
                addTunnel(rand.nextLong(), chunkX, chunkZ, x, y, z, width * 2.0F, yaw, pitch, 0, 0, 0.5);
            }
        }
    }

    private void addTunnel(long tunnelSeed, int chunkX, int chunkZ, double x, double y, double z, float width,
                           float yaw, float pitch, int step, int steps, double verticalScale) {
        double centreX = chunkX * 16 + 8, centreZ = chunkZ * 16 + 8;
        float yawChange = 0.0F, pitchChange = 0.0F;
        Random random = new Random(tunnelSeed);
        if (steps <= 0) {
            int max = RANGE * 16 - 16;
            steps = max - random.nextInt(max / 4);
        }
        boolean room = false;
        if (step == -1) {
            step = steps / 2;
            room = true;
        }
        int branchStep = random.nextInt(steps / 2) + steps / 4;
        for (boolean steep = random.nextInt(6) == 0; step < steps; step++) {
            double radius = 1.5 + MathHelper.sin(step * (float) Math.PI / steps) * width * 1.0F;
            double radiusY = radius * verticalScale;
            float cosPitch = MathHelper.cos(pitch);
            float sinPitch = MathHelper.sin(pitch);
            x += MathHelper.cos(yaw) * cosPitch;
            y += sinPitch;
            z += MathHelper.sin(yaw) * cosPitch;
            pitch *= steep ? 0.92F : 0.7F;
            pitch += pitchChange * 0.1F;
            yaw += yawChange * 0.1F;
            pitchChange *= 0.9F;
            yawChange *= 0.75F;
            pitchChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yawChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;

            if (!room && step == branchStep && width > 1.0F) {
                addTunnel(random.nextLong(), chunkX, chunkZ, x, y, z, random.nextFloat() * 0.5F + 0.5F,
                    yaw - (float) Math.PI / 2F, pitch / 3.0F, step, steps, 1.0);
                addTunnel(random.nextLong(), chunkX, chunkZ, x, y, z, random.nextFloat() * 0.5F + 0.5F,
                    yaw + (float) Math.PI / 2F, pitch / 3.0F, step, steps, 1.0);
                return;
            }
            if (!room && random.nextInt(4) == 0) continue;

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
            if (maxY > 120) maxY = 120;
            if (minZ < 0) minZ = 0;
            if (maxZ > 16) maxZ = 16;

            if (touchesLava(minX, maxX, minY, maxY, minZ, maxZ)) continue;
            for (int localX = minX; localX < maxX; localX++) {
                double nx = (localX + chunkX * 16 + 0.5 - x) / radius;
                for (int localZ = minZ; localZ < maxZ; localZ++) {
                    double nz = (localZ + chunkZ * 16 + 0.5 - z) / radius;
                    for (int localY = maxY - 1; localY >= minY; localY--) {
                        double ny = (localY + 0.5 - y) / radiusY;
                        if (ny > -0.7 && nx * nx + ny * ny + nz * nz < 1.0) {
                            int id = GenBlocks.id(primer.get(localX, localY, localZ));
                            if (id == b.netherrackId || id == b.dirtId || id == b.grassId) primer.set(localX, localY, localZ, b.AIR);
                        }
                    }
                }
            }
            if (room) break;
        }
    }

    /** The box's shell (and the layer below) is checked for lava first. */
    private boolean touchesLava(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        for (int x = minX; x < maxX; x++)
            for (int z = minZ; z < maxZ; z++)
                for (int y = maxY + 1; y >= minY - 1; y--) {
                    if (y < 0 || y >= 128) continue;
                    int id = GenBlocks.id(primer.get(x, y, z));
                    if (id == b.flowingLavaId || id == b.lavaId) return true;
                    if (y != minY - 1 && x != minX && x != maxX - 1 && z != minZ && z != maxZ - 1) y = minY;
                }
        return false;
    }
}

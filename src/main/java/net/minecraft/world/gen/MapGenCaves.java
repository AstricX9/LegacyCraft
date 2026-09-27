package net.minecraft.world.gen;

import net.minecraft.util.MathHelper;
import net.minecraft.world.biome.Biome;

import java.util.Random;

/**
 * Cave tunnels and rooms (vanilla MapGenCaves). Every chunk within 8 chunks
 * may start a cave system from a Random seeded by its position; each system
 * wanders from its start and carves whatever part of it crosses the chunk
 * being generated, so caves continue seamlessly across chunk borders without
 * any chunk needing its neighbours.
 *
 * <p>HYPOTHESIS: recalled from 1.8.9; draw order and float/double use follow
 * the original so shapes match block for block if the recall is right.
 */
public final class MapGenCaves {
    private static final int RANGE = 8;
    private final GenBlocks b;
    private final long seed;
    private final Random rand = new Random();
    private ChunkPrimer primer;
    private Biome[] biomes;

    public MapGenCaves(GenBlocks blocks, long seed) {
        this.b = blocks;
        this.seed = seed;
    }

    /** Carves the chunk; {@code biomes} are its column biomes (index z * 16 + x). */
    public void generate(int chunkX, int chunkZ, ChunkPrimer primer, Biome[] biomes) {
        this.primer = primer;
        this.biomes = biomes;
        rand.setSeed(seed);
        long xMultiplier = rand.nextLong(), zMultiplier = rand.nextLong();
        for (int x = chunkX - RANGE; x <= chunkX + RANGE; x++) {
            for (int z = chunkZ - RANGE; z <= chunkZ + RANGE; z++) {
                rand.setSeed((long) x * xMultiplier ^ (long) z * zMultiplier ^ seed);
                startCaves(x, z, chunkX, chunkZ);
            }
        }
        this.primer = null;
        this.biomes = null;
    }

    private void startCaves(int sourceX, int sourceZ, int chunkX, int chunkZ) {
        int count = rand.nextInt(rand.nextInt(rand.nextInt(15) + 1) + 1);
        if (rand.nextInt(7) != 0) count = 0;
        for (int i = 0; i < count; i++) {
            double x = sourceX * 16 + rand.nextInt(16);
            double y = rand.nextInt(rand.nextInt(120) + 8);
            double z = sourceZ * 16 + rand.nextInt(16);
            int tunnels = 1;
            if (rand.nextInt(4) == 0) {
                addRoom(rand.nextLong(), chunkX, chunkZ, x, y, z);
                tunnels += rand.nextInt(4);
            }
            for (int j = 0; j < tunnels; j++) {
                float yaw = rand.nextFloat() * ((float) Math.PI * 2F);
                float pitch = (rand.nextFloat() - 0.5F) * 2.0F / 8.0F;
                float width = rand.nextFloat() * 2.0F + rand.nextFloat();
                if (rand.nextInt(10) == 0) width *= rand.nextFloat() * rand.nextFloat() * 3.0F + 1.0F;
                addTunnel(rand.nextLong(), chunkX, chunkZ, x, y, z, width, yaw, pitch, 0, 0, 1.0);
            }
        }
    }

    private void addRoom(long seed, int chunkX, int chunkZ, double x, double y, double z) {
        addTunnel(seed, chunkX, chunkZ, x, y, z, 1.0F + rand.nextFloat() * 6.0F, 0.0F, 0.0F, -1, -1, 0.5);
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

            if (!room && step == branchStep && width > 1.0F && steps > 0) {
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
            if (maxY > 248) maxY = 248;
            if (minZ < 0) minZ = 0;
            if (maxZ > 16) maxZ = 16;

            if (!touchesWater(primer, b, minX, maxX, minY, maxY, minZ, maxZ)) {
                for (int localX = minX; localX < maxX; localX++) {
                    double nx = (localX + chunkX * 16 + 0.5 - x) / radius;
                    for (int localZ = minZ; localZ < maxZ; localZ++) {
                        double nz = (localZ + chunkZ * 16 + 0.5 - z) / radius;
                        boolean foundTop = false;
                        if (nx * nx + nz * nz >= 1.0) continue;
                        for (int localY = maxY; localY > minY; localY--) {
                            double ny = (localY - 1 + 0.5 - y) / radiusY;
                            if (ny > -0.7 && nx * nx + ny * ny + nz * nz < 1.0) {
                                int state = primer.get(localX, localY, localZ);
                                int above = primer.get(localX, localY + 1, localZ);
                                int id = GenBlocks.id(state);
                                if (id == b.grassId || id == b.myceliumId) foundTop = true;
                                if (canReplace(state, above)) {
                                    if (localY - 1 < 10) {
                                        primer.set(localX, localY, localZ, b.LAVA);
                                    } else {
                                        primer.set(localX, localY, localZ, b.AIR);
                                        if (GenBlocks.id(above) == b.sandId)
                                            primer.set(localX, localY + 1, localZ,
                                                GenBlocks.meta(above) == 1 ? b.RED_SANDSTONE : b.SANDSTONE);
                                        if (foundTop && GenBlocks.id(primer.get(localX, localY - 1, localZ)) == b.dirtId) {
                                            // The biome's top block, reset to its default variant (podzol comes back as dirt).
                                            int top = biomes[localZ * 16 + localX].topBlock().packed();
                                            primer.set(localX, localY - 1, localZ, GenBlocks.withMeta(top, 0));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (room) break;
            }
        }
    }

    /** Tunnels stop short of water: the box's shell (and the layer just below it) is checked first. */
    static boolean touchesWater(ChunkPrimer primer, GenBlocks b, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int y = maxY + 1; y >= minY - 1; y--) {
                    if (y >= 0 && y < 256) {
                        int id = GenBlocks.id(primer.get(x, y, z));
                        if (id == b.flowingWaterId || id == b.waterId) return true;
                        if (y != minY - 1 && x != minX && x != maxX - 1 && z != minZ && z != maxZ - 1) y = minY;
                    }
                }
            }
        }
        return false;
    }

    private boolean canReplace(int state, int above) {
        int id = GenBlocks.id(state);
        if (id == b.stoneId || id == b.dirtId || id == b.grassId || id == b.hardenedClayId || id == b.stainedClayId
            || id == b.sandstoneId || id == b.redSandstoneId || id == b.myceliumId || id == b.snowLayerId) return true;
        return (id == b.sandId || id == b.gravelId) && !b.isWater(above);
    }
}

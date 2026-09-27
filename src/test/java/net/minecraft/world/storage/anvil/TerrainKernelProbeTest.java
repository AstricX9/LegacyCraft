package net.minecraft.world.storage.anvil;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.TerrainGenerator;
import net.minecraft.world.gen.layer.BiomeLayers;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Not a check — a probe. Splits the terrain error by whether a column's 5x5
 * biome kernel is uniform. Inside one big biome the kernel cannot change
 * anything (every neighbour contributes the same depth and scale), so if those
 * columns are exact the blending is at fault, and if they are not the fault is
 * in the per-column density maths instead.
 */
class TerrainKernelProbeTest {
    private static final Path WORLD = Path.of(System.getProperty("legacycraft.vanillaWorld", "C:/Server/world"));
    private static final Set<Integer> NOT_TERRAIN = Set.of(
        0, 6, 8, 9, 10, 11, 17, 18, 30, 31, 32, 37, 38, 39, 40, 50, 54, 59, 63, 68, 78, 79,
        81, 83, 86, 99, 100, 103, 104, 105, 106, 111, 141, 142, 161, 162, 175);

    @Test
    void isTheBlendingAtFaultOrTheColumnMaths() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(WORLD.resolve("region")), "no vanilla world");
        long seed = NbtIo.readCompressed(WORLD.resolve("level.dat")).getCompound("Data").getLong("RandomSeed");
        BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());
        BiomeRegistry biomes = BiomeRegistry.load(new ClasspathResourceManager(), blocks);
        BiomeLayers layers = new BiomeLayers(seed);
        TerrainGenerator terrain = new TerrainGenerator(seed, layers, biomes);

        long[] uniform = new long[3], mixed = new long[3];
        // Error by position inside the 4x4 cell: (0,0) is an actual density sample, the rest are interpolated.
        long[][] bySubPosition = new long[16][3];
        for (Path file : regionFiles()) {
            RegionFile region = RegionFile.open(file);
            for (int cx = 0; cx < RegionFile.CHUNKS; cx++) {
                for (int cz = 0; cz < RegionFile.CHUNKS; cz++) {
                    int chunkX = region.regionX() * RegionFile.CHUNKS + cx, chunkZ = region.regionZ() * RegionFile.CHUNKS + cz;
                    var root = region.readChunk(chunkX, chunkZ);
                    if (root == null) continue;
                    var imported = AnvilChunkReader.read(root, 0);
                    int[] ours = terrain.terrainHeights(chunkX, chunkZ);
                    int[] biomeGrid = layers.coarse().getInts(chunkX * 4 - 2, chunkZ * 4 - 2, 10, 10);
                    for (int gridX = 0; gridX < 4; gridX++) {
                        for (int gridZ = 0; gridZ < 4; gridZ++) {
                            long[] bucket = kernelIsUniform(biomeGrid, gridX, gridZ) ? uniform : mixed;
                            for (int x = gridX * 4; x < gridX * 4 + 4; x++) {
                                for (int z = gridZ * 4; z < gridZ * 4 + 4; z++) {
                                    int delta = ours[z * Chunk.SIZE + x] - vanillaHeight(imported, x, z);
                                    bucket[0]++;
                                    bucket[1] += delta;
                                    if (delta == 0) bucket[2]++;
                                    long[] sub = bySubPosition[(x % 4) * 4 + (z % 4)];
                                    sub[0]++;
                                    sub[1] += delta;
                                    if (delta == 0) sub[2]++;
                                }
                            }
                        }
                    }
                }
            }
        }
        report("uniform kernel (biome interior)", uniform);
        report("mixed kernel (near a border)", mixed);
        for (int subX = 0; subX < 4; subX++)
            for (int subZ = 0; subZ < 4; subZ++)
                report("cell offset " + subX + "," + subZ, bySubPosition[subX * 4 + subZ]);
    }

    private static void report(String label, long[] stats) {
        if (stats[0] == 0) { System.out.println(label + ": none"); return; }
        System.out.printf("%-34s columns %7d   exact %5.1f%%   mean error %+.2f%n",
            label, stats[0], 100.0 * stats[2] / stats[0], (double) stats[1] / stats[0]);
    }

    /** True when all 25 biomes the kernel reads for this grid cell are the same. */
    private static boolean kernelIsUniform(int[] grid, int gridX, int gridZ) {
        int centre = grid[gridX + 2 + (gridZ + 2) * 10];
        for (int offsetX = -2; offsetX <= 2; offsetX++)
            for (int offsetZ = -2; offsetZ <= 2; offsetZ++)
                if (grid[gridX + offsetX + 2 + (gridZ + offsetZ + 2) * 10] != centre) return false;
        return true;
    }

    private static int vanillaHeight(AnvilChunkReader.Imported imported, int x, int z) {
        for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
            int id = imported.chunk().get(x, y, z) >>> 4;
            if (!NOT_TERRAIN.contains(id)) return y;
        }
        return -1;
    }

    private Iterable<Path> regionFiles() throws IOException {
        try (var files = Files.list(WORLD.resolve("region"))) {
            return files.filter(p -> p.getFileName().toString().endsWith(".mca")).toList();
        }
    }
}

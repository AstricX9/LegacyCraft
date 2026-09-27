package net.minecraft.world.storage.anvil;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTag;
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
 * Not a check — a probe. Puts our height next to vanilla's along a line and
 * measures how well the two move together, which separates "the noise field is
 * in the wrong place" from "the noise field is right but mapped to the wrong
 * heights".
 */
class TerrainShapeProbeTest {
    private static final Path WORLD = Path.of(System.getProperty("legacycraft.vanillaWorld", "C:/Server/world"));
    private static final Set<Integer> NOT_TERRAIN = Set.of(
        0, 6, 8, 9, 10, 11, 17, 18, 30, 31, 32, 37, 38, 39, 40, 50, 54, 59, 63, 68, 78, 79,
        81, 83, 86, 99, 100, 103, 104, 105, 106, 111, 141, 142, 161, 162, 175);

    @Test
    void doOurHillsSitWhereVanillasDo() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(WORLD.resolve("region")), "no vanilla world");
        long seed = NbtIo.readCompressed(WORLD.resolve("level.dat")).getCompound("Data").getLong("RandomSeed");
        BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());
        BiomeRegistry biomes = BiomeRegistry.load(new ClasspathResourceManager(), blocks);
        BiomeLayers layers = new BiomeLayers(seed);
        // The control: same biome map, noise seeded wrongly. If the correlation barely drops,
        // the biome map is doing all the work and our noise field is not vanilla's.
        boolean control = Boolean.getBoolean("legacycraft.controlNoise");
        TerrainGenerator terrain = new TerrainGenerator(control ? seed + 1 : seed, layers, biomes);
        RegionFile region = RegionFile.open(WORLD.resolve("region/r.-1.0.mca"));

        // Correlate the two height fields over many chunks: 1.0 would mean identical shape.
        double sumOurs = 0, sumTheirs = 0, sumBoth = 0, sumOursSquared = 0, sumTheirsSquared = 0;
        long count = 0;
        StringBuilder transect = new StringBuilder();
        for (int cx = 0; cx < RegionFile.CHUNKS; cx++) {
            for (int cz = 0; cz < RegionFile.CHUNKS; cz++) {
                int chunkX = region.regionX() * RegionFile.CHUNKS + cx, chunkZ = region.regionZ() * RegionFile.CHUNKS + cz;
                NbtTag.Compound root = region.readChunk(chunkX, chunkZ);
                if (root == null) continue;
                var imported = AnvilChunkReader.read(root, 0);
                int[] ours = terrain.terrainHeights(chunkX, chunkZ);
                for (int x = 0; x < Chunk.SIZE; x++) {
                    for (int z = 0; z < Chunk.SIZE; z++) {
                        double mine = ours[z * Chunk.SIZE + x], theirs = vanillaHeight(imported, x, z);
                        sumOurs += mine; sumTheirs += theirs; sumBoth += mine * theirs;
                        sumOursSquared += mine * mine; sumTheirsSquared += theirs * theirs;
                        count++;
                        if (transect.length() < 2000 && z == 8 && cz == 16)
                            transect.append(String.format("%d\t%.0f\t%.0f\t%s%n",
                                chunkX * 16 + x, mine, theirs, biomes.byId(imported.biome(x, z)).name().path()));
                    }
                }
            }
        }
        double covariance = sumBoth / count - sumOurs / count * (sumTheirs / count);
        double ourDeviation = Math.sqrt(sumOursSquared / count - Math.pow(sumOurs / count, 2));
        double theirDeviation = Math.sqrt(sumTheirsSquared / count - Math.pow(sumTheirs / count, 2));
        System.out.printf("columns %d   mean ours %.2f vanilla %.2f   spread ours %.2f vanilla %.2f   correlation %.4f%n",
            count, sumOurs / count, sumTheirs / count, ourDeviation, theirDeviation, covariance / (ourDeviation * theirDeviation));
        System.out.println("x\tours\tvanilla\tbiome");
        System.out.print(transect);
    }

    private static int vanillaHeight(AnvilChunkReader.Imported imported, int x, int z) {
        for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
            int id = imported.chunk().get(x, y, z) >>> 4;
            if (!NOT_TERRAIN.contains(id)) return y;
        }
        return -1;
    }
}

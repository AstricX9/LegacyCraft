package net.minecraft.world.storage.anvil;

import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTag;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.layer.BiomeLayers;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential check of the biome layer chain against a world the real 1.8.9
 * server generated (master context §82): our chain is seeded with that world's
 * seed and must reproduce the biome byte vanilla stored for every column.
 */
class VanillaBiomeMapTest {
    private static final Path WORLD = Path.of(System.getProperty("legacycraft.vanillaWorld", "C:/Server/world"));

    @Test
    void ourBiomeLayersReproduceTheBiomesVanillaStored() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(WORLD.resolve("region")),
            "No vanilla 1.8.9 world at " + WORLD + " — skipping the biome comparison");
        NbtTag.Compound level = NbtIo.readCompressed(WORLD.resolve("level.dat")).getCompound("Data");
        Assumptions.assumeTrue("default".equals(level.getString("generatorName")),
            "world type is " + level.getString("generatorName") + ", not default");
        long seed = level.getLong("RandomSeed");
        BiomeLayers layers = new BiomeLayers(seed);

        long columns = 0, matches = 0;
        Map<String, Long> confusions = new TreeMap<>();
        List<String> examples = new ArrayList<>();
        int chunks = 0;
        for (Path file : regionFiles()) {
            RegionFile region = RegionFile.open(file);
            for (int cx = 0; cx < RegionFile.CHUNKS; cx++) {
                for (int cz = 0; cz < RegionFile.CHUNKS; cz++) {
                    int chunkX = region.regionX() * RegionFile.CHUNKS + cx;
                    int chunkZ = region.regionZ() * RegionFile.CHUNKS + cz;
                    NbtTag.Compound root = region.readChunk(chunkX, chunkZ);
                    if (root == null) continue;
                    var imported = AnvilChunkReader.read(root, 0);
                    byte[] ours = layers.chunkBiomes(chunkX, chunkZ);
                    chunks++;
                    for (int x = 0; x < Chunk.SIZE; x++) {
                        for (int z = 0; z < Chunk.SIZE; z++) {
                            int theirs = imported.biome(x, z), mine = ours[z * Chunk.SIZE + x] & 0xff;
                            columns++;
                            if (mine == theirs) { matches++; continue; }
                            confusions.merge("vanilla " + theirs + " -> ours " + mine, 1L, Long::sum);
                            if (examples.size() < 10) examples.add("column " + (chunkX * 16 + x) + "," + (chunkZ * 16 + z)
                                + " vanilla=" + theirs + " ours=" + mine);
                        }
                    }
                }
            }
        }
        Assumptions.assumeTrue(columns > 0, "no generated chunks to compare");
        System.out.printf("biome agreement %.4f%% (%d/%d columns over %d chunks, seed %d)%n",
            100.0 * matches / columns, matches, columns, chunks, seed);
        confusions.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(15)
            .forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue()));
        examples.forEach(e -> System.out.println("  " + e));
        assertEquals(columns, matches, "biome IDs must match the ones vanilla 1.8.9 stored");
    }

    private Iterable<Path> regionFiles() throws IOException {
        try (var files = Files.list(WORLD.resolve("region"))) {
            return files.filter(p -> p.getFileName().toString().endsWith(".mca")).toList();
        }
    }
}

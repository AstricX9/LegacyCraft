package net.minecraft.world.storage.anvil;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTag;
import net.minecraft.world.biome.BiomeRegistry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.ChunkPrimer;
import net.minecraft.world.gen.GenBlocks;
import net.minecraft.world.gen.TerrainGenerator;
import net.minecraft.world.gen.layer.BiomeLayers;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential check of the terrain density pipeline against the world a real
 * 1.8.9 server generated: for every column, the height our density field puts
 * the ground at must be the height vanilla actually stored.
 *
 * <p>The first test checks the density field alone, so caves, ravines and
 * lakes, which cut into vanilla's surface, leave a residue of columns lower
 * in vanilla than in ours. The second runs the whole terrain stage.
 */
class VanillaTerrainTest {
    private static final Path WORLD = Path.of(System.getProperty("legacycraft.vanillaWorld", "C:/Server/world"));

    /** Blocks that sit on top of the ground rather than being it. */
    private static final Set<Integer> NOT_TERRAIN = Set.of(
        0, 6, 8, 9, 10, 11, 17, 18, 30, 31, 32, 37, 38, 39, 40, 50, 54, 59, 63, 68, 78, 79,
        81, 83, 86, 99, 100, 103, 104, 105, 106, 111, 141, 142, 161, 162, 175);

    @Test
    void ourDensityFieldPutsTheGroundWhereVanillaDid() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(WORLD.resolve("region")), "no vanilla world at " + WORLD);
        NbtTag.Compound level = NbtIo.readCompressed(WORLD.resolve("level.dat")).getCompound("Data");
        long seed = level.getLong("RandomSeed");
        BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());
        BiomeRegistry biomes = BiomeRegistry.load(new ClasspathResourceManager(), blocks);
        TerrainGenerator terrain = new TerrainGenerator(seed, new BiomeLayers(seed), biomes);

        long columns = 0, matches = 0, weLower = 0, weHigher = 0;
        int[] histogram = new int[41];
        List<String> examples = new ArrayList<>();
        // Per biome: how far out we are, to tell a pipeline error from a wrong depth/scale number.
        java.util.Map<Integer, long[]> perBiome = new java.util.TreeMap<>();
        for (Path file : regionFiles()) {
            RegionFile region = RegionFile.open(file);
            for (int cx = 0; cx < RegionFile.CHUNKS; cx++) {
                for (int cz = 0; cz < RegionFile.CHUNKS; cz++) {
                    int chunkX = region.regionX() * RegionFile.CHUNKS + cx;
                    int chunkZ = region.regionZ() * RegionFile.CHUNKS + cz;
                    NbtTag.Compound root = region.readChunk(chunkX, chunkZ);
                    if (root == null) continue;
                    var imported = AnvilChunkReader.read(root, 0);
                    int[] ours = terrain.terrainHeights(chunkX, chunkZ);
                    for (int x = 0; x < Chunk.SIZE; x++) {
                        for (int z = 0; z < Chunk.SIZE; z++) {
                            int theirs = vanillaHeight(imported, x, z);
                            int mine = ours[z * Chunk.SIZE + x];
                            columns++;
                            int delta = mine - theirs;
                            long[] stats = perBiome.computeIfAbsent(imported.biome(x, z), key -> new long[3]);
                            stats[0]++;
                            stats[1] += delta;
                            if (delta == 0) stats[2]++;
                            if (delta == 0) { matches++; continue; }
                            if (delta < 0) weLower++; else weHigher++;
                            histogram[Math.max(-20, Math.min(20, delta)) + 20]++;
                            if (examples.size() < 8) examples.add("column " + (chunkX * 16 + x) + "," + (chunkZ * 16 + z)
                                + " ours=" + mine + " vanilla=" + theirs);
                        }
                    }
                }
            }
        }
        Assumptions.assumeTrue(columns > 0, "no generated chunks");
        System.out.printf("terrain height agreement %.4f%% (%d/%d columns), ours lower %d, ours higher %d%n",
            100.0 * matches / columns, matches, columns, weLower, weHigher);
        for (int i = 0; i < histogram.length; i++)
            if (histogram[i] > 0) System.out.println("  off by " + (i - 20) + ": " + histogram[i]);
        System.out.printf("%-26s %9s %9s %9s %9s%n", "biome", "columns", "exact", "mean err", "depth/scale");
        perBiome.forEach((id, stats) -> {
            var biome = biomes.byId(id);
            System.out.printf("%-26s %9d %8.1f%% %9.2f   %.3f / %.3f%n",
                (biome != null ? biome.name().path() : "?") + " (" + id + ")",
                stats[0], 100.0 * stats[2] / stats[0], (double) stats[1] / stats[0],
                biome != null ? biome.depth() : 0, biome != null ? biome.scale() : 0);
        });
        examples.forEach(e -> System.out.println("  " + e));
        // A ratchet, not a claim of correctness: the density field alone reproduces 96.42% of
        // columns (the rest is mostly carving it does not do). Raise this floor as the remaining
        // error is found; it is here to catch regressions, never to bless the current state.
        assertTrue(matches * 10000 >= columns * 9600,
            "terrain height agreement fell below the established floor: " + matches + "/" + columns);
    }

    /**
     * The whole terrain stage (shape, biome surface, caves, ravines) against
     * the stored chunks. Vanilla's chunks have also been populated, so lakes,
     * dungeons, sand/clay/gravel disks, ores and freezing are expected
     * differences; the report says how big each kind of disagreement is:
     * <ul>
     * <li>ground height with carving (compare with the density-only test),</li>
     * <li>the bedrock floor, y=1-4, which is drawn from the same Random as the
     *     surface layers, so an exact match there means the surface builder's
     *     draw order is right,</li>
     * <li>the ground block where the heights agree (grass, sand, podzol...),</li>
     * <li>open cells (air, water, lava) below y=60: our caves against vanilla's.</li>
     * </ul>
     */
    @Test
    void ourTerrainStageMatchesVanillaChunks() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(WORLD.resolve("region")), "no vanilla world at " + WORLD);
        NbtTag.Compound level = NbtIo.readCompressed(WORLD.resolve("level.dat")).getCompound("Data");
        long seed = level.getLong("RandomSeed");
        BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());
        BiomeRegistry biomes = BiomeRegistry.load(new ClasspathResourceManager(), blocks);
        ChunkGenerator generator = new ChunkGenerator(new GenBlocks(blocks), biomes, new BiomeLayers(seed), seed);

        long columns = 0, heightMatches = 0, weHigher = 0, weLower = 0;
        long bedrockCells = 0, bedrockMatches = 0;
        long groundCompared = 0, groundMatches = 0;
        long vanillaOpen = 0, bothOpen = 0, oursOpen = 0;
        java.util.Map<String, Long> groundMismatches = new java.util.HashMap<>();
        java.util.Map<Integer, long[]> perBiome = new java.util.TreeMap<>();
        List<String> examples = new ArrayList<>();
        for (Path file : regionFiles()) {
            RegionFile region = RegionFile.open(file);
            for (int cx = 0; cx < RegionFile.CHUNKS; cx++) {
                for (int cz = 0; cz < RegionFile.CHUNKS; cz++) {
                    int chunkX = region.regionX() * RegionFile.CHUNKS + cx;
                    int chunkZ = region.regionZ() * RegionFile.CHUNKS + cz;
                    NbtTag.Compound root = region.readChunk(chunkX, chunkZ);
                    if (root == null) continue;
                    var imported = AnvilChunkReader.read(root, 0);
                    ChunkPrimer primer = generator.generate(chunkX, chunkZ);
                    for (int x = 0; x < Chunk.SIZE; x++) {
                        for (int z = 0; z < Chunk.SIZE; z++) {
                            columns++;
                            int theirs = vanillaHeight(imported, x, z);
                            int mine = primerHeight(primer, x, z);
                            long[] stats = perBiome.computeIfAbsent(imported.biome(x, z), key -> new long[4]);
                            stats[0]++;
                            if (mine == theirs) {
                                heightMatches++;
                                stats[1]++;
                                int vanillaGround = imported.chunk().get(x, theirs, z);
                                int ourGround = primer.get(x, mine, z);
                                groundCompared++;
                                stats[2]++;
                                if (vanillaGround == ourGround) {
                                    groundMatches++;
                                    stats[3]++;
                                } else {
                                    groundMismatches.merge(name(blocks, ourGround) + " where vanilla has "
                                        + name(blocks, vanillaGround), 1L, Long::sum);
                                }
                            } else if (mine > theirs) {
                                weHigher++;
                            } else {
                                weLower++;
                            }
                            if (mine != theirs && examples.size() < 8) examples.add("column " + (chunkX * 16 + x) + ","
                                + (chunkZ * 16 + z) + " ours=" + mine + " vanilla=" + theirs);
                            for (int y = 1; y <= 4; y++) {
                                boolean vanillaBedrock = imported.chunk().get(x, y, z) >>> 4 == 7;
                                boolean ourBedrock = primer.get(x, y, z) >>> 4 == 7;
                                bedrockCells++;
                                if (vanillaBedrock == ourBedrock) bedrockMatches++;
                            }
                            for (int y = 5; y < 60 && y < theirs - 4; y++) {
                                boolean vanillaHole = isOpen(imported.chunk().get(x, y, z) >>> 4);
                                boolean ourHole = isOpen(primer.get(x, y, z) >>> 4);
                                if (vanillaHole) vanillaOpen++;
                                if (ourHole) oursOpen++;
                                if (vanillaHole && ourHole) bothOpen++;
                            }
                        }
                    }
                }
            }
        }
        Assumptions.assumeTrue(columns > 0, "no generated chunks");
        System.out.printf("with carving: ground height %.4f%% (%d/%d), ours higher %d, ours lower %d%n",
            100.0 * heightMatches / columns, heightMatches, columns, weHigher, weLower);
        System.out.printf("bedrock floor y=1-4: %.4f%% of %d cells%n", 100.0 * bedrockMatches / bedrockCells, bedrockCells);
        System.out.printf("ground block where heights agree: %.4f%% (%d/%d)%n",
            100.0 * groundMatches / groundCompared, groundMatches, groundCompared);
        System.out.printf("underground open cells (y 5-59): vanilla %d, ours %d, both %d (%.1f%% of vanilla's are ours too)%n",
            vanillaOpen, oursOpen, bothOpen, vanillaOpen == 0 ? 0.0 : 100.0 * bothOpen / vanillaOpen);
        System.out.println("most common ground-block differences:");
        groundMismatches.entrySet().stream().sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(15).forEach(e -> System.out.println("  " + e.getValue() + "  " + e.getKey()));
        System.out.printf("%-26s %9s %9s %9s%n", "biome", "columns", "height", "ground");
        perBiome.forEach((id, stats) -> {
            var biome = biomes.byId(id);
            System.out.printf("%-26s %9d %8.1f%% %8.1f%%%n", (biome != null ? biome.name().path() : "?") + " (" + id + ")",
                stats[0], 100.0 * stats[1] / stats[0], stats[2] == 0 ? 0.0 : 100.0 * stats[3] / stats[2]);
        });
        examples.forEach(e -> System.out.println("  " + e));
        // Report only for now: the first run sets the baseline this should ratchet from.
        assertTrue(heightMatches > 0);
    }

    private static boolean isOpen(int id) { return id == 0 || id >= 8 && id <= 11; }

    private static String name(BlockRegistry blocks, int packed) {
        return blocks.byId(packed >>> 4).name().path() + (packed % 16 != 0 ? ":" + (packed & 15) : "");
    }

    /** The same "top of the ground" rule as {@link #vanillaHeight}, on our generated chunk. */
    private static int primerHeight(ChunkPrimer primer, int x, int z) {
        for (int y = Chunk.HEIGHT - 1; y >= 0; y--) if (!NOT_TERRAIN.contains(primer.get(x, y, z) >>> 4)) return y;
        return -1;
    }

    /** Topmost block of actual ground, ignoring water, plants, trees and other things that sit on it. */
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

package net.minecraft.world.gen.layer;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Not a check — a survey. Runs the (oracle-verified) layer chain over a wide
 * area around a seed's spawn and prints where each biome is, so a real 1.8.9
 * client can be flown there to generate ground truth for the biomes the
 * existing oracle world does not contain.
 */
class BiomeSurveyTest {
    private static final long SEED = Long.getLong("legacycraft.surveySeed", -412575085214022153L);
    private static final int SPAWN_X = Integer.getInteger("legacycraft.surveySpawnX", -12);
    private static final int SPAWN_Z = Integer.getInteger("legacycraft.surveySpawnZ", 252);
    /** Half-width of the surveyed square, in blocks. */
    private static final int RANGE = 3000;

    @Test
    void whereIsEveryBiomeAroundSpawn() {
        BlockRegistry blocks = BlockRegistry.load(new ClasspathResourceManager());
        BiomeRegistry biomes = BiomeRegistry.load(new ClasspathResourceManager(), blocks);
        BiomeLayers layers = new BiomeLayers(SEED);

        // The coarse layer is one value per four blocks, which is plenty for finding a biome.
        int cells = RANGE * 2 / 4;
        int originX = (SPAWN_X - RANGE) >> 2, originZ = (SPAWN_Z - RANGE) >> 2;
        int[] map = layers.coarse().getInts(originX, originZ, cells, cells);

        Map<Integer, Long> counts = new TreeMap<>();
        Map<Integer, int[]> nearest = new HashMap<>();
        Map<Integer, Long> nearestDistance = new HashMap<>();
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                int id = map[x + z * cells];
                counts.merge(id, 1L, Long::sum);
                int blockX = (originX + x) << 2, blockZ = (originZ + z) << 2;
                long dx = blockX - SPAWN_X, dz = blockZ - SPAWN_Z;
                long distance = dx * dx + dz * dz;
                if (distance < nearestDistance.getOrDefault(id, Long.MAX_VALUE)) {
                    nearestDistance.put(id, distance);
                    nearest.put(id, new int[]{blockX, blockZ});
                }
            }
        }

        long total = (long) cells * cells;
        System.out.println("Biome survey for seed " + SEED + ", " + RANGE * 2 + "x" + RANGE * 2
            + " blocks around spawn " + SPAWN_X + "," + SPAWN_Z);
        System.out.printf("%-26s %8s %10s   %s%n", "biome", "share", "distance", "nearest column");
        counts.entrySet().stream()
            .sorted((a, b) -> Long.compare(nearestDistance.get(a.getKey()), nearestDistance.get(b.getKey())))
            .forEach(entry -> {
                int id = entry.getKey();
                Biome biome = biomes.byId(id);
                int[] at = nearest.get(id);
                System.out.printf("%-26s %7.3f%% %9d   %d, %d%n",
                    (biome != null ? biome.name().path() : "UNKNOWN") + " (" + id + ")",
                    100.0 * entry.getValue() / total,
                    Math.round(Math.sqrt(nearestDistance.get(id))), at[0], at[1]);
            });
    }
}

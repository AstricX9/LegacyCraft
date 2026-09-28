package net.minecraft.world.storage.anvil;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtTag;
import net.minecraft.testing.GameData;
import net.minecraft.testing.Oracle;
import net.minecraft.testing.VanillaWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks our block rules against a world the real 1.8.9 server built
 * (master context §82). Everything vanilla placed must be something we agree
 * can stay there, and the metadata we decode must point the right way: a
 * torch at a support, a door's halves stacked, a bed's head beside its foot,
 * cocoa on a jungle log. A violation means our metadata layout or our
 * support rule is wrong, not that vanilla is.
 */
@Oracle
final class VanillaStateRulesTest {
    private static final Path WORLD = VanillaWorld.PATH;
    private final BlockRegistry blocks = GameData.blocks();

    private static String describe(World world, BlockState state, int x, int y, int z) {
        StringBuilder text = new StringBuilder(state.block().name().path() + state.values() + " at " + x + "," + y + "," + z
            + " below=" + world.getState(x, y - 1, z).block().name().path()
            + " above=" + world.getState(x, y + 1, z).block().name().path());
        for (net.minecraft.util.Direction side : net.minecraft.util.Direction.HORIZONTALS)
            text.append(" ").append(side.getName()).append("=")
                .append(world.getState(x + side.xOffset, y, z + side.zOffset).block().name().path());
        return text.toString();
    }

    @Test
    void everyBlockVanillaPlacedSatisfiesOurRules() throws IOException {
        VanillaWorld.assumePresent();
        Map<String, Integer> violations = new TreeMap<>(), census = new TreeMap<>();
        Map<String, String> examples = new LinkedHashMap<>();
        long checked = 0;
        List<Path> regionFiles;
        try (var files = Files.list(WORLD.resolve("region"))) {
            regionFiles = files.filter(p -> p.getFileName().toString().endsWith(".mca")).sorted().toList();
        }
        // One region at a time so a big world does not have to fit in memory at once.
        for (Path file : regionFiles) {
        RegionFile region = RegionFile.open(file);
        World world = GameData.newWorld(0L);
        boolean[][] populated = new boolean[RegionFile.CHUNKS][RegionFile.CHUNKS];
        int loaded = 0;
        for (int cx = 0; cx < RegionFile.CHUNKS; cx++) {
            for (int cz = 0; cz < RegionFile.CHUNKS; cz++) {
                NbtTag.Compound root = region.readChunk(region.regionX() * 32 + cx, region.regionZ() * 32 + cz);
                if (root == null) continue;
                var imported = AnvilChunkReader.read(root, loaded++);
                populated[cx][cz] = imported.populated();
                world.restoreChunk(imported.chunk());
            }
        }
        if (loaded == 0) continue;

        for (int cx = 1; cx < RegionFile.CHUNKS - 1; cx++) {
            for (int cz = 1; cz < RegionFile.CHUNKS - 1; cz++) {
                // Only chunks whose neighbours exist: structures run across chunk borders.
                if (!populated[cx][cz] || !populated[cx - 1][cz] || !populated[cx + 1][cz]
                    || !populated[cx][cz - 1] || !populated[cx][cz + 1]) continue;
                for (int x = 0; x < Chunk.SIZE; x++) {
                    for (int z = 0; z < Chunk.SIZE; z++) {
                        for (int y = 1; y < Chunk.HEIGHT; y++) {
                            int worldX = (region.regionX() * 32 + cx) * Chunk.SIZE + x;
                            int worldZ = (region.regionZ() * 32 + cz) * Chunk.SIZE + z;
                            BlockState state = world.getState(worldX, y, worldZ);
                            Block block = state.block();
                            if (block.isAir()) continue;
                            checked++;
                            if (block.getClass() != Block.class) census.merge(block.name().path(), 1, Integer::sum);
                            if (block.canStay(world, worldX, y, worldZ, state)) continue;
                            String name = block.name().path();
                            violations.merge(name, 1, Integer::sum);
                            if (violations.get(name) <= 3) examples.merge(name,
                                describe(world, state, worldX, y, worldZ), (a, b) -> a + "\n      " + b);
                        }
                    }
                }
            }
        }
        }
        System.out.println("checked " + checked + " placed blocks from the vanilla world");
        System.out.println("blocks whose rules are non-trivial: " + census);
        if (!violations.isEmpty()) {
            System.out.println("rule violations by block:");
            violations.forEach((name, count) -> System.out.println("  " + name + ": " + count + "   e.g. " + examples.get(name)));
        }
        // The check would be vacuous without the oriented, supported blocks it is meant to exercise.
        for (String name : List.of("torch", "rail", "vine", "reeds", "cactus", "fence", "deadbush", "waterlily"))
            assertTrue(census.getOrDefault(name, 0) > 0, "the world contains no " + name + " to check");

        List<String> unexpected = new ArrayList<>();
        violations.forEach((name, count) -> {
            // Vanilla leaves plants behind when later generation (lakes) swaps the ground under
            // them or removes it: nothing updates the block, so it is never re-validated.
            boolean strandedPlant = blocks.get("minecraft:" + name) instanceof net.minecraft.block.BlockBush;
            if (!strandedPlant) unexpected.add(name + " x" + count + "\n      " + examples.get(name));
        });
        assertTrue(unexpected.isEmpty(), "vanilla placed blocks our rules reject:\n  " + String.join("\n  ", unexpected));
        long stranded = violations.values().stream().mapToLong(Integer::longValue).sum();
        assertTrue(stranded < checked / 10000,
            "too many plants rejected (" + stranded + " of " + checked + ") to be vanilla's stranded leftovers");
    }
}

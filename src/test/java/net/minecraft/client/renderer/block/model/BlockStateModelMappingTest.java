package net.minecraft.client.renderer.block.model;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.state.BlockStateDefinition;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies data/mc189/block_states against the supplied 1.8.9 blockstate
 * files (evidence tier 1): every state a block can be in — each metadata value
 * combined with every value of its neighbour-derived properties — must name a
 * variant those files define, and that variant must bake.
 */
final class BlockStateModelMappingTest {
    private final ClasspathResourceManager resources = GameData.resources();
    private final BlockRegistry blocks = GameData.blocks();
    private final BlockModelManager models = new BlockModelManager(resources, blocks);

    @Test
    void everyReachableStateMapsToASuppliedVariantThatBakes() {
        List<String> failures = new ArrayList<>();
        Set<StateMapper.ModelLocation> checked = new java.util.HashSet<>();
        for (Block block : blocks.values()) {
            if (block.renderType() != Block.RenderType.MODEL || block.modelOverride() != null) continue;
            BlockStateDefinition definition = block.stateDefinition();
            List<String> derived = new ArrayList<>();
            for (String property : definition.properties().keySet())
                if (!definition.isStored(property)) derived.add(property);
            for (int meta = 0; meta < 16; meta++) {
                for (SortedMap<String, String> values : combinations(definition.decode(meta), derived, definition.properties())) {
                    StateMapper.ModelLocation location = StateMapper.map(block, values);
                    if (!checked.add(location)) continue;
                    if (!models.hasVariant(location)) failures.add(block + " " + values + " -> " + location);
                    else if (models.isMissing(models.lookup(location))) failures.add(block + " bakes as missing: " + location);
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " unmapped states:\n" + String.join("\n", failures));
        assertTrue(checked.size() > 1500, "Expected to exercise every variant, only checked " + checked.size());
    }

    @Test
    void everySuppliedBlockstateFileIsReachable() throws Exception {
        Set<String> reached = new TreeSet<>();
        for (Block block : blocks.values()) {
            if (block.renderType() != Block.RenderType.MODEL || block.modelOverride() != null) continue;
            BlockStateDefinition definition = block.stateDefinition();
            List<String> derived = new ArrayList<>();
            for (String property : definition.properties().keySet())
                if (!definition.isStored(property)) derived.add(property);
            for (int meta = 0; meta < 16; meta++)
                for (SortedMap<String, String> values : combinations(definition.decode(meta), derived, definition.properties()))
                    reached.add(StateMapper.map(block, values).blockstate().path());
        }
        Set<String> supplied = new TreeSet<>();
        for (var file : resources.list(new net.minecraft.util.ResourceLocation("minecraft:blockstates"), ".json")) {
            String path = file.path();
            supplied.add(path.substring(path.lastIndexOf('/') + 1, path.length() - 5));
        }
        supplied.removeAll(reached);
        // item_frame is an entity, not a block, but its model ships as a blockstate.
        assertEquals(Set.of("item_frame"), supplied, "Blockstate files no block state reaches");
    }

    private static List<SortedMap<String, String>> combinations(SortedMap<String, String> stored, List<String> derived,
                                                                Map<String, List<String>> properties) {
        List<SortedMap<String, String>> result = new ArrayList<>();
        result.add(new TreeMap<>(stored));
        for (String property : derived) {
            List<SortedMap<String, String>> next = new ArrayList<>();
            for (SortedMap<String, String> partial : result) {
                for (String value : properties.get(property)) {
                    SortedMap<String, String> copy = new TreeMap<>(partial);
                    copy.put(property, value);
                    next.add(copy);
                }
            }
            result = next;
        }
        return result;
    }

    @Test
    void variantKeysFollowTheSortedPropertyConvention() {
        Block stairs = blocks.get("minecraft:oak_stairs");
        var location = StateMapper.map(stairs, BlockState.of(stairs, 6).values());
        assertEquals("minecraft:oak_stairs", location.blockstate().toString());
        assertEquals("facing=south,half=top,shape=straight", location.variant());

        Block planks = blocks.get("minecraft:planks");
        assertEquals("minecraft:birch_planks#normal", StateMapper.map(planks, BlockState.of(planks, 2).values()).toString());

        Block leaves = blocks.get("minecraft:leaves");
        assertEquals("minecraft:jungle_leaves#normal", StateMapper.map(leaves, BlockState.of(leaves, 15).values()).toString());

        Block slab = blocks.get("minecraft:double_stone_slab");
        assertEquals("minecraft:stone_double_slab#all", StateMapper.map(slab, BlockState.of(slab, 8).values()).toString());
        assertEquals("minecraft:wood_old_double_slab#normal", StateMapper.map(slab, BlockState.of(slab, 2).values()).toString());

        Block quartz = blocks.get("minecraft:quartz_block");
        assertEquals("minecraft:quartz_column#axis=x", StateMapper.map(quartz, BlockState.of(quartz, 3).values()).toString());
    }
}

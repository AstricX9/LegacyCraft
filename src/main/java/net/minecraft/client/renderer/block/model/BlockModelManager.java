package net.minecraft.client.renderer.block.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.state.ActualState;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves (actual) block states to baked models: state → blockstate file and
 * variant key ({@link StateMapper}) → the variant's model list, each entry
 * baked with its x/y rotation and uvlock. Baked results are cached (lookups
 * are synchronized: chunk meshes are built on worker threads while the main
 * thread draws items); unknown
 * variants fall back to a missing-model cube and are reported once.
 */
public final class BlockModelManager {
    private record VariantSpec(ResourceLocation model, int x, int y, boolean uvlock, int weight) {}
    private record BakeKey(ResourceLocation model, int x, int y, boolean uvlock) {}

    private final IResourceManager resources;
    private final BlockRegistry registry;
    private ModelBaker baker;
    private final Map<ResourceLocation, Map<String, List<VariantSpec>>> blockstateFiles = new HashMap<>();
    private final Map<BakeKey, BakedModel> baked = new HashMap<>();
    private final Map<StateMapper.ModelLocation, WeightedModel> variants = new HashMap<>();
    private final Map<ResourceLocation, WeightedModel> overrides = new HashMap<>();
    private final Map<String, WeightedModel> builtin = new HashMap<>();
    private final Set<String> reported = new HashSet<>();
    private final WeightedModel missing = WeightedModel.of(missingModel());

    public BlockModelManager(IResourceManager resources, BlockRegistry registry) {
        this.resources = resources;
        this.registry = registry;
        this.baker = new ModelBaker(resources);
        bakeDefaults();
    }

    /** Bakes every block's default state up front so asset problems surface at startup. */
    private void bakeDefaults() {
        for (Block block : registry.values()) {
            if (block.renderType() == Block.RenderType.MODEL || block.modelOverride() != null)
                variantsFor(ActualState.withoutWorld(block.defaultState()));
        }
    }

    /** Forgets every blockstate, model and bake and reads them again (resource packs changed). */
    public synchronized void reload() {
        blockstateFiles.clear();
        baked.clear();
        variants.clear();
        overrides.clear();
        builtin.clear();
        reported.clear();
        baker = new ModelBaker(resources);
        bakeDefaults();
    }

    /** The model drawn for a state at a position (random variants are keyed by position). */
    public BakedModel getModel(ActualState state, int x, int y, int z) {
        WeightedModel models = variantsFor(state);
        return models == null ? null : models.select(x, y, z);
    }

    /** A state's model without world context (inventory icons, tests). */
    public BakedModel getModel(BlockState state) {
        WeightedModel models = variantsFor(ActualState.withoutWorld(state));
        return models == null ? null : models.first();
    }

    /**
     * A block model baked on its own, unrotated: what an item model whose
     * parent is a block model draws (the piston's inventory model, the fence's
     * two posts, a dispenser facing north), or {@code null} if it can't be baked.
     */
    public synchronized BakedModel itemModel(ResourceLocation model) {
        BakeKey key = new BakeKey(model, 0, 0, false);
        BakedModel cached = baked.get(key);
        if (cached != null) return cached == missing.first() ? null : cached;
        BakedModel bakedModel;
        try {
            bakedModel = baker.bake(model, 0, 0, false);
        } catch (IOException | RuntimeException error) {
            report("Could not bake item model " + model + ": " + error.getMessage());
            bakedModel = missing.first();
        }
        baked.put(key, bakedModel);
        return bakedModel == missing.first() ? null : bakedModel;
    }

    /** The model list for a state, or {@code null} when the block is not drawn from a model. */
    public synchronized WeightedModel variantsFor(ActualState state) {
        Block block = state.block();
        if (block.modelOverride() != null) {
            return overrides.computeIfAbsent(block.modelOverride(),
                model -> bakeList(List.of(new VariantSpec(model, 0, 0, false, 1)), model.toString()));
        }
        if (block.renderType() == Block.RenderType.ENTITY) {
            return builtin.computeIfAbsent(block.name().path(),
                name -> WeightedModel.of(new BakedModel(List.of(), builtinParticle(name), false)));
        }
        if (block.renderType() != Block.RenderType.MODEL) {
            return block.renderType() == Block.RenderType.INVISIBLE ? null : missing;
        }
        return lookup(StateMapper.map(block, state.values()));
    }

    /** Whether the supplied blockstate files define this variant (used to verify the state tables). */
    public synchronized boolean hasVariant(StateMapper.ModelLocation location) {
        Map<String, List<VariantSpec>> file = blockstateFile(location.blockstate());
        return file != null && file.containsKey(location.variant());
    }

    /** Whether a lookup fell back to the missing model, or baked one of its entries as missing. */
    synchronized boolean isMissing(WeightedModel models) {
        if (models == missing) return true;
        for (WeightedModel.Entry entry : models.entries()) if (entry.model() == missing.first()) return true;
        return false;
    }

    public synchronized WeightedModel lookup(StateMapper.ModelLocation location) {
        WeightedModel cached = variants.get(location);
        if (cached != null) return cached;
        Map<String, List<VariantSpec>> file = blockstateFile(location.blockstate());
        List<VariantSpec> specs = file == null ? null : file.get(location.variant());
        WeightedModel result;
        if (specs == null) {
            report("Missing blockstate variant " + location);
            result = missing;
        } else {
            result = bakeList(specs, location.toString());
        }
        variants.put(location, result);
        return result;
    }

    private WeightedModel bakeList(List<VariantSpec> specs, String source) {
        List<WeightedModel.Entry> entries = new ArrayList<>();
        for (VariantSpec spec : specs) {
            BakeKey key = new BakeKey(spec.model(), spec.x(), spec.y(), spec.uvlock());
            BakedModel model = baked.get(key);
            if (model == null) {
                try {
                    model = baker.bake(spec.model(), spec.x(), spec.y(), spec.uvlock());
                } catch (IOException | RuntimeException error) {
                    report("Could not bake " + spec.model() + " for " + source + ": " + error.getMessage());
                    model = missing.first();
                }
                baked.put(key, model);
            }
            entries.add(new WeightedModel.Entry(model, spec.weight()));
        }
        return new WeightedModel(entries);
    }

    private Map<String, List<VariantSpec>> blockstateFile(ResourceLocation blockstate) {
        if (blockstateFiles.containsKey(blockstate)) return blockstateFiles.get(blockstate);
        Map<String, List<VariantSpec>> parsed = null;
        ResourceLocation file = new ResourceLocation(blockstate.namespace(), "blockstates/" + blockstate.path() + ".json");
        try (var reader = new InputStreamReader(resources.open(file), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            parsed = new HashMap<>();
            for (var entry : root.getAsJsonObject("variants").entrySet()) {
                List<VariantSpec> specs = new ArrayList<>();
                JsonElement value = entry.getValue();
                if (value.isJsonArray()) {
                    for (JsonElement option : value.getAsJsonArray()) specs.add(spec(blockstate, option.getAsJsonObject()));
                } else {
                    specs.add(spec(blockstate, value.getAsJsonObject()));
                }
                parsed.put(entry.getKey(), List.copyOf(specs));
            }
        } catch (IOException | RuntimeException error) {
            report("Could not read blockstate " + file + ": " + error.getMessage());
        }
        blockstateFiles.put(blockstate, parsed);
        return parsed;
    }

    private static VariantSpec spec(ResourceLocation blockstate, JsonObject json) {
        return new VariantSpec(ModelBaker.qualify(blockstate.namespace(), json.get("model").getAsString()),
            json.has("x") ? json.get("x").getAsInt() : 0,
            json.has("y") ? json.get("y").getAsInt() : 0,
            json.has("uvlock") && json.get("uvlock").getAsBoolean(),
            json.has("weight") ? json.get("weight").getAsInt() : 1);
    }

    /**
     * Blocks drawn by tile-entity renderers have no quads, only the texture
     * their breaking flecks are cut from (BlockModelShapes.getTexture):
     * oak planks for chests, signs and banners, obsidian for the ender chest,
     * soul sand for mob heads. The end portal has none in 1.8.9 (the missing
     * texture); obsidian stands in, though nothing can break it.
     */
    private static ResourceLocation builtinParticle(String block) {
        String texture = switch (block) {
            case "ender_chest", "end_portal" -> "obsidian";
            case "skull" -> "soul_sand";
            default -> "planks_oak";
        };
        return new ResourceLocation("minecraft:textures/blocks/" + texture + ".png");
    }

    private void report(String message) {
        if (reported.add(message)) System.err.println(message);
    }

    private static BakedModel missingModel() {
        ResourceLocation stone = new ResourceLocation("minecraft:textures/blocks/stone.png");
        List<BakedQuad> quads = new ArrayList<>();
        float[] from = {0, 0, 0}, to = {16, 16, 16};
        for (Direction face : Direction.values()) {
            quads.add(new BakedQuad(ModelBaker.facePositions(face, from, to),
                new float[]{0,0, 0,1, 1,1, 1,0}, face, face, stone, -1, true));
        }
        return new BakedModel(quads, stone, true);
    }
}

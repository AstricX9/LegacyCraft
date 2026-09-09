package net.minecraft.client.renderer.block.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
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

/** Resolves blockstate variants and inherited 1.8 model JSON into renderable quads. */
public final class BlockModelManager {
    private final IResourceManager resources;
    private final Map<Block, BakedModel> cache = new HashMap<>();

    public BlockModelManager(IResourceManager resources, BlockRegistry registry) {
        this.resources = resources;
        for (Block block : registry.values()) {
            if (block.renderType() == Block.RenderType.MODEL) cache.put(block, bakeSafely(block));
        }
    }

    public BakedModel getModel(Block block) { return cache.get(block); }

    private BakedModel bakeSafely(Block block) {
        try {
            Variant variant = block.modelOverride() != null
                ? new Variant(block.modelOverride(), 0, 0)
                : readDefaultVariant(block.name());
            return bakeModel(variant);
        } catch (RuntimeException | IOException error) {
            System.err.println("Could not bake " + block.name() + ": " + error.getMessage());
            return missingModel();
        }
    }

    private Variant readDefaultVariant(ResourceLocation blockName) throws IOException {
        ResourceLocation stateFile = new ResourceLocation(blockName.namespace(),
            "blockstates/" + blockName.path() + ".json");
        JsonObject root = readJson(stateFile);
        JsonObject variants = root.getAsJsonObject("variants");
        if (variants == null || variants.entrySet().isEmpty())
            throw new IOException("No variants in " + stateFile);
        JsonElement selected = variants.has("normal") ? variants.get("normal")
            : variants.entrySet().iterator().next().getValue();
        if (selected.isJsonArray()) selected = selected.getAsJsonArray().get(0);
        JsonObject variant = selected.getAsJsonObject();
        ResourceLocation model = qualifyModel(blockName.namespace(), variant.get("model").getAsString());
        return new Variant(model, intValue(variant, "x"), intValue(variant, "y"));
    }

    private BakedModel bakeModel(Variant variant) throws IOException {
        List<ModelLayer> layers = new ArrayList<>();
        readModelChain(variant.model, layers, new HashSet<>());
        Map<String, String> textures = new HashMap<>();
        JsonArray elements = null;
        String textureNamespace = variant.model.namespace();
        for (int i = layers.size() - 1; i >= 0; i--) {
            ModelLayer layer = layers.get(i);
            textureNamespace = layer.location.namespace();
            if (layer.json.has("textures")) {
                for (var entry : layer.json.getAsJsonObject("textures").entrySet())
                    textures.put(entry.getKey(), entry.getValue().getAsString());
            }
            if (elements == null && layer.json.has("elements")) elements = layer.json.getAsJsonArray("elements");
        }
        if (elements == null) throw new IOException("No model elements for " + variant.model);
        List<BakedQuad> quads = new ArrayList<>();
        for (JsonElement elementValue : elements) {
            JsonObject element = elementValue.getAsJsonObject();
            float[] from = vector(element.getAsJsonArray("from"));
            float[] to = vector(element.getAsJsonArray("to"));
            for (var faceEntry : element.getAsJsonObject("faces").entrySet()) {
                Direction face = Direction.fromName(faceEntry.getKey());
                JsonObject faceJson = faceEntry.getValue().getAsJsonObject();
                Direction cull = faceJson.has("cullface") ? Direction.fromName(faceJson.get("cullface").getAsString()) : null;
                if (cull != null) cull = cull.rotateY(variant.y);
                String textureReference = resolveTexture(faceJson.get("texture").getAsString(), textures, new HashSet<>());
                ResourceLocation texture = textureLocation(textureNamespace, textureReference);
                float[] uv = faceJson.has("uv") ? uv(faceJson.getAsJsonArray("uv")) : new float[]{0,0,16,16};
                int tintIndex = faceJson.has("tintindex") ? faceJson.get("tintindex").getAsInt() : -1;
                quads.add(new BakedQuad(from[0]/16f,from[1]/16f,from[2]/16f,
                    to[0]/16f,to[1]/16f,to[2]/16f, face.rotateY(variant.y), cull,
                    texture,tintIndex,uv[0]/16f,uv[1]/16f,uv[2]/16f,uv[3]/16f,
                    variant.x,variant.y));
            }
        }
        String particleRef = textures.containsKey("particle")
            ? resolveTexture(textures.get("particle"), textures, new HashSet<>())
            : quads.get(0).texture().path().replace("textures/", "").replace(".png", "");
        return new BakedModel(quads, textureLocation(textureNamespace, particleRef));
    }

    private void readModelChain(ResourceLocation model, List<ModelLayer> layers, Set<ResourceLocation> seen) throws IOException {
        if (!seen.add(model)) throw new IOException("Model parent cycle at " + model);
        JsonObject json = readJson(modelFile(model));
        layers.add(new ModelLayer(model, json));
        if (json.has("parent")) {
            String parent = json.get("parent").getAsString();
            if (!parent.startsWith("builtin/")) readModelChain(qualifyModel(model.namespace(), parent), layers, seen);
        }
    }

    private String resolveTexture(String reference, Map<String,String> textures, Set<String> seen) throws IOException {
        if (!reference.startsWith("#")) return reference;
        String key = reference.substring(1);
        if (!seen.add(key)) throw new IOException("Texture reference cycle at #" + key);
        String value = textures.get(key);
        if (value == null) throw new IOException("Unknown texture variable #" + key);
        return resolveTexture(value, textures, seen);
    }

    private JsonObject readJson(ResourceLocation location) throws IOException {
        try (var reader = new InputStreamReader(resources.open(location), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static ResourceLocation qualifyModel(String namespace, String reference) {
        return reference.indexOf(':') >= 0 ? new ResourceLocation(reference) : new ResourceLocation(namespace, reference);
    }
    private static ResourceLocation modelFile(ResourceLocation model) {
        String path = model.path().startsWith("block/") ? model.path() : "block/" + model.path();
        return new ResourceLocation(model.namespace(), "models/" + path + ".json");
    }
    private static ResourceLocation textureLocation(String namespace, String reference) {
        ResourceLocation raw = reference.indexOf(':') >= 0 ? new ResourceLocation(reference) : new ResourceLocation(namespace, reference);
        String path = raw.path();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        return new ResourceLocation(raw.namespace(), path);
    }
    private static int intValue(JsonObject object, String key) { return object.has(key) ? object.get(key).getAsInt() : 0; }
    private static float[] vector(JsonArray values) { return new float[]{values.get(0).getAsFloat(),values.get(1).getAsFloat(),values.get(2).getAsFloat()}; }
    private static float[] uv(JsonArray values) { return new float[]{values.get(0).getAsFloat(),values.get(1).getAsFloat(),values.get(2).getAsFloat(),values.get(3).getAsFloat()}; }

    private BakedModel missingModel() {
        ResourceLocation stone = new ResourceLocation("minecraft:textures/blocks/stone.png");
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction face : Direction.values())
            quads.add(new BakedQuad(0,0,0,1,1,1,face,face,stone,-1,0,0,1,1,0,0));
        return new BakedModel(quads, stone);
    }

    private record Variant(ResourceLocation model, int x, int y) {}
    private record ModelLayer(ResourceLocation location, JsonObject json) {}
}

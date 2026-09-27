package net.minecraft.client.renderer.block.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
 * Bakes a 1.8 JSON block model (parent inheritance, texture variables,
 * elements with element rotation, per-face UV/rotation/cullface/tint) under a
 * blockstate variant's x/y rotation and uvlock flag into {@link BakedQuad}s.
 *
 * <p>Conventions: variant rotation turns the model about the block centre, x
 * first then y, each by the negative of the given angle (so {@code y: 90}
 * turns a north face east and {@code x: 90} turns a north face down, matching
 * the supplied piston/furnace blockstates). Element rotation follows the
 * right-hand rule about its axis, with {@code rescale} stretching the two
 * perpendicular axes by {@code 1/cos(angle)}.
 */
final class ModelBaker {
    private final IResourceManager resources;

    ModelBaker(IResourceManager resources) { this.resources = resources; }

    BakedModel bake(ResourceLocation model, int rotationX, int rotationY, boolean uvlock) throws IOException {
        if (rotationX % 90 != 0 || rotationY % 90 != 0)
            throw new IOException("Variant rotation must be a multiple of 90 degrees for " + model);
        List<JsonObject> layers = new ArrayList<>();
        readModelChain(model, layers, new HashSet<>());
        Map<String, String> textures = new HashMap<>();
        JsonArray elements = null;
        for (int i = layers.size() - 1; i >= 0; i--) {
            JsonObject layer = layers.get(i);
            if (layer.has("textures")) {
                for (var entry : layer.getAsJsonObject("textures").entrySet())
                    textures.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        for (JsonObject layer : layers) {
            if (layer.has("elements")) { elements = layer.getAsJsonArray("elements"); break; }
        }
        if (elements == null) throw new IOException("No model elements for " + model);

        List<BakedQuad> quads = new ArrayList<>();
        for (JsonElement elementValue : elements) {
            JsonObject element = elementValue.getAsJsonObject();
            float[] from = vector(element.getAsJsonArray("from"));
            float[] to = vector(element.getAsJsonArray("to"));
            boolean shade = !element.has("shade") || element.get("shade").getAsBoolean();
            JsonObject elementRotation = element.has("rotation") ? element.getAsJsonObject("rotation") : null;
            for (var faceEntry : element.getAsJsonObject("faces").entrySet()) {
                Direction face = Direction.fromName(faceEntry.getKey());
                JsonObject faceJson = faceEntry.getValue().getAsJsonObject();
                float[] uv = faceJson.has("uv") ? uv(faceJson.getAsJsonArray("uv")) : defaultUv(face, from, to);
                int uvRotation = faceJson.has("rotation") ? faceJson.get("rotation").getAsInt() : 0;
                if (uvRotation % 90 != 0) throw new IOException("Face UV rotation must be a multiple of 90 in " + model);

                float[] positions = facePositions(face, from, to);
                float[] uvs = new float[8];
                for (int vertex = 0; vertex < 4; vertex++) {
                    int corner = Math.floorMod(vertex + uvRotation / 90, 4);
                    uvs[vertex * 2] = (corner == 0 || corner == 1 ? uv[0] : uv[2]) / 16f;
                    uvs[vertex * 2 + 1] = (corner == 0 || corner == 3 ? uv[1] : uv[3]) / 16f;
                }
                if (elementRotation != null) rotateElement(positions, elementRotation);
                for (int vertex = 0; vertex < 4; vertex++) rotateVariant(positions, vertex * 3, rotationX, rotationY, true);
                Direction bakedFace = rotateDirection(face, rotationX, rotationY);
                if (uvlock && (rotationX != 0 || rotationY != 0)) lockUv(positions, uvs, bakedFace);

                Direction cull = faceJson.has("cullface")
                    ? rotateDirection(Direction.fromName(faceJson.get("cullface").getAsString()), rotationX, rotationY) : null;
                String textureReference = resolveTexture(faceJson.get("texture").getAsString(), textures, new HashSet<>());
                int tintIndex = faceJson.has("tintindex") ? faceJson.get("tintindex").getAsInt() : -1;
                quads.add(new BakedQuad(positions, uvs, bakedFace, cull, textureLocation(textureReference), tintIndex, shade));
            }
        }
        if (quads.isEmpty()) throw new IOException("Model " + model + " has no faces");
        ResourceLocation particle = textures.containsKey("particle")
            ? textureLocation(resolveTexture(textures.get("particle"), textures, new HashSet<>()))
            : quads.get(0).texture();
        // The nearest model in the parent chain that sets "ambientocclusion" decides; the default is on.
        boolean ambientOcclusion = true;
        for (JsonObject layer : layers) {
            if (layer.has("ambientocclusion")) { ambientOcclusion = layer.get("ambientocclusion").getAsBoolean(); break; }
        }
        return new BakedModel(quads, particle, ambientOcclusion);
    }

    /** Vertex order per face; vertex i takes UV corner i of (u0,v0) (u0,v1) (u1,v1) (u1,v0). */
    static float[] facePositions(Direction face, float[] from, float[] to) {
        float fx = from[0] / 16f, fy = from[1] / 16f, fz = from[2] / 16f;
        float tx = to[0] / 16f, ty = to[1] / 16f, tz = to[2] / 16f;
        return switch (face) {
            case DOWN -> new float[]{fx,fy,tz, fx,fy,fz, tx,fy,fz, tx,fy,tz};
            case UP -> new float[]{fx,ty,fz, fx,ty,tz, tx,ty,tz, tx,ty,fz};
            case NORTH -> new float[]{tx,ty,fz, tx,fy,fz, fx,fy,fz, fx,ty,fz};
            case SOUTH -> new float[]{fx,ty,tz, fx,fy,tz, tx,fy,tz, tx,ty,tz};
            case WEST -> new float[]{fx,ty,fz, fx,fy,fz, fx,fy,tz, fx,ty,tz};
            case EAST -> new float[]{tx,ty,tz, tx,fy,tz, tx,fy,fz, tx,ty,fz};
        };
    }

    /** UVs a face gets when the model omits them: the element's projection onto that face. */
    static float[] defaultUv(Direction face, float[] from, float[] to) {
        return switch (face) {
            case DOWN -> new float[]{from[0], 16 - to[2], to[0], 16 - from[2]};
            case UP -> new float[]{from[0], from[2], to[0], to[2]};
            case NORTH -> new float[]{16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
            case SOUTH -> new float[]{from[0], 16 - to[1], to[0], 16 - from[1]};
            case WEST -> new float[]{from[2], 16 - to[1], to[2], 16 - from[1]};
            case EAST -> new float[]{16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
        };
    }

    /** uvlock: texture coordinates follow the world grid instead of turning with the model. HYPOTHESIS for faces with explicit UVs. */
    private static void lockUv(float[] positions, float[] uvs, Direction face) {
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = positions[vertex * 3], y = positions[vertex * 3 + 1], z = positions[vertex * 3 + 2];
            float u, v;
            switch (face) {
                case DOWN -> { u = x; v = 1 - z; }
                case UP -> { u = x; v = z; }
                case NORTH -> { u = 1 - x; v = 1 - y; }
                case SOUTH -> { u = x; v = 1 - y; }
                case WEST -> { u = z; v = 1 - y; }
                default -> { u = 1 - z; v = 1 - y; }
            }
            uvs[vertex * 2] = clamp01(u);
            uvs[vertex * 2 + 1] = clamp01(v);
        }
    }

    private static void rotateElement(float[] positions, JsonObject rotation) throws IOException {
        float[] origin = vector(rotation.getAsJsonArray("origin"));
        float angle = rotation.get("angle").getAsFloat();
        String axis = rotation.get("axis").getAsString();
        boolean rescale = rotation.has("rescale") && rotation.get("rescale").getAsBoolean();
        if (angle == 0) return;
        double radians = Math.toRadians(angle), cos = Math.cos(radians), sin = Math.sin(radians);
        double scale = rescale ? 1 / Math.cos(radians) : 1;
        for (int i = 0; i < 12; i += 3) {
            double x = positions[i] - origin[0] / 16.0, y = positions[i + 1] - origin[1] / 16.0, z = positions[i + 2] - origin[2] / 16.0;
            double nx = x, ny = y, nz = z;
            switch (axis) {
                case "x" -> { ny = (y * cos - z * sin) * scale; nz = (y * sin + z * cos) * scale; }
                case "y" -> { nx = (x * cos + z * sin) * scale; nz = (-x * sin + z * cos) * scale; }
                case "z" -> { nx = (x * cos - y * sin) * scale; ny = (x * sin + y * cos) * scale; }
                default -> throw new IOException("Unknown element rotation axis " + axis);
            }
            positions[i] = (float) (nx + origin[0] / 16.0);
            positions[i + 1] = (float) (ny + origin[1] / 16.0);
            positions[i + 2] = (float) (nz + origin[2] / 16.0);
        }
    }

    private static void rotateVariant(float[] vector, int offset, int rotationX, int rotationY, boolean aroundCentre) {
        float centre = aroundCentre ? .5f : 0f;
        float x = vector[offset] - centre, y = vector[offset + 1] - centre, z = vector[offset + 2] - centre;
        int cosX = quarterCos(-rotationX), sinX = quarterSin(-rotationX);
        float ny = y * cosX - z * sinX, nz = y * sinX + z * cosX;
        y = ny; z = nz;
        int cosY = quarterCos(-rotationY), sinY = quarterSin(-rotationY);
        float nx = x * cosY + z * sinY;
        nz = -x * sinY + z * cosY;
        vector[offset] = nx + centre;
        vector[offset + 1] = y + centre;
        vector[offset + 2] = nz + centre;
    }

    static Direction rotateDirection(Direction direction, int rotationX, int rotationY) {
        float[] normal = {direction.xOffset, direction.yOffset, direction.zOffset};
        rotateVariant(normal, 0, rotationX, rotationY, false);
        return Direction.nearest(normal[0], normal[1], normal[2]);
    }

    private static int quarterCos(int degrees) { return switch (Math.floorMod(degrees, 360)) { case 0 -> 1; case 180 -> -1; default -> 0; }; }
    private static int quarterSin(int degrees) { return switch (Math.floorMod(degrees, 360)) { case 90 -> 1; case 270 -> -1; default -> 0; }; }

    private void readModelChain(ResourceLocation model, List<JsonObject> layers, Set<ResourceLocation> seen) throws IOException {
        if (!seen.add(model)) throw new IOException("Model parent cycle at " + model);
        JsonObject json = readJson(modelFile(model));
        layers.add(json);
        if (json.has("parent")) {
            String parent = json.get("parent").getAsString();
            if (!parent.startsWith("builtin/")) readModelChain(qualify(model.namespace(), parent), layers, seen);
        }
    }

    private static String resolveTexture(String reference, Map<String, String> textures, Set<String> seen) throws IOException {
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

    static ResourceLocation qualify(String namespace, String reference) {
        return reference.indexOf(':') >= 0 ? new ResourceLocation(reference) : new ResourceLocation(namespace, reference);
    }
    private static ResourceLocation modelFile(ResourceLocation model) {
        String path = model.path().startsWith("block/") ? model.path() : "block/" + model.path();
        return new ResourceLocation(model.namespace(), "models/" + path + ".json");
    }
    /** Model texture names without a namespace are vanilla textures. */
    private static ResourceLocation textureLocation(String reference) {
        ResourceLocation raw = new ResourceLocation(reference);
        String path = raw.path();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        return new ResourceLocation(raw.namespace(), path);
    }
    private static float clamp01(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static float[] vector(JsonArray values) { return new float[]{values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat()}; }
    private static float[] uv(JsonArray values) { return new float[]{values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat(), values.get(3).getAsFloat()}; }
}

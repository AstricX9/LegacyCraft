package net.minecraft.world.gen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The "Customized" world type's settings, stored in level.dat
 * {@code generatorOptions} as a JSON object with 1.8.9's key names
 * (e.g. {@code {"coordinateScale":684.412,...,"lapisSpread":16}}).
 *
 * <p>Defaults are COMMUNITY_CORROBORATED (they are the constants the
 * DEFAULT generator uses — the same numbers TerrainGenerator already carries);
 * slider ranges and page layout are HYPOTHESIS until compared with the real
 * Customize screen (unknowns.yml). LegacyCraft does not yet feed these values
 * into terrain generation (DEV-011).
 */
public final class ChunkProviderSettings {
    public enum Kind { FLOAT, INT, BOOLEAN }

    /**
     * One setting: its JSON key, its translation key (label), type, slider range,
     * default and page (0 basic, 1 ores, 2 advanced, 3 the same advanced values as text fields).
     */
    public record Field(String key, String label, Kind kind, float min, float max, float defaultValue, int page) {}

    public static final List<Field> FIELDS;

    static {
        List<Field> fields = new ArrayList<>();
        String c = "createWorld.customize.custom.";
        fields.add(new Field("seaLevel", c + "seaLevel", Kind.INT, 1, 255, 63, 0));
        fields.add(bool("useCaves", c));
        fields.add(bool("useStrongholds", c));
        fields.add(bool("useVillages", c));
        fields.add(bool("useMineShafts", c));
        fields.add(bool("useTemples", c));
        fields.add(bool("useMonuments", c));
        fields.add(bool("useRavines", c));
        fields.add(bool("useDungeons", c));
        fields.add(new Field("dungeonChance", c + "dungeonChance", Kind.INT, 1, 100, 8, 0));
        fields.add(bool("useWaterLakes", c));
        fields.add(new Field("waterLakeChance", c + "waterLakeChance", Kind.INT, 1, 100, 4, 0));
        fields.add(bool("useLavaLakes", c));
        fields.add(new Field("lavaLakeChance", c + "lavaLakeChance", Kind.INT, 10, 100, 80, 0));
        fields.add(new Field("useLavaOceans", c + "useLavaOceans", Kind.BOOLEAN, 0, 1, 0, 0));
        fields.add(new Field("fixedBiome", c + "fixedBiome", Kind.INT, -1, 37, -1, 0));
        fields.add(new Field("biomeSize", c + "biomeSize", Kind.INT, 1, 8, 4, 0));
        fields.add(new Field("riverSize", c + "riverSize", Kind.INT, 1, 5, 4, 0));

        ore(fields, "dirt", "tile.dirt.name", 33, 10, 0, 256);
        ore(fields, "gravel", "tile.gravel.name", 33, 8, 0, 256);
        ore(fields, "granite", "tile.stone.granite.name", 33, 10, 0, 80);
        ore(fields, "diorite", "tile.stone.diorite.name", 33, 10, 0, 80);
        ore(fields, "andesite", "tile.stone.andesite.name", 33, 10, 0, 80);
        ore(fields, "coal", "tile.oreCoal.name", 17, 20, 0, 128);
        ore(fields, "iron", "tile.oreIron.name", 9, 20, 0, 64);
        ore(fields, "gold", "tile.oreGold.name", 9, 2, 0, 32);
        ore(fields, "redstone", "tile.oreRedstone.name", 8, 8, 0, 16);
        ore(fields, "diamond", "tile.oreDiamond.name", 8, 1, 0, 16);
        fields.add(new Field("lapisSize", "tile.oreLapis.name|" + c + "size", Kind.INT, 1, 50, 7, 1));
        fields.add(new Field("lapisCount", "tile.oreLapis.name|" + c + "count", Kind.INT, 0, 40, 1, 1));
        fields.add(new Field("lapisCenterHeight", "tile.oreLapis.name|" + c + "center", Kind.INT, 0, 255, 16, 1));
        fields.add(new Field("lapisSpread", "tile.oreLapis.name|" + c + "spread", Kind.INT, 0, 255, 16, 1));

        fields.add(new Field("mainNoiseScaleX", c + "mainNoiseScaleX", Kind.FLOAT, 1, 5000, 80, 2));
        fields.add(new Field("mainNoiseScaleY", c + "mainNoiseScaleY", Kind.FLOAT, 1, 5000, 160, 2));
        fields.add(new Field("mainNoiseScaleZ", c + "mainNoiseScaleZ", Kind.FLOAT, 1, 5000, 80, 2));
        fields.add(new Field("depthNoiseScaleX", c + "depthNoiseScaleX", Kind.FLOAT, 1, 2000, 200, 2));
        fields.add(new Field("depthNoiseScaleZ", c + "depthNoiseScaleZ", Kind.FLOAT, 1, 2000, 200, 2));
        fields.add(new Field("depthNoiseScaleExponent", c + "depthNoiseScaleExponent", Kind.FLOAT, 0.01f, 20, 0.5f, 2));
        fields.add(new Field("baseSize", c + "baseSize", Kind.FLOAT, 1, 25, 8.5f, 2));
        fields.add(new Field("coordinateScale", c + "coordinateScale", Kind.FLOAT, 1, 6000, 684.412f, 2));
        fields.add(new Field("heightScale", c + "heightScale", Kind.FLOAT, 1, 6000, 684.412f, 2));
        fields.add(new Field("stretchY", c + "stretchY", Kind.FLOAT, 0.01f, 50, 12, 2));
        fields.add(new Field("upperLimitScale", c + "upperLimitScale", Kind.FLOAT, 1, 5000, 512, 2));
        fields.add(new Field("lowerLimitScale", c + "lowerLimitScale", Kind.FLOAT, 1, 5000, 512, 2));
        fields.add(new Field("biomeDepthWeight", c + "biomeDepthWeight", Kind.FLOAT, 1, 20, 1, 2));
        fields.add(new Field("biomeDepthOffset", c + "biomeDepthOffset", Kind.FLOAT, 0, 20, 0, 2));
        fields.add(new Field("biomeScaleWeight", c + "biomeScaleWeight", Kind.FLOAT, 1, 20, 1, 2));
        fields.add(new Field("biomeScaleOffset", c + "biomeScaleOffset", Kind.FLOAT, 0, 20, 0, 2));
        FIELDS = List.copyOf(fields);
    }

    private static Field bool(String key, String prefix) { return new Field(key, prefix + key, Kind.BOOLEAN, 0, 1, 1, 0); }

    private static void ore(List<Field> fields, String name, String label, int size, int count, int min, int max) {
        String c = "createWorld.customize.custom.";
        fields.add(new Field(name + "Size", label + "|" + c + "size", Kind.INT, 1, 50, size, 1));
        fields.add(new Field(name + "Count", label + "|" + c + "count", Kind.INT, 0, 40, count, 1));
        fields.add(new Field(name + "MinHeight", label + "|" + c + "minHeight", Kind.INT, 0, 255, min, 1));
        fields.add(new Field(name + "MaxHeight", label + "|" + c + "maxHeight", Kind.INT, 0, 256, max, 1));
    }

    private final Map<String, Float> values = new LinkedHashMap<>();

    public ChunkProviderSettings() { setDefaults(); }

    public void setDefaults() {
        for (Field field : FIELDS) values.put(field.key(), field.defaultValue());
    }

    public float get(String key) { return values.get(key); }
    public boolean getBoolean(String key) { return values.get(key) != 0; }

    public void set(Field field, float value) {
        value = Math.max(field.min(), Math.min(field.max(), value));
        if (field.kind() != Kind.FLOAT) value = Math.round(value);
        values.put(field.key(), value);
    }

    public static Field field(String key) {
        for (Field field : FIELDS) if (field.key().equals(key)) return field;
        throw new IllegalArgumentException("Unknown customized setting " + key);
    }

    /** Parses generatorOptions JSON; unknown keys are ignored, missing keys keep defaults. */
    public static ChunkProviderSettings fromJson(String json) {
        ChunkProviderSettings settings = new ChunkProviderSettings();
        if (json == null || json.isBlank()) return settings;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return settings;
            JsonObject object = parsed.getAsJsonObject();
            for (Field field : FIELDS) {
                JsonElement value = object.get(field.key());
                if (value == null || !value.isJsonPrimitive()) continue;
                if (field.kind() == Kind.BOOLEAN) settings.set(field, value.getAsBoolean() ? 1 : 0);
                else settings.set(field, value.getAsFloat());
            }
        } catch (RuntimeException malformed) {
            return new ChunkProviderSettings();
        }
        return settings;
    }

    public String toJson() {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Field field : FIELDS) {
            if (!first) out.append(',');
            first = false;
            out.append('"').append(field.key()).append("\":");
            float value = values.get(field.key());
            switch (field.kind()) {
                case BOOLEAN -> out.append(value != 0);
                case INT -> out.append((int) value);
                case FLOAT -> out.append(value);
            }
        }
        return out.append('}').toString();
    }
}

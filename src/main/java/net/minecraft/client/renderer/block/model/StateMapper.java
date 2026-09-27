package net.minecraft.client.renderer.block.model;

import net.minecraft.block.Block;
import net.minecraft.block.state.BlockStateDefinition;
import net.minecraft.util.ResourceLocation;

import java.util.Map;
import java.util.SortedMap;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Maps a block's (actual) property values to a blockstate file and variant
 * key, the 1.8 way: {@code property=value} pairs sorted by property name and
 * joined with commas, {@code normal} when nothing remains. Properties used in
 * the file-name template or listed as ignored are left out of the key.
 */
public final class StateMapper {
    /** A blockstate file plus one variant key inside it. */
    public record ModelLocation(ResourceLocation blockstate, String variant) {
        public ResourceLocation file() {
            return new ResourceLocation(blockstate.namespace(), "blockstates/" + blockstate.path() + ".json");
        }
        @Override public String toString() { return blockstate + "#" + variant; }
    }

    private StateMapper() {}

    public static ModelLocation map(Block block, Map<String, String> values) {
        BlockStateDefinition.ModelMapping mapping = block.stateDefinition().model();
        String namespace = block.name().namespace();
        if (mapping.mapper() != null) return custom(mapping.mapper(), block, values);
        SortedMap<String, String> remaining = new TreeMap<>(values);
        for (String ignored : mapping.ignored()) remaining.remove(ignored);
        String file = block.name().path();
        if (mapping.file() != null) {
            file = mapping.file();
            for (String placeholder : BlockStateDefinition.placeholders(mapping.file())) {
                file = file.replace("{" + placeholder + "}", values.get(placeholder));
                remaining.remove(placeholder);
            }
        }
        return new ModelLocation(new ResourceLocation(namespace, file), variantKey(remaining));
    }

    public static String variantKey(SortedMap<String, String> values) {
        if (values.isEmpty()) return "normal";
        StringJoiner key = new StringJoiner(",");
        for (var entry : values.entrySet()) key.add(entry.getKey() + "=" + entry.getValue());
        return key.toString();
    }

    private static ModelLocation custom(String mapper, Block block, Map<String, String> values) {
        String namespace = block.name().namespace();
        return switch (mapper) {
            // Only podzol shows snow; plain and coarse dirt have a single variant.
            case "dirt" -> {
                String variant = values.get("variant");
                yield new ModelLocation(new ResourceLocation(namespace, variant),
                    variant.equals("podzol") ? "snowy=" + values.get("snowy") : "normal");
            }
            // A stem bent towards its fruit is drawn the same at every age.
            case "stem" -> {
                String facing = values.get("facing");
                yield new ModelLocation(block.name(), facing.equals("up")
                    ? "age=" + values.get("age") + ",facing=up" : "facing=" + facing);
            }
            case "quartz" -> switch (values.get("variant")) {
                case "chiseled" -> new ModelLocation(new ResourceLocation(namespace, "chiseled_quartz_block"), "normal");
                case "lines_y" -> new ModelLocation(new ResourceLocation(namespace, "quartz_column"), "axis=y");
                case "lines_x" -> new ModelLocation(new ResourceLocation(namespace, "quartz_column"), "axis=x");
                case "lines_z" -> new ModelLocation(new ResourceLocation(namespace, "quartz_column"), "axis=z");
                default -> new ModelLocation(new ResourceLocation(namespace, "quartz_block"), "normal");
            };
            // Seamless (metadata bit 8) double slabs use the all-top-texture variant.
            case "double_slab" -> new ModelLocation(
                new ResourceLocation(namespace, values.get("variant") + "_double_slab"),
                values.get("seamless").equals("true") ? "all" : "normal");
            default -> throw new IllegalStateException("Unknown state mapper " + mapper + " for " + block);
        };
    }
}

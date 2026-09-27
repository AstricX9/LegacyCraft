package net.minecraft.world.gen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRegistry;
import net.minecraft.block.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Superflat preset string, version 3 as 1.8.9 writes it:
 * {@code 3;<layers bottom-up>;<biome id>;<structures>} with layers
 * {@code [count*]id[:meta]} (numeric or namespaced id) and structures
 * {@code name[(key=value ...)]} comma-separated.
 * Example (Classic Flat): {@code 3;7,2*3,2;1;village}.
 *
 * <p>COMMUNITY_CORROBORATED format; the preset strings in {@link #PRESETS} are
 * HYPOTHESIS until pasted from a real 1.8.9 client (unknowns.yml).
 */
public final class FlatGeneratorInfo {
    public static final String DEFAULT_PRESET = "3;7,2*3,2;1;village";

    /** 1.8.9's built-in presets: display name, icon block, preset string. */
    public record Preset(String name, String iconBlock, int iconMeta, String value) {}

    public static final List<Preset> PRESETS = List.of(
        new Preset("Classic Flat", "minecraft:grass", 0, "3;7,2*3,2;1;village"),
        new Preset("Tunnelers' Dream", "minecraft:stone", 0, "3;7,230*1,5*3,2;3;biome_1,dungeon,decoration,stronghold,mineshaft"),
        new Preset("Water World", "minecraft:water", 0, "3;7,5*1,5*3,5*12,90*9;24;biome_1,oceanmonument"),
        new Preset("Overworld", "minecraft:tallgrass", 1, "3;7,59*1,3*3,2;1;stronghold,biome_1,village,decoration,dungeon,lake,mineshaft,lava_lake"),
        new Preset("Snowy Kingdom", "minecraft:snow_layer", 0, "3;7,59*1,3*3,2,78;12;biome_1,village"),
        new Preset("Bottomless Pit", "minecraft:feather", 0, "3;2*4,3*3,2;1;biome_1,village"),
        new Preset("Desert", "minecraft:sand", 0, "3;7,3*1,52*24,8*12;2;stronghold,biome_1,village,decoration,dungeon,mineshaft"),
        new Preset("Redstone Ready", "minecraft:redstone", 0, "3;7,3*1,52*24;2;"));

    /** One run of identical blocks. */
    public record Layer(int count, BlockState state) {}

    private final List<Layer> layers = new ArrayList<>();
    private final Map<String, Map<String, String>> structures = new LinkedHashMap<>();
    private int biome = 1;

    public List<Layer> layers() { return layers; }
    public Map<String, Map<String, String>> structures() { return structures; }
    public int biome() { return biome; }
    public void setBiome(int biome) { this.biome = biome; }

    /** Total height of every layer. */
    public int height() {
        int total = 0;
        for (Layer layer : layers) total += layer.count();
        return total;
    }

    /** The state at a given y, or null above the top layer. */
    public BlockState stateAt(int y) {
        int base = 0;
        for (Layer layer : layers) {
            if (y < base + layer.count()) return layer.state();
            base += layer.count();
        }
        return null;
    }

    /** Parses a preset, falling back to Classic Flat for anything malformed (as vanilla does). */
    public static FlatGeneratorInfo parse(String text, BlockRegistry blocks) {
        FlatGeneratorInfo parsed = tryParse(text, blocks);
        return parsed != null ? parsed : tryParse(DEFAULT_PRESET, blocks);
    }

    static FlatGeneratorInfo tryParse(String text, BlockRegistry blocks) {
        if (text == null) return null;
        String[] parts = text.split(";", -1);
        if (parts.length < 2) return null;
        int version;
        int index = 0;
        if (parts.length == 1 || !parts[0].matches("\\d+")) version = 0;
        else { version = Integer.parseInt(parts[0]); index = 1; }
        if (version > 3) return null;
        FlatGeneratorInfo info = new FlatGeneratorInfo();
        for (String entry : parts[index++].split(",")) {
            Layer layer = parseLayer(entry.trim(), version, blocks);
            if (layer == null) return null;
            info.layers.add(layer);
        }
        if (info.layers.isEmpty() || info.height() > 256) return null;
        if (index < parts.length && !parts[index].isEmpty()) {
            try { info.biome = Integer.parseInt(parts[index]); } catch (NumberFormatException bad) { return null; }
        }
        index++;
        if (index < parts.length) {
            if (!parts[index].isEmpty()) {
                for (String structure : parts[index].split(",")) {
                    String name = structure;
                    Map<String, String> options = new LinkedHashMap<>();
                    int open = structure.indexOf('(');
                    if (open >= 0 && structure.endsWith(")")) {
                        name = structure.substring(0, open);
                        for (String option : structure.substring(open + 1, structure.length() - 1).split(" ")) {
                            int equals = option.indexOf('=');
                            if (equals > 0) options.put(option.substring(0, equals), option.substring(equals + 1));
                        }
                    }
                    info.structures.put(name, options);
                }
            }
        } else {
            // No structure section at all: vanilla gives the world villages.
            info.structures.put("village", new LinkedHashMap<>());
        }
        return info;
    }

    private static Layer parseLayer(String entry, int version, BlockRegistry blocks) {
        if (entry.isEmpty()) return null;
        int count = 1;
        String separator = version >= 3 ? "*" : "x";
        int star = entry.indexOf(separator);
        if (star > 0) {
            try { count = Integer.parseInt(entry.substring(0, star)); } catch (NumberFormatException bad) { return null; }
            entry = entry.substring(star + 1);
        }
        if (count <= 0) return null;
        int meta = 0;
        String id = entry;
        String[] pieces = entry.split(":");
        boolean hasMeta = pieces.length == 3 || (pieces.length == 2 && pieces[1].matches("\\d+"));
        if (hasMeta) {
            try { meta = Integer.parseInt(pieces[pieces.length - 1]); } catch (NumberFormatException bad) { return null; }
            id = entry.substring(0, entry.lastIndexOf(':'));
        }
        Block block;
        if (id.matches("\\d+")) {
            int numeric = Integer.parseInt(id);
            if (numeric >= blocks.size()) return null;
            block = blocks.byId(numeric);
        } else {
            block = blocks.get(id.contains(":") ? id : "minecraft:" + id);
            if (block == null) return null;
        }
        if (meta < 0 || meta > 15) meta = 0;
        return new Layer(count, BlockState.of(block, meta));
    }

    /** The version-3 string vanilla would write for these settings. */
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("3;");
        for (int i = 0; i < layers.size(); i++) {
            if (i > 0) out.append(',');
            Layer layer = layers.get(i);
            if (layer.count() > 1) out.append(layer.count()).append('*');
            out.append(layer.state().block().registryId());
            if (layer.state().metadata() != 0) out.append(':').append(layer.state().metadata());
        }
        out.append(';').append(biome).append(';');
        boolean first = true;
        for (var structure : structures.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append(structure.getKey());
            if (!structure.getValue().isEmpty()) {
                out.append('(');
                boolean firstOption = true;
                for (var option : structure.getValue().entrySet()) {
                    if (!firstOption) out.append(' ');
                    firstOption = false;
                    out.append(option.getKey()).append('=').append(option.getValue());
                }
                out.append(')');
            }
        }
        return out.toString();
    }
}

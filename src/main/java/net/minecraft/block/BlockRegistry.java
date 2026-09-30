package net.minecraft.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.state.BlockStateDefinition;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Loads the 1.8.9 legacy block table (IDs 0-197) through its generated per-block definitions. */
public final class BlockRegistry {
    private static final ResourceLocation INDEX = new ResourceLocation("legacycraft:indexes/blocks.json");
    private final Map<ResourceLocation, Block> byName = new LinkedHashMap<>();
    private final List<Block> byId = new ArrayList<>();

    public static BlockRegistry load(IResourceManager resources) {
        BlockRegistry registry = new BlockRegistry();
        try {
            JsonObject index;
            try (var reader = new InputStreamReader(resources.open(INDEX), StandardCharsets.UTF_8)) {
                index = JsonParser.parseReader(reader).getAsJsonObject();
            }
            int expectedCount = index.get("count").getAsInt();
            Set<ResourceLocation> names = new HashSet<>();
            for (JsonElement rawEntry : index.getAsJsonArray("entries")) {
                JsonObject entry = rawEntry.getAsJsonObject();
                int id = entry.get("index").getAsInt();
                if (id != registry.byId.size())
                    throw new IllegalStateException("Non-contiguous block index: expected "
                        + registry.byId.size() + " but found " + id);
                ResourceLocation definition = new ResourceLocation(entry.get("definition").getAsString());
                JsonObject value;
                try (var reader = new InputStreamReader(resources.open(definition), StandardCharsets.UTF_8)) {
                    value = JsonParser.parseReader(reader).getAsJsonObject();
                }
                ResourceLocation name = new ResourceLocation(value.get("name").getAsString());
                if (!name.toString().equals(entry.get("name").getAsString()))
                    throw new IllegalStateException("Definition/index name mismatch for " + definition);
                if (value.get("index").getAsInt() != id)
                    throw new IllegalStateException("Definition/index ID mismatch for " + name);
                if (!names.add(name)) throw new IllegalStateException("Duplicate block " + name);
                JsonObject states = null;
                if (entry.has("states")) {
                    ResourceLocation statesFile = new ResourceLocation(entry.get("states").getAsString());
                    try (var reader = new InputStreamReader(resources.open(statesFile), StandardCharsets.UTF_8)) {
                        states = JsonParser.parseReader(reader).getAsJsonObject();
                    }
                    if (!name.toString().equals(states.get("name").getAsString()))
                        throw new IllegalStateException("States/index name mismatch for " + statesFile);
                }
                registry.register(id, name, value, states);
            }
            if (registry.size() != expectedCount)
                throw new IllegalStateException("Block index declared " + expectedCount
                    + " entries but loaded " + registry.size());
            if (registry.byId.isEmpty() || !registry.byId.get(0).name().toString().equals("minecraft:air"))
                throw new IllegalStateException("Block index 0 must be minecraft:air");
            return registry;
        } catch (IOException error) {
            throw new IllegalStateException("Could not build the block registry from resources", error);
        }
    }

    /** @param value the block's data definition; @param statesJson its separate state definition, or {@code null} if stateless */
    private void register(int id, ResourceLocation name, JsonObject value, JsonObject statesJson) {
        if (value.has("states") || value.has("properties") || value.has("meta"))
            throw new IllegalStateException("Block data for " + name + " must not contain state definitions");
        String material = stringValue(value, "material", "rock");
        boolean solid = booleanValue(value, "solid", true);
        boolean opaque = booleanValue(value, "opaque", true);
        boolean fullCube = booleanValue(value, "fullCube", opaque);
        boolean translucent = booleanValue(value, "translucent", false);
        float hardness = floatValue(value, "hardness", 1.5f);
        Block.RenderType renderType = Block.RenderType.valueOf(
            stringValue(value, "renderType", "model").toUpperCase(Locale.ROOT));
        Block.CullSame cullSame = Block.CullSame.valueOf(
            stringValue(value, "cullSame", "none").toUpperCase(Locale.ROOT));
        int tint = parseColor(stringValue(value, "tintColor", "#FFFFFF"));
        int lightLevel = intValue(value, "lightLevel", 0);
        if (lightLevel < 0 || lightLevel > 15)
            throw new IllegalStateException("Invalid light level for " + name + ": " + lightLevel);
        int lightOpacity = intValue(value, "lightOpacity", opaque ? 15 : 0);
        if (lightOpacity < 0 || lightOpacity > 15)
            throw new IllegalStateException("Invalid light opacity for " + name + ": " + lightOpacity);
        String model = stringValue(value, "model", null);
        ResourceLocation modelOverride = model == null ? null : new ResourceLocation(model);
        BlockStateDefinition states = BlockStateDefinition.parse(name.toString(), statesJson);
        AxisAlignedBB bounds = value.has("bounds") ? box(value.getAsJsonArray("bounds")) : AxisAlignedBB.FULL;
        List<AxisAlignedBB> collision = null;
        if (value.has("collision")) {
            collision = new ArrayList<>();
            for (JsonElement raw : value.getAsJsonArray("collision")) collision.add(box(raw.getAsJsonArray()));
        }
        boolean targetable = booleanValue(value, "targetable", true);
        Block.Properties properties = new Block.Properties(id, name, material, solid, opaque, fullCube, translucent,
            hardness, renderType, cullSame, tint, lightLevel, lightOpacity, booleanValue(value, "useNeighborBrightness", false),
            modelOverride, states, bounds, collision, targetable);
        Block block = create(stringValue(value, "class", "block"), properties, value);
        byName.put(name, block);
        byId.add(block);
    }

    /** Builds the block class a data file names with {@code "class"} (plain {@link Block} when absent). */
    private static Block create(String type, Block.Properties properties, JsonObject value) {
        return switch (type) {
            case "block" -> new Block(properties);
            case "anvil" -> new BlockAnvil(properties);
            case "bed" -> new BlockBed(properties);
            case "brewing_stand" -> new BlockBrewingStand(properties);
            case "bush" -> new BlockBush(properties);
            case "button" -> new BlockButton(properties);
            case "cactus" -> new BlockCactus(properties);
            case "cake" -> new BlockCake(properties);
            case "carpet" -> new BlockCarpet(properties);
            case "cauldron" -> new BlockCauldron(properties);
            case "chest" -> new BlockChest(properties);
            case "cocoa" -> new BlockCocoa(properties);
            case "crops" -> new BlockCrops(properties);
            case "dead_bush" -> new BlockDeadBush(properties);
            case "dirt" -> new BlockDirt(properties);
            case "dispenser" -> new BlockDispenser(properties);
            case "door" -> new BlockDoor(properties);
            case "double_plant" -> new BlockDoublePlant(properties);
            case "end_portal_frame" -> new BlockEndPortalFrame(properties);
            case "ender_chest" -> new BlockEnderChest(properties);
            case "fence" -> new BlockFence(properties);
            case "fence_gate" -> new BlockFenceGate(properties);
            case "fire" -> new BlockFire(properties);
            case "flower_pot" -> new BlockFlowerPot(properties);
            case "furnace" -> new BlockFurnace(properties);
            case "grass" -> new BlockGrass(properties);
            case "hopper" -> new BlockHopper(properties);
            case "ladder" -> new BlockLadder(properties);
            case "lever" -> new BlockLever(properties);
            case "lily_pad" -> new BlockLilyPad(properties);
            case "mushroom" -> new BlockMushroom(properties);
            case "nether_wart" -> new BlockNetherWart(properties);
            case "pane" -> new BlockPane(properties);
            case "piston" -> new BlockPistonBase(properties);
            case "piston_extension" -> new BlockPistonExtension(properties);
            case "portal" -> new BlockPortal(properties);
            case "pressure_plate" -> new BlockPressurePlate(properties);
            case "pumpkin" -> new BlockPumpkin(properties);
            case "quartz" -> new BlockQuartz(properties);
            case "rail" -> new BlockRail(properties);
            case "redstone_diode" -> new BlockRedstoneDiode(properties);
            case "redstone_wire" -> new BlockRedstoneWire(properties);
            case "reed" -> new BlockReed(properties);
            case "rotated_pillar" -> new BlockRotatedPillar(properties);
            case "skull" -> new BlockSkull(properties);
            case "slab" -> new BlockSlab(properties, requiredString(value, "doubleSlab", properties));
            case "snow_layer" -> new BlockSnowLayer(properties);
            case "stairs" -> new BlockStairs(properties);
            case "standing_sign" -> new BlockStandingSign(properties);
            case "stem" -> new BlockStem(properties, requiredString(value, "fruit", properties));
            case "torch" -> new BlockTorch(properties);
            case "trapdoor" -> new BlockTrapDoor(properties);
            case "tripwire" -> new BlockTripWire(properties);
            case "tripwire_hook" -> new BlockTripWireHook(properties);
            case "vine" -> new BlockVine(properties);
            case "wall" -> new BlockWall(properties);
            case "wall_sign" -> new BlockWallSign(properties);
            default -> throw new IllegalStateException("Unknown block class " + type + " for " + properties.name());
        };
    }

    private static String requiredString(JsonObject value, String key, Block.Properties properties) {
        if (!value.has(key)) throw new IllegalStateException(properties.name() + " needs \"" + key + "\" for its block class");
        return value.get(key).getAsString();
    }

    /** A box authored in sixteenths: [minX, minY, minZ, maxX, maxY, maxZ]. */
    private static AxisAlignedBB box(JsonArray values) {
        if (values.size() != 6) throw new IllegalStateException("A block box needs six numbers");
        return AxisAlignedBB.sixteenths(values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble(),
            values.get(3).getAsDouble(), values.get(4).getAsDouble(), values.get(5).getAsDouble());
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }
    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value == null ? fallback : value.getAsBoolean();
    }
    private static float floatValue(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        return value == null ? fallback : value.getAsFloat();
    }
    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null ? fallback : value.getAsInt();
    }
    private static int parseColor(String color) {
        return Integer.parseInt(color.startsWith("#") ? color.substring(1) : color, 16) & 0xffffff;
    }

    public Block get(ResourceLocation name) { return byName.get(name); }
    public Block get(String name) {
        Block block = get(new ResourceLocation(name));
        if (block == null) throw new IllegalArgumentException("Unknown block " + name);
        return block;
    }
    public Block byId(int id) { return id >= 0 && id < byId.size() ? byId.get(id) : byId.get(0); }
    public Block air() { return byId.get(0); }
    public int size() { return byId.size(); }
    public Collection<Block> values() { return List.copyOf(byId); }
}

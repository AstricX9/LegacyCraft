package net.minecraft.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolValues;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * What a broken block drops (master context §16), from
 * {@code assets/legacycraft/loot/blocks.json} plus the few 1.8.9 rules that
 * depend on randomness or growth stage, which are coded here: leaves
 * (saplings 1/20, jungle 1/40, apples 1/200 from oak and dark oak), grass
 * and ferns (seeds 1/8), dead bushes (0-2 sticks), vines, snow layers (one
 * snowball per layer), gravel (flint 1/10), crops, stems, nether wart,
 * cocoa and huge mushroom blocks. Shears take leaves, grass, ferns, dead
 * bushes and vines as themselves. HYPOTHESIS until compared with vanilla.
 */
public final class BlockDrops {
    /** One line of the drop table: {@code item} (or the block's own item when null), a count range and a chance. */
    record Rule(int[] meta, String item, String block, int min, int max, float chance) {
        boolean appliesTo(int metadata) {
            if (meta == null) return true;
            for (int value : meta) if (value == metadata) return true;
            return false;
        }
    }

    /** Resolves item names and "the item that places this block variant". */
    public interface Items {
        Item byName(String name);
        /** The item placing (block, metadata), matching on the variant bits when metadata also encodes facing etc. */
        Item placing(String block, int metadata);
    }

    private final Map<String, List<Rule>> rules;
    private final Items items;

    private BlockDrops(Map<String, List<Rule>> rules, Items items) {
        this.rules = rules;
        this.items = items;
    }

    public static BlockDrops load(IResourceManager resources, Items items) {
        ResourceLocation location = new ResourceLocation("legacycraft:loot/blocks.json");
        try (var reader = new InputStreamReader(resources.open(location), StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject(), items);
        } catch (IOException error) {
            throw new IllegalStateException("Could not read " + location, error);
        }
    }

    public static BlockDrops parse(JsonObject root, Items items) {
        Map<String, List<Rule>> rules = new HashMap<>();
        for (var entry : root.getAsJsonObject("blocks").entrySet()) {
            List<Rule> list = new ArrayList<>();
            for (JsonElement raw : entry.getValue().getAsJsonArray()) {
                JsonObject rule = raw.getAsJsonObject();
                int[] meta = null;
                if (rule.has("meta")) {
                    var values = rule.getAsJsonArray("meta");
                    meta = new int[values.size()];
                    for (int i = 0; i < meta.length; i++) meta[i] = values.get(i).getAsInt();
                }
                int min = 1, max = 1;
                if (rule.has("count")) {
                    min = rule.getAsJsonArray("count").get(0).getAsInt();
                    max = rule.getAsJsonArray("count").get(1).getAsInt();
                }
                list.add(new Rule(meta, rule.has("item") ? rule.get("item").getAsString() : null,
                    rule.has("block") ? rule.get("block").getAsString() : null, min, max,
                    rule.has("chance") ? rule.get("chance").getAsFloat() : 1f));
            }
            rules.put(entry.getKey(), list);
        }
        return new BlockDrops(rules, items);
    }

    /**
     * Drops for a player breaking {@code block} (by name) with {@code metadata}
     * using {@code tool} (null = empty hand). Returns nothing when the tool
     * cannot harvest the block. For the upper half of a double plant pass the
     * lower half's metadata.
     */
    public List<ItemStack> drops(String block, String material, int metadata, Item tool, Random random) {
        return drops(block, material, metadata, tool, 0, false, false, random);
    }

    /** Blocks with a tile entity, which Silk Touch never takes whole (Block.canSilkHarvest). */
    private static final java.util.Set<String> TILE_ENTITIES = java.util.Set.of("furnace", "lit_furnace", "dispenser",
        "dropper", "mob_spawner", "noteblock", "jukebox", "command_block", "beacon", "chest", "trapped_chest", "hopper",
        "brewing_stand", "enchanting_table", "daylight_detector", "daylight_detector_inverted", "standing_sign",
        "wall_sign", "skull", "flower_pot", "piston_extension", "end_portal");

    /**
     * Whether Silk Touch takes a block as itself (Block.canSilkHarvest): full
     * cubes without a tile entity, except double slabs, plus panes and ender
     * chests, which override it.
     */
    public static boolean canSilkHarvest(String block, boolean fullCube) {
        if (block.equals("glass_pane") || block.equals("stained_glass_pane") || block.equals("ender_chest")) return true;
        if (block.startsWith("double_") && block.endsWith("slab") || block.equals("double_wooden_slab")) return false;
        return fullCube && !TILE_ENTITIES.contains(block);
    }

    /**
     * Drops with the held tool's enchantments: {@code silkTouch} on a block
     * that {@link #canSilkHarvest} gives the block itself (createStackedBlock:
     * lit redstone ore gives redstone ore, a silverfish block its stone), and
     * {@code fortune} (the Fortune level) raises ore, glowstone, melon, sea
     * lantern, redstone, crop, nether wart and tall grass yields and the
     * flint, sapling and apple chances (quantityDroppedWithBonus and friends).
     * HYPOTHESIS: recalled 1.8.9 formulas.
     */
    public List<ItemStack> drops(String block, String material, int metadata, Item tool, int fortune, boolean silkTouch,
                                 boolean fullCube, Random random) {
        if (!ToolValues.canHarvest(tool, block, material)) return new ArrayList<>();
        return dropsOf(block, material, metadata, tool, fortune, silkTouch, fullCube, random);
    }

    /**
     * What an explosion knocks loose (Block.dropBlockAsItemWithChance via
     * Explosion.doExplosionB): the block's normal drops whatever tool would
     * be needed, each kept with {@code chance} (1 / explosion size).
     */
    public List<ItemStack> explosionDrops(String block, String material, int metadata, float chance, Random random) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : dropsOf(block, material, metadata, null, 0, false, false, random))
            if (random.nextFloat() <= chance) result.add(stack);
        return result;
    }

    private List<ItemStack> dropsOf(String block, String material, int metadata, Item tool, int fortune, boolean silkTouch,
                                    boolean fullCube, Random random) {
        List<ItemStack> result = new ArrayList<>();
        if (silkTouch && canSilkHarvest(block, fullCube)) {
            Item whole = switch (block) {
                case "lit_redstone_ore" -> items.placing("redstone_ore", 0);
                case "monster_egg" -> switch (metadata) {
                    case 1 -> items.placing("cobblestone", 0);
                    case 2, 3, 4, 5 -> items.placing("stonebrick", metadata - 2);
                    default -> items.placing("stone", 0);
                };
                default -> items.placing(block, metadata);
            };
            if (whole != null) {
                result.add(new ItemStack(whole, 1));
                return result;
            }
        }
        boolean shears = ToolValues.type(tool) == ToolValues.Type.SHEARS;
        switch (block) {
            case "leaves", "leaves2" -> {
                if (shears) { add(result, items.placing(block, metadata), 1); return result; }
                int variant = metadata & 3;
                boolean jungle = block.equals("leaves") && variant == 3;
                int saplingChance = jungle ? 40 : 20;
                if (fortune > 0) saplingChance = Math.max(10, saplingChance - (2 << fortune));
                if (random.nextInt(saplingChance) == 0) add(result, items.byName(sapling(block, variant)), 1);
                boolean appleTree = block.equals("leaves") ? variant == 0 : variant == 1;
                int appleChance = 200;
                if (fortune > 0) appleChance = Math.max(40, appleChance - (10 << fortune));
                if (appleTree && random.nextInt(appleChance) == 0) add(result, items.byName("apple"), 1);
                return result;
            }
            case "tallgrass" -> {
                if (shears) add(result, items.placing(block, metadata), 1);
                else if (random.nextInt(8) == 0) add(result, items.byName("wheat_seeds"), 1 + random.nextInt(fortune * 2 + 1));
                return result;
            }
            case "double_plant" -> {
                int variant = metadata & 7;
                if (variant == 2 || variant == 3) {
                    if (shears) add(result, items.byName(variant == 2 ? "tall_grass" : "fern"), 2);
                    else if (random.nextInt(8) == 0) add(result, items.byName("wheat_seeds"), 1);
                } else {
                    add(result, items.placing(block, variant), 1);
                }
                return result;
            }
            case "deadbush" -> {
                if (shears) add(result, items.placing(block, metadata), 1);
                else add(result, items.byName("stick"), random.nextInt(3));
                return result;
            }
            case "vine" -> {
                if (shears) add(result, items.placing(block, metadata), 1);
                return result;
            }
            case "snow_layer" -> {
                add(result, items.byName("snowball"), (metadata & 7) + 1);
                return result;
            }
            case "gravel" -> {
                add(result, items.byName(random.nextInt(10 - Math.min(fortune, 3) * 3) == 0 ? "flint" : "gravel"), 1);
                return result;
            }
            case "wheat", "carrots", "potatoes" -> {
                int age = metadata & 7;
                String seed = block.equals("wheat") ? "wheat_seeds" : block.equals("carrots") ? "carrot" : "potato";
                String crop = block.equals("wheat") ? "wheat" : seed;
                add(result, items.byName(age >= 7 ? crop : seed), 1);
                if (age >= 7) {
                    int extra = 0;
                    for (int i = 0; i < 3 + fortune; i++) if (random.nextInt(15) <= age) extra++;
                    add(result, items.byName(seed), extra);
                    if (block.equals("potatoes") && random.nextInt(50) == 0) add(result, items.byName("poisonous_potato"), 1);
                }
                return result;
            }
            case "pumpkin_stem", "melon_stem" -> {
                int age = metadata & 7, seeds = 0;
                for (int i = 0; i < 3; i++) if (random.nextInt(15) <= age) seeds++;
                add(result, items.byName(block.equals("pumpkin_stem") ? "pumpkin_seeds" : "melon_seeds"), seeds);
                return result;
            }
            case "nether_wart" -> {
                int count = 1;
                if ((metadata & 3) >= 3) {
                    count = 2 + random.nextInt(3);
                    if (fortune > 0) count += random.nextInt(fortune + 1);
                }
                add(result, items.byName("nether_wart"), count);
                return result;
            }
            case "cocoa" -> {
                add(result, items.byName("dye_brown"), (metadata >> 2) >= 2 ? 3 : 1);
                return result;
            }
            case "brown_mushroom_block", "red_mushroom_block" -> {
                add(result, items.byName(block.startsWith("brown") ? "brown_mushroom" : "red_mushroom"),
                    Math.max(0, random.nextInt(10) - 7));
                return result;
            }
            default -> {}
        }
        List<Rule> list = rules.get(block);
        if (list == null) {
            add(result, items.placing(block, metadata), 1);
            return result;
        }
        for (Rule rule : list) {
            if (!rule.appliesTo(metadata)) continue;
            if (rule.chance() < 1f && random.nextFloat() >= rule.chance()) continue;
            int count = rule.min() + (rule.max() > rule.min() ? random.nextInt(rule.max() - rule.min() + 1) : 0);
            if (fortune > 0) count = withFortune(block, count, fortune, random);
            Item item = rule.item() != null ? items.byName(rule.item())
                : items.placing(rule.block() != null ? rule.block() : block, metadata);
            add(result, item, count);
        }
        return result;
    }

    /** A table drop's count with Fortune (quantityDroppedWithBonus of BlockOre, BlockRedstoneOre, BlockGlowstone, BlockMelon, BlockSeaLantern). */
    static int withFortune(String block, int count, int fortune, Random random) {
        return switch (block) {
            case "coal_ore", "diamond_ore", "emerald_ore", "quartz_ore", "lapis_ore" ->
                count * (Math.max(0, random.nextInt(fortune + 2) - 1) + 1);
            case "redstone_ore", "lit_redstone_ore" -> count + random.nextInt(fortune + 1);
            case "glowstone" -> Math.max(1, Math.min(4, count + random.nextInt(fortune + 1)));
            case "melon_block" -> Math.min(9, count + random.nextInt(1 + fortune));
            case "sea_lantern" -> Math.max(1, Math.min(5, count + random.nextInt(fortune + 1)));
            default -> count;
        };
    }

    private static String sapling(String block, int variant) {
        if (block.equals("leaves2")) return variant == 1 ? "dark_oak_sapling" : "acacia_sapling";
        return switch (variant) {
            case 1 -> "spruce_sapling";
            case 2 -> "birch_sapling";
            case 3 -> "jungle_sapling";
            default -> "oak_sapling";
        };
    }

    private static void add(List<ItemStack> result, Item item, int count) {
        if (item == null) return;
        while (count > 0) {
            int size = Math.min(count, item.maxStackSize());
            result.add(new ItemStack(item, size));
            count -= size;
        }
    }

    /** {@link Items} over a set of items: "the item placing a block variant" matched on progressively fewer metadata bits. */
    public static Items itemsOf(Collection<Item> all) {
        Map<String, Item> byName = new HashMap<>();
        Map<String, List<Item>> byBlock = new HashMap<>();
        for (Item item : all) {
            byName.put(item.name().path(), item);
            if (item.placedBlock() != null) byBlock.computeIfAbsent(item.placedBlock().name().path(), key -> new ArrayList<>()).add(item);
        }
        return new Items() {
            @Override public Item byName(String name) { return byName.get(name); }
            @Override public Item placing(String block, int metadata) {
                List<Item> candidates = byBlock.get(block);
                if (candidates == null || candidates.isEmpty()) return null;
                for (int mask : new int[]{0xF, 0x7, 0x3, 0xC})
                    for (Item item : candidates)
                        if ((item.placedMetadata() & mask) == (metadata & mask)) return item;
                return candidates.get(0);
            }
        };
    }
}

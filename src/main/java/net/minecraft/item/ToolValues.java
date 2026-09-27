package net.minecraft.item;

import java.util.Map;
import java.util.Set;

/**
 * What a held item does to block breaking in 1.8.9 (master context §30):
 * which blocks a tool is effective on and how fast it digs them, which blocks
 * need the right tool to drop anything, and how much durability a break costs.
 * COMMUNITY_CORROBORATED values: tool efficiency wood 2, stone 4, iron 6,
 * diamond 8, gold 12; harvest levels wood/gold 0, stone 1, iron 2, diamond 3.
 *
 * <p>Works on block names and material names (the values in
 * {@code data/mc189/block_data}), so it can be tested without a registry.
 */
public final class ToolValues {
    public enum Type { NONE, PICKAXE, AXE, SHOVEL, SWORD, HOE, SHEARS }

    private record Material(float efficiency, int level) {}
    private static final Map<String, Material> MATERIALS = Map.of(
        "wooden", new Material(2, 0), "stone", new Material(4, 1), "iron", new Material(6, 2),
        "diamond", new Material(8, 3), "golden", new Material(12, 0));

    /** Blocks a pickaxe is effective on besides rock/iron/anvil materials (ItemPickaxe's list). */
    private static final Set<String> PICKAXE_BLOCKS = Set.of("activator_rail", "coal_ore", "cobblestone", "detector_rail",
        "diamond_block", "diamond_ore", "double_stone_slab", "golden_rail", "gold_block", "gold_ore", "ice", "iron_block",
        "iron_ore", "lapis_block", "lapis_ore", "lit_redstone_ore", "mossy_cobblestone", "netherrack", "packed_ice", "rail",
        "redstone_ore", "sandstone", "red_sandstone", "stone", "stone_slab");
    private static final Set<String> AXE_BLOCKS = Set.of("planks", "bookshelf", "log", "log2", "chest", "pumpkin",
        "lit_pumpkin", "melon_block", "ladder");
    private static final Set<String> SHOVEL_BLOCKS = Set.of("clay", "dirt", "farmland", "grass", "gravel", "mycelium",
        "sand", "snow", "snow_layer", "soul_sand");
    /** Materials that drop nothing unless broken with the right tool (Material.isToolNotRequired() == false). */
    private static final Set<String> TOOL_REQUIRED = Set.of("rock", "iron", "anvil", "snow", "web");

    private ToolValues() {}

    public static Type type(Item item) {
        if (item == null) return Type.NONE;
        String path = item.name().path();
        if (path.equals("shears")) return Type.SHEARS;
        int split = path.lastIndexOf('_');
        if (split < 0 || !MATERIALS.containsKey(path.substring(0, split))) return Type.NONE;
        return switch (path.substring(split + 1)) {
            case "pickaxe" -> Type.PICKAXE;
            case "axe" -> Type.AXE;
            case "shovel" -> Type.SHOVEL;
            case "sword" -> Type.SWORD;
            case "hoe" -> Type.HOE;
            default -> Type.NONE;
        };
    }

    private static Material material(Item item) {
        String path = item.name().path();
        return MATERIALS.get(path.substring(0, path.lastIndexOf('_')));
    }

    /** Harvest level of a pickaxe/axe/shovel (-1 for anything else). */
    public static int harvestLevel(Item item) {
        Type type = type(item);
        return type == Type.PICKAXE || type == Type.AXE || type == Type.SHOVEL ? material(item).level() : -1;
    }

    /** Dig speed multiplier (vanilla getStrVsBlock): 1 unless the item is suited to the block. */
    public static float strVsBlock(Item item, String block, String material) {
        Type type = type(item);
        return switch (type) {
            case PICKAXE -> material.equals("rock") || material.equals("iron") || material.equals("anvil")
                || PICKAXE_BLOCKS.contains(block) ? material(item).efficiency() : 1f;
            case AXE -> material.equals("wood") || material.equals("plants") || material.equals("vine")
                || AXE_BLOCKS.contains(block) ? material(item).efficiency() : 1f;
            case SHOVEL -> SHOVEL_BLOCKS.contains(block) ? material(item).efficiency() : 1f;
            case SWORD -> block.equals("web") ? 15f : material.equals("plants") || material.equals("vine")
                || material.equals("leaves") || material.equals("gourd") ? 1.5f : 1f;
            case SHEARS -> block.equals("web") || material.equals("leaves") ? 15f : block.equals("wool") ? 5f : 1f;
            default -> 1f;
        };
    }

    /** Whether breaking the block with this item (null = hand) drops anything (canHarvestBlock). */
    public static boolean canHarvest(Item item, String block, String material) {
        if (!TOOL_REQUIRED.contains(material)) return true;
        Type type = type(item);
        return switch (type) {
            case PICKAXE -> {
                int needed = switch (block) {
                    case "obsidian" -> 3;
                    case "diamond_block", "diamond_ore", "emerald_ore", "emerald_block", "gold_block", "gold_ore",
                         "redstone_ore", "lit_redstone_ore" -> 2;
                    case "iron_block", "iron_ore", "lapis_block", "lapis_ore" -> 1;
                    default -> 0;
                };
                yield material.equals("rock") || material.equals("iron") || material.equals("anvil")
                    ? material(item).level() >= needed : false;
            }
            case SHOVEL -> block.equals("snow_layer") || block.equals("snow");
            case SWORD -> block.equals("web");
            case SHEARS -> block.equals("web") || block.equals("redstone_wire") || block.equals("tripwire");
            default -> false;
        };
    }

    /**
     * Fraction of the block broken per tick (vanilla getPlayerRelativeBlockHardness):
     * {@code strVsBlock / hardness / 30} when the item can harvest it, {@code / 100}
     * when it cannot, divided by 5 while airborne or with the head underwater.
     * Unbreakable blocks (hardness &lt; 0) give 0; hardness 0 breaks at once.
     */
    public static float breakProgressPerTick(Item item, String block, String material, float hardness,
                                             boolean onGround, boolean headInWater) {
        if (hardness < 0) return 0f;
        if (hardness == 0) return 1f;
        float speed = strVsBlock(item, block, material);
        if (headInWater) speed /= 5f;
        if (!onGround) speed /= 5f;
        return speed / hardness / (canHarvest(item, block, material) ? 30f : 100f);
    }

    /** Durability a break costs: tools 1, swords 2 (only for blocks with hardness above 0), shears 1 on what they cut. */
    public static int damageForBreak(Item item, String block, String material, float hardness) {
        Type type = type(item);
        return switch (type) {
            case PICKAXE, AXE, SHOVEL -> hardness != 0 ? 1 : 0;
            case SWORD -> hardness != 0 ? 2 : 0;
            case SHEARS -> material.equals("leaves") || block.equals("web") || block.equals("tallgrass") || block.equals("vine")
                || block.equals("tripwire") || block.equals("wool") ? 1 : 0;
            default -> 0;
        };
    }
}

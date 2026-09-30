package net.minecraft.enchantment;

import net.minecraft.entity.DamageSource;
import net.minecraft.inventory.ArmorValues;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The 1.8.9 enchantments (Enchantment and its subclasses; HYPOTHESIS: ids,
 * weights, level ranges and the enchantability windows are recalled). Each
 * has a legacy id (what items store), a translation key, a weight (how
 * often the table picks it, also its "rarity" for anvil costs), a maximum
 * level, the kind of item the table puts it on, and for each level the
 * range of enchantability that can roll it.
 */
public final class Enchantment {
    /** What the enchanting table puts an enchantment on (EnumEnchantmentType). */
    public enum Type {
        ALL, ARMOR, ARMOR_FEET, ARMOR_LEGS, ARMOR_TORSO, ARMOR_HEAD, WEAPON, DIGGER, FISHING_ROD, BREAKABLE, BOW;

        /** EnumEnchantmentType.canEnchantItem. */
        public boolean canEnchantItem(Item item) {
            if (this == ALL) return true;
            if (this == BREAKABLE && item.isDamageable()) return true;
            if (ArmorValues.isArmorPiece(item)) {
                if (this == ARMOR) return true;
                int slot = ArmorValues.armorSlot(item);
                return switch (slot) {
                    case 3 -> this == ARMOR_HEAD;
                    case 2 -> this == ARMOR_TORSO;
                    case 1 -> this == ARMOR_LEGS;
                    case 0 -> this == ARMOR_FEET;
                    default -> false;
                };
            }
            ToolValues.Type tool = ToolValues.type(item);
            return switch (this) {
                case DIGGER -> tool == ToolValues.Type.PICKAXE || tool == ToolValues.Type.AXE || tool == ToolValues.Type.SHOVEL;
                case WEAPON -> tool == ToolValues.Type.SWORD;
                case FISHING_ROD -> item.name().path().equals("fishing_rod");
                case BOW -> item.name().path().equals("bow");
                default -> false;
            };
        }
    }

    /** Which family an enchantment belongs to, for the compatibility rules and effects. */
    enum Kind { PROTECTION, RESPIRATION, AQUA_AFFINITY, THORNS, DEPTH_STRIDER, DAMAGE, KNOCKBACK, FIRE_ASPECT, LOOTING,
        EFFICIENCY, SILK_TOUCH, UNBREAKING, FORTUNE, POWER, PUNCH, FLAME, INFINITY, LUCK_OF_THE_SEA, LURE }

    public final int id;
    /** The translation key without "enchantment." ("protect.all"). */
    public final String name;
    public final int weight;
    public final int maxLevel;
    public final Type type;
    final Kind kind;
    /** For protection (0 all, 1 fire, 2 fall, 3 explosion, 4 projectile) and damage (0 all, 1 undead, 2 arthropods): the variant. */
    final int variant;
    private final int base, perLevel, window;

    private Enchantment(int id, String name, int weight, int maxLevel, Type type, Kind kind, int variant,
                        int base, int perLevel, int window) {
        this.id = id;
        this.name = name;
        this.weight = weight;
        this.maxLevel = maxLevel;
        this.type = type;
        this.kind = kind;
        this.variant = variant;
        this.base = base;
        this.perLevel = perLevel;
        this.window = window;
    }

    private static final Enchantment[] BY_ID = new Enchantment[256];
    private static final List<Enchantment> LIST = new ArrayList<>();

    private static Enchantment add(int id, String name, int weight, int maxLevel, Type type, Kind kind, int variant,
                                   int base, int perLevel, int window) {
        Enchantment enchantment = new Enchantment(id, name, weight, maxLevel, type, kind, variant, base, perLevel, window);
        BY_ID[id] = enchantment;
        return enchantment;
    }

    public static final Enchantment PROTECTION = add(0, "protect.all", 10, 4, Type.ARMOR, Kind.PROTECTION, 0, 1, 11, 20);
    public static final Enchantment FIRE_PROTECTION = add(1, "protect.fire", 5, 4, Type.ARMOR, Kind.PROTECTION, 1, 10, 8, 12);
    public static final Enchantment FEATHER_FALLING = add(2, "protect.fall", 5, 4, Type.ARMOR_FEET, Kind.PROTECTION, 2, 5, 6, 10);
    public static final Enchantment BLAST_PROTECTION = add(3, "protect.explosion", 2, 4, Type.ARMOR, Kind.PROTECTION, 3, 5, 8, 12);
    public static final Enchantment PROJECTILE_PROTECTION = add(4, "protect.projectile", 5, 4, Type.ARMOR, Kind.PROTECTION, 4, 3, 6, 15);
    public static final Enchantment RESPIRATION = add(5, "oxygen", 2, 3, Type.ARMOR_HEAD, Kind.RESPIRATION, 0, 0, 10, 30);
    public static final Enchantment AQUA_AFFINITY = add(6, "waterWorker", 2, 1, Type.ARMOR_HEAD, Kind.AQUA_AFFINITY, 0, 1, 0, 40);
    public static final Enchantment THORNS = add(7, "thorns", 1, 3, Type.ARMOR_TORSO, Kind.THORNS, 0, 10, 20, 50);
    public static final Enchantment DEPTH_STRIDER = add(8, "waterWalker", 2, 3, Type.ARMOR_FEET, Kind.DEPTH_STRIDER, 0, 0, 10, 15);
    public static final Enchantment SHARPNESS = add(16, "damage.all", 10, 5, Type.WEAPON, Kind.DAMAGE, 0, 1, 11, 20);
    public static final Enchantment SMITE = add(17, "damage.undead", 5, 5, Type.WEAPON, Kind.DAMAGE, 1, 5, 8, 20);
    public static final Enchantment BANE_OF_ARTHROPODS = add(18, "damage.arthropods", 5, 5, Type.WEAPON, Kind.DAMAGE, 2, 5, 8, 20);
    public static final Enchantment KNOCKBACK = add(19, "knockback", 5, 2, Type.WEAPON, Kind.KNOCKBACK, 0, 5, 20, 50);
    public static final Enchantment FIRE_ASPECT = add(20, "fire", 2, 2, Type.WEAPON, Kind.FIRE_ASPECT, 0, 10, 20, 50);
    public static final Enchantment LOOTING = add(21, "lootBonus", 2, 3, Type.WEAPON, Kind.LOOTING, 0, 15, 9, 50);
    public static final Enchantment EFFICIENCY = add(32, "digging", 10, 5, Type.DIGGER, Kind.EFFICIENCY, 0, 1, 10, 50);
    public static final Enchantment SILK_TOUCH = add(33, "untouching", 1, 1, Type.DIGGER, Kind.SILK_TOUCH, 0, 15, 0, 50);
    public static final Enchantment UNBREAKING = add(34, "durability", 5, 3, Type.BREAKABLE, Kind.UNBREAKING, 0, 5, 8, 50);
    public static final Enchantment FORTUNE = add(35, "lootBonusDigger", 2, 3, Type.DIGGER, Kind.FORTUNE, 0, 15, 9, 50);
    public static final Enchantment POWER = add(48, "arrowDamage", 10, 5, Type.BOW, Kind.POWER, 0, 1, 10, 15);
    public static final Enchantment PUNCH = add(49, "arrowKnockback", 2, 2, Type.BOW, Kind.PUNCH, 0, 12, 20, 25);
    public static final Enchantment FLAME = add(50, "arrowFire", 2, 1, Type.BOW, Kind.FLAME, 0, 20, 0, 30);
    public static final Enchantment INFINITY = add(51, "arrowInfinite", 1, 1, Type.BOW, Kind.INFINITY, 0, 20, 0, 30);
    public static final Enchantment LUCK_OF_THE_SEA = add(61, "lootBonusFishing", 2, 3, Type.FISHING_ROD, Kind.LUCK_OF_THE_SEA, 0, 15, 9, 50);
    public static final Enchantment LURE = add(62, "fishingSpeed", 2, 3, Type.FISHING_ROD, Kind.LURE, 0, 15, 9, 50);

    static {
        for (Enchantment enchantment : BY_ID) if (enchantment != null) LIST.add(enchantment);
    }

    /** Every enchantment in id order (Enchantment.enchantmentsBookList). */
    public static final List<Enchantment> ALL = Collections.unmodifiableList(LIST);

    public static Enchantment byId(int id) { return id >= 0 && id < BY_ID.length ? BY_ID[id] : null; }

    /** By its 1.8.9 command name ("sharpness", "protection") or numeric id; null when unknown. */
    public static Enchantment byName(String name) {
        for (Enchantment enchantment : ALL) if (enchantment.commandName().equals(name)) return enchantment;
        try {
            return byId(Integer.parseInt(name));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** The resource-style name 1.8.9 registers it under (Enchantment.enchantmentsBookList location). */
    public String commandName() {
        return switch (id) {
            case 0 -> "protection"; case 1 -> "fire_protection"; case 2 -> "feather_falling"; case 3 -> "blast_protection";
            case 4 -> "projectile_protection"; case 5 -> "respiration"; case 6 -> "aqua_affinity"; case 7 -> "thorns";
            case 8 -> "depth_strider"; case 16 -> "sharpness"; case 17 -> "smite"; case 18 -> "bane_of_arthropods";
            case 19 -> "knockback"; case 20 -> "fire_aspect"; case 21 -> "looting"; case 32 -> "efficiency";
            case 33 -> "silk_touch"; case 34 -> "unbreaking"; case 35 -> "fortune"; case 48 -> "power"; case 49 -> "punch";
            case 50 -> "flame"; case 51 -> "infinity"; case 61 -> "luck_of_the_sea"; case 62 -> "lure";
            default -> name;
        };
    }

    /** "enchantment.protect.all". */
    public String translationKey() { return "enchantment." + name; }

    public int minLevel() { return 1; }

    /**
     * The lowest enchantability that rolls this level (getMinEnchantability):
     * for most, a base plus a step per level after the first; Respiration and
     * Depth Strider are 10 per level, Aqua Affinity 1, the one-level ones fixed.
     */
    public int minEnchantability(int level) {
        return switch (kind) {
            case RESPIRATION, DEPTH_STRIDER -> perLevel * level;
            default -> base + (level - 1) * perLevel;
        };
    }

    /**
     * The highest enchantability that rolls this level (getMaxEnchantability):
     * the minimum plus a window, except for the enchantments whose code adds
     * 50 to the base class's minimum ({@code 1 + 10 x level}) instead
     * (Thorns, Knockback, Fire Aspect, Looting, Efficiency, Silk Touch,
     * Unbreaking, Fortune, Luck of the Sea, Lure).
     */
    public int maxEnchantability(int level) {
        return window == 50 ? 1 + level * 10 + 50 : minEnchantability(level) + window;
    }

    /**
     * Whether two enchantments can share an item (canApplyTogether): never the
     * same one twice; protections exclude each other except Feather Falling;
     * Sharpness, Smite and Bane exclude each other; Silk Touch excludes
     * Fortune; and the loot bonuses (Looting, Fortune, Luck of the Sea, Lure)
     * exclude Silk Touch. That makes Silk Touch-then-Looting allowed but not
     * Looting-then-Silk Touch, as in vanilla.
     */
    public boolean canApplyTogether(Enchantment other) {
        if (other == null || other == this) return false;
        if (kind == Kind.PROTECTION && other.kind == Kind.PROTECTION)
            return variant == 2 || other.variant == 2;
        if (kind == Kind.DAMAGE && other.kind == Kind.DAMAGE) return false;
        if (kind == Kind.SILK_TOUCH && other.kind == Kind.FORTUNE) return false;
        boolean lootBonus = kind == Kind.LOOTING || kind == Kind.FORTUNE || kind == Kind.LUCK_OF_THE_SEA || kind == Kind.LURE;
        if (lootBonus && other.kind == Kind.SILK_TOUCH) return false;
        return true;
    }

    /**
     * Whether an anvil may put this on the item (Enchantment.canApply): its
     * table type, plus Sharpness and friends on axes, Efficiency, Silk Touch
     * on shears, Unbreaking on anything that wears, Thorns on any armour.
     */
    public boolean canApply(ItemStack stack) {
        Item item = stack.item();
        if (kind == Kind.DAMAGE && ToolValues.type(item) == ToolValues.Type.AXE) return true;
        if ((kind == Kind.EFFICIENCY || kind == Kind.SILK_TOUCH) && ToolValues.type(item) == ToolValues.Type.SHEARS) return true;
        if (kind == Kind.UNBREAKING && item.isDamageable()) return true;
        if (kind == Kind.THORNS && ArmorValues.isArmorPiece(item)) return true;
        return type.canEnchantItem(item);
    }

    /** Protection's damage reduction for one piece (EnchantmentProtection.calcModifierDamage), 0 for others. */
    int protectionModifier(int level, DamageSource source) {
        if (kind != Kind.PROTECTION || source.canHarmInCreative()) return 0;
        float f = (6 + level * level) / 3f;
        return switch (variant) {
            case 0 -> (int) Math.floor(f * 0.75f);
            case 1 -> source.isFire() ? (int) Math.floor(f * 1.25f) : 0;
            case 2 -> source == DamageSource.FALL ? (int) Math.floor(f * 2.5f) : 0;
            case 3 -> source.isExplosion() ? (int) Math.floor(f * 1.5f) : 0;
            case 4 -> source.isProjectile() ? (int) Math.floor(f * 1.5f) : 0;
            default -> 0;
        };
    }

    @Override public String toString() { return commandName(); }
}

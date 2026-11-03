package net.minecraft.enchantment;

import net.minecraft.entity.DamageSource;
import net.minecraft.inventory.ArmorValues;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolValues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Enchantment rules shared by the table, the anvil and play (1.8.9
 * EnchantmentHelper; HYPOTHESIS: recalled). The table's random rolls follow
 * vanilla's order of calls on one {@link Random}, so a given seed offers the
 * same enchantments vanilla would.
 */
public final class EnchantmentHelper {
    private EnchantmentHelper() {}

    /** One rolled enchantment and level (EnchantmentData). */
    public record Data(Enchantment enchantment, int level) {}

    // ---- reading items --------------------------------------------------------------------

    /**
     * The enchantments of a stack as id to level (getEnchantments): an
     * enchanted book's stored ones, anything else's own.
     */
    public static LinkedHashMap<Integer, Integer> enchantments(ItemStack stack) {
        if (stack == null) return new LinkedHashMap<>();
        return new LinkedHashMap<>(isEnchantedBook(stack) ? stack.storedEnchantments() : stack.enchantments());
    }

    /**
     * Writes enchantments back (setEnchantments): an enchanted book stores
     * each (keeping higher levels it already has), anything else replaces its
     * own; unknown ids are dropped.
     */
    public static void setEnchantments(Map<Integer, Integer> map, ItemStack stack) {
        LinkedHashMap<Integer, Integer> known = new LinkedHashMap<>();
        for (var entry : map.entrySet()) if (Enchantment.byId(entry.getKey()) != null) known.put(entry.getKey(), entry.getValue());
        if (isEnchantedBook(stack)) {
            for (var entry : known.entrySet()) stack.addStoredEnchantment(entry.getKey(), entry.getValue());
        } else {
            stack.setEnchantments(known);
        }
    }

    public static boolean isEnchantedBook(ItemStack stack) { return stack != null && stack.item().name().path().equals("enchanted_book"); }
    public static boolean isBook(ItemStack stack) { return stack != null && stack.item().name().path().equals("book"); }

    /** The level of an enchantment on a stack (getEnchantmentLevel); 0 when absent. */
    public static int level(Enchantment enchantment, ItemStack stack) {
        return stack == null ? 0 : stack.level(enchantment.id);
    }

    /** The highest level of an enchantment across stacks (getMaxEnchantmentLevel). */
    public static int maxLevel(Enchantment enchantment, ItemStack[] stacks) {
        int best = 0;
        if (stacks != null) for (ItemStack stack : stacks) best = Math.max(best, level(enchantment, stack));
        return best;
    }

    /** Whether the stack shows the enchantment glint (Item.hasEffect): enchanted, or an enchanted book. */
    public static boolean hasGlint(ItemStack stack) {
        return stack != null && (stack.isEnchanted() || isEnchantedBook(stack) || hasEffect(stack.item()));
    }

    /**
     * Items that always shine (Item.hasEffect overrides): the enchanted golden
     * apple, potions with effects, bottles o' enchanting, nether stars and
     * written books.
     */
    public static boolean hasEffect(net.minecraft.item.Item item) {
        if (item.kind() == net.minecraft.item.Item.Kind.ENCHANTED_APPLE) return true;
        String id = item.vanillaId().path();
        if (id.equals("potion")) return !net.minecraft.item.Potions.effects(item.vanillaData()).isEmpty();
        return id.equals("experience_bottle") || id.equals("nether_star") || id.equals("written_book");
    }

    // ---- the enchanting table ---------------------------------------------------------------

    /**
     * How well an item takes enchantments (Item.getItemEnchantability): tools,
     * swords by material (wood 15, stone 5, iron 14, diamond 10, gold 22),
     * armour by material (leather 15, chainmail 12, iron 9, diamond 10, gold
     * 25), books, bows and fishing rods 1, anything else (hoes, shears) 0.
     */
    public static int enchantability(Item item) {
        String path = item.name().path();
        if (path.equals("book") || path.equals("bow") || path.equals("fishing_rod")) return 1;
        String armour = ArmorValues.material(item);
        if (armour != null) return switch (armour) {
            case "leather" -> 15; case "chainmail" -> 12; case "iron" -> 9; case "diamond" -> 10; case "golden" -> 25;
            default -> 0;
        };
        ToolValues.Type type = ToolValues.type(item);
        if (type == ToolValues.Type.PICKAXE || type == ToolValues.Type.AXE || type == ToolValues.Type.SHOVEL
            || type == ToolValues.Type.SWORD) {
            return switch (path.substring(0, path.lastIndexOf('_'))) {
                case "wooden" -> 15; case "stone" -> 5; case "iron" -> 14; case "diamond" -> 10; case "golden" -> 22;
                default -> 0;
            };
        }
        return 0;
    }

    /**
     * Whether the table will enchant a stack (ItemStack.isItemEnchantable): a
     * single damageable, unstackable item, or a single book, not already enchanted.
     */
    public static boolean isEnchantable(ItemStack stack) {
        if (stack == null || stack.isEnchanted()) return false;
        if (isBook(stack)) return stack.count() == 1;
        return stack.item().maxStackSize() == 1 && stack.item().isDamageable();
    }

    /**
     * The level one of the table's three buttons offers (calcItemStackEnchantability):
     * {@code j = 1..8 + power/2 + 0..power} with at most 15 bookshelves; the
     * top button offers {@code max(j/3, 1)}, the middle {@code 2j/3 + 1}, the
     * bottom {@code max(j, 2 x power)}.
     */
    public static int buttonLevel(Random random, int slot, int bookshelves, ItemStack stack) {
        if (enchantability(stack.item()) <= 0) return 0;
        if (bookshelves > 15) bookshelves = 15;
        int j = random.nextInt(8) + 1 + (bookshelves >> 1) + random.nextInt(bookshelves + 1);
        return slot == 0 ? Math.max(j / 3, 1) : slot == 1 ? j * 2 / 3 + 1 : Math.max(j, bookshelves * 2);
    }

    /**
     * The enchantments a level buys (buildEnchantmentList): the item's
     * enchantability shifts the level by up to ±15%, one enchantment is
     * picked by weight from those whose window holds that level, then while
     * {@code random(50) <= level} (halving the level each time) another
     * compatible one is added. Null when nothing fits.
     */
    public static List<Data> buildEnchantmentList(Random random, ItemStack stack, int level) {
        int enchantability = enchantability(stack.item());
        if (enchantability <= 0) return null;
        enchantability /= 4;
        enchantability = 1 + random.nextInt((enchantability >> 1) + 1) + random.nextInt((enchantability >> 1) + 1);
        int total = enchantability + level;
        float spread = (random.nextFloat() + random.nextFloat() - 1f) * 0.15f;
        int modified = (int) (total * (1f + spread) + 0.5f);
        if (modified < 1) modified = 1;
        List<Data> list = null;
        Map<Integer, Data> map = candidates(modified, stack);
        if (map != null && !map.isEmpty()) {
            Data first = weighted(random, map.values());
            if (first != null) {
                list = new ArrayList<>();
                list.add(first);
                for (int l = modified; random.nextInt(50) <= l; l >>= 1) {
                    Iterator<Integer> ids = map.keySet().iterator();
                    while (ids.hasNext()) {
                        Enchantment candidate = Enchantment.byId(ids.next());
                        for (Data chosen : list) {
                            if (!chosen.enchantment().canApplyTogether(candidate)) {
                                ids.remove();
                                break;
                            }
                        }
                    }
                    if (!map.isEmpty()) list.add(weighted(random, map.values()));
                }
            }
        }
        return list;
    }

    /**
     * Every enchantment (at its highest fitting level) whose window holds
     * {@code level} and that the table puts on the item, or on anything for a
     * book (mapEnchantmentData). A HashMap by id, iterated in HashMap order as vanilla's.
     */
    static Map<Integer, Data> candidates(int level, ItemStack stack) {
        Map<Integer, Data> map = null;
        boolean book = isBook(stack);
        for (Enchantment enchantment : Enchantment.ALL) {
            if (!enchantment.type.canEnchantItem(stack.item()) && !book) continue;
            for (int i = enchantment.minLevel(); i <= enchantment.maxLevel; i++) {
                if (level >= enchantment.minEnchantability(i) && level <= enchantment.maxEnchantability(i)) {
                    if (map == null) map = new HashMap<>();
                    map.put(enchantment.id, new Data(enchantment, i));
                }
            }
        }
        return map;
    }

    /** WeightedRandom.getRandomItem over enchantment weights. */
    private static Data weighted(Random random, Collection<Data> items) {
        int total = 0;
        for (Data data : items) total += data.enchantment().weight;
        if (total <= 0) throw new IllegalArgumentException("no weight");
        int pick = random.nextInt(total);
        for (Data data : items) {
            pick -= data.enchantment().weight;
            if (pick < 0) return data;
        }
        return null;
    }

    // ---- effects ----------------------------------------------------------------------------

    /**
     * The protection points worn armour gives against a source
     * (getEnchantmentModifierDamage): each Protection-type enchantment's
     * modifier summed, capped at 25, then half of it rounded up plus a random
     * 0..half.
     */
    public static int protectionPoints(ItemStack[] armour, DamageSource source, Random random) {
        int modifier = 0;
        if (armour != null)
            for (ItemStack stack : armour) {
                if (stack == null) continue;
                for (var entry : stack.enchantments().entrySet()) {
                    Enchantment enchantment = Enchantment.byId(entry.getKey());
                    if (enchantment != null) modifier += enchantment.protectionModifier(entry.getValue(), source);
                }
            }
        if (modifier > 25) modifier = 25;
        else if (modifier < 0) modifier = 0;
        return (modifier + 1 >> 1) + random.nextInt((modifier >> 1) + 1);
    }

    /**
     * Fire Protection shortens burning (EnchantmentProtection.getFireTimeForEntity):
     * the highest level worn takes 15% per level off.
     */
    public static int fireDuration(ItemStack[] armour, int ticks) {
        int level = maxLevel(Enchantment.FIRE_PROTECTION, armour);
        return level > 0 ? ticks - (int) Math.floor(ticks * level * 0.15f) : ticks;
    }

    /**
     * Whether one point of wear is prevented (EnchantmentDurability.negateDamage):
     * armour always takes wear 60% of the time, otherwise the chance to avoid
     * it is level / (level + 1).
     */
    public static boolean negateDamage(ItemStack stack, int level, Random random) {
        if (ArmorValues.isArmorPiece(stack.item()) && random.nextFloat() < 0.6f) return false;
        return random.nextInt(level + 1) > 0;
    }

    /**
     * Wears a stack by {@code amount}, each point possibly saved by Unbreaking
     * (ItemStack.attemptDamageItem + damageItem). Returns true when the last
     * item of the stack breaks.
     */
    public static boolean damageItem(ItemStack stack, int amount, Random random) {
        if (!stack.item().isDamageable() || amount <= 0) return false;
        int unbreaking = level(Enchantment.UNBREAKING, stack);
        int saved = 0;
        for (int i = 0; unbreaking > 0 && i < amount; i++) if (negateDamage(stack, unbreaking, random)) saved++;
        amount -= saved;
        if (amount <= 0) return false;
        return stack.damage(amount);
    }

    /** Levels of the Roman numerals 1.8.9's lang files have ("enchantment.level.1" .. 10). */
    public static String levelKey(int level) { return "enchantment.level." + level; }
}

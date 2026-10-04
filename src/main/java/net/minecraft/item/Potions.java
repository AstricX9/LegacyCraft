package net.minecraft.item;

import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Potion data values (1.8.9 PotionHelper and ItemPotion, HYPOTHESIS:
 * recalled and simplified to the potions brewing makes). The low four bits
 * pick the effect, bit 5 (32) makes it level II where it can be, bit 6 (64)
 * extends it to 8/3 of the time, bit 13 (8192) marks a drinkable potion
 * and bit 14 (16384) a splash potion, which lasts 3/4 as long.
 */
public final class Potions {
    private Potions() {}

    /** An effect a potion of these low bits gives: the type, its base ticks, and whether level II exists. */
    private record Base(Potion potion, int ticks, boolean upgradable) {}

    private static Base base(int bits) {
        return switch (bits & 15) {
            case 1 -> new Base(Potion.REGENERATION, 900, true);
            case 2 -> new Base(Potion.MOVE_SPEED, 3600, true);
            case 3 -> new Base(Potion.FIRE_RESISTANCE, 3600, false);
            case 4 -> new Base(Potion.POISON, 900, true);
            case 5 -> new Base(Potion.HEAL, 1, true);
            case 6 -> new Base(Potion.NIGHT_VISION, 3600, false);
            case 8 -> new Base(Potion.WEAKNESS, 1800, false);
            case 9 -> new Base(Potion.DAMAGE_BOOST, 3600, true);
            case 10 -> new Base(Potion.MOVE_SLOWDOWN, 1800, false);
            case 11 -> new Base(Potion.JUMP, 3600, true);
            case 12 -> new Base(Potion.HARM, 1, true);
            case 13 -> new Base(Potion.WATER_BREATHING, 3600, false);
            case 14 -> new Base(Potion.INVISIBILITY, 3600, false);
            default -> null;
        };
    }

    public static boolean isSplash(int data) { return (data & 16384) != 0; }

    /** ItemPotion.isEffectInstant: whether this potion's effects all act at once (Healing, Harming). */
    public static boolean isInstant(int data) {
        List<PotionEffect> effects = effects(data);
        if (effects.isEmpty()) return false;
        for (PotionEffect effect : effects) if (!effect.potion().isInstant()) return false;
        return true;
    }

    /** PotionHelper.getPotionEffects: what drinking (or a full splash of) this potion gives; empty for water and the like. */
    public static List<PotionEffect> effects(int data) {
        List<PotionEffect> result = new ArrayList<>();
        if ((data & (8192 | 16384)) == 0) return result;
        Base base = base(data);
        if (base == null) return result;
        int amplifier = (data & 32) != 0 && base.upgradable ? 1 : 0;
        int ticks = base.ticks;
        if (!base.potion.isInstant()) {
            ticks >>= amplifier;
            if ((data & 64) != 0) ticks = ticks * 8 / 3;
            if (isSplash(data)) ticks = (int) Math.round(ticks * 0.75);
        }
        result.add(new PotionEffect(base.potion, ticks, amplifier));
        return result;
    }

    /** PotionHelper.getLiquidColor: the effects' colours averaged by level; water blue when none. */
    public static int color(int data) {
        List<PotionEffect> effects = effects(data);
        if (effects.isEmpty()) return 0x385DC6;
        float red = 0, green = 0, blue = 0, count = 0;
        for (PotionEffect effect : effects) {
            int c = effect.potion().liquidColor;
            for (int i = 0; i <= effect.amplifier(); i++) {
                red += (c >> 16 & 255) / 255f;
                green += (c >> 8 & 255) / 255f;
                blue += (c & 255) / 255f;
                count++;
            }
        }
        return (int) (red / count * 255f) << 16 | (int) (green / count * 255f) << 8 | (int) (blue / count * 255f);
    }

    /**
     * ItemPotion.getItemStackDisplayName: "Water Bottle" for 0, otherwise
     * "Splash " for splash potions then "Potion of X" from the first effect,
     * or "Awkward/Thick/Mundane Potion" without effects.
     */
    public static String name(int data, java.util.function.Function<String, String> translate) {
        if ((data & 0x7FFF) == 0) return translate.apply("item.emptyPotion.name").trim();
        String prefix = isSplash(data) ? translate.apply("potion.prefix.grenade").trim() + " " : "";
        List<PotionEffect> effects = effects(data);
        if (!effects.isEmpty()) return prefix + translate.apply(effects.get(0).potion().name + ".postfix").trim();
        String kind = switch (data & 127) {
            case 16 -> "potion.prefix.awkward";
            case 32 -> "potion.prefix.thick";
            default -> "potion.prefix.mundane";
        };
        return prefix + translate.apply(kind).trim() + " " + translate.apply("item.potion.name").trim();
    }

    /**
     * ItemPotion.addInformation: each effect in grey (red if harmful) with
     * its level and time, or "No Effects".
     */
    public static List<String> tooltip(int data, java.util.function.Function<String, String> translate) {
        List<String> lines = new ArrayList<>();
        List<PotionEffect> effects = effects(data);
        if (effects.isEmpty()) {
            lines.add("§7" + translate.apply("potion.empty").trim());
            return lines;
        }
        for (PotionEffect effect : effects) {
            String line = translate.apply(effect.potion().name).trim();
            if (effect.amplifier() > 0) line += " " + translate.apply("potion.potency." + effect.amplifier()).trim();
            if (effect.duration() > 20) line += " (" + Potion.durationString(effect) + ")";
            lines.add((effect.potion().bad ? "§c" : "§7") + line);
        }
        return lines;
    }

    /**
     * ItemPotion.getSubItems: for each of the 16 effect bits, drinkable then
     * splash, the base, level II and extended potion; ones with the same
     * effects as an earlier one take its place in the list (so the 3:00 Fire
     * Resistance is 8227 and the Potion of Healing 8261).
     */
    public static List<Integer> creativeList() {
        LinkedHashMap<String, Integer> cache = new LinkedHashMap<>();
        for (int i = 0; i <= 15; i++)
            for (int j = 0; j <= 1; j++) {
                int base = j == 0 ? i | 8192 : i | 16384;
                for (int l = 0; l <= 2; l++) {
                    int data = l == 0 ? base : l == 1 ? base | 32 : base | 64;
                    List<PotionEffect> effects = effects(data);
                    if (effects.isEmpty()) continue;
                    StringBuilder key = new StringBuilder(isSplash(data) ? "s" : "d");
                    for (PotionEffect e : effects) key.append(e.potion().id).append(':').append(e.amplifier()).append(':').append(e.duration()).append(',');
                    cache.put(key.toString(), data);
                }
            }
        return new ArrayList<>(cache.values());
    }
}

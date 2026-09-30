package net.minecraft.inventory;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolValues;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An open anvil (1.8.9 ContainerRepair; HYPOTHESIS: recalled costs). Two
 * inputs and a result that is worked out whenever an input or the typed name
 * changes ({@link #update}):
 * <ul>
 *   <li>repairing with the material (planks, cobblestone, ingots, diamonds,
 *       leather): each item restores a quarter of the durability and costs 1;</li>
 *   <li>combining with the same item: the durabilities add plus 12%, costing 2;</li>
 *   <li>each enchantment of the second item: equal levels go up one (to the
 *       maximum), else the higher is kept, costing its level times 8, 4, 2 or 1
 *       for weight 1, 2, 5 or 10 (halved, at least 1, from a book); one that
 *       conflicts with an enchantment already there is left out and costs 1;</li>
 *   <li>a new name (or removing one) costs 1.</li>
 * </ul>
 * The prior work of both inputs is added, and the result's prior work
 * becomes {@code 2n + 1} of the larger. 40 levels or more is "Too
 * Expensive!" outside Creative, except that a rename alone is held at 39.
 */
public final class Anvil {
    private final ItemStack[] inputs = new ItemStack[2];
    private ItemStack output;
    private int cost;
    private int materialCost;
    private String name = "";
    private final boolean creative;

    public Anvil(boolean creative) { this.creative = creative; }

    public ItemStack input(int index) { return inputs[index]; }
    public ItemStack output() { return output; }
    /** The levels the result costs (maximumCost); 0 with nothing to do. */
    public int cost() { return cost; }
    /** How many of the second item a material repair uses up. */
    public int materialCost() { return materialCost; }
    public boolean isCreative() { return creative; }

    /** One of the two input slots; changing it works the result out again. */
    public Slot inputSlot(int index) {
        return new Slot() {
            public ItemStack get() { return inputs[index]; }
            public void set(ItemStack stack) {
                inputs[index] = stack == null || stack.isEmpty() ? null : stack;
                update();
            }
        };
    }

    /** The result slot (nothing can be put in it). */
    public Slot outputSlot() { return new OutputSlot(); }

    /** The result slot's type, so screens can treat it specially. */
    public final class OutputSlot implements Slot {
        public ItemStack get() { return output; }
        public void set(ItemStack stack) { output = stack == null || stack.isEmpty() ? null : stack; }
        public boolean accepts(ItemStack stack) { return false; }
        public Anvil anvil() { return Anvil.this; }
    }

    /**
     * The name typed in the text box (updateItemName): blank for "keep the
     * item's own name", which removes an anvil name the item had.
     */
    public void setName(String typed) {
        name = typed == null ? "" : typed;
        if (output != null) {
            if (name.isBlank()) output.clearCustomName();
            else output.setCustomName(name);
        }
        update();
    }

    public String name() { return name; }

    /** Whether the result can be taken (SlotRepair.canTakeStack). */
    public boolean canTake(int experienceLevel) {
        return (creative || experienceLevel >= cost) && cost > 0 && output != null;
    }

    /**
     * Taking the result (SlotRepair.onPickupFromSlot): the first input is
     * used up, the second loses {@link #materialCost} items or all of it, and
     * the cost is reset. Returns the levels to charge (0 in Creative).
     */
    public int take() {
        int levels = creative ? 0 : cost;
        inputs[0] = null;
        if (materialCost > 0 && inputs[1] != null && inputs[1].count() > materialCost) inputs[1].split(materialCost);
        else inputs[1] = null;
        output = null;
        cost = 0;
        materialCost = 0;
        return levels;
    }

    /** Closing the anvil: the inputs go back to the player. */
    public void returnAll(InventoryPlayer inventory, Consumer<ItemStack> drop) {
        for (int i = 0; i < 2; i++) {
            ItemStack stack = inputs[i];
            if (stack == null) continue;
            inputs[i] = null;
            inventory.addItemStackToInventory(stack);
            if (!stack.isEmpty()) drop.accept(stack);
        }
        output = null;
        cost = 0;
    }

    /** ContainerRepair.updateRepairOutput. */
    public void update() {
        ItemStack first = inputs[0];
        cost = 1;
        int work = 0, prior = 0, renameCost = 0;
        if (first == null) {
            output = null;
            cost = 0;
            return;
        }
        ItemStack result = first.copy();
        ItemStack second = inputs[1];
        LinkedHashMap<Integer, Integer> enchantments = EnchantmentHelper.enchantments(result);
        boolean book = false;
        prior += first.repairCost() + (second == null ? 0 : second.repairCost());
        materialCost = 0;
        if (second != null) {
            book = EnchantmentHelper.isEnchantedBook(second) && !second.storedEnchantments().isEmpty();
            if (result.item().isDamageable() && isRepairMaterial(first.item(), second.item())) {
                int repair = Math.min(result.damage(), result.item().maxDamage() / 4);
                if (repair <= 0) {
                    output = null;
                    cost = 0;
                    return;
                }
                int used;
                for (used = 0; repair > 0 && used < second.count(); used++) {
                    result.setDamage(result.damage() - repair);
                    work++;
                    repair = Math.min(result.damage(), result.item().maxDamage() / 4);
                }
                materialCost = used;
            } else {
                if (!book && (result.item() != second.item() || !result.item().isDamageable())) {
                    output = null;
                    cost = 0;
                    return;
                }
                if (result.item().isDamageable() && !book) {
                    int max = result.item().maxDamage();
                    int left = max - first.damage();
                    int added = max - second.damage() + max * 12 / 100;
                    int damage = Math.max(0, max - (left + added));
                    if (damage < result.damage()) {
                        result.setDamage(damage);
                        work += 2;
                    }
                }
                for (Map.Entry<Integer, Integer> entry : EnchantmentHelper.enchantments(second).entrySet()) {
                    int id = entry.getKey();
                    Enchantment enchantment = Enchantment.byId(id);
                    if (enchantment == null) continue;
                    int have = enchantments.getOrDefault(id, 0);
                    int level = entry.getValue();
                    level = have == level ? level + 1 : Math.max(level, have);
                    boolean applies = enchantment.canApply(first) || creative || EnchantmentHelper.isEnchantedBook(first);
                    for (int other : enchantments.keySet()) {
                        if (other != id && !enchantment.canApplyTogether(Enchantment.byId(other))) {
                            applies = false;
                            work++;
                        }
                    }
                    if (applies) {
                        if (level > enchantment.maxLevel) level = enchantment.maxLevel;
                        enchantments.put(id, level);
                        int multiplier = switch (enchantment.weight) {
                            case 1 -> 8;
                            case 2 -> 4;
                            case 5 -> 2;
                            case 10 -> 1;
                            default -> 0;
                        };
                        if (book) multiplier = Math.max(1, multiplier / 2);
                        work += multiplier * level;
                    }
                }
            }
        }
        if (name.isBlank()) {
            if (first.hasCustomName()) {
                renameCost = 1;
                work += renameCost;
                result.clearCustomName();
            }
        } else if (!name.equals(first.customName())) {
            renameCost = 1;
            work += renameCost;
            result.setCustomName(name);
        }
        cost = prior + work;
        if (work <= 0) result = null;
        if (renameCost == work && renameCost > 0 && cost >= 40) cost = 39;
        if (cost >= 40 && !creative) result = null;
        if (result != null) {
            int repairCost = result.repairCost();
            if (second != null && repairCost < second.repairCost()) repairCost = second.repairCost();
            result.setRepairCost(repairCost * 2 + 1);
            EnchantmentHelper.setEnchantments(enchantments, result);
        }
        output = result;
    }

    /**
     * Whether {@code material} repairs {@code item} (Item.getIsRepairable):
     * wooden tools take planks, stone cobblestone, iron and chainmail iron
     * ingots, golden gold ingots, diamond diamonds, leather armour leather.
     */
    public static boolean isRepairMaterial(Item item, Item material) {
        String prefix = materialPrefix(item);
        if (prefix == null) return false;
        String path = material.name().path();
        return switch (prefix) {
            case "wooden" -> path.endsWith("_planks");
            case "stone" -> path.equals("cobblestone");
            case "iron", "chainmail" -> path.equals("iron_ingot");
            case "golden" -> path.equals("gold_ingot");
            case "diamond" -> path.equals("diamond");
            case "leather" -> path.equals("leather");
            default -> false;
        };
    }

    private static String materialPrefix(Item item) {
        String armour = ArmorValues.material(item);
        if (armour != null) return armour;
        ToolValues.Type type = ToolValues.type(item);
        if (type == ToolValues.Type.PICKAXE || type == ToolValues.Type.AXE || type == ToolValues.Type.SHOVEL
            || type == ToolValues.Type.SWORD || type == ToolValues.Type.HOE) {
            String path = item.name().path();
            int cut = path.lastIndexOf('_');
            return cut > 0 ? path.substring(0, cut) : null;
        }
        return null;
    }
}

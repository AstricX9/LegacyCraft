package net.minecraft.inventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A furnace's contents and fire (1.8.9 TileEntityFurnace; HYPOTHESIS:
 * recalled update rules). Slot 0 is the input, 1 the fuel, 2 the output.
 *
 * <p>Every tick ({@link #update}) the fire burns down by one. When it is out
 * and the input can be smelted, one fuel item is used up and the fire starts
 * again for that fuel's burn time (a lava bucket leaves its bucket). While
 * the fire burns and the input can be smelted the cook time counts up; at
 * 200 one item is smelted into the output. Without fire the cook time falls
 * back two per tick while fuel or input is missing, and resets straight to 0
 * when both are there but nothing can be smelted. A wet sponge smelted with
 * an empty bucket in the fuel slot fills the bucket with water. Putting a
 * different item in the input slot restarts the cook time.
 */
public final class Furnace {
    public static final int INPUT = 0, FUEL = 1, OUTPUT = 2;

    private final ItemStack[] slots = new ItemStack[3];
    private final FurnaceRecipes recipes;
    private final Function<String, Item> items;
    /** Ticks the fire has left, the burn time of the fuel that lit it (for the flame gauge), and the cook progress. */
    private int burnTime, currentItemBurnTime, cookTime, totalCookTime = FurnaceRecipes.COOK_TIME;

    public Furnace(FurnaceRecipes recipes, Function<String, Item> items) {
        this.recipes = recipes;
        this.items = items;
    }

    public ItemStack get(int slot) { return slots[slot]; }

    /**
     * Sets a slot (TileEntityFurnace.setInventorySlotContents): a different
     * item in the input slot restarts the cook time.
     */
    public void set(int slot, ItemStack stack) {
        if (stack != null && stack.isEmpty()) stack = null;
        boolean same = stack != null && slots[slot] != null && stack.item() == slots[slot].item()
            && stack.damage() == slots[slot].damage();
        slots[slot] = stack;
        if (slot == INPUT && !same) {
            totalCookTime = FurnaceRecipes.COOK_TIME;
            cookTime = 0;
        }
    }

    public boolean isBurning() { return burnTime > 0; }
    public int burnTime() { return burnTime; }
    public int currentItemBurnTime() { return currentItemBurnTime; }
    public int cookTime() { return cookTime; }
    public int totalCookTime() { return totalCookTime; }
    public FurnaceRecipes recipes() { return recipes; }

    /** One tick (TileEntityFurnace.update). Returns whether the fire went out or was lit (the block changes). */
    public boolean update() {
        boolean wasBurning = isBurning();
        if (isBurning()) burnTime--;
        if (isBurning() || slots[FUEL] != null && slots[INPUT] != null) {
            if (!isBurning() && canSmelt()) {
                currentItemBurnTime = burnTime = FurnaceRecipes.burnTime(slots[FUEL]);
                if (isBurning() && slots[FUEL] != null) {
                    ItemStack fuel = slots[FUEL];
                    fuel.split(1);
                    if (fuel.isEmpty()) {
                        String container = FurnaceRecipes.containerItem(fuel.item());
                        Item left = container == null || items == null ? null : items.apply(container);
                        slots[FUEL] = left == null ? null : new ItemStack(left);
                    }
                }
            }
            if (isBurning() && canSmelt()) {
                if (++cookTime == totalCookTime) {
                    cookTime = 0;
                    totalCookTime = FurnaceRecipes.COOK_TIME;
                    smeltItem();
                }
            } else {
                cookTime = 0;
            }
        } else if (!isBurning() && cookTime > 0) {
            cookTime = Math.max(0, Math.min(totalCookTime, cookTime - 2));
        }
        return wasBurning != isBurning();
    }

    /** Whether the input smelts into something the output slot can still take (canSmelt). */
    public boolean canSmelt() {
        if (slots[INPUT] == null || recipes == null) return false;
        ItemStack result = recipes.result(slots[INPUT]);
        if (result == null) return false;
        ItemStack output = slots[OUTPUT];
        if (output == null) return true;
        if (output.item() != result.item() || output.damage() != result.damage()) return false;
        int total = output.count() + result.count();
        return total <= 64 && total <= output.item().maxStackSize();
    }

    /** Turns one input item into its result in the output slot (smeltItem). */
    private void smeltItem() {
        if (!canSmelt()) return;
        ItemStack input = slots[INPUT];
        ItemStack result = recipes.result(input);
        if (slots[OUTPUT] == null) slots[OUTPUT] = result.copy();
        else slots[OUTPUT].mergeFrom(result);
        if (input.item().name().path().equals("sponge_wet") && slots[FUEL] != null
            && slots[FUEL].item().name().path().equals("bucket") && items != null) {
            Item water = items.apply("water_bucket");
            if (water != null) slots[FUEL] = new ItemStack(water);
        }
        input.split(1);
        if (input.isEmpty()) slots[INPUT] = null;
    }

    /** Pixels of the smelting arrow to show, out of {@code width} (ContainerFurnace/GuiFurnace getCookProgressScaled). */
    public int cookProgressScaled(int width) {
        return totalCookTime != 0 && cookTime != 0 ? cookTime * width / totalCookTime : 0;
    }

    /** Pixels of the flame to show, out of {@code height} (getBurnLeftScaled; a gauge that never learnt its fuel counts from 200). */
    public int burnLeftScaled(int height) {
        int total = currentItemBurnTime == 0 ? 200 : currentItemBurnTime;
        return burnTime * height / total;
    }

    /** Everything in the furnace, emptying it (the block broke: InventoryHelper.dropInventoryItems). */
    public List<ItemStack> takeContents() {
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) contents.add(slots[i]);
            slots[i] = null;
        }
        return contents;
    }

    // ---- slots for the furnace screen (ContainerFurnace) ------------------------------------

    /** The input slot: anything; a new item restarts cooking. */
    public Slot inputSlot() {
        return new Slot() {
            public ItemStack get() { return slots[INPUT]; }
            public void set(ItemStack stack) { Furnace.this.set(INPUT, stack); }
        };
    }

    /** The fuel slot (SlotFurnaceFuel): only fuel, or empty buckets one at a time. */
    public Slot fuelSlot() {
        return new Slot() {
            public ItemStack get() { return slots[FUEL]; }
            public void set(ItemStack stack) { Furnace.this.set(FUEL, stack); }
            public boolean accepts(ItemStack stack) { return FurnaceRecipes.isFuel(stack) || isBucket(stack); }
            public int limit(ItemStack stack) { return isBucket(stack) ? 1 : 64; }
        };
    }

    /**
     * The output slot (SlotFurnaceOutput): nothing can be put in, and whatever
     * the player takes out, however it is taken (click, shift-click,
     * double-click gathering, number key, drop key), is reported to
     * {@code onTaken} with how many (experience and achievements). It hands
     * out copies, so a taking is seen when the smaller stack is set back.
     */
    public final class OutputSlot implements Slot {
        private final java.util.function.ObjIntConsumer<Item> onTaken;

        OutputSlot(java.util.function.ObjIntConsumer<Item> onTaken) { this.onTaken = onTaken; }

        public ItemStack get() { return slots[OUTPUT] == null ? null : slots[OUTPUT].copy(); }
        public void set(ItemStack stack) {
            ItemStack before = slots[OUTPUT];
            Furnace.this.set(OUTPUT, stack);
            if (before == null) return;
            int left = stack != null && !stack.isEmpty() && stack.canMerge(before) ? stack.count() : 0;
            if (left < before.count()) onTaken.accept(before.item(), before.count() - left);
        }
        public boolean accepts(ItemStack stack) { return false; }
        public Furnace furnace() { return Furnace.this; }
    }

    /** The output slot; {@code onTaken} hears of every item the player takes out. */
    public OutputSlot outputSlot(java.util.function.ObjIntConsumer<Item> onTaken) {
        return new OutputSlot(onTaken == null ? (item, count) -> {} : onTaken);
    }

    public OutputSlot outputSlot() { return outputSlot(null); }

    static boolean isBucket(ItemStack stack) { return stack != null && stack.item().name().path().equals("bucket"); }

    /**
     * Experience for taking {@code count} smelted items out
     * (SlotFurnaceOutput.onCrafting): count x the recipe's experience, the
     * fraction rounded up with that chance; a whole point or more per item
     * gives one per item.
     */
    public static int experienceFor(int count, float perItem, java.util.Random random) {
        if (perItem == 0f) return 0;
        if (perItem >= 1f) return count;
        float total = count * perItem;
        int whole = (int) Math.floor(total);
        if (whole < (int) Math.ceil(total) && random.nextFloat() < total - whole) whole++;
        return whole;
    }

    // ---- saving (TileEntityFurnace.writeToNBT / readFromNBT) --------------------------------

    /** Writes the slots (by item name) and the burn and cook times. */
    public void write(DataOutput output) throws IOException {
        output.writeShort(burnTime);
        output.writeShort(cookTime);
        output.writeShort(totalCookTime);
        for (ItemStack stack : slots) {
            output.writeBoolean(stack != null);
            if (stack == null) continue;
            output.writeUTF(stack.item().name().toString());
            output.writeByte(stack.count());
            output.writeShort(stack.damage());
        }
    }

    /**
     * Reads what {@link #write} wrote. Like vanilla, the fuel gauge's total is
     * taken from what is in the fuel slot now (0 when empty), not from the fuel
     * that is burning. {@code items} resolves LegacyCraft item names
     * ("bucket"); unknown items are dropped.
     */
    public static Furnace read(DataInput input, FurnaceRecipes recipes, Function<String, Item> items) throws IOException {
        Furnace furnace = new Furnace(recipes, items);
        furnace.burnTime = input.readShort();
        furnace.cookTime = input.readShort();
        furnace.totalCookTime = input.readShort();
        for (int i = 0; i < furnace.slots.length; i++) {
            if (!input.readBoolean()) continue;
            String name = input.readUTF();
            int count = input.readByte(), damage = input.readShort();
            Item item = items == null ? null : items.apply(name.startsWith("minecraft:") ? name.substring(10) : name);
            if (item == null || count < 1) continue;
            furnace.slots[i] = new ItemStack(item, Math.min(count, item.maxStackSize()), Math.max(0, Math.min(damage, item.maxDamage())));
        }
        furnace.currentItemBurnTime = FurnaceRecipes.burnTime(furnace.slots[FUEL]);
        return furnace;
    }
}

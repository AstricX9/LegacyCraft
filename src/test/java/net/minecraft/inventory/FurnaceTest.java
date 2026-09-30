package net.minecraft.inventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import net.minecraft.item.crafting.FurnaceRecipes;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The furnace's fire, cooking and slots (HYPOTHESIS: recalled 1.8.9 TileEntityFurnace.update and SlotFurnaceOutput). */
final class FurnaceTest {
    private static Item item(String name) { return TestItems.item(name); }
    private static ItemStack s(String name, int count) { return new ItemStack(item(name), count); }

    private static FurnaceRecipes recipes() {
        FurnaceRecipes recipes = new FurnaceRecipes();
        recipes.add(item("iron_ore"), item("iron_ingot"), 0.7f);
        recipes.add(item("beef"), item("cooked_beef"), 0.35f);
        recipes.add(item("sponge_wet"), item("sponge"), 0.15f);
        return recipes;
    }

    private static Furnace furnace() { return new Furnace(recipes(), TestItems::item); }

    private static void run(Furnace furnace, int ticks) { for (int i = 0; i < ticks; i++) furnace.update(); }

    @Test
    void oneCoalSmeltsEightItemsTwoHundredTicksEach() {
        Furnace furnace = furnace();
        furnace.set(Furnace.INPUT, s("iron_ore", 10));
        furnace.set(Furnace.FUEL, s("coal", 1));
        assertTrue(furnace.update(), "lighting changes the block");
        assertTrue(furnace.isBurning());
        assertNull(furnace.get(Furnace.FUEL), "the coal is used up at once");
        assertEquals(1, furnace.cookTime());
        run(furnace, 198);
        assertNull(furnace.get(Furnace.OUTPUT));
        furnace.update();
        assertEquals(1, furnace.get(Furnace.OUTPUT).count(), "done on the 200th tick");
        assertEquals(9, furnace.get(Furnace.INPUT).count());
        run(furnace, 1600 - 200);
        assertEquals(8, furnace.get(Furnace.OUTPUT).count(), "the eighth is done on the fire's last tick");
        assertTrue(furnace.isBurning());
        assertTrue(furnace.update(), "then it goes out");
        assertFalse(furnace.isBurning());
        assertEquals(2, furnace.get(Furnace.INPUT).count());
    }

    @Test
    void theFireGoesOutAndTheBlockChangesBack() {
        Furnace furnace = furnace();
        furnace.set(Furnace.INPUT, s("beef", 1));
        furnace.set(Furnace.FUEL, s("stick", 1));
        assertTrue(furnace.update());
        int changes = 0;
        for (int i = 0; i < 150; i++) if (furnace.update()) changes++;
        assertEquals(1, changes, "goes out once, after 100 ticks");
        assertFalse(furnace.isBurning());
        assertNull(furnace.get(Furnace.OUTPUT), "a stick is half an item");
    }

    @Test
    void withoutFuelTheArrowFallsBackTwoPerTick() {
        Furnace furnace = furnace();
        furnace.set(Furnace.INPUT, s("beef", 1));
        furnace.set(Furnace.FUEL, s("stick", 1));
        run(furnace, 100);
        assertEquals(100, furnace.cookTime());
        furnace.update();
        assertFalse(furnace.isBurning());
        assertEquals(98, furnace.cookTime(), "no fuel left: back by two");
        run(furnace, 60);
        assertEquals(0, furnace.cookTime());
    }

    @Test
    void theFireBurnsOnWithNothingToSmeltButTheArrowResets() {
        Furnace furnace = furnace();
        furnace.set(Furnace.INPUT, s("beef", 1));
        furnace.set(Furnace.FUEL, s("coal", 1));
        run(furnace, 50);
        furnace.set(Furnace.INPUT, null);
        furnace.update();
        assertTrue(furnace.isBurning());
        assertEquals(0, furnace.cookTime());
        assertEquals(1600 - 50, furnace.burnTime());
    }

    @Test
    void aDifferentInputRestartsCookingButMoreOfTheSameDoesNot() {
        Furnace furnace = furnace();
        furnace.set(Furnace.INPUT, s("beef", 1));
        furnace.set(Furnace.FUEL, s("coal", 1));
        run(furnace, 50);
        furnace.set(Furnace.INPUT, s("beef", 5));
        assertEquals(50, furnace.cookTime());
        furnace.set(Furnace.INPUT, s("iron_ore", 5));
        assertEquals(0, furnace.cookTime());
    }

    @Test
    void nothingLightsWhenTheInputCannotBeSmelted() {
        Furnace furnace = furnace();
        furnace.set(Furnace.INPUT, s("dirt", 1));
        furnace.set(Furnace.FUEL, s("coal", 3));
        assertFalse(furnace.update());
        assertFalse(furnace.isBurning());
        assertEquals(3, furnace.get(Furnace.FUEL).count());
    }

    @Test
    void aFullOrDifferentOutputStopsSmelting() {
        Furnace furnace = furnace();
        furnace.set(Furnace.INPUT, s("beef", 1));
        furnace.set(Furnace.OUTPUT, s("iron_ingot", 1));
        assertFalse(furnace.canSmelt());
        furnace.set(Furnace.OUTPUT, s("cooked_beef", 64));
        assertFalse(furnace.canSmelt());
        furnace.set(Furnace.OUTPUT, s("cooked_beef", 63));
        assertTrue(furnace.canSmelt());
    }

    @Test
    void lavaLeavesAnEmptyBucketInTheFuelSlot() {
        Furnace furnace = furnace();
        furnace.set(Furnace.INPUT, s("beef", 1));
        furnace.set(Furnace.FUEL, s("lava_bucket", 1));
        furnace.update();
        assertEquals(20000, furnace.currentItemBurnTime());
        assertEquals(item("bucket"), furnace.get(Furnace.FUEL).item());
    }

    @Test
    void aWetSpongeFillsAnEmptyBucketInTheFuelSlot() {
        Furnace furnace = furnace();
        furnace.set(Furnace.INPUT, s("sponge_wet", 1));
        furnace.set(Furnace.FUEL, s("coal", 1));
        run(furnace, 10);
        furnace.set(Furnace.FUEL, s("bucket", 1));
        run(furnace, 190);
        assertEquals(item("sponge"), furnace.get(Furnace.OUTPUT).item());
        assertEquals(item("water_bucket"), furnace.get(Furnace.FUEL).item());
    }

    @Test
    void theFuelSlotTakesFuelAndSingleBuckets() {
        Slot fuel = furnace().fuelSlot();
        assertTrue(fuel.accepts(s("coal", 1)));
        assertTrue(fuel.accepts(s("bucket", 1)));
        assertFalse(fuel.accepts(s("iron_ore", 1)));
        assertEquals(1, fuel.limit(s("bucket", 1)));
        assertEquals(64, fuel.limit(s("coal", 1)));
        assertFalse(furnace().outputSlot().accepts(s("cooked_beef", 1)));
    }

    @Test
    void progressBarsScaleToTheirSprites() {
        Furnace furnace = furnace();
        furnace.set(Furnace.INPUT, s("beef", 1));
        furnace.set(Furnace.FUEL, s("coal", 1));
        run(furnace, 100);
        assertEquals(12, furnace.cookProgressScaled(24));
        assertEquals(furnace.burnTime() * 13 / 1600, furnace.burnLeftScaled(13));
        assertEquals(12, furnace.burnLeftScaled(13));
        assertEquals(0, furnace().burnLeftScaled(13));
    }

    @Test
    void experienceRoundsTheFractionUpByChance() {
        assertEquals(0, Furnace.experienceFor(10, 0f, new Random(1)));
        assertEquals(5, Furnace.experienceFor(5, 1f, new Random(1)));
        assertEquals(7, Furnace.experienceFor(10, 0.7f, new Random(1)));
        int total = 0;
        Random random = new Random(7);
        for (int i = 0; i < 10000; i++) total += Furnace.experienceFor(1, 0.35f, random);
        assertTrue(total > 3200 && total < 3800, "about 0.35 per item on average, got " + total);
    }

    @Test
    void savingKeepsSlotsAndTimesAndRecountsTheGaugeFromTheFuelSlot() throws Exception {
        Furnace furnace = furnace();
        furnace.set(Furnace.INPUT, s("iron_ore", 5));
        furnace.set(Furnace.FUEL, s("stick", 3));
        run(furnace, 30);
        var bytes = new ByteArrayOutputStream();
        furnace.write(new DataOutputStream(bytes));
        Furnace loaded = Furnace.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), recipes(),
            TestItems::item);
        assertEquals(furnace.burnTime(), loaded.burnTime());
        assertEquals(furnace.cookTime(), loaded.cookTime());
        assertEquals(5, loaded.get(Furnace.INPUT).count());
        assertEquals(item("stick"), loaded.get(Furnace.FUEL).item());
        assertEquals(2, loaded.get(Furnace.FUEL).count());
        assertEquals(100, loaded.currentItemBurnTime(), "vanilla re-reads the gauge from the fuel slot");
    }

    @Test
    void breakingEmptiesTheFurnace() {
        Furnace furnace = furnace();
        furnace.set(Furnace.INPUT, s("iron_ore", 5));
        furnace.set(Furnace.OUTPUT, s("iron_ingot", 2));
        assertEquals(2, furnace.takeContents().size());
        assertNull(furnace.get(Furnace.INPUT));
        assertNull(furnace.get(Furnace.OUTPUT));
    }
}

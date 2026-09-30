package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.inventory.Furnace;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.testing.GameData;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Furnaces in the world: made on demand, ticked, lit and put out, and spilled when broken (HYPOTHESIS: recalled BlockFurnace). */
final class WorldFurnacesTest {
    private static final FurnaceRecipes RECIPES = FurnaceRecipes.load(GameData.resources(), GameData.items());
    private static final int X = 0, Y = 120, Z = 0;

    /** A world whose furnaces can smelt, with a furnace facing east (metadata 5) up in the air. */
    private static World worldWithFurnace() {
        World world = GameData.newWorld(42L);
        world.setSmelting(RECIPES, name -> GameData.items().get(new ResourceLocation("minecraft", name)));
        world.setState(X, Y, Z, BlockState.of(block("furnace"), 5));
        return world;
    }

    private static Block block(String name) { return GameData.blocks().get("minecraft:" + name); }
    private static ItemStack s(String name, int count) { return new ItemStack(GameData.items().get("minecraft:" + name), count); }

    @Test
    void onlyAFurnaceBlockHasAFurnaceAndItIsKept() {
        World world = worldWithFurnace();
        assertNull(world.furnaceAt(X, Y + 1, Z));
        Furnace furnace = world.furnaceAt(X, Y, Z);
        assertNotNull(furnace);
        assertSame(furnace, world.furnaceAt(X, Y, Z));
        assertSame(RECIPES, furnace.recipes());
    }

    @Test
    void theBlockLightsAndGoesOutWithTheFireKeepingItsFacing() {
        World world = worldWithFurnace();
        Furnace furnace = world.furnaceAt(X, Y, Z);
        furnace.set(Furnace.INPUT, s("beef", 1));
        furnace.set(Furnace.FUEL, s("stick", 1));
        world.tick();
        assertEquals(BlockState.of(block("lit_furnace"), 5), world.getState(X, Y, Z));
        assertSame(furnace, world.furnaceAt(X, Y, Z), "lighting keeps the contents");
        for (int i = 0; i < 100; i++) world.tick();
        assertEquals(BlockState.of(block("furnace"), 5), world.getState(X, Y, Z));
        assertSame(furnace, world.furnaceAt(X, Y, Z));
    }

    @Test
    void oreBecomesIngotsWhileNobodyLooks() {
        World world = worldWithFurnace();
        Furnace furnace = world.furnaceAt(X, Y, Z);
        furnace.set(Furnace.INPUT, s("iron_ore", 3));
        furnace.set(Furnace.FUEL, s("coal", 1));
        for (int i = 0; i < 600; i++) world.tick();
        assertEquals(s("iron_ingot", 3).item(), furnace.get(Furnace.OUTPUT).item());
        assertEquals(3, furnace.get(Furnace.OUTPUT).count());
        assertNull(furnace.get(Furnace.INPUT));
    }

    @Test
    void breakingTheFurnaceSpillsItsContentsAndForgetsIt() {
        World world = worldWithFurnace();
        List<ItemStack> spilled = new ArrayList<>();
        world.setEvents(new World.Events() {
            @Override public void containerBroken(int x, int y, int z, List<ItemStack> contents) { spilled.addAll(contents); }
        });
        Furnace furnace = world.furnaceAt(X, Y, Z);
        furnace.set(Furnace.INPUT, s("iron_ore", 5));
        furnace.set(Furnace.OUTPUT, s("iron_ingot", 2));
        world.setStateAndUpdate(X, Y, Z, BlockState.of(block("air"), 0));
        assertEquals(2, spilled.size());
        assertNull(world.furnaceAt(X, Y, Z));
        world.setState(X, Y, Z, BlockState.of(block("furnace"), 5));
        assertNull(world.furnaceAt(X, Y, Z).get(Furnace.INPUT), "a new furnace starts empty");
    }
}

package net.minecraft.block;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 1.9 blocks and enchantments: chorus support, grass paths, Frost Walker ice and Mending. */
final class WorldOfColorUpdateTest {
    private static final int Y = 240;

    private static BlockState state(String name) { return BlockState.defaultOf(GameData.blocks().get("minecraft:" + name)); }

    @Test
    void chorusPlantsGrowFromEndStoneAndFlowersNeedSupport() {
        World world = GameData.newWorld(1L);
        world.setState(0, Y, 0, state("end_stone"));
        BlockState plant = state("chorus_plant");
        assertTrue(plant.block().canStay(world, 0, Y + 1, 0, plant));
        assertFalse(plant.block().canStay(world, 5, Y + 1, 5, plant), "floating plant");
        world.setState(0, Y + 1, 0, plant);
        BlockState flower = state("chorus_flower");
        assertTrue(flower.block().canStay(world, 0, Y + 2, 0, flower), "on a plant");
        assertTrue(flower.block().canStay(world, 1, Y + 1, 0, flower), "beside one plant over air");
        var actual = plant.block().actualState(world, 0, Y + 1, 0, plant);
        assertEquals("true", actual.get("down"));
        assertEquals("false", actual.get("up"));
    }

    @Test
    void grassPathTurnsToDirtUnderASolidBlock() {
        World world = GameData.newWorld(1L);
        world.setStateAndUpdate(0, Y, 0, state("grass_path"));
        world.setStateAndUpdate(0, Y + 1, 0, state("stone"));
        assertEquals("dirt", world.get(0, Y, 0).name().path());
    }

    @Test
    void frostWalkerFreezesStillWaterThatThenMelts() {
        World world = GameData.newWorld(1L);
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) world.setState(x, Y, z, state("water"));
        int frozen = BlockFrostedIce.freezeAround(world, 0.5, Y + 1, 0.5, 1, new Random(1));
        assertEquals(29, frozen, "radius 3 disc");
        assertEquals("frosted_ice", world.get(0, Y, 0).name().path());
        assertEquals("water", world.get(4, Y, 0).name().path());
        for (int i = 0; i < 400; i++) world.tick();
        assertEquals("water", world.get(0, Y, 0).name().path(), "melted back");
    }

    @Test
    void mendingSpendsExperienceOnDamagedGear() {
        ItemStack boots = new ItemStack(TestItems.item("iron_boots", 1, 195));
        boots.setDamage(10);
        boots.addEnchantment(Enchantment.MENDING.id, 1);
        assertEquals(0, EnchantmentHelper.mend(5, new ItemStack[] {boots}, new Random(1)));
        assertEquals(0, boots.damage());
        assertEquals(7, EnchantmentHelper.mend(7, new ItemStack[] {boots}, new Random(1)), "nothing left to mend");
    }

    @Test
    void ripeBeetrootsDropABeetrootAndSeeds() {
        BlockDrops drops = BlockDrops.parse(com.google.gson.JsonParser.parseString("{\"blocks\":{}}").getAsJsonObject(),
            new BlockDrops.Items() {
                @Override public net.minecraft.item.Item byName(String name) { return TestItems.item(name); }
                @Override public net.minecraft.item.Item placing(String block, int metadata) { return TestItems.item(block); }
            });
        var ripe = drops.drops("beetroots", "plants", 3, null, 0, false, false, new Random(1));
        assertEquals("beetroot", ripe.get(0).item().name().path());
        var young = drops.drops("beetroots", "plants", 1, null, 0, false, false, new Random(1));
        assertEquals("beetroot_seeds", young.get(0).item().name().path());
    }
}

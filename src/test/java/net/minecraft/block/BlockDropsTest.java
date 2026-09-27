package net.minecraft.block;

import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TestItems;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BlockDropsTest {
    private BlockDrops drops;
    private final Item woodPick = TestItems.item("wooden_pickaxe", 1, 59);
    private final Item ironPick = TestItems.item("iron_pickaxe", 1, 250);
    private final Item shears = TestItems.item("shears", 1, 238);
    private final Item shovel = TestItems.item("wooden_shovel", 1, 59);

    /** Items named after the block they place: "stone.1" is the item placing stone with metadata 1. */
    private final BlockDrops.Items items = new BlockDrops.Items() {
        @Override public Item byName(String name) { return TestItems.item(name); }
        @Override public Item placing(String block, int metadata) { return TestItems.item(block + "." + metadata); }
    };

    @BeforeEach
    void load() throws Exception {
        String json = Files.readString(Path.of("src/main/resources/assets/legacycraft/loot/blocks.json"));
        drops = BlockDrops.parse(JsonParser.parseString(json).getAsJsonObject(), items);
    }

    private List<ItemStack> drop(String block, String material, int meta, Item tool) {
        return drops.drops(block, material, meta, tool, new Random(1));
    }

    private static String names(List<ItemStack> stacks) {
        StringBuilder text = new StringBuilder();
        for (ItemStack stack : stacks) text.append(stack.item().name().path()).append('x').append(stack.count()).append(' ');
        return text.toString().trim();
    }

    @Test
    void stoneNeedsAPickaxeAndGivesCobblestone() {
        assertTrue(drop("stone", "rock", 0, null).isEmpty());
        assertEquals("cobblestonex1", names(drop("stone", "rock", 0, woodPick)));
        assertEquals("stone.1x1", names(drop("stone", "rock", 1, woodPick)), "granite drops itself");
    }

    @Test
    void oresDropTheirItems() {
        assertEquals("diamondx1", names(drop("diamond_ore", "rock", 0, ironPick)));
        assertTrue(drop("diamond_ore", "rock", 0, woodPick).isEmpty());
        for (int seed = 0; seed < 50; seed++) {
            int lapis = drops.drops("lapis_ore", "rock", 0, ironPick, new Random(seed)).get(0).count();
            assertTrue(lapis >= 4 && lapis <= 8);
            int redstone = drops.drops("redstone_ore", "rock", 0, ironPick, new Random(seed)).get(0).count();
            assertTrue(redstone >= 4 && redstone <= 5);
        }
    }

    @Test
    void simpleBlocksDropThemselvesOrTheirSource() {
        assertEquals("dirtx1", names(drop("grass", "grass", 0, null)));
        assertEquals("dirt.1x1", names(drop("dirt", "ground", 1, null)), "coarse dirt stays coarse");
        assertEquals("dirtx1", names(drop("dirt", "ground", 2, null)), "podzol drops dirt");
        assertEquals("log.6x1", names(drop("log", "wood", 6, null)));
        assertEquals("wooden_slab.2x2", names(drop("double_wooden_slab", "wood", 2, null)));
        assertEquals("bookx3", names(drop("bookshelf", "wood", 0, null)));
        assertEquals("", names(drop("glass", "glass", 0, null)));
        assertEquals("wooden_door.1x1", names(drop("wooden_door", "wood", 1, null)));
        assertEquals("", names(drop("wooden_door", "wood", 9, null)), "the upper half drops nothing");
    }

    @Test
    void snowLayersNeedAShovelAndGiveOneSnowballPerLayer() {
        assertTrue(drop("snow_layer", "snow", 2, null).isEmpty());
        assertEquals("snowballx3", names(drop("snow_layer", "snow", 2, shovel)));
    }

    @Test
    void leavesGiveSaplingsRarelyAndThemselvesToShears() {
        assertEquals("leaves.4x1", names(drop("leaves", "leaves", 4, shears)));
        int saplings = 0, apples = 0;
        Random random = new Random(42);
        for (int i = 0; i < 20000; i++) {
            for (ItemStack stack : drops.drops("leaves", "leaves", 0, null, random)) {
                if (stack.item().name().path().equals("oak_sapling")) saplings++;
                if (stack.item().name().path().equals("apple")) apples++;
            }
        }
        assertEquals(1000, saplings, 120, "about 1 in 20");
        assertEquals(100, apples, 40, "about 1 in 200");
    }

    @Test
    void cropsDropSeedsUntilRipe() {
        assertEquals("wheat_seedsx1", names(drop("wheat", "plants", 3, null)));
        List<ItemStack> ripe = drop("wheat", "plants", 7, null);
        assertEquals("wheat", ripe.get(0).item().name().path());
        assertEquals("dye_brownx3", names(drop("cocoa", "plants", 8, null)));
        assertEquals("dye_brownx1", names(drop("cocoa", "plants", 1, null)));
        int wart = drop("nether_wart", "plants", 3, null).get(0).count();
        assertTrue(wart >= 2 && wart <= 4);
    }

    @Test
    void doublePlantsDropTheFlowerOrSeeds() {
        assertEquals("double_plant.0x1", names(drop("double_plant", "vine", 0, null)));
        assertEquals("tall_grassx2", names(drop("double_plant", "vine", 2, shears)));
    }
}

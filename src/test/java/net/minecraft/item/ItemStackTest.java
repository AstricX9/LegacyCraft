package net.minecraft.item;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.inventory.Inventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemStackTest {
    @Test
    void inventoryMergesStacksAndDamageBreaksTools() {
        var resources = new ClasspathResourceManager();
        var items = ItemRegistry.load(resources, BlockRegistry.load(resources));
        Item apple = items.get("minecraft:apple");
        Inventory inventory = new Inventory(2);
        inventory.set(0, new ItemStack(apple, 60));
        assertEquals(0, inventory.insert(new ItemStack(apple, 8)));
        assertEquals(64, inventory.get(0).count());
        assertEquals(4, inventory.get(1).count());

        ItemStack sword = new ItemStack(items.get("minecraft:diamond_sword"));
        assertTrue(sword.damage(1562));
        assertTrue(sword.isEmpty());
    }
}

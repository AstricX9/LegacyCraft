package net.minecraft.block.state;

import net.minecraft.block.BlockRegistry;
import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Neighbour-derived properties computed by each block class's actualState. */
final class ActualStateTest {
    private final BlockRegistry blocks = GameData.blocks();

    private TestBlockAccess world() { return new TestBlockAccess(blocks); }

    @Test
    void fencesConnectToFencesOfTheirMaterialGatesAndOpaqueCubes() {
        TestBlockAccess world = world()
            .set(0, 64, 0, "minecraft:fence", 0)
            .set(0, 64, -1, "minecraft:spruce_fence", 0)       // north: another wooden fence
            .set(1, 64, 0, "minecraft:stone", 0)               // east: opaque cube
            .set(0, 64, 1, "minecraft:nether_brick_fence", 0)  // south: different material
            .set(-1, 64, 0, "minecraft:glass", 0);             // west: not opaque
        ActualState fence = world.actual(0, 64, 0);
        assertEquals("true", fence.get("north"));
        assertEquals("true", fence.get("east"));
        assertEquals("false", fence.get("south"));
        assertEquals("false", fence.get("west"));

        world.set(-1, 64, 0, "minecraft:fence_gate", 0);
        assertEquals("true", world.actual(0, 64, 0).get("west"));
        world.set(-1, 64, 0, "minecraft:pumpkin", 0);
        assertEquals("false", world.actual(0, 64, 0).get("west"), "fences never attach to gourds");
    }

    @Test
    void panesConnectToGlassAndOtherPanes() {
        TestBlockAccess world = world()
            .set(0, 64, 0, "minecraft:glass_pane", 0)
            .set(1, 64, 0, "minecraft:glass", 0)
            .set(-1, 64, 0, "minecraft:iron_bars", 0)
            .set(0, 64, 1, "minecraft:fence", 0);
        ActualState pane = world.actual(0, 64, 0);
        assertEquals("true", pane.get("east"));
        assertEquals("true", pane.get("west"));
        assertEquals("false", pane.get("south"));
        assertEquals("false", pane.get("north"));
    }

    @Test
    void wallsDropTheirPostOnlyForUncoveredStraightRuns() {
        TestBlockAccess world = world()
            .set(0, 64, 0, "minecraft:cobblestone_wall", 0)
            .set(0, 64, -1, "minecraft:cobblestone_wall", 1)
            .set(0, 64, 1, "minecraft:cobblestone_wall", 0);
        assertEquals("false", world.actual(0, 64, 0).get("up"));
        world.set(0, 65, 0, "minecraft:torch", 5);
        assertEquals("true", world.actual(0, 64, 0).get("up"));
        world.set(0, 65, 0, "minecraft:air", 0).set(1, 64, 0, "minecraft:stone", 0);
        assertEquals("true", world.actual(0, 64, 0).get("up"));
    }

    @Test
    void grassIsSnowyUnderSnow() {
        TestBlockAccess world = world().set(0, 64, 0, "minecraft:grass", 0);
        assertEquals("false", world.actual(0, 64, 0).get("snowy"));
        world.set(0, 65, 0, "minecraft:snow_layer", 0);
        assertEquals("true", world.actual(0, 64, 0).get("snowy"));
    }

    @Test
    void doorHalvesShareTheirProperties() {
        TestBlockAccess world = world()
            .set(0, 64, 0, "minecraft:wooden_door", 0b0110)  // lower: facing west, open
            .set(0, 65, 0, "minecraft:wooden_door", 0b1001); // upper: hinge right
        ActualState lower = world.actual(0, 64, 0), upper = world.actual(0, 65, 0);
        assertEquals("right", lower.get("hinge"));
        assertEquals("west", upper.get("facing"));
        assertEquals("true", upper.get("open"));
    }

    @Test
    void upperDoublePlantTakesItsVariantFromBelow() {
        TestBlockAccess world = world()
            .set(0, 64, 0, "minecraft:double_plant", 4)
            .set(0, 65, 0, "minecraft:double_plant", 8);
        assertEquals("double_rose", world.actual(0, 65, 0).get("variant"));
    }

    @Test
    void stemsBendTowardsTheirFruit() {
        TestBlockAccess world = world().set(0, 64, 0, "minecraft:pumpkin_stem", 7);
        assertEquals("up", world.actual(0, 64, 0).get("facing"));
        world.set(1, 64, 0, "minecraft:pumpkin", 0);
        assertEquals("east", world.actual(0, 64, 0).get("facing"));
        world.set(0, 64, -1, "minecraft:pumpkin", 0);
        assertEquals("north", world.actual(0, 64, 0).get("facing"), "north is scanned first");
    }

    @Test
    void redstoneDustAttachesToPowerSourcesAndClimbsBlocks() {
        TestBlockAccess world = world()
            .set(0, 64, 0, "minecraft:redstone_wire", 0)
            .set(1, 64, 0, "minecraft:redstone_torch", 5)
            .set(-1, 64, 0, "minecraft:stone", 0)
            .set(-1, 65, 0, "minecraft:redstone_wire", 0)
            .set(0, 64, 1, "minecraft:unpowered_repeater", 1)  // faces west: side-on
            .set(0, 64, -1, "minecraft:unpowered_repeater", 0); // faces south: points at the dust
        ActualState dust = world.actual(0, 64, 0);
        assertEquals("side", dust.get("east"));
        assertEquals("up", dust.get("west"));
        assertEquals("none", dust.get("south"));
        assertEquals("side", dust.get("north"));
    }
}

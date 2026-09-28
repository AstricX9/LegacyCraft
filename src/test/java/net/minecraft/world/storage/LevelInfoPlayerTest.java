package net.minecraft.world.storage;

import net.minecraft.nbt.NbtTag;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The player data in level.dat: inventory, abilities and game modes. */
final class LevelInfoPlayerTest {
    @Test
    void inventoryAndAbilitiesRoundTrip() {
        LevelInfo info = LevelInfo.create("Test", new WorldSettings(1L, WorldSettings.GameType.CREATIVE, true, false,
            WorldType.DEFAULT, true, false, ""));
        info.player = new LevelInfo.PlayerPosition(1, 70, 2, 0, 0, true);
        info.inventory = List.of(new LevelInfo.SavedStack(0, "minecraft:stone", 64, 0),
            new LevelInfo.SavedStack(103, "minecraft:iron_helmet", 1, 12));
        info.selectedItemSlot = 4;
        info.abilities = LevelInfo.Abilities.of(WorldSettings.GameType.CREATIVE, true);

        NbtTag.Compound root = info.toNbt();
        NbtTag.Compound player = root.getCompound("Data").getCompound("Player");
        NbtTag.NbtList list = (NbtTag.NbtList) player.get("Inventory");
        assertEquals(NbtTag.COMPOUND, list.elementType());
        assertEquals(new NbtTag.Byte((byte) 103), list.compound(1).get("Slot"));
        assertEquals(new NbtTag.Short((short) 12), list.compound(1).get("Damage"));

        LevelInfo read = LevelInfo.fromNbt(root);
        assertEquals(info.inventory, read.inventory);
        assertEquals(4, read.selectedItemSlot);
        assertEquals(info.abilities, read.abilities);
        assertTrue(read.abilities.flying());
        assertTrue(read.abilities.instabuild());
        assertEquals(0.05f, read.abilities.flySpeed());
    }

    @Test
    void gameModesGrantVanillaAbilities() {
        LevelInfo.Abilities survival = LevelInfo.Abilities.of(WorldSettings.GameType.SURVIVAL, true);
        assertFalse(survival.mayfly());
        assertFalse(survival.flying(), "survival cannot keep flying");
        assertTrue(survival.mayBuild());
        LevelInfo.Abilities spectator = LevelInfo.Abilities.of(WorldSettings.GameType.SPECTATOR, false);
        assertTrue(spectator.flying());
        assertFalse(spectator.mayBuild());
        assertFalse(LevelInfo.Abilities.of(WorldSettings.GameType.ADVENTURE, false).mayBuild());
    }

    @Test
    void worldsWithoutAnInventoryReadAsNull() {
        LevelInfo info = LevelInfo.create("Test", new WorldSettings(1L, WorldSettings.GameType.SURVIVAL, true, false,
            WorldType.DEFAULT, false, false, ""));
        assertNull(LevelInfo.fromNbt(info.toNbt()).inventory);
    }
}

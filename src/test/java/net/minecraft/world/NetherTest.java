package net.minecraft.world;

import net.minecraft.testing.GameData;
import net.minecraft.world.gen.NetherFortress;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The Nether's terrain, fortresses and portal placement (HYPOTHESIS: recalled 1.8.9 rules, DEV-036). */
final class NetherTest {
    private static World nether(long seed) {
        return new World(GameData.blocks(), GameData.biomes(), seed, null, Dimension.NETHER);
    }

    private static String name(World world, int x, int y, int z) { return world.get(x, y, z).toString(); }

    @Test
    void dimensionConstants() {
        assertEquals(-1, Dimension.NETHER.id);
        assertEquals(128, Dimension.NETHER.actualHeight);
        assertFalse(Dimension.NETHER.hasSky);
        assertTrue(Dimension.NETHER.waterVaporizes());
        assertEquals("DIM-1", Dimension.NETHER.folder());
        assertSame(Dimension.NETHER, Dimension.byId(-1));
    }

    @Test
    void bedrockFloorAndCeilingAndNothingAboveTheCeiling() {
        World world = nether(42L);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            assertEquals("minecraft:bedrock", name(world, x, 0, z));
            assertEquals("minecraft:bedrock", name(world, x, 127, z));
            assertTrue(world.get(x, 128, z).isAir() || world.get(x, 128, z).toString().equals("minecraft:air"));
        }
    }

    @Test
    void terrainIsNetherBlocksWithLavaBelow32() {
        World world = nether(7L);
        int netherrack = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 1; y < 127; y++) {
            String n = name(world, x, y, z);
            if (n.equals("minecraft:netherrack")) netherrack++;
            assertNotEquals("minecraft:stone", n);
            assertNotEquals("minecraft:water", n);
            assertNotEquals("minecraft:grass", n);
            if (n.equals("minecraft:lava") || n.equals("minecraft:flowing_lava"))
                assertTrue(y < 32 || world.netherGenerator() != null, "lava seas are below 32; springs can sit anywhere");
        }
        assertTrue(netherrack > 16 * 16 * 30, "mostly netherrack");
    }

    @Test
    void noSkyLightInTheNether() {
        World world = nether(3L);
        world.get(0, 64, 0);
        assertFalse(world.dimension().hasSky);
        assertTrue(world.isNether());
    }

    @Test
    void fortressLayoutIsDeterministicPerSeed() {
        NetherFortress a = nether(1234L).netherGenerator().fortresses();
        NetherFortress b = nether(1234L).netherGenerator().fortresses();
        int found = 0;
        for (int cx = -40; cx <= 40; cx++) for (int cz = -40; cz <= 40; cz++) {
            NetherFortress.Start sa = a.startAt(cx, cz), sb = b.startAt(cx, cz);
            assertEquals(sa == null, sb == null);
            if (sa == null) continue;
            found++;
            assertEquals(sa.pieces.size(), sb.pieces.size());
            assertTrue(sa.pieces.size() > 1, "a fortress grows past its start piece");
            assertEquals(sa.box.minX, sb.box.minX);
            assertEquals(sa.box.maxZ, sb.box.maxZ);
            assertTrue(sa.box.minY >= 0 && sa.box.maxY < 128);
        }
        assertTrue(found > 0, "some fortress within 640 blocks");
    }

    @Test
    void fortressesRoundTripThroughFortressDat() {
        NetherFortress source = nether(1234L).netherGenerator().fortresses();
        NetherFortress.Start original = null;
        int ox = 0, oz = 0;
        for (int cx = -40; cx <= 40 && original == null; cx++) for (int cz = -40; cz <= 40 && original == null; cz++) {
            original = source.startAt(cx, cz);
            ox = cx; oz = cz;
        }
        assertNotNull(original);
        net.minecraft.nbt.NbtTag.Compound root = source.writeNbt();
        net.minecraft.nbt.NbtTag.Compound features = root.getCompound("data").getCompound("Features");
        net.minecraft.nbt.NbtTag.Compound start = features.getCompound("[" + ox + "," + oz + "]");
        assertEquals("Fortress", start.getString("id"));
        assertEquals("NeStart", start.getList("Children").compound(0).getString("id"));
        assertEquals(original.pieces.size(), start.getList("Children").size());

        // A different seed, so nothing would generate here without the saved data.
        NetherFortress loaded = nether(1L).netherGenerator().fortresses();
        loaded.readNbt(root);
        NetherFortress.Start back = loaded.startAt(ox, oz);
        assertNotNull(back);
        assertEquals(original.pieces.size(), back.pieces.size());
        assertEquals(original.box.minY, back.box.minY);
        for (int i = 0; i < back.pieces.size(); i++) {
            assertEquals(original.pieces.get(i).box.minX, back.pieces.get(i).box.minX);
            assertEquals(original.pieces.get(i).box.maxZ, back.pieces.get(i).box.maxZ);
        }
    }

    @Test
    void portalIsBuiltThenFoundAgain() {
        World world = nether(99L);
        double[] spot = Teleporter.placeInPortal(world, 8.5, 64, 8.5, new Random(5));
        assertEquals("minecraft:portal", name(world, (int) Math.floor(spot[0]), (int) Math.floor(spot[1]), (int) Math.floor(spot[2])));
        double[] again = Teleporter.existingPortal(world, 20.5, 70, 20.5);
        assertNotNull(again);
        assertEquals(spot[0], again[0], 1e-9);
        assertEquals(spot[1], again[1], 1e-9);
        assertEquals(spot[2], again[2], 1e-9);
    }

    @Test
    void newPortalHasAnObsidianFrame() {
        World world = nether(11L);
        double[] spot = Teleporter.placeInPortal(world, 0.5, 64, 0.5, new Random(1));
        int x = (int) Math.floor(spot[0]), y = (int) Math.floor(spot[1]), z = (int) Math.floor(spot[2]);
        assertEquals("minecraft:obsidian", name(world, x, y - 1, z), "obsidian under the portal");
        int top = y;
        while (name(world, x, top, z).equals("minecraft:portal")) top++;
        assertEquals(3, top - y, "portals are 3 tall");
        assertEquals("minecraft:obsidian", name(world, x, top, z));
    }
}

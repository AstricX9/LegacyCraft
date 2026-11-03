package net.minecraft.world.gen;

import net.minecraft.testing.GameData;
import net.minecraft.world.Dimension;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** End cities on the outer islands, built from the 1.9 structure files. */
final class EndCityTest {
    private static World end() { return new World(GameData.blocks(), GameData.biomes(), 42L, null, Dimension.END); }

    private static int[] findStart(EndCity cities) {
        for (int r = 64; r < 160; r++)
            for (int cx = -r; cx <= r; cx++)
                for (int cz = -r; cz <= r; cz++)
                    if (Math.max(Math.abs(cx), Math.abs(cz)) == r && cities.isStart(cx, cz)) return new int[]{cx, cz};
        return null;
    }

    @Test
    void templatesLoadWithTheirSizes() {
        StructureTemplate ship = StructureTemplate.get("endcity/ship");
        assertEquals(13, ship.sizeX);
        assertEquals(24, ship.sizeY);
        assertEquals(29, ship.sizeZ);
        assertFalse(ship.blocks.isEmpty());
    }

    @Test
    void citiesStandOnOuterIslandsAndAreBuiltOfPurpur() {
        World world = end();
        EndCity cities = world.endGenerator().cities();
        int[] start = findStart(cities);
        assertNotNull(start, "a city within 160 chunks");
        assertTrue((long) start[0] * start[0] + (long) start[1] * start[1] > 4096L);
        List<EndCity.Piece> pieces = cities.startAt(start[0], start[1]);
        assertTrue(pieces.size() >= 5, "pieces: " + pieces.size());
        assertEquals("base_floor", pieces.get(0).name);
        for (int i = 0; i < pieces.size(); i++)
            for (int j = i + 1; j < pieces.size(); j++) assertNotSame(pieces.get(i), pieces.get(j));
        // Generate around the first house and look for its purpur.
        EndCity.Piece base = pieces.get(0);
        int bx = base.x >> 4, bz = base.z >> 4, purpur = 0;
        for (int cx = bx - 2; cx <= bx + 2; cx++)
            for (int cz = bz - 2; cz <= bz + 2; cz++) world.get(cx * 16, 64, cz * 16);
        for (int x = base.x - 12; x <= base.x + 12; x++)
            for (int z = base.z - 12; z <= base.z + 12; z++)
                for (int y = base.y; y < base.y + 20; y++)
                    if (world.get(x, y, z).name().path().startsWith("purpur")) purpur++;
        assertTrue(purpur > 100, "purpur blocks around the first house: " + purpur);
    }

    @Test
    void rotatedStairsAndPillarsTurnWithThePiece() {
        // Stairs facing east (0) turned clockwise face south (2); an x pillar (4) turned a quarter becomes z (8).
        assertEquals(2, StructureTemplate.rotateMeta("purpur_stairs", 0, StructureTemplate.Rotation.CLOCKWISE_90));
        assertEquals(6, StructureTemplate.rotateMeta("purpur_stairs", 4, StructureTemplate.Rotation.CLOCKWISE_90));
        assertEquals(8, StructureTemplate.rotateMeta("purpur_pillar", 4, StructureTemplate.Rotation.COUNTERCLOCKWISE_90));
        assertEquals(5, StructureTemplate.rotateMeta("chest", 2, StructureTemplate.Rotation.CLOCKWISE_90));
        assertEquals(1, StructureTemplate.rotateMeta("end_rod", 1, StructureTemplate.Rotation.CLOCKWISE_180));
    }
}

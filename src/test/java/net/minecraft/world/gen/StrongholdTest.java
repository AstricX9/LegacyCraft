package net.minecraft.world.gen;

import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Strongholds and the eye of ender that finds them (HYPOTHESIS: recalled 1.8.9 rules). */
final class StrongholdTest {
    @Test
    void threeStrongholdsAThirdOfATurnApart() {
        Stronghold strongholds = GameData.newWorld(1234L).strongholds();
        int[][] coords = strongholds.chunkCoords();
        assertEquals(3, coords.length);
        for (int[] c : coords) {
            double d = Math.hypot(c[0], c[1]);
            // 40-72 chunks out, moved at most 112 blocks (7 chunks) to land.
            assertTrue(d > 40 - 8 && d < 72 + 8, "distance " + d);
        }
        assertArrayEquals(coords, GameData.newWorld(1234L).strongholds().chunkCoords());
    }

    @Test
    void eachHasAPortalRoomBelowSeaLevel() {
        List<Stronghold.Start> starts = GameData.newWorld(99L).strongholds().starts();
        assertEquals(3, starts.size());
        for (Stronghold.Start start : starts) {
            assertNotNull(start.portalRoom);
            assertTrue(start.box.maxY <= 53, "top " + start.box.maxY);
            assertTrue(start.box.minY > 1);
            assertEquals(Stronghold.Type.STAIRS, start.pieces.get(0).type);
            assertEquals(Stronghold.Type.CROSSING, start.pieces.get(1).type, "the staircase leads into a five-way crossing");
            // Joining corridors reach one block into the piece they join; nothing else overlaps.
            for (int i = 0; i < start.pieces.size(); i++)
                for (int j = i + 1; j < start.pieces.size(); j++)
                    if (start.pieces.get(i).type != Stronghold.Type.CORRIDOR && start.pieces.get(j).type != Stronghold.Type.CORRIDOR)
                        assertFalse(start.pieces.get(i).box.intersects(start.pieces.get(j).box), "pieces overlap");
        }
    }

    @Test
    void thePortalRoomHasTwelveFramesAndASpawner() {
        World world = GameData.newWorld(99L);
        Stronghold.Piece room = world.strongholds().starts().get(0).portalRoom;
        for (int cx = (room.box.minX >> 4) - 2; cx <= (room.box.maxX >> 4) + 2; cx++)
            for (int cz = (room.box.minZ >> 4) - 2; cz <= (room.box.maxZ >> 4) + 2; cz++) world.get(cx * 16, 0, cz * 16);
        int frames = 0, spawners = 0;
        for (int x = room.box.minX; x <= room.box.maxX; x++)
            for (int z = room.box.minZ; z <= room.box.maxZ; z++)
                for (int y = room.box.minY; y <= room.box.maxY; y++) {
                    String name = world.get(x, y, z).name().path();
                    if (name.equals("end_portal_frame")) frames++;
                    if (name.equals("mob_spawner")) spawners++;
                }
        assertEquals(12, frames);
        assertEquals(1, spawners);
    }

    @Test
    void nearestPicksTheClosestStronghold() {
        Stronghold strongholds = GameData.newWorld(7L).strongholds();
        for (int[] c : strongholds.chunkCoords()) {
            int[] found = strongholds.nearest(c[0] * 16 + 8, 40, c[1] * 16 + 8);
            assertEquals(c[0], found[0] >> 4);
            assertEquals(c[1], found[2] >> 4);
        }
    }
}

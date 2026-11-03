package net.minecraft.world;

import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** End gateways: the bedrock frame and the trip out to the outer islands. */
final class EndGatewayTest {
    private static World end() { return new World(GameData.blocks(), GameData.biomes(), 42L, null, Dimension.END); }

    @Test
    void gatewaySitsInABedrockFrame() {
        World world = end();
        EndGateway.build(world, 96, 75, 0);
        assertEquals("end_gateway", world.get(96, 75, 0).name().path());
        assertEquals("bedrock", world.get(96, 77, 0).name().path());
        assertEquals("bedrock", world.get(96, 73, 0).name().path());
        assertEquals("bedrock", world.get(97, 76, 0).name().path());
        assertTrue(world.get(97, 75, 0).isAir());
        assertTrue(world.get(97, 76, 1).isAir());
    }

    @Test
    void exitIsOnLandFarOutAlongTheGateway() {
        World world = end();
        int[] exit = EndGateway.findExit(world, 96, 0, new Random(1));
        double distance = Math.sqrt((double) exit[0] * exit[0] + (double) exit[2] * exit[2]);
        assertTrue(distance > 700 && distance < 1400, "exit at " + distance);
        assertTrue(Math.abs(exit[2]) < 40, "exit stays along +x: " + exit[2]);
        int[] stand = EndGateway.standingSpot(world, exit[0], exit[1], exit[2]);
        assertTrue(world.get(stand[0], stand[1] - 2, stand[2]).isOpaque(), "solid ground under the exit");
    }
}

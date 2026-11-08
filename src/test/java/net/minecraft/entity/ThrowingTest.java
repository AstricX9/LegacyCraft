package net.minecraft.entity;

import net.minecraft.client.entity.Player;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Thrown things (snowballs, eggs, pearls, splash potions) leave along the player's view, not behind them. */
final class ThrowingTest {
    private static Player player(World world) {
        Player player = new Player(world);
        player.setPositionAndRotation(0.5, 201, 0.5, 30, -10);
        return player;
    }

    private static void assertAlongView(Player player, double mx, double my, double mz) {
        double length = Math.sqrt(mx * mx + my * my + mz * mz);
        double dot = (mx * player.dirX() + my * player.dirY() + mz * player.dirZ()) / length;
        assertTrue(dot > 0.99, "flies along the view, dot " + dot);
    }

    @Test
    void aSnowballFliesWhereThePlayerLooks() {
        World world = GameData.newWorld(5L);
        Player player = player(world);
        EntitySnowball ball = new EntitySnowball(world, 0, 0, 0).aimFrom(player, new Random(1));
        assertAlongView(player, ball.motionX, ball.motionY, ball.motionZ);
    }
}

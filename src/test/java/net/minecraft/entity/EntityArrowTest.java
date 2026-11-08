package net.minecraft.entity;

import net.minecraft.client.entity.Player;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

final class EntityArrowTest {
    /** A bow shot flies the way the player looks, at full speed (it used to fly out behind them). */
    @Test
    void shotFliesAlongView() {
        World world = GameData.newWorld(42L);
        Player player = new Player(world);
        for (float yaw : new float[] {0f, 90f, 200f, -45f}) {
            player.yaw = yaw;
            player.pitch = -10f;
            EntityArrow arrow = EntityArrow.shotBy(world, player, 2f, new Random(1));
            double speed = Math.sqrt(arrow.motionX * arrow.motionX + arrow.motionY * arrow.motionY + arrow.motionZ * arrow.motionZ);
            assertEquals(3.0, speed, 0.1);
            double along = (arrow.motionX * player.dirX() + arrow.motionY * player.dirY() + arrow.motionZ * player.dirZ()) / speed;
            assertTrue(along > 0.99, "yaw " + yaw + ": arrow off the view by cos " + along);
        }
    }
}

package net.minecraft.entity;

import net.minecraft.block.BlockState;
import net.minecraft.client.entity.Player;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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

    private static final class Host implements EntityHost {
        final List<Entity> entities = new ArrayList<>();
        @Override public Player player() { return null; }
        @Override public List<Entity> entities() { return new ArrayList<>(entities); }
        @Override public void spawn(Entity entity) { if (entity instanceof MovingEntity m) m.setHost(this); entities.add(entity); }
        @Override public void playSound(String event, double x, double y, double z, float volume, float pitch) {}
        @Override public void particle(String kind, double x, double y, double z, double vx, double vy, double vz) {}
        @Override public void explode(Entity exploder, double x, double y, double z, float size, boolean smoking, String attacker) {}
        @Override public int difficulty() { return 2; }
        @Override public boolean gameRule(String name) { return true; }
        @Override public long worldTime() { return 6000; }
        @Override public void breakBlock(int x, int y, int z, boolean drop) {}
    }

    /** An arrow sticks where it meets the block, not hanging in the air a tick's flight short of it. */
    @Test
    void sticksAtTheBlockFace() {
        World world = GameData.newWorld(5L);
        BlockState stone = BlockState.defaultOf(GameData.blocks().get("minecraft:stone"));
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 12; z++) world.setState(x, 200, z, stone);
        Host host = new Host();
        EntityArrow arrow = new EntityArrow(world, 0.5, 203, 10.5);
        arrow.setHeading(0, -0.5, -1, 3f, 0, new Random(1));
        host.spawn(arrow);
        for (int t = 0; t < 20 && !arrow.inGround; t++) arrow.tick();
        assertTrue(arrow.inGround);
        assertEquals(201.0, arrow.y, 0.06, "stuck on top of the stone");
    }
}

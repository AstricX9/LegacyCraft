package net.minecraft.entity;

import net.minecraft.block.BlockState;
import net.minecraft.client.entity.Player;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Eyes of ender, ender pearls and endermen (HYPOTHESIS: recalled 1.8.9 rules). */
final class EnderTest {
    private static final int FLOOR = 200;
    private World world;
    private FakeHost host;

    private static final class FakeHost implements EntityHost {
        final List<Entity> entities = new ArrayList<>();
        Player player;
        @Override public Player player() { return player; }
        @Override public List<Entity> entities() { return new ArrayList<>(entities); }
        @Override public void spawn(Entity entity) {
            if (entity instanceof MovingEntity moving) moving.setHost(this);
            entities.add(entity);
        }
        @Override public void playSound(String event, double x, double y, double z, float volume, float pitch) {}
        @Override public void particle(String kind, double x, double y, double z, double vx, double vy, double vz) {}
        @Override public void explode(Entity exploder, double x, double y, double z, float size, boolean smoking, String attacker) {}
        @Override public int difficulty() { return 2; }
        @Override public boolean gameRule(String name) { return true; }
        @Override public long worldTime() { return 18000; }
    }

    @BeforeEach
    void platform() {
        world = GameData.newWorld(5L);
        BlockState stone = BlockState.defaultOf(GameData.blocks().get("minecraft:stone"));
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) world.setState(x, FLOOR, z, stone);
        host = new FakeHost();
    }

    @Test
    void anEyeFliesTwelveBlocksTowardTheTargetRisingThenEnds() {
        EntityEnderEye eye = new EntityEnderEye(world, 0.5, FLOOR + 2, 0.5);
        host.spawn(eye);
        eye.moveTowards(1000, 30, 0);
        assertEquals(12.5, eye.targetX, 1e-4);
        assertEquals(FLOOR + 10, eye.targetY, 1e-9);
        for (int i = 0; i < 80; i++) eye.tick();
        assertFalse(eye.isRemoved());
        assertTrue(eye.x() > 8 && eye.x() < 14, "x " + eye.x());
        assertTrue(eye.y() > FLOOR + 4, "y " + eye.y());
        eye.tick();
        assertTrue(eye.isRemoved());
    }

    @Test
    void aPearlTeleportsItsThrowerAndHurts() {
        Player player = new Player(world);
        player.setPositionAndRotation(0.5, FLOOR + 1, 0.5, 0, 0);
        host.player = player;
        EntityEnderPearl pearl = new EntityEnderPearl(world, 5.5, FLOOR + 1.2, 5.5);
        pearl.thrownByPlayer = true;
        host.spawn(pearl);
        float health = player.health;
        pearl.motionY = -0.5;
        for (int i = 0; i < 5 && !pearl.isRemoved(); i++) pearl.tick();
        assertTrue(pearl.isRemoved());
        assertEquals(5.5, player.x, 0.5);
        assertEquals(5.5, player.z, 0.5);
        assertEquals(health - 5f, player.health, 1e-6);
    }

    @Test
    void anEndermanNoticesAStareUnlessThePlayerWearsAPumpkin() {
        Player player = new Player(world);
        player.setPositionAndRotation(0.5, FLOOR + 1, 0.5, 0, 0);
        host.player = player;
        EntityEnderman enderman = new EntityEnderman(world, 0.5, FLOOR + 1, 0.5);
        host.spawn(enderman);
        // Put it straight along the player's view at the height its eyes would meet.
        double d = 8;
        enderman.setPositionAndRotation(0.5 + player.dirX() * d, player.eyeY() + player.dirY() * d - enderman.eyeHeight(),
            0.5 + player.dirZ() * d, 0, 0);
        assertTrue(enderman.isStaredAtBy(player));
        player.armor[3] = new net.minecraft.item.ItemStack(GameData.items().get("minecraft:pumpkin"));
        assertFalse(enderman.isStaredAtBy(player));
    }
}

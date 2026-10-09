package net.minecraft.world;

import net.minecraft.client.entity.Player;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHost;
import net.minecraft.entity.MovingEntity;
import net.minecraft.testing.GameData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Natural spawning only looks at terrain that is already there. */
final class MobSpawnerTest {
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
        @Override public void achievement(String id) {}
    }

    /**
     * Spawn packs wander up to 20 blocks from their start, past the edge of
     * the generated area; a spot there doesn't generate its chunk (it would
     * on the tick thread, which is what made servers with mobs slow).
     */
    @Test
    void spawningNeverGeneratesChunks() {
        World world = GameData.newWorld(12345L);
        for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) world.getChunk(cx, cz);
        FakeHost host = new FakeHost();
        host.player = new Player(world, 8.5, 8.5);
        int before = world.loadedChunkCount();
        Random random = new Random(1);
        for (int i = 0; i < 400; i++) {
            MobSpawner.spawnMonsters(world, host, random, 100_000, 64, 100_000);
            MobSpawner.spawnCreatures(world, host, random, 400L * i, 100_000, 64, 100_000);
        }
        assertEquals(before, world.loadedChunkCount(), "spawning generated new chunks");
    }
}

package net.minecraft.server.dedicated;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.network.v47.Clientbound;
import net.minecraft.network.v47.Serverbound;
import net.minecraft.network.v47.Slot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mobs over the wire: hostile mobs spawn on their own at night, a zombie
 * shows up on the client, chases and hurts the player, and dies to their sword.
 */
class MobServerTest {
    private static final int DIAMOND_SWORD = 276, ZOMBIE = 54;
    private static DedicatedServer server;

    @BeforeAll
    static void start() throws Exception { server = TestClient.startServer("gamemode=0\ndifficulty=2\nspawn-animals=false\n"); }

    @AfterAll
    static void stop() { server.stop(); }

    /** Runs this on the server's tick thread and waits for its answer. */
    private static <T> T onTick(java.util.function.Supplier<T> task) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        server.execute(() -> {
            try { result.complete(task.get()); } catch (RuntimeException e) { result.completeExceptionally(e); }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    @Test
    void zombieChasesHurtsAndDies() throws Exception {
        try (TestClient steve = new TestClient(server.boundPort())) {
            steve.login("Steve");
            Clientbound.PlayerPositionLook spawn = steve.expect(Clientbound.PlayerPositionLook.class, p -> true);
            steve.send(new Serverbound.Move(true, true, spawn.x(), spawn.y(), spawn.z(), 0, 0, true));
            // A diamond sword in the hand, handed over in Creative.
            steve.send(new Serverbound.Chat("/gamemode creative"));
            steve.expect(Clientbound.ChangeGameState.class, c -> c.value() == 1);
            steve.send(new Serverbound.CreativeInventoryAction(36, new Slot(DIAMOND_SWORD, 1, 0, null)));
            steve.send(new Serverbound.Chat("/gamemode survival"));
            steve.expect(Clientbound.ChangeGameState.class, c -> c.value() == 0);

            // Midnight, and a zombie a few blocks away on the ground.
            int zombieId = onTick(() -> {
                server.timeOfDay = 18000;
                // Somewhere 2-4 blocks off on the player's own level (not up a tree).
                int feetX = (int) Math.floor(spawn.x()), feetY = (int) Math.floor(spawn.y()), feetZ = (int) Math.floor(spawn.z());
                int x = feetX + 3, z = feetZ;
                search:
                for (int r = 2; r <= 4; r++)
                    for (int dx = -r; dx <= r; dx++)
                        for (int dz = -r; dz <= r; dz++)
                            if (Math.abs(dx) + Math.abs(dz) == r && server.surfaceY(feetX + dx, feetZ + dz) == feetY) {
                                x = feetX + dx;
                                z = feetZ + dz;
                                break search;
                            }
                EntityZombie zombie = new EntityZombie(server.world, x + 0.5, server.surfaceY(x, z), z + 0.5);
                server.mobs.spawn(zombie);
                return server.entities.idOf(zombie.uuid());
            });
            Clientbound.SpawnMob shown = steve.expect(Clientbound.SpawnMob.class, m -> m.entityId() == zombieId);
            assertEquals(ZOMBIE, shown.type());

            // It walks up and hits: 3 attack on Normal, less the bare player's no armour.
            float health = steve.expect(Clientbound.UpdateHealth.class, h -> h.health() < 20f).health();
            assertEquals(17f, health, "a zombie's hit on Normal");

            // Three sword hits (8 each, less its 2 armour points) kill it; the client is told, then it's gone.
            for (int hit = 0; hit < 3; hit++) {
                steve.send(new Serverbound.UseEntity(zombieId, Serverbound.UseEntity.ATTACK, 0, 0, 0));
                if (hit < 2) steve.expect(Clientbound.EntityStatus.class, s -> s.entityId() == zombieId && s.status() == Clientbound.EntityStatus.HURT);
                Thread.sleep(600);
            }
            steve.expect(Clientbound.EntityStatus.class, s -> s.entityId() == zombieId && s.status() == Clientbound.EntityStatus.DEAD);
            steve.expect(Clientbound.DestroyEntities.class, d -> java.util.Arrays.stream(d.entityIds()).anyMatch(id -> id == zombieId));
        }
    }

    @Test
    void monstersSpawnOnTheirOwnAtNight() throws Exception {
        try (TestClient alex = new TestClient(server.boundPort())) {
            alex.login("Alex");
            Clientbound.PlayerPositionLook spawn = alex.expect(Clientbound.PlayerPositionLook.class, p -> true);
            alex.send(new Serverbound.Move(true, true, spawn.x(), spawn.y(), spawn.z(), 0, 0, true));
            onTick(() -> { server.timeOfDay = 18000; return null; });
            long monsters = 0;
            for (int i = 0; i < 100 && monsters < 5; i++) {
                Thread.sleep(100);
                monsters = onTick(() -> server.entities.values().stream().filter(e -> e instanceof IMob && !e.isRemoved()).count());
            }
            assertTrue(monsters >= 5, "only " + monsters + " monsters spawned around the player at night");
            // Every one keeps at least 24 blocks from the player.
            for (Entity entity : onTick(() -> server.entities.values()))
                if (entity instanceof IMob && entity instanceof net.minecraft.entity.EntityLiving mob && mob.ticksExisted < 2) {
                    // Measured in 3D like the spawner, so one in a cave below can be closer across the ground.
                    double dx = mob.x() - spawn.x(), dy = mob.y() - spawn.y(), dz = mob.z() - spawn.z();
                    assertTrue(dx * dx + dy * dy + dz * dz >= 24 * 24 - 4, "a monster spawned within 24 blocks");
                }
        }
    }
}

package net.minecraft.server.dedicated;

import net.minecraft.network.v47.Clientbound;
import net.minecraft.network.v47.Serverbound;
import net.minecraft.network.v47.Slot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Survival over the wire, as a 1.8.9 client plays it: timed digging with
 * drops picked up, crafting in the inventory grid, fall damage, the player's
 * things surviving a reconnect, and death with a respawn.
 */
class SurvivalServerTest {
    private static final int STONE = 1, DIRT = 3, GRASS = 2, PLANKS = 5, LOG = 17;
    private static DedicatedServer server;

    @BeforeAll
    static void start() throws Exception { server = TestClient.startServer("gamemode=0\ndifficulty=1\n"); }

    @AfterAll
    static void stop() { server.stop(); }

    @Test
    void digCraftFallDieAndRespawn() throws Exception {
        int port = server.boundPort();
        Clientbound.PlayerPositionLook spawn;
        try (TestClient steve = new TestClient(port)) {
            assertEquals(0, steve.login("Steve").gameMode());
            spawn = steve.expect(Clientbound.PlayerPositionLook.class, p -> true);
            Clientbound.WindowItems window = steve.expect(Clientbound.WindowItems.class, w -> w.windowId() == 0);
            assertEquals(45, window.items().length);
            assertTrue(Arrays.stream(window.items()).allMatch(Slot::isEmpty), "a new player starts empty-handed");
            assertEquals(20f, steve.expect(Clientbound.UpdateHealth.class, h -> true).health());
            steve.send(new Serverbound.Move(true, true, spawn.x(), spawn.y(), spawn.z(), 0, 90, true));

            // The ground under the player: finishing at once is refused, finishing after the dig time breaks it.
            int x = (int) Math.floor(spawn.x()), y = (int) Math.floor(spawn.y()) - 1, z = (int) Math.floor(spawn.z());
            int ground = server.world.getState(x, y, z).packed() >> 4;
            assertTrue(ground == GRASS || ground == DIRT, "spawn stands on grass, found block " + ground);
            steve.send(new Serverbound.Digging(Serverbound.Digging.START, x, y, z, 1));
            steve.send(new Serverbound.Digging(Serverbound.Digging.FINISH, x, y, z, 1));
            Clientbound.BlockChange refused = steve.expect(Clientbound.BlockChange.class, c -> c.x() == x && c.y() == y && c.z() == z);
            assertEquals(ground, refused.state() >> 4, "a too-quick dig puts the block back");

            steve.send(new Serverbound.Digging(Serverbound.Digging.START, x, y, z, 1));
            Thread.sleep(1100);                                         // grass by hand: 0.6 hardness, 18 ticks
            steve.send(new Serverbound.Digging(Serverbound.Digging.FINISH, x, y, z, 1));
            assertEquals(0, steve.expect(Clientbound.BlockChange.class, c -> c.x() == x && c.y() == y && c.z() == z).state());
            Clientbound.SpawnObject drop = steve.expect(Clientbound.SpawnObject.class, o -> o.type() == Clientbound.SpawnObject.ITEM);

            // Drop into the hole and the dirt is picked up into the first hotbar slot.
            steve.send(new Serverbound.Move(true, false, spawn.x(), y, spawn.z(), 0, 0, true));
            steve.expect(Clientbound.CollectItem.class, c -> c.collectedId() == drop.entityId());
            Clientbound.SetSlot picked = steve.expect(Clientbound.SetSlot.class, s -> s.windowId() == 0 && s.slot() == 36);
            assertEquals(DIRT, picked.item().itemId());

            // Crafting: a log (handed over in Creative) into the 2x2 grid makes four planks; shift-click takes them.
            steve.send(new Serverbound.Chat("/gamemode creative"));
            steve.expect(Clientbound.ChangeGameState.class, c -> c.reason() == Clientbound.ChangeGameState.GAME_MODE);
            steve.send(new Serverbound.CreativeInventoryAction(37, new Slot(LOG, 1, 0, null)));
            steve.send(new Serverbound.Chat("/gamemode survival"));
            steve.expect(Clientbound.ChangeGameState.class, c -> c.value() == 0);
            steve.send(new Serverbound.ClickWindow(0, 37, 0, 1, 0, new Slot(LOG, 1, 0, null)));
            steve.expect(Clientbound.ConfirmTransaction.class, c -> c.action() == 1 && c.accepted());
            steve.send(new Serverbound.ClickWindow(0, 1, 0, 2, 0, Slot.EMPTY));
            Clientbound.WindowItems grid = steve.expect(Clientbound.WindowItems.class, w -> w.windowId() == 0 && w.items()[1].itemId() == LOG);
            assertEquals(PLANKS, grid.items()[0].itemId(), "the result slot shows planks");
            assertEquals(4, grid.items()[0].count());
            steve.send(new Serverbound.ClickWindow(0, 0, 0, 3, 1, grid.items()[0]));
            Clientbound.WindowItems crafted = steve.expect(Clientbound.WindowItems.class, w -> w.windowId() == 0 && w.items()[1].isEmpty());
            assertEquals(4, Arrays.stream(crafted.items()).skip(9).filter(s -> s.itemId() == PLANKS).mapToInt(Slot::count).sum());
            steve.send(new Serverbound.CloseWindow(0));
        }

        // Reconnecting brings the inventory back.
        try (TestClient steve = new TestClient(port)) {
            steve.login("Steve");
            Clientbound.PlayerPositionLook here = steve.expect(Clientbound.PlayerPositionLook.class, p -> true);
            Clientbound.WindowItems window = steve.expect(Clientbound.WindowItems.class, w -> w.windowId() == 0);
            assertEquals(DIRT, window.items()[36].itemId());
            assertEquals(4, Arrays.stream(window.items()).filter(s -> s.itemId() == PLANKS).mapToInt(Slot::count).sum());
            steve.send(new Serverbound.Move(true, true, here.x(), here.y(), here.z(), 0, 0, true));

            // A nine-block fall costs six hearts' worth of health points.
            for (int up = 10; up >= 1; up--) steve.send(new Serverbound.Move(true, false, here.x(), here.y() + up, here.z(), 0, 0, false));
            steve.send(new Serverbound.Move(true, false, here.x(), here.y(), here.z(), 0, 0, true));
            assertEquals(14f, steve.expect(Clientbound.UpdateHealth.class, h -> h.health() < 20f).health());

            // Dying scatters everything and shows the death screen; respawning starts over at spawn.
            steve.send(new Serverbound.Chat("/kill"));
            assertEquals(0f, steve.expect(Clientbound.UpdateHealth.class, h -> h.health() <= 0f).health());
            steve.expect(Clientbound.SpawnObject.class, o -> o.type() == Clientbound.SpawnObject.ITEM);
            steve.send(new Serverbound.ClientStatus(Serverbound.ClientStatus.RESPAWN));
            steve.expect(Clientbound.Respawn.class, r -> true);
            Clientbound.PlayerPositionLook respawned = steve.expect(Clientbound.PlayerPositionLook.class, p -> true);
            assertEquals(server.spawnX + 0.5, respawned.x());
            Clientbound.WindowItems empty = steve.expect(Clientbound.WindowItems.class, w -> w.windowId() == 0);
            assertTrue(Arrays.stream(empty.items()).allMatch(Slot::isEmpty), "everything was dropped on death");
            assertEquals(20f, steve.expect(Clientbound.UpdateHealth.class, h -> true).health());
        }
    }

    @Test
    void craftingTableOpensAThreeByThreeWindow() throws Exception {
        try (TestClient alex = new TestClient(server.boundPort())) {
            alex.login("Alex");
            Clientbound.PlayerPositionLook spawn = alex.expect(Clientbound.PlayerPositionLook.class, p -> true);
            alex.send(new Serverbound.Move(true, true, spawn.x(), spawn.y(), spawn.z(), 0, 0, true));
            alex.send(new Serverbound.Chat("/gamemode creative"));
            alex.expect(Clientbound.ChangeGameState.class, c -> c.value() == 1);
            alex.send(new Serverbound.CreativeInventoryAction(36, new Slot(58, 1, 0, null)));
            int feetX = (int) Math.floor(spawn.x()), feetY = (int) Math.floor(spawn.y()), feetZ = (int) Math.floor(spawn.z());
            int[] column = null;
            for (int dx = -3; dx <= 3 && column == null; dx++)
                for (int dz = -3; dz <= 3 && column == null; dz++) {
                    int surface = server.surfaceY(feetX + dx, feetZ + dz);
                    if (Math.abs(dx) + Math.abs(dz) > 1 && surface >= feetY - 1 && surface <= feetY)
                        column = new int[]{feetX + dx, surface - 1, feetZ + dz};
                }
            assertNotNull(column, "no flat column near spawn");
            int x = column[0], top = column[1], z = column[2];
            alex.send(new Serverbound.BlockPlacement(x, top, z, 1, new Slot(58, 1, 0, null), 8, 16, 8));
            alex.expect(Clientbound.BlockChange.class, c -> c.x() == x && c.y() == top + 1 && c.state() >> 4 == 58);
            alex.send(new Serverbound.BlockPlacement(x, top + 1, z, 1, new Slot(58, 1, 0, null), 8, 16, 8));
            Clientbound.OpenWindow open = alex.expect(Clientbound.OpenWindow.class, o -> true);
            assertEquals("minecraft:crafting_table", open.type());
            assertEquals(46, alex.expect(Clientbound.WindowItems.class, w -> w.windowId() == open.windowId()).items().length);
            alex.send(new Serverbound.CloseWindow(open.windowId()));
        }
    }
}

package net.minecraft.server;

import net.minecraft.entity.Entity;
import net.minecraft.testing.GameData;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The integrated server ticks entities and removes dead ones. */
final class IntegratedServerTest {
    @Test
    void ticksAndRemovesEntitiesAuthoritatively() {
        World world = GameData.newWorld(1L);
        IntegratedServer server = new IntegratedServer(world);
        Entity entity = new Entity(world, 0, 70, 0) {
            @Override public void tick() { remove(); }
        };
        int id = server.entities().add(entity);
        server.tick();
        assertEquals(1, server.tickCount());
        assertNull(server.entities().get(id));
    }
}

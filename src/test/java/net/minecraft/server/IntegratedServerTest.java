package net.minecraft.server;

import net.minecraft.block.BlockRegistry;
import net.minecraft.client.resources.ClasspathResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IntegratedServerTest {
    @Test
    void ticksAndRemovesEntitiesAuthoritatively() {
        World world = new World(BlockRegistry.load(new ClasspathResourceManager()), 1L);
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

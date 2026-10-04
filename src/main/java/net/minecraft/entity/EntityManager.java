package net.minecraft.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Assigns runtime entity IDs and performs removal-safe server ticks. */
public final class EntityManager {
    private final Map<Integer, Entity> byId = new HashMap<>();
    private final Map<UUID, Integer> idsByUuid = new HashMap<>();
    private int nextId = 1;

    /** The entity list as last read, shared until something is added or removed (every mob reads it every tick). */
    private List<Entity> snapshot;

    public int add(Entity entity) {
        snapshot = null;
        if (idsByUuid.containsKey(entity.uuid())) throw new IllegalArgumentException("Duplicate entity UUID");
        int id = nextId++;
        byId.put(id, entity);
        idsByUuid.put(entity.uuid(), id);
        return id;
    }
    public Entity get(int id) { return byId.get(id); }
    public int idOf(UUID uuid) { return idsByUuid.getOrDefault(uuid, -1); }
    public List<Entity> values() {
        List<Entity> current = snapshot;
        if (current == null) snapshot = current = List.copyOf(byId.values());
        return current;
    }
    public void tick() {
        for (Entity entity : new ArrayList<>(byId.values())) if (!entity.isRemoved()) entity.tick();
        boolean removedAny = byId.entrySet().removeIf(entry -> {
            if (!entry.getValue().isRemoved()) return false;
            idsByUuid.remove(entry.getValue().uuid());
            return true;
        });
        if (removedAny) snapshot = null;
    }
}

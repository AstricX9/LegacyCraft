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

    public int add(Entity entity) {
        if (idsByUuid.containsKey(entity.uuid())) throw new IllegalArgumentException("Duplicate entity UUID");
        int id = nextId++;
        byId.put(id, entity);
        idsByUuid.put(entity.uuid(), id);
        return id;
    }
    public Entity get(int id) { return byId.get(id); }
    public int idOf(UUID uuid) { return idsByUuid.getOrDefault(uuid, -1); }
    public List<Entity> values() { return List.copyOf(byId.values()); }
    public void tick() {
        for (Entity entity : new ArrayList<>(byId.values())) if (!entity.isRemoved()) entity.tick();
        byId.entrySet().removeIf(entry -> {
            if (!entry.getValue().isRemoved()) return false;
            idsByUuid.remove(entry.getValue().uuid());
            return true;
        });
    }
}

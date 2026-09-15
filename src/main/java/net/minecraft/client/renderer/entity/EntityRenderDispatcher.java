package net.minecraft.client.renderer.entity;

import net.minecraft.entity.Entity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves the nearest registered renderer in an entity's class hierarchy. */
public final class EntityRenderDispatcher {
    private final Map<Class<?>, EntityRenderer<?>> renderers = new LinkedHashMap<>();
    public <T extends Entity> void register(Class<T> type, EntityRenderer<? super T> renderer) { renderers.put(type, renderer); }
    @SuppressWarnings("unchecked")
    public void render(Entity entity, float partialTick) {
        Class<?> type = entity.getClass();
        while (type != null) {
            EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) renderers.get(type);
            if (renderer != null) { renderer.render(entity, partialTick); return; }
            type = type.getSuperclass();
        }
        throw new IllegalArgumentException("No renderer for " + entity.getClass().getName());
    }
}

package net.minecraft.client.renderer.entity;

import net.minecraft.entity.Entity;

/** Rendering adapter for a concrete entity type. */
@FunctionalInterface
public interface EntityRenderer<T extends Entity> { void render(T entity, float partialTick); }

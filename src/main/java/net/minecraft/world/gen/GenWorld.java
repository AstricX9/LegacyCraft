package net.minecraft.world.gen;

import net.minecraft.world.biome.Biome;

/**
 * What population features see of the world: packed block states
 * ({@code id << 4 | metadata}) read and written without block updates or
 * immediate relighting (vanilla writes with flag 2 during population), the
 * biome and heightmap of a column, and light as it stands.
 *
 * <p>Reading or writing a position in a chunk that does not exist yet
 * generates it, as vanilla does when a feature reaches past the chunks it
 * was populated for.
 */
public interface GenWorld {
    int get(int x, int y, int z);
    void set(int x, int y, int z, int packed);
    Biome biome(int x, int z);
    /** Vanilla getHeight: one above the highest block that dims light (leaves and water count). */
    int height(int x, int z);
    /** Vanilla getLight: the brighter of sky and block light, with the sky at full daylight. */
    int light(int x, int y, int z);
    int blockLight(int x, int y, int z);
    /** Whether sky light reaches the position at all (lakes turn exposed dirt to grass). */
    boolean seesSky(int x, int y, int z);
    GenBlocks blocks();
}

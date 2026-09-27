package net.minecraft.client.renderer;

import net.minecraft.world.IBlockAccess;

/**
 * What building a chunk mesh reads: block states, light and biomes. Meshes
 * are built on worker threads from a {@link SectionSnapshot} of this, never
 * from the live world, so generation and edits on the main thread cannot
 * race the mesher.
 */
interface RenderView extends IBlockAccess, BiomeColors.BiomeLookup {
    int skyLight(int x, int y, int z);
    int blockLight(int x, int y, int z);
}

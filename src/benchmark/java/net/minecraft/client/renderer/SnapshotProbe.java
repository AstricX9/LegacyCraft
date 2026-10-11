package net.minecraft.client.renderer;

import net.minecraft.world.World;

/** Lets the benchmark build {@link SectionSnapshot}s (package-private) the way the world renderer does before meshing. */
public final class SnapshotProbe {
    private SnapshotProbe() {}

    /** Copies one section and folds a few of its cells into the checksum, so the copy can't be optimised away. */
    public static long snapshot(World world, int sectionX, int sectionY, int sectionZ) {
        SectionSnapshot snapshot = new SectionSnapshot(world, sectionX, sectionY, sectionZ);
        int x = sectionX * 16, y = sectionY * 16, z = sectionZ * 16;
        return snapshot.packed(x, y, z) * 31L + snapshot.skyLight(x + 15, y + 15, z + 15) + snapshot.blockLight(x + 7, y, z + 7);
    }
}

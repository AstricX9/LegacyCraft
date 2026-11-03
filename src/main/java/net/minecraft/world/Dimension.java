package net.minecraft.world;

/**
 * The dimensions LegacyCraft has (1.8.9 WorldProvider subclasses): their
 * save id, whether they have a sky (sky light, sun, weather), how tall the
 * playable space is (getActualHeight: 128 in the Nether) and how far one
 * block there goes in the overworld (the Nether's 8).
 */
public enum Dimension {
    OVERWORLD(0, true, 256, 1.0),
    NETHER(-1, false, 128, 8.0);

    public final int id;
    public final boolean hasSky;
    public final int actualHeight;
    /** Overworld blocks per block here (portal travel divides or multiplies by it). */
    public final double scale;

    Dimension(int id, boolean hasSky, int actualHeight, double scale) {
        this.id = id;
        this.hasSky = hasSky;
        this.actualHeight = actualHeight;
        this.scale = scale;
    }

    public static Dimension byId(int id) { return id == -1 ? NETHER : OVERWORLD; }

    /** The save folder below the world folder (vanilla DIM-1), or "" for the overworld. */
    public String folder() { return this == NETHER ? "DIM-1" : ""; }

    /** WorldProviderHell.doesWaterVaporize. */
    public boolean waterVaporizes() { return this == NETHER; }
}

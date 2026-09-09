package net.minecraft.client.gui;

/** Integer GUI scaling with the same minimum logical size contract as the legacy client. */
public final class ScaledResolution {
    private final int scaledWidth;
    private final int scaledHeight;
    private final int scaleFactor;
    private final double scaledWidthDouble;
    private final double scaledHeightDouble;

    public ScaledResolution(int displayWidth, int displayHeight, int configuredGuiScale, boolean unicode) {
        int factor = 1;
        int requested = configuredGuiScale == 0 ? 1000 : configuredGuiScale;
        while (factor < requested
            && displayWidth / (factor + 1) >= 320
            && displayHeight / (factor + 1) >= 240) {
            factor++;
        }
        if (unicode && factor % 2 != 0 && factor != 1) factor--;
        scaleFactor = factor;
        scaledWidthDouble = displayWidth / (double) factor;
        scaledHeightDouble = displayHeight / (double) factor;
        scaledWidth = (int) Math.ceil(scaledWidthDouble);
        scaledHeight = (int) Math.ceil(scaledHeightDouble);
    }

    public int getScaledWidth() { return scaledWidth; }
    public int getScaledHeight() { return scaledHeight; }
    public int getScaleFactor() { return scaleFactor; }
    public double getScaledWidthDouble() { return scaledWidthDouble; }
    public double getScaledHeightDouble() { return scaledHeightDouble; }
}

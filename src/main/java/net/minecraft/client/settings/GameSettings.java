package net.minecraft.client.settings;

/** Mutable client-only options. Persistence and the options screen can bind to this object later. */
public final class GameSettings {
    private int guiScale = 2;
    private int renderDistanceBlocks = 48;
    private float fov = 70f;
    private boolean unicodeFont;
    private boolean vsync = true;

    public int getGuiScale() { return guiScale; }
    public void setGuiScale(int guiScale) { this.guiScale = Math.max(0, Math.min(4, guiScale)); }
    public int getRenderDistanceBlocks() { return renderDistanceBlocks; }
    public float getFov() { return fov; }
    public void setFov(float fov) { this.fov = Math.max(30f, Math.min(110f, fov)); }
    public boolean isUnicodeFont() { return unicodeFont; }
    public void setUnicodeFont(boolean unicodeFont) { this.unicodeFont = unicodeFont; }
    public boolean isVsync() { return vsync; }
    public void setVsync(boolean vsync) { this.vsync = vsync; }

    public void cycleRenderDistance() {
        renderDistanceBlocks = renderDistanceBlocks == 32 ? 48 : renderDistanceBlocks == 48 ? 64 : 32;
    }
}

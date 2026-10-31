package net.minecraft.client.renderer;

import net.minecraft.client.gui.GuiTextures;
import net.minecraft.util.ResourceLocation;

import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * The overworld sky of 1.8.9 (RenderGlobal.renderSky / renderClouds and the
 * sky parts of EntityRenderer and World): a sky-coloured plane overhead and
 * a darker one below the horizon, the sunrise/sunset glow, the textured sun
 * and moon (with its eight phases) turning with the time of day, stars at
 * night, the fog and clear colour, and clouds at y=128 in both the fancy
 * (3D, 12-block cells) and fast (flat) styles.
 *
 * <p>HYPOTHESIS throughout: the formulas and constants are recalled from
 * how 1.8.9 behaves, not copied; no rain, thunder, lightning flashes,
 * blindness, underwater fog or End/Nether skies (deviations.yml DEV-017).
 */
public final class SkyRenderer {
    public static final ResourceLocation SUN = new ResourceLocation("minecraft:textures/environment/sun.png");
    public static final ResourceLocation MOON_PHASES = new ResourceLocation("minecraft:textures/environment/moon_phases.png");
    public static final ResourceLocation CLOUDS = new ResourceLocation("minecraft:textures/environment/clouds.png");
    public static final float CLOUD_HEIGHT = 128f;
    /** The overworld's horizon height (WorldInfo/WorldType.getHorizon). */
    public static final double HORIZON = 63.0;

    private final GuiTextures textures;
    private int skyList = -1, voidList = -1, starList = -1;
    private int cloudTicks;
    /** Smoothed light brightness at the camera, darkening the fog in caves (fogColor1/fogColor2 in vanilla). */
    private float fogLight = 1f, previousFogLight = 1f;

    /** The colours of this frame, computed by {@link #prepare}. */
    private float skyR, skyG, skyB, fogR, fogG, fogB;
    private float angle;
    private float[] sunrise;

    public SkyRenderer(GuiTextures textures) { this.textures = textures; }

    /**
     * The Nether (WorldProviderHell): no sky, sun, stars or clouds; the
     * clock stands at midnight (celestial angle 0.5); the fog is a dull red
     * (0.2, 0.03, 0.03) darkened by the light at the player through a
     * brightness table that never drops below 0.1.
     */
    private boolean nether;
    public void setNether(boolean nether) { this.nether = nether; }
    public boolean isNether() { return nether; }

    /** WorldProviderHell.generateLightBrightnessTable: the overworld curve lifted to a 0.1 floor. */
    public static float netherBrightness(int level) { return brightness(level) * 0.9f + 0.1f; }

    // ---- time of day ------------------------------------------------------------------------

    /** 0 at noon, 0.25 at sunset, 0.5 at midnight, 0.75 at sunrise; eased so days are slightly longer than nights. */
    public static float celestialAngle(long worldTime, float partialTicks) {
        int time = (int) (worldTime % 24000L);
        float f = (time + partialTicks) / 24000f - 0.25f;
        if (f < 0f) f++;
        if (f > 1f) f--;
        float linear = f;
        f = 1f - (float) ((Math.cos(f * Math.PI) + 1.0) / 2.0);
        return linear + (f - linear) / 3f;
    }

    /** The sun's brightness for the lightmap: 0.2 at night to 1 by day (World.getSunBrightness). */
    public static float sunBrightness(float angle) {
        float f = 1f - ((float) Math.cos(angle * Math.PI * 2) * 2f + 0.2f);
        f = clamp(f);
        f = 1f - f;
        return f * 0.8f + 0.2f;
    }

    /** The sky light subtracted at this time, 0 by day to 11 at night (World.calculateSkylightSubtracted). */
    public static int skylightSubtracted(float angle) {
        float f = 1f - ((float) Math.cos(angle * Math.PI * 2) * 2f + 0.5f);
        f = clamp(f);
        return (int) (f * 11f);
    }

    public static float starBrightness(float angle) {
        float f = 1f - ((float) Math.cos(angle * Math.PI * 2) * 2f + 0.25f);
        f = clamp(f);
        return f * f * 0.5f;
    }

    public static int moonPhase(long worldTime) { return (int) (worldTime / 24000L % 8L + 8L) % 8; }

    /** Sky colour at a biome temperature (BiomeGenBase.getSkyColorByTemp scaled by daylight). */
    public static float[] skyColor(float angle, float temperature) {
        float day = clamp((float) Math.cos(angle * Math.PI * 2) * 2f + 0.5f);
        float t = Math.max(-1f, Math.min(1f, temperature / 3f));
        int rgb = java.awt.Color.HSBtoRGB(0.62222224f - t * 0.05f, 0.5f + t * 0.1f, 1f);
        return new float[]{(rgb >> 16 & 255) / 255f * day, (rgb >> 8 & 255) / 255f * day, (rgb & 255) / 255f * day};
    }

    /** The base fog colour before the sunrise and sky blends (WorldProviderSurface.getFogColor). */
    public static float[] baseFogColor(float angle) {
        float day = clamp((float) Math.cos(angle * Math.PI * 2) * 2f + 0.5f);
        return new float[]{0.7529412f * (day * 0.94f + 0.06f), 0.84705883f * (day * 0.94f + 0.06f), day * 0.91f + 0.09f};
    }

    /** RGBA of the sunrise/sunset glow, or null when the sun is well above or below the horizon. */
    public static float[] sunriseColor(float angle) {
        float f = (float) Math.cos(angle * Math.PI * 2);
        if (f < -0.4f || f > 0.4f) return null;
        float g = f / 0.4f * 0.5f + 0.5f;
        float alpha = 1f - (1f - (float) Math.sin(g * Math.PI)) * 0.99f;
        alpha *= alpha;
        return new float[]{g * 0.3f + 0.7f, g * g * 0.7f + 0.2f, 0.2f, alpha};
    }

    public static float[] cloudColor(float angle) {
        float day = clamp((float) Math.cos(angle * Math.PI * 2) * 2f + 0.5f);
        return new float[]{day * 0.9f + 0.1f, day * 0.9f + 0.1f, day * 0.85f + 0.15f};
    }

    /**
     * Fog (and clear) colour: the base fog tinted toward the sunrise when
     * looking at it, pulled toward the sky colour at short render distances,
     * darkened where the camera is dark, and fading to black near the void.
     */
    public static float[] fogColor(float angle, float[] sky, double lookX, double lookY, double lookZ,
                                   int renderDistanceChunks, float lightFactor, double feetY, boolean flat) {
        return fogColor(angle, sky, lookX, lookY, lookZ, renderDistanceChunks, lightFactor, feetY, flat, null);
    }

    /**
     * As above, with the camera inside a liquid ({@code "water"} or
     * {@code "lava"}): the fog becomes dark blue (0.02, 0.02, 0.2) or red
     * (0.6, 0.1, 0) before the darkening and void fade apply.
     */
    public static float[] fogColor(float angle, float[] sky, double lookX, double lookY, double lookZ,
                                   int renderDistanceChunks, float lightFactor, double feetY, boolean flat, String liquid) {
        float[] fog = baseFogColor(angle);
        if (renderDistanceChunks >= 4) {
            double side = Math.sin(angle * Math.PI * 2) > 0 ? -1 : 1;
            float facing = (float) (lookX * side);
            float[] glow = sunriseColor(angle);
            if (facing > 0 && glow != null) {
                facing *= glow[3];
                for (int c = 0; c < 3; c++) fog[c] = fog[c] * (1f - facing) + glow[c] * facing;
            }
        }
        float toSky = 1f - (float) Math.pow(0.25f + 0.75f * renderDistanceChunks / 32f, 0.25);
        for (int c = 0; c < 3; c++) fog[c] = fog[c] + (sky[c] - fog[c]) * toSky;
        if ("water".equals(liquid)) fog = new float[]{0.02f, 0.02f, 0.2f};
        else if ("lava".equals(liquid)) fog = new float[]{0.6f, 0.1f, 0.0f};
        for (int c = 0; c < 3; c++) fog[c] *= lightFactor;
        double voidFactor = feetY * (flat ? 1.0 : 0.03125);
        if (voidFactor < 1.0) {
            if (voidFactor < 0) voidFactor = 0;
            voidFactor *= voidFactor;
            for (int c = 0; c < 3; c++) fog[c] *= (float) voidFactor;
        }
        return fog;
    }

    /** EntityRenderer.updateFogColor in the Nether: the hell fog colour, faded toward the (black) sky with distance. */
    public static float[] netherFog(float[] sky, int renderDistanceChunks, float lightFactor, double feetY, String liquid) {
        float[] fog = {0.2f, 0.03f, 0.03f};
        float toSky = 1f - (float) Math.pow(0.25f + 0.75f * renderDistanceChunks / 32f, 0.25);
        for (int c = 0; c < 3; c++) fog[c] = fog[c] + (sky[c] - fog[c]) * toSky;
        if ("water".equals(liquid)) fog = new float[]{0.02f, 0.02f, 0.2f};
        else if ("lava".equals(liquid)) fog = new float[]{0.6f, 0.1f, 0.0f};
        for (int c = 0; c < 3; c++) fog[c] *= lightFactor;
        double voidFactor = feetY * 0.03125;
        if (voidFactor < 1.0) {
            if (voidFactor < 0) voidFactor = 0;
            voidFactor *= voidFactor;
            for (int c = 0; c < 3; c++) fog[c] *= (float) voidFactor;
        }
        return fog;
    }

    /** Brightness 0-1 of a combined light level (the overworld's lightBrightnessTable). */
    public static float brightness(int level) {
        float darkness = 1f - level / 15f;
        return (1f - darkness) / (darkness * 3f + 1f);
    }

    // ---- per tick and per frame -------------------------------------------------------------

    /**
     * Once per unpaused client tick: drifts the clouds and eases the fog
     * brightness toward the light at the camera's feet.
     */
    public void tick(long worldTime, int skyLight, int blockLight, int renderDistanceChunks) {
        cloudTicks++;
        int light = nether ? blockLight : Math.max(skyLight - skylightSubtracted(celestialAngle(worldTime, 1f)), blockLight);
        float far = renderDistanceChunks / 32f;
        float lit = nether ? netherBrightness(Math.max(0, light)) : brightness(Math.max(0, light));
        float target = lit * (1f - far) + far;
        previousFogLight = fogLight;
        fogLight += (target - fogLight) * 0.1f;
    }

    /** Computes this frame's sky and fog colours; returns the fog colour, which is also the clear colour. */
    public float[] prepare(long worldTime, float partialTicks, float temperature, double lookX, double lookY, double lookZ,
                           int renderDistanceChunks, double feetY, boolean flat, String liquid) {
        angle = nether ? 0.5f : celestialAngle(worldTime, partialTicks);
        sunrise = nether ? null : sunriseColor(angle);
        float[] sky = skyColor(angle, temperature);
        skyR = sky[0]; skyG = sky[1]; skyB = sky[2];
        float light = previousFogLight + (fogLight - previousFogLight) * partialTicks;
        float[] fog = nether ? netherFog(sky, renderDistanceChunks, light, feetY, liquid)
            : fogColor(angle, sky, lookX, lookY, lookZ, renderDistanceChunks, light, feetY, flat, liquid);
        fogR = fog[0]; fogG = fog[1]; fogB = fog[2];
        return fog;
    }

    public float angle() { return angle; }

    /** This frame's sky colour (red, green, blue). */
    public float[] skyColor() { return new float[]{skyR, skyG, skyB}; }

    /**
     * Draws the sky around the camera. The modelview must hold only the
     * camera's rotation; fog is expected to be set up for the sky (0 to the
     * far distance) and the projection's far plane twice the render distance.
     */
    public void renderSky(long worldTime, double eyeY) {
        if (nether) return;
        if (skyList < 0) buildLists();
        net.minecraft.client.shaders.Shaders.use("gbuffers_skybasic");
        glDisable(GL_TEXTURE_2D);
        // Culled as in vanilla: the sky and void planes are one-sided, so the
        // dark void plane is hidden when the eye is below it (under y=63).
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glDepthMask(false);
        glEnable(GL_FOG);
        glColor3f(skyR, skyG, skyB);
        glCallList(skyList);
        if (net.minecraft.client.shaders.Shaders.active()) horizon();
        glDisable(GL_FOG);
        glDisable(GL_ALPHA_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        if (sunrise != null) {
            glShadeModel(GL_SMOOTH);
            glPushMatrix();
            glRotatef(90f, 1f, 0f, 0f);
            glRotatef(Math.sin(angle * Math.PI * 2) < 0 ? 180f : 0f, 0f, 0f, 1f);
            glRotatef(90f, 0f, 0f, 1f);
            glBegin(GL_TRIANGLE_FAN);
            glColor4f(sunrise[0], sunrise[1], sunrise[2], sunrise[3]);
            glVertex3f(0f, 100f, 0f);
            glColor4f(sunrise[0], sunrise[1], sunrise[2], 0f);
            for (int i = 0; i <= 16; i++) {
                float a = i * (float) Math.PI * 2f / 16f;
                float s = (float) Math.sin(a), c = (float) Math.cos(a);
                glVertex3f(s * 120f, c * 120f, -c * 40f * sunrise[3]);
            }
            glEnd();
            glPopMatrix();
        }

        net.minecraft.client.shaders.Shaders.use("gbuffers_skytextured");
        glEnable(GL_TEXTURE_2D);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        glPushMatrix();
        glColor4f(1f, 1f, 1f, 1f);
        glRotatef(-90f, 0f, 1f, 0f);
        glRotatef(angle * 360f, 1f, 0f, 0f);
        float size = 30f;
        textures.bind(SUN);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex3f(-size, 100f, -size);
        glTexCoord2f(1, 0); glVertex3f(size, 100f, -size);
        glTexCoord2f(1, 1); glVertex3f(size, 100f, size);
        glTexCoord2f(0, 1); glVertex3f(-size, 100f, size);
        glEnd();
        size = 20f;
        textures.bind(MOON_PHASES);
        int phase = moonPhase(worldTime);
        int column = phase % 4, row = phase / 4 % 2;
        float u0 = column / 4f, v0 = row / 2f, u1 = (column + 1) / 4f, v1 = (row + 1) / 2f;
        glBegin(GL_QUADS);
        glTexCoord2f(u1, v1); glVertex3f(-size, -100f, size);
        glTexCoord2f(u0, v1); glVertex3f(size, -100f, size);
        glTexCoord2f(u0, v0); glVertex3f(size, -100f, -size);
        glTexCoord2f(u1, v0); glVertex3f(-size, -100f, -size);
        glEnd();
        net.minecraft.client.shaders.Shaders.use("gbuffers_skybasic");
        glDisable(GL_TEXTURE_2D);
        float stars = starBrightness(angle);
        if (stars > 0f) {
            glColor4f(stars, stars, stars, stars);
            glDisable(GL_CULL_FACE);
            glCallList(starList);
            glEnable(GL_CULL_FACE);
        }
        glColor4f(1f, 1f, 1f, 1f);
        glDisable(GL_BLEND);
        glEnable(GL_ALPHA_TEST);
        glEnable(GL_FOG);
        glPopMatrix();

        glDisable(GL_TEXTURE_2D);
        double belowHorizon = eyeY - HORIZON;
        if (belowHorizon < 0) {
            // Under the horizon the view down is closed off in black before the dark plane.
            glColor3f(0f, 0f, 0f);
            glPushMatrix();
            glTranslatef(0f, 12f, 0f);
            glCallList(voidList);
            glPopMatrix();
            float top = -1f, bottom = -(float) (belowHorizon + 65.0);
            glDisable(GL_CULL_FACE);
            glBegin(GL_QUADS);
            glVertex3f(-1, bottom, 1); glVertex3f(1, bottom, 1); glVertex3f(1, top, 1); glVertex3f(-1, top, 1);
            glVertex3f(-1, top, -1); glVertex3f(1, top, -1); glVertex3f(1, bottom, -1); glVertex3f(-1, bottom, -1);
            glVertex3f(1, top, -1); glVertex3f(1, top, 1); glVertex3f(1, bottom, 1); glVertex3f(1, bottom, -1);
            glVertex3f(-1, bottom, -1); glVertex3f(-1, bottom, 1); glVertex3f(-1, top, 1); glVertex3f(-1, top, -1);
            glVertex3f(-1, bottom, -1); glVertex3f(-1, bottom, 1); glVertex3f(1, bottom, 1); glVertex3f(1, bottom, -1);
            glEnd();
            glEnable(GL_CULL_FACE);
        }
        glColor3f(skyR * 0.2f + 0.04f, skyG * 0.2f + 0.04f, skyB * 0.6f + 0.1f);
        glPushMatrix();
        glTranslatef(0f, -(float) (belowHorizon - 16.0), 0f);
        glCallList(voidList);
        glPopMatrix();
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glDepthMask(true);
        glColor4f(1f, 1f, 1f, 1f);
    }

    /**
     * Draws the clouds. The modelview must hold only the camera's rotation;
     * the caller sets the terrain fog and a far plane four times the render
     * distance.
     */
    public void renderClouds(double eyeX, double eyeY, double eyeZ, float partialTicks, boolean fancy) {
        if (nether) return;
        float[] color = cloudColor(angle);
        textures.bind(CLOUDS);
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        double ticks = cloudTicks + partialTicks;
        if (fancy) renderFancyClouds(eyeX, eyeY, eyeZ, ticks, color);
        else renderFastClouds(eyeX, eyeY, eyeZ, ticks, color);
        glColor4f(1f, 1f, 1f, 1f);
        glColorMask(true, true, true, true);
    }

    private void renderFastClouds(double eyeX, double eyeY, double eyeZ, double ticks, float[] color) {
        final int cell = 32, cells = 256 / cell;
        final float scale = 4.8828125E-4f;
        double x = eyeX + ticks * 0.03, z = eyeZ;
        x -= Math.floor(x / 2048.0) * 2048;
        z -= Math.floor(z / 2048.0) * 2048;
        float y = CLOUD_HEIGHT - (float) eyeY + 0.33f;
        float u = (float) (x * scale), v = (float) (z * scale);
        glColor4f(color[0], color[1], color[2], 0.8f);
        glBegin(GL_QUADS);
        for (int i = -cell * cells; i < cell * cells; i += cell) {
            for (int j = -cell * cells; j < cell * cells; j += cell) {
                glTexCoord2f(i * scale + u, (j + cell) * scale + v); glVertex3f(i, y, j + cell);
                glTexCoord2f((i + cell) * scale + u, (j + cell) * scale + v); glVertex3f(i + cell, y, j + cell);
                glTexCoord2f((i + cell) * scale + u, j * scale + v); glVertex3f(i + cell, y, j);
                glTexCoord2f(i * scale + u, j * scale + v); glVertex3f(i, y, j);
            }
        }
        glEnd();
    }

    private void renderFancyClouds(double eyeX, double eyeY, double eyeZ, double ticks, float[] color) {
        final float thickness = 4f, texel = 1f / 256f, inset = 9.765625E-4f;
        final int size = 8, radius = 4;
        double cx = (eyeX + ticks * 0.03) / 12.0, cz = eyeZ / 12.0 + 0.33;
        float y = CLOUD_HEIGHT - (float) eyeY + 0.33f;
        cx -= Math.floor(cx / 2048.0) * 2048;
        cz -= Math.floor(cz / 2048.0) * 2048;
        float baseU = (float) Math.floor(cx) * texel, baseV = (float) Math.floor(cz) * texel;
        float fracX = (float) (cx - Math.floor(cx)), fracZ = (float) (cz - Math.floor(cz));
        float r = color[0], g = color[1], b = color[2];
        glPushMatrix();
        glScalef(12f, 1f, 12f);
        for (int pass = 0; pass < 2; pass++) {
            // First only depth, so the second pass shows each cloud's nearest faces without see-through overlaps.
            if (pass == 0) glColorMask(false, false, false, false);
            else glColorMask(true, true, true, true);
            glBegin(GL_QUADS);
            for (int gx = -radius + 1; gx <= radius; gx++) {
                for (int gz = -radius + 1; gz <= radius; gz++) {
                    float cellX = gx * size, cellZ = gz * size;
                    float x0 = cellX - fracX, z0 = cellZ - fracZ;
                    float u0 = cellX * texel + baseU, v0 = cellZ * texel + baseV;
                    float u1 = (cellX + size) * texel + baseU, v1 = (cellZ + size) * texel + baseV;
                    if (y > -thickness - 1f) {
                        glColor4f(r * 0.7f, g * 0.7f, b * 0.7f, 0.8f);
                        glTexCoord2f(u0, v1); glVertex3f(x0, y, z0 + size);
                        glTexCoord2f(u1, v1); glVertex3f(x0 + size, y, z0 + size);
                        glTexCoord2f(u1, v0); glVertex3f(x0 + size, y, z0);
                        glTexCoord2f(u0, v0); glVertex3f(x0, y, z0);
                    }
                    if (y <= thickness + 1f) {
                        float top = y + thickness - inset;
                        glColor4f(r, g, b, 0.8f);
                        glTexCoord2f(u0, v1); glVertex3f(x0, top, z0 + size);
                        glTexCoord2f(u1, v1); glVertex3f(x0 + size, top, z0 + size);
                        glTexCoord2f(u1, v0); glVertex3f(x0 + size, top, z0);
                        glTexCoord2f(u0, v0); glVertex3f(x0, top, z0);
                    }
                    glColor4f(r * 0.9f, g * 0.9f, b * 0.9f, 0.8f);
                    if (gx > -1) {
                        for (int k = 0; k < size; k++) {
                            float px = x0 + k, u = (cellX + k + 0.5f) * texel + baseU;
                            glTexCoord2f(u, v1); glVertex3f(px, y, z0 + size);
                            glTexCoord2f(u, v1); glVertex3f(px, y + thickness, z0 + size);
                            glTexCoord2f(u, v0); glVertex3f(px, y + thickness, z0);
                            glTexCoord2f(u, v0); glVertex3f(px, y, z0);
                        }
                    }
                    if (gx <= 1) {
                        for (int k = 0; k < size; k++) {
                            float px = x0 + k + 1f - inset, u = (cellX + k + 0.5f) * texel + baseU;
                            glTexCoord2f(u, v1); glVertex3f(px, y, z0 + size);
                            glTexCoord2f(u, v1); glVertex3f(px, y + thickness, z0 + size);
                            glTexCoord2f(u, v0); glVertex3f(px, y + thickness, z0);
                            glTexCoord2f(u, v0); glVertex3f(px, y, z0);
                        }
                    }
                    glColor4f(r * 0.8f, g * 0.8f, b * 0.8f, 0.8f);
                    if (gz > -1) {
                        for (int k = 0; k < size; k++) {
                            float pz = z0 + k, v = (cellZ + k + 0.5f) * texel + baseV;
                            glTexCoord2f(u0, v); glVertex3f(x0, y + thickness, pz);
                            glTexCoord2f(u1, v); glVertex3f(x0 + size, y + thickness, pz);
                            glTexCoord2f(u1, v); glVertex3f(x0 + size, y, pz);
                            glTexCoord2f(u0, v); glVertex3f(x0, y, pz);
                        }
                    }
                    if (gz <= 1) {
                        for (int k = 0; k < size; k++) {
                            float pz = z0 + k + 1f - inset, v = (cellZ + k + 0.5f) * texel + baseV;
                            glTexCoord2f(u0, v); glVertex3f(x0, y + thickness, pz);
                            glTexCoord2f(u1, v); glVertex3f(x0 + size, y + thickness, pz);
                            glTexCoord2f(u1, v); glVertex3f(x0 + size, y, pz);
                            glTexCoord2f(u0, v); glVertex3f(x0, y, pz);
                        }
                    }
                }
            }
            glEnd();
        }
        glPopMatrix();
    }

    // ---- display lists ----------------------------------------------------------------------

    private void buildLists() {
        skyList = glGenLists(3);
        voidList = skyList + 1;
        starList = skyList + 2;
        glNewList(skyList, GL_COMPILE);
        plane(16f, false);
        glEndList();
        glNewList(voidList, GL_COMPILE);
        plane(-16f, true);
        glEndList();
        glNewList(starList, GL_COMPILE);
        glBegin(GL_QUADS);
        float[] vertices = starVertices();
        for (int i = 0; i < vertices.length; i += 3) glVertex3f(vertices[i], vertices[i + 1], vertices[i + 2]);
        glEnd();
        glEndList();
    }

    /**
     * Shader packs colour the sky per pixel, but the sky and void planes leave
     * a gap round the horizon that would show the clear colour as a flat band.
     * Like Iris, close it with a small cylinder and floor around the eye.
     */
    private static void horizon() {
        glDisable(GL_CULL_FACE);
        glBegin(GL_QUADS);
        for (int i = 0; i < 16; i++) {
            double a0 = i * Math.PI / 8, a1 = (i + 1) * Math.PI / 8;
            float x0 = (float) Math.cos(a0) * 16f, z0 = (float) Math.sin(a0) * 16f;
            float x1 = (float) Math.cos(a1) * 16f, z1 = (float) Math.sin(a1) * 16f;
            glVertex3f(x0, 16f, z0); glVertex3f(x1, 16f, z1); glVertex3f(x1, -16f, z1); glVertex3f(x0, -16f, z0);
            glVertex3f(x0, -16f, z0); glVertex3f(x1, -16f, z1); glVertex3f(0f, -16f, 0f); glVertex3f(0f, -16f, 0f);
        }
        glEnd();
        glEnable(GL_CULL_FACE);
    }

    /** A 768x768 grid of 64-block quads at a height, facing down (sky) or up (the plane below the horizon). */
    private static void plane(float y, boolean facingUp) {
        final int step = 64, extent = step * (256 / step + 2);
        glBegin(GL_QUADS);
        for (int x = -extent; x <= extent; x += step) {
            for (int z = -extent; z <= extent; z += step) {
                if (facingUp) {
                    glVertex3f(x + step, y, z); glVertex3f(x, y, z);
                    glVertex3f(x, y, z + step); glVertex3f(x + step, y, z + step);
                } else {
                    glVertex3f(x, y, z); glVertex3f(x + step, y, z);
                    glVertex3f(x + step, y, z + step); glVertex3f(x, y, z + step);
                }
            }
        }
        glEnd();
    }

    /**
     * The star field: random directions on a sphere of radius 100, each a
     * small randomly turned square 0.15-0.25 blocks across (half-size),
     * always the same sky. Returns xyz triples, four vertices per star.
     */
    public static float[] starVertices() {
        Random random = new Random(10842L);
        float[] out = new float[1500 * 12];
        int n = 0;
        for (int i = 0; i < 1500; i++) {
            double x = random.nextFloat() * 2f - 1f, y = random.nextFloat() * 2f - 1f, z = random.nextFloat() * 2f - 1f;
            double half = 0.15f + random.nextFloat() * 0.1f;
            double lengthSquared = x * x + y * y + z * z;
            if (lengthSquared >= 1.0 || lengthSquared <= 0.01) continue;
            double inverse = 1.0 / Math.sqrt(lengthSquared);
            x *= inverse; y *= inverse; z *= inverse;
            double px = x * 100, py = y * 100, pz = z * 100;
            double yawAngle = Math.atan2(x, z), sinYaw = Math.sin(yawAngle), cosYaw = Math.cos(yawAngle);
            double pitchAngle = Math.atan2(Math.sqrt(x * x + z * z), y), sinPitch = Math.sin(pitchAngle), cosPitch = Math.cos(pitchAngle);
            double spin = random.nextDouble() * Math.PI * 2, sinSpin = Math.sin(spin), cosSpin = Math.cos(spin);
            for (int corner = 0; corner < 4; corner++) {
                double a = ((corner & 2) - 1) * half;
                double b = ((corner + 1 & 2) - 1) * half;
                double turnedA = a * cosSpin - b * sinSpin;
                double turnedB = b * cosSpin + a * sinSpin;
                double up = turnedA * sinPitch;
                double out1 = -turnedA * cosPitch;
                double dx = out1 * sinYaw - turnedB * cosYaw;
                double dz = turnedB * sinYaw + out1 * cosYaw;
                out[n++] = (float) (px + dx);
                out[n++] = (float) (py + up);
                out[n++] = (float) (pz + dz);
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    public void destroy() {
        if (skyList >= 0) glDeleteLists(skyList, 3);
        skyList = voidList = starList = -1;
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}

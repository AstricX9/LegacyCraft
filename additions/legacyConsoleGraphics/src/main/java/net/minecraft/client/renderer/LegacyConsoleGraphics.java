package net.minecraft.client.renderer;

import net.minecraft.client.additions.Additions;
import net.minecraft.world.Dimension;

/**
 * Native console-inspired atmosphere and lightmap maths. HYPOTHESIS: an artistic
 * approximation of the 4J appearance, not measured console transfer functions.
 * Native fog and lightmap, with a world colour transfer pass for gamma/contrast.
 * Baked face shade and ambient occlusion remain unchanged.
 */
public final class LegacyConsoleGraphics {
    public static final Profile OFF = new Profile(false, .35f, .9f, .25f, .35f, .2f, 1f);

    private LegacyConsoleGraphics() {}

    /** Immutable frame snapshot: slider changes take effect together on the next frame. */
    public record Profile(boolean enabled, float fogStart, float fogEnd, float brightness,
                          float warmth, float contrast, float waterVisibility) implements net.minecraft.client.additions.api.GraphicsProfile {
        @Override public boolean replacesSky() { return enabled; }
        @Override public boolean replacesShaderPacks() { return enabled; }
        @Override public boolean qualityFog() { return enabled; }
        @Override public double skyFarPlane(float distance) { return Math.max(128.0,distance*2.0); }
        @Override public float visibility(float blindness) { return enabled?1f-clamp(blindness):1f; }
        @Override public float sunriseOpacity(float rain) { return 1f-rain; }
        @Override public float celestialOpacity(float rain,float visibility) { return (1f-rain*.9f)*visibility; }
        @Override public float[] atmosphere(float[] colour,float angle,float rain,float thunder) {
            return LegacyConsoleGraphics.atmosphere(colour,angle,rain,thunder,this);
        }
        @Override public float sunBrightness(float original,float rain,float thunder,Dimension dimension) {
            return enabled && dimension==Dimension.OVERWORLD?original*(1f-rain*.2f)*(1f-thunder*rain*.3f):original;
        }
        @Override public float[] lightmap(float red,float green,float blue,float[] original,float sky,float sun,float gamma,Dimension dimension) {
            if(!enabled) return original;
            float daylight=dimension==Dimension.OVERWORLD?sky:0f;
            return new float[]{lightChannel(red,0,daylight,sun,gamma,this),lightChannel(green,1,daylight,sun,gamma,this),lightChannel(blue,2,daylight,sun,gamma,this)};
        }
        @Override public net.minecraft.client.additions.api.GraphicsProfile.Fog fog(net.minecraft.client.additions.api.GraphicsProfile.Fog original,
            float distance,float fov,float aspect,boolean radial,Dimension dimension,String liquid,float blindness) {
            var fog=LegacyConsoleGraphics.fog(this,fogViewDistance(distance,fov,aspect,radial),dimension,liquid,blindness);
            return new net.minecraft.client.additions.api.GraphicsProfile.Fog(fog.start(),fog.end(),fog.density());
        }
        @Override public void drawSkyBackground(float[] sky,float[] fog,boolean submerged) { ConsoleSky.render(sky,fog,submerged); }
    }

    public static Profile profile(Additions additions) {
        String id = Additions.LEGACY_CONSOLE_GRAPHICS;
        if (!additions.isOn(id)) return OFF;
        return new Profile(true, percent(additions, "fogStart"), percent(additions, "fogEnd"),
            percent(additions, "brightness"), percent(additions, "warmth"), percent(additions, "contrast"),
            percent(additions, "waterVisibility"));
    }

    private static float percent(Additions additions, String key) {
        return (float) additions.number(Additions.LEGACY_CONSOLE_GRAPHICS, key) / 100f;
    }

    /** Density zero means linear fog; positive density means exponential liquid fog. */
    public record Fog(float start, float end, float density) {}

    /**
     * Eye-plane fog reaches the screen sides later than radial fog. Shorten
     * its working distance to hide the circular chunk boundary even at wide FOVs.
     */
    public static float fogViewDistance(float distance, float verticalFov, float aspect, boolean radial) {
        if (radial) return distance;
        double halfWidth = Math.tan(Math.toRadians(verticalFov) * .5) * Math.max(1f, aspect);
        return (float) (distance / Math.sqrt(1 + halfWidth * halfWidth));
    }

    public static Fog fog(Profile profile, float distance, Dimension dimension, String liquid, float blindness) {
        float end = Math.min(distance * clamp(profile.fogEnd()), Math.max(8f, distance - 16f));
        float start = Math.min(distance * clamp(profile.fogStart()), Math.max(0f, end - 8f));
        if (dimension == Dimension.NETHER) {
            start = Math.min(start, distance * .05f);
            end = Math.min(end, Math.min(distance, 192f) * .5f);
        }
        if (blindness > 0f) {
            end += (5f - end) * clamp(blindness);
            start = Math.min(start, end * .25f);
            return new Fog(start, end, 0f);
        }
        if ("lava".equals(liquid)) return new Fog(0f, 1f, 2f);
        if ("water".equals(liquid))
            return new Fog(0f, 1f, .085f / Math.max(.5f, Math.min(1.5f, profile.waterVisibility())));
        return new Fog(start, end, 0f);
    }

    /** Shared grading for sky, fog and clouds: weather desaturates and darkens continuously. */
    public static float[] atmosphere(float[] color, float angle, float rain, float thunder, Profile profile) {
        if (!profile.enabled()) return color;
        rain = clamp(rain);
        thunder = clamp(thunder) * rain;
        float grey = color[0] * .3f + color[1] * .59f + color[2] * .11f;
        float desaturate = rain * .65f + thunder * .2f;
        float dim = (1f - rain * .28f) * (1f - thunder * .4f);
        float warm = profile.warmth() * daylight(angle) * (1f - rain * .85f);
        float[] result = new float[3];
        for (int c = 0; c < 3; c++) {
            float tint = c == 0 ? 1f + warm * .035f : c == 1 ? 1f + warm * .008f : 1f - warm * .055f;
            result[c] = clamp((color[c] + (grey - color[c]) * desaturate) * dim * tint);
        }
        return result;
    }

    /** A smooth sky dome meets the very same colour used for fully fogged terrain. */
    public static float[] horizon(float[] sky, float[] fog, float elevation) {
        float t = clamp(elevation / .45f);
        t = t * t * (3f - 2f * t);
        return new float[]{fog[0] + (sky[0] - fog[0]) * t,
            fog[1] + (sky[1] - fog[1]) * t, fog[2] + (sky[2] - fog[2]) * t};
    }

    /** Console gamma is gentler than Java's fourth-power curve; the black floor is retained. */
    static float lightChannel(float channel, int component, float sky, float sun, float gamma, Profile profile) {
        float inverse = 1f - channel;
        float lifted = 1f - inverse * inverse * inverse;
        // Keep the original default console lighting. User gamma and contrast
        // act on rendered colours, where textures and face shading are present.
        float amount = clamp(gamma * .75f + .0875f);
        float value = channel + (lifted - channel) * amount;
        float warm = profile.warmth() * sky * clamp((sun - .2f) / .8f);
        value *= component == 0 ? 1f + warm * .025f : component == 1 ? 1f : 1f - warm * .06f;
        // Bounded S-curve retains black/white endpoints, unlike a lifted screen overlay.
        value = clamp(value);
        value += .05f * (value - .5f) * value * (1f - value);
        return clamp(value * .96f + .03f);
    }

    /** Gamma exponent shared with the GPU's world colour transfer function. */
    static float gammaExponent(Profile profile) { return 1f / (1f + clamp(profile.brightness()) * .75f); }

    static float contrastAmount(Profile profile) { return clamp(profile.contrast()) * .85f; }

    /** Reference transfer for verification; black/white endpoints and alpha are preserved on the GPU. */
    static float gradeChannel(float channel, Profile profile) {
        if (!profile.enabled()) return channel;
        float value = (float) Math.pow(clamp(channel), gammaExponent(profile));
        float curved = value * value * (3f - 2f * value);
        return value + (curved - value) * contrastAmount(profile);
    }

    private static float daylight(float angle) {
        return clamp((SkyRenderer.sunBrightness(angle) - .2f) / .8f);
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}

package net.minecraft.client.renderer;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Biome grass, foliage and water colours, read from the vanilla colour maps
 * (VERIFIED: the supplied {@code textures/colormap/grass.png} and
 * {@code foliage.png}). A biome's climate indexes the map — hot and dry is
 * yellow, cold is blue-green — and a few biomes override or tint the result.
 * Colours are averaged over the 3x3 columns around a position, as 1.8 does,
 * so biome borders fade instead of cutting.
 */
public final class BiomeColors {
    private final int[] grassMap, foliageMap;

    private BiomeColors(int[] grassMap, int[] foliageMap) {
        this.grassMap = grassMap;
        this.foliageMap = foliageMap;
    }

    public static BiomeColors load(IResourceManager resources) {
        return new BiomeColors(read(resources, "minecraft:textures/colormap/grass.png"),
            read(resources, "minecraft:textures/colormap/foliage.png"));
    }

    private static int[] read(IResourceManager resources, String location) {
        try (InputStream stream = resources.open(new ResourceLocation(location))) {
            BufferedImage image = ImageIO.read(stream);
            int[] pixels = new int[256 * 256];
            image.getRGB(0, 0, 256, 256, pixels, 0, 256);
            return pixels;
        } catch (IOException error) {
            throw new IllegalStateException("Could not read the colour map " + location, error);
        }
    }

    /** Colour-map lookup: drier and hotter moves left/up through the map. */
    private int sample(int[] map, float temperature, float rainfall) {
        float temp = clamp(temperature), rain = clamp(rainfall) * temp;
        int x = (int) ((1 - temp) * 255), y = (int) ((1 - rain) * 255);
        return map[y << 8 | x] & 0xFFFFFF;
    }

    public int grass(Biome biome) {
        if (biome.grassColor() >= 0) return biome.grassColor();
        int color = sample(grassMap, biome.temperature(), biome.rainfall());
        if (biome.grassMix() >= 0) return (color & 0xFEFEFE) + biome.grassMix() >> 1;
        return color;
    }

    public int foliage(Biome biome) {
        return biome.foliageColor() >= 0 ? biome.foliageColor() : sample(foliageMap, biome.temperature(), biome.rainfall());
    }

    public int water(Biome biome) { return biome.waterColor(); }

    /** Colour-map grass colour for a climate (item colours use 0.5 / 1.0, like ColorizerGrass defaults). */
    public int grassColor(float temperature, float rainfall) { return sample(grassMap, temperature, rainfall); }

    /** Colour-map foliage colour for a climate. */
    public int foliageColor(float temperature, float rainfall) { return sample(foliageMap, temperature, rainfall); }

    /** Where blending reads biomes from: the world itself, or a chunk mesher's snapshot of it. */
    @FunctionalInterface
    public interface BiomeLookup { Biome biomeAt(int x, int z); }

    public int grassAt(BiomeLookup world, int x, int z) { return blend(world, x, z, Kind.GRASS); }
    public int foliageAt(BiomeLookup world, int x, int z) { return blend(world, x, z, Kind.FOLIAGE); }
    public int waterAt(BiomeLookup world, int x, int z) { return blend(world, x, z, Kind.WATER); }

    private enum Kind { GRASS, FOLIAGE, WATER }

    /** Average of the nine columns around the position, like vanilla's biome colour blending. */
    private int blend(BiomeLookup world, int x, int z, Kind kind) {
        int red = 0, green = 0, blue = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                Biome biome = world.biomeAt(x + dx, z + dz);
                int color = switch (kind) { case GRASS -> grass(biome); case FOLIAGE -> foliage(biome); case WATER -> water(biome); };
                red += (color >> 16) & 255;
                green += (color >> 8) & 255;
                blue += color & 255;
            }
        }
        return (red / 9) << 16 | (green / 9) << 8 | (blue / 9);
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}

package net.minecraft.client.renderer.entity;

import net.minecraft.block.BlockState;
import net.minecraft.client.gui.GuiTextures;
import net.minecraft.client.renderer.entity.AnimalModels.Cube;
import net.minecraft.client.renderer.entity.AnimalModels.Part;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.Calendar;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * The blocks 1.8.9 draws with tile-entity renderers instead of block models
 * (HYPOTHESIS: geometry and transforms recalled from TileEntityChestRenderer,
 * TileEntityEnderChestRenderer, TileEntitySignRenderer,
 * TileEntityBannerRenderer and TileEntityEndPortalRenderer):
 * <ul>
 *   <li>chests, trapped chests and ender chests (ModelChest, and
 *       ModelLargeChest for two chests side by side), lids closed;</li>
 *   <li>standing and wall signs (ModelSign), without text: LegacyCraft does
 *       not store sign text yet;</li>
 *   <li>standing and wall banners (ModelBanner) swaying in the wind, their
 *       cloth the base colour only: patterns are not stored yet;</li>
 *   <li>the end portal's sixteen drifting star layers.</li>
 * </ul>
 * Mob heads stay with {@link SkullRenderer}. The same models draw the chest
 * and banner items (TileEntityItemStackRenderer).
 */
public final class BlockEntityRenderer {
    private static final ResourceLocation CHEST = tex("chest/normal"), CHEST_DOUBLE = tex("chest/normal_double"),
        TRAPPED = tex("chest/trapped"), TRAPPED_DOUBLE = tex("chest/trapped_double"),
        CHRISTMAS = tex("chest/christmas"), CHRISTMAS_DOUBLE = tex("chest/christmas_double"), ENDER = tex("chest/ender"),
        SIGN = tex("sign"), BANNER_BASE = tex("banner_base"), BANNER_MASK = tex("banner/base"),
        END_SKY = new ResourceLocation("minecraft:textures/environment/end_sky.png"), END_PORTAL = tex("end_portal");

    /** EnumDyeColor's map colours by dye damage (0 black ... 15 white): the banner cloth colours. */
    private static final int[] DYE_COLORS = {0x191919, 0x993333, 0x667F33, 0x664C33, 0x334CB2, 0x7F3FB2, 0x4C7F99,
        0x999999, 0x4C4C4C, 0xF27FA5, 0x7FCC19, 0xE5E533, 0x6699D8, 0xB24CD8, 0xD87F33, 0xFFFFFF};

    private final GuiTextures textures;
    /** TileEntityChestRenderer.isChristmas: the festive chests from 24 to 26 December. */
    private final boolean christmas;
    private final FloatBuffer plane = org.lwjgl.BufferUtils.createFloatBuffer(4);

    public BlockEntityRenderer(GuiTextures textures) {
        this.textures = textures;
        Calendar calendar = Calendar.getInstance();
        christmas = calendar.get(Calendar.MONTH) == Calendar.DECEMBER
            && calendar.get(Calendar.DAY_OF_MONTH) >= 24 && calendar.get(Calendar.DAY_OF_MONTH) <= 26;
    }

    /** Whether this block is drawn here rather than baked into the chunk mesh. */
    public static boolean draws(String block) {
        return switch (block) {
            case "chest", "trapped_chest", "ender_chest", "standing_sign", "wall_sign", "standing_banner", "wall_banner",
                 "end_portal" -> true;
            default -> false;
        };
    }

    /** What the renderer needs to know about the world around a block. */
    public interface Neighbours {
        BlockState state(int x, int y, int z);
    }

    /**
     * Draws one block at its world position. {@code light} multiplies the
     * colour; {@code camera} is the viewer's feet (the end portal's layers
     * are placed relative to it); {@code ticks} is the world's total time.
     */
    public void render(BlockState state, int x, int y, int z, Neighbours world, float[] light, long ticks,
                       float partialTicks, double[] camera) {
        String name = state.block().name().path();
        switch (name) {
            case "chest", "trapped_chest" -> chest(state, x, y, z, world, light);
            case "ender_chest" -> drawChest(ENDER, false, x, y, z, facing(state), 0, light);
            case "standing_sign" -> sign(true, Integer.parseInt(state.get("rotation")), x, y, z, light);
            case "wall_sign" -> sign(false, facing(state), x, y, z, light);
            case "standing_banner" -> banner(true, Integer.parseInt(state.get("rotation")), x, y, z, 0, ticks, partialTicks, light);
            case "wall_banner" -> banner(false, facing(state), x, y, z, 0, ticks, partialTicks, light);
            case "end_portal" -> endPortal(x, y, z, camera);
            default -> { }
        }
    }

    /** Items drawn by these models (TileEntityItemStackRenderer): chests and banners. */
    public static boolean drawsItem(String item) {
        return item.equals("chest") || item.equals("trapped_chest") || item.equals("ender_chest") || item.startsWith("banner");
    }

    /**
     * An item in the unit block at the origin, as TileEntityItemStackRenderer
     * draws it: a single chest facing the default way, or a standing banner
     * of the item's colour ({@code data} is its dye damage).
     */
    public void renderItem(String item, int data, float red, float green, float blue) {
        float[] light = {red, green, blue};
        switch (item) {
            case "chest" -> drawChest(christmas ? CHRISTMAS : CHEST, false, 0, 0, 0, 0, 0, light);
            case "trapped_chest" -> drawChest(christmas ? CHRISTMAS : TRAPPED, false, 0, 0, 0, 0, 0, light);
            case "ender_chest" -> drawChest(ENDER, false, 0, 0, 0, 0, 0, light);
            default -> banner(true, 0, 0, 0, 0, data, 0, 0f, light);
        }
    }

    // ---- chests ----------------------------------------------------------------------------------

    /**
     * TileEntityChest.checkForAdjacentChests: a chest with a chest of its kind
     * to the west or north is drawn by that one; one with a partner to the
     * east or south draws the pair.
     */
    private void chest(BlockState state, int x, int y, int z, Neighbours world, float[] light) {
        var block = state.block();
        if (world.state(x - 1, y, z).block() == block || world.state(x, y, z - 1).block() == block) return;
        int partner = world.state(x + 1, y, z).block() == block ? 1 : world.state(x, y, z + 1).block() == block ? 2 : 0;
        boolean trapped = block.name().path().equals("trapped_chest");
        ResourceLocation texture = partner == 0
            ? christmas ? CHRISTMAS : trapped ? TRAPPED : CHEST
            : christmas ? CHRISTMAS_DOUBLE : trapped ? TRAPPED_DOUBLE : CHEST_DOUBLE;
        drawChest(texture, partner != 0, x, y, z, facing(state), partner, light);
    }

    /**
     * TileEntityChestRenderer.renderTileEntityAt with the lid shut: the model
     * is flipped upright, turned to face 2-5 (north, south, west, east; 0 for
     * an item), shifted onto the pair when the partner lies along the
     * turned model's width.
     */
    private void drawChest(ResourceLocation texture, boolean large, double x, double y, double z, int facing, int partner,
                           float[] light) {
        textures.bind(texture);
        glPushMatrix();
        glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glColor4f(light[0], light[1], light[2], 1f);
        glTranslated(x, y + 1, z + 1);
        glScalef(1f, -1f, -1f);
        glTranslatef(0.5f, 0.5f, 0.5f);
        float angle = switch (facing) { case 2 -> 180f; case 4 -> 90f; case 5 -> -90f; default -> 0f; };
        if (facing == 2 && partner == 1) glTranslatef(1f, 0f, 0f);
        if (facing == 5 && partner == 2) glTranslatef(0f, 0f, -1f);
        glRotatef(angle, 0f, 1f, 0f);
        glTranslatef(-0.5f, -0.5f, -0.5f);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        glDisable(GL_CULL_FACE);
        float previousWidth = AnimalModels.textureWidth, previousHeight = AnimalModels.textureHeight;
        AnimalModels.textureWidth = large ? 128f : 64f;
        AnimalModels.textureHeight = 64f;
        int width = large ? 30 : 14, knobX = large ? 16 : 8;
        // ModelChest/ModelLargeChest: lid, knob and base, each about its own pivot.
        new Part(1, 7, 15, new Cube(0, -5, -14, width, 5, 14, 0, 0, 0)).draw();
        new Part(knobX, 7, 15, new Cube(-1, -2, -15, 2, 4, 1, 0, 0, 0)).draw();
        new Part(1, 6, 1, new Cube(0, 0, 0, width, 10, 14, 0, 0, 19)).draw();
        AnimalModels.textureWidth = previousWidth;
        AnimalModels.textureHeight = previousHeight;
        glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glPopMatrix();
        glColor4f(1f, 1f, 1f, 1f);
    }

    // ---- signs -----------------------------------------------------------------------------------

    /**
     * TileEntitySignRenderer: two-thirds size; a standing sign turned in
     * sixteenths of a turn on its post, a wall sign facing out from the wall
     * (2-5) without the post.
     */
    private void sign(boolean standing, int value, double x, double y, double z, float[] light) {
        float scale = 2f / 3f;
        textures.bind(SIGN);
        glPushMatrix();
        glTranslated(x + 0.5, y + 0.75f * scale, z + 0.5);
        if (standing) {
            glRotatef(-(value * 360f) / 16f, 0f, 1f, 0f);
        } else {
            glRotatef(-wallAngle(value), 0f, 1f, 0f);
            glTranslatef(0f, -0.3125f, -0.4375f);
        }
        glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        glDisable(GL_CULL_FACE);
        glColor4f(light[0], light[1], light[2], 1f);
        glScalef(scale, -scale, -scale);
        // ModelSign on its 64x32 texture: the board, and the post under it.
        new Part(0, 0, 0, new Cube(-12, -14, -1, 24, 12, 2, 0, 0, 0)).draw();
        if (standing) new Part(0, 0, 0, new Cube(-1, -2, -1, 2, 14, 2, 0, 0, 14)).draw();
        glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glPopMatrix();
        glColor4f(1f, 1f, 1f, 1f);
    }

    /** The turn of a wall sign or banner facing 2-5 (north, south, west, east). */
    private static float wallAngle(int facing) {
        return switch (facing) { case 2 -> 180f; case 4 -> 90f; case 5 -> -90f; default -> 0f; };
    }

    // ---- banners ---------------------------------------------------------------------------------

    /**
     * TileEntityBannerRenderer: two-thirds size; the cloth hangs from the
     * cross bar and sways with the world's time, each banner on its own
     * phase from its position. Standing banners turn in sixteenths of a
     * turn on their pole; wall banners hang from the wall facing out.
     */
    private void banner(boolean standing, int value, double x, double y, double z, int color, long ticks, float partialTicks,
                        float[] light) {
        float scale = 2f / 3f;
        glPushMatrix();
        if (standing) {
            glTranslated(x + 0.5, y + 0.75f * scale, z + 0.5);
            glRotatef(-(value * 360f) / 16f, 0f, 1f, 0f);
        } else {
            glTranslated(x + 0.5, y - 0.25f * scale, z + 0.5);
            glRotatef(-wallAngle(value), 0f, 1f, 0f);
            glTranslatef(0f, -0.3125f, -0.4375f);
        }
        float phase = (float) ((long) x * 7 + (long) y * 9 + (long) z * 13) + ticks + partialTicks;
        float sway = (-0.0125f + 0.01f * (float) Math.cos(phase * (float) Math.PI * 0.02f)) * (float) Math.PI;
        int dye = Math.floorMod(color, 16);
        textures.bindGenerated(new ResourceLocation("legacycraft:generated/banner_" + dye), () -> bannerTexture(dye));
        glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        glDisable(GL_CULL_FACE);
        glColor4f(light[0], light[1], light[2], 1f);
        glScalef(scale, -scale, -scale);
        float previousWidth = AnimalModels.textureWidth, previousHeight = AnimalModels.textureHeight;
        AnimalModels.textureWidth = 64f;
        AnimalModels.textureHeight = 64f;
        // ModelBanner: the cloth (pivoting at the bar, 32 pixels up), the pole and the cross bar.
        Part slate = new Part(0, -32, 0, new Cube(-10, 0, -2, 20, 40, 1, 0, 0, 0));
        slate.rx = sway;
        slate.draw();
        if (standing) new Part(0, 0, 0, new Cube(-1, -30, -1, 2, 42, 2, 0, 44, 0)).draw();
        new Part(0, 0, 0, new Cube(-10, -32, -1, 20, 2, 2, 0, 0, 42)).draw();
        AnimalModels.textureWidth = previousWidth;
        AnimalModels.textureHeight = previousHeight;
        glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glPopMatrix();
        glColor4f(1f, 1f, 1f, 1f);
    }

    /**
     * LayeredColorMaskTexture with the one "base" layer: banner_base with
     * every pixel the base mask covers multiplied by the dye's colour and
     * given the mask's opacity.
     */
    private BufferedImage bannerTexture(int dye) {
        BufferedImage base = textures.read(BANNER_BASE), mask = textures.read(BANNER_MASK);
        if (base == null) return null;
        if (mask == null) return base;
        int color = DYE_COLORS[dye];
        int width = Math.min(base.getWidth(), mask.getWidth()), height = Math.min(base.getHeight(), mask.getHeight());
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int coverage = mask.getRGB(px, py) & 255;
                if ((mask.getRGB(px, py) >>> 24) == 0 || coverage == 0) continue;
                int pixel = base.getRGB(px, py);
                int r = (pixel >> 16 & 255) * (color >> 16 & 255) / 255;
                int g = (pixel >> 8 & 255) * (color >> 8 & 255) / 255;
                int b = (pixel & 255) * (color & 255) / 255;
                base.setRGB(px, py, coverage << 24 | r << 16 | g << 8 | b);
            }
        }
        return base;
    }

    // ---- the end portal --------------------------------------------------------------------------

    /**
     * TileEntityEndPortalRenderer: sixteen layers on the plane three quarters
     * up the block. The first is the dark end sky; each later one is the
     * star texture in a random tint, added on top, projected from the
     * viewer's position (eye-linear texture coordinates, so the stars stay
     * put on screen as the portal moves), turned and scaled per layer and
     * drifting slowly with the clock. {@code camera} is the viewer's feet,
     * where the world is drawn from in 1.8.9.
     */
    private void endPortal(int x, int y, int z, double[] camera) {
        float camX = (float) camera[0], camY = (float) camera[1], camZ = (float) camera[2];
        // The modelview here is in world coordinates; 1.8.9 draws tile entities relative to the viewer.
        glPushMatrix();
        glTranslated(camera[0], camera[1], camera[2]);
        double rx = x - camera[0], ry = y - camera[1], rz = z - camera[2];
        glDisable(GL_LIGHTING);
        glDisable(GL_CULL_FACE);
        Random random = new Random(31100L);
        float top = 0.75f;
        for (int layer = 0; layer < 16; layer++) {
            glPushMatrix();
            float depth = 16 - layer, size = 0.0625f, brightness = 1f / (depth + 1f);
            if (layer == 0) {
                textures.bind(END_SKY);
                brightness = 0.1f;
                depth = 65f;
                size = 0.125f;
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            }
            if (layer >= 1) textures.bind(END_PORTAL);
            if (layer == 1) {
                glEnable(GL_BLEND);
                glBlendFunc(GL_ONE, GL_ONE);
                size = 0.5f;
            }
            float plane0 = (float) -(ry + top);
            float near = plane0, far = plane0 + depth;
            float lift = (float) (ry + top) + near / far;
            glTranslatef(camX, lift, camZ);
            glTexGeni(GL_S, GL_TEXTURE_GEN_MODE, GL_OBJECT_LINEAR);
            glTexGeni(GL_T, GL_TEXTURE_GEN_MODE, GL_OBJECT_LINEAR);
            glTexGeni(GL_R, GL_TEXTURE_GEN_MODE, GL_OBJECT_LINEAR);
            glTexGeni(GL_Q, GL_TEXTURE_GEN_MODE, GL_EYE_LINEAR);
            glTexGenfv(GL_S, GL_OBJECT_PLANE, plane(1, 0, 0, 0));
            glTexGenfv(GL_T, GL_OBJECT_PLANE, plane(0, 0, 1, 0));
            glTexGenfv(GL_R, GL_OBJECT_PLANE, plane(0, 0, 0, 1));
            glTexGenfv(GL_Q, GL_EYE_PLANE, plane(0, 1, 0, 0));
            glEnable(GL_TEXTURE_GEN_S);
            glEnable(GL_TEXTURE_GEN_T);
            glEnable(GL_TEXTURE_GEN_R);
            glEnable(GL_TEXTURE_GEN_Q);
            glPopMatrix();
            glMatrixMode(GL_TEXTURE);
            glPushMatrix();
            glLoadIdentity();
            glTranslatef(0f, (System.currentTimeMillis() % 700000L) / 700000f, 0f);
            glScalef(size, size, size);
            glTranslatef(0.5f, 0.5f, 0f);
            glRotatef((layer * layer * 4321 + layer * 9) * 2f, 0f, 0f, 1f);
            glTranslatef(-0.5f, -0.5f, 0f);
            glTranslatef(-camX, -camZ, -camY);
            glTranslatef(0f, 0f, -camY);
            float red = (random.nextFloat() * 0.5f + 0.1f) * brightness;
            float green = (random.nextFloat() * 0.4f + 0.4f) * brightness;
            float blue = (random.nextFloat() * 0.5f + 0.5f) * brightness;
            if (layer == 0) red = green = blue = brightness;
            glColor4f(red, green, blue, 1f);
            glBegin(GL_QUADS);
            glVertex3d(rx, ry + top, rz);
            glVertex3d(rx, ry + top, rz + 1);
            glVertex3d(rx + 1, ry + top, rz + 1);
            glVertex3d(rx + 1, ry + top, rz);
            glEnd();
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
        }
        glDisable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_TEXTURE_GEN_S);
        glDisable(GL_TEXTURE_GEN_T);
        glDisable(GL_TEXTURE_GEN_R);
        glDisable(GL_TEXTURE_GEN_Q);
        glColor4f(1f, 1f, 1f, 1f);
        glPopMatrix();
    }

    private FloatBuffer plane(float a, float b, float c, float d) {
        plane.clear();
        plane.put(a).put(b).put(c).put(d).flip();
        return plane;
    }

    private static int facing(BlockState state) {
        return switch (state.get("facing")) { case "north" -> 2; case "south" -> 3; case "west" -> 4; case "east" -> 5; default -> 3; };
    }

    private static ResourceLocation tex(String path) { return new ResourceLocation("minecraft:textures/entity/" + path + ".png"); }
}

package net.minecraft.client.renderer.entity;

import net.minecraft.client.gui.GuiTextures;
import net.minecraft.client.renderer.entity.AnimalModels.Cube;
import net.minecraft.client.renderer.entity.AnimalModels.Part;
import net.minecraft.util.ResourceLocation;

import static org.lwjgl.opengl.GL11.*;

/**
 * Mob heads (1.8.9 TileEntitySkullRenderer with ModelSkeletonHead and
 * ModelHumanoidHead, HYPOTHESIS: recalled): an 8x8x8 head on the mob's own
 * texture; zombie and player heads use the 64x64 humanoid texture and add
 * the hat layer 0.25 pixels out. On the floor the head sits in the middle
 * of the block turned in sixteenths of a turn; on a wall it hangs a
 * quarter block up, 0.26 blocks from the wall, facing out.
 */
public final class SkullRenderer {
    private SkullRenderer() {}

    /** SkullType 0 skeleton, 1 wither skeleton, 2 zombie, 3 player (Steve), 4 creeper. */
    public static final ResourceLocation[] TEXTURES = {
        new ResourceLocation("minecraft:textures/entity/skeleton/skeleton.png"),
        new ResourceLocation("minecraft:textures/entity/skeleton/wither_skeleton.png"),
        new ResourceLocation("minecraft:textures/entity/zombie/zombie.png"),
        new ResourceLocation("minecraft:textures/entity/steve.png"),
        new ResourceLocation("minecraft:textures/entity/creeper/creeper.png")};

    /** The item name of each head (ItemSkull's skullTypes). */
    public static final String[] ITEMS = {"skull_skeleton", "skull_wither", "skull_zombie", "skull_char", "skull_creeper"};

    public static int clampType(int type) { return type < 0 || type >= TEXTURES.length ? 0 : type; }

    /** The head model, turned by {@code yaw} and {@code pitch} (degrees), in the current model space. */
    public static void head(int type, float yaw, float pitch) {
        boolean humanoid = type == 2 || type == 3;
        AnimalModels.textureWidth = 64f;
        AnimalModels.textureHeight = humanoid ? 64f : 32f;
        Part head = new Part(0, 0, 0, new Cube(-4, -8, -4, 8, 8, 8, 0, 0, 0, false));
        head.ry = (float) Math.toRadians(yaw);
        head.rx = (float) Math.toRadians(pitch);
        head.draw();
        if (humanoid) {
            Part hat = new Part(0, 0, 0, new Cube(-4, -8, -4, 8, 8, 8, 0.25f, 32, 0, false));
            hat.ry = head.ry;
            hat.rx = head.rx;
            hat.draw();
        }
        AnimalModels.textureWidth = 64f;
        AnimalModels.textureHeight = 32f;
    }

    /**
     * TileEntitySkullRenderer.renderSkull: a head at block corner (x, y, z)
     * with block facing 1 (floor, turned {@code rotation} degrees) or 2-5
     * (north, south, west, east walls), coloured by {@code light}.
     */
    public static void render(GuiTextures textures, double x, double y, double z, int facing, float rotation, int type, float[] light) {
        type = clampType(type);
        textures.bind(TEXTURES[type]);
        glPushMatrix();
        glDisable(GL_CULL_FACE);
        switch (facing) {
            case 2 -> glTranslated(x + 0.5, y + 0.25, z + 0.74);
            case 3 -> { glTranslated(x + 0.5, y + 0.25, z + 0.26); rotation = 180f; }
            case 4 -> { glTranslated(x + 0.74, y + 0.25, z + 0.5); rotation = 270f; }
            case 5 -> { glTranslated(x + 0.26, y + 0.25, z + 0.5); rotation = 90f; }
            default -> glTranslated(x + 0.5, y, z + 0.5);
        }
        glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glScalef(-1f, -1f, 1f);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        glColor4f(light[0], light[1], light[2], 1f);
        head(type, rotation, 0f);
        glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glPopMatrix();
        glColor4f(1f, 1f, 1f, 1f);
    }

    /** The head type an item stands for (skull_wither -> 1), or -1. */
    public static int typeOf(String itemPath) {
        for (int i = 0; i < ITEMS.length; i++) if (ITEMS[i].equals(itemPath)) return i;
        return -1;
    }
}

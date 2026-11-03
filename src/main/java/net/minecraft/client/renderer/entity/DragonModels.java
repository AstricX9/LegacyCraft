package net.minecraft.client.renderer.entity;

import net.minecraft.client.renderer.entity.AnimalModels.Cube;
import net.minecraft.client.renderer.entity.AnimalModels.Part;
import net.minecraft.entity.boss.EntityDragon;

import static org.lwjgl.opengl.GL11.*;

/**
 * ModelDragon (256x256) and ModelEnderCrystal (64x32) from 1.8.9, HYPOTHESIS:
 * recalled. The dragon's neck and tail are one neck segment drawn again at
 * each point along the trail of its past positions.
 */
final class DragonModels {
    private DragonModels() {}

    private static final float F = -16f;
    // The -16 shifts only the head's depth (z), as in ModelDragon.
    private static final Part HEAD = new Part(0, 0, 0,
        new Cube(-6, -1, -8 + F, 12, 5, 16, 0, 176, 44),
        new Cube(-8, -8, 6 + F, 16, 16, 16, 0, 112, 30),
        new Cube(-1 - 4, -12, 12 + F, 2, 4, 6, 0, 0, 0, true),
        new Cube(-1 - 4, -3, -6 + F, 2, 2, 4, 0, 112, 0, true),
        new Cube(-1 + 4, -12, 12 + F, 2, 4, 6, 0, 0, 0),
        new Cube(-1 + 4, -3, -6 + F, 2, 2, 4, 0, 112, 0));
    private static final Part JAW = new Part(0, 4, 8 + F, new Cube(-6, 0, -16, 12, 4, 16, 0, 176, 65));
    private static final Part NECK = new Part(0, 0, 0,
        new Cube(-5, -5, -5, 10, 10, 10, 0, 192, 104),
        new Cube(-1, -9, -3, 2, 4, 6, 0, 48, 0));
    private static final Part BODY = new Part(0, 4, 8,
        new Cube(-12, 0, -16, 24, 24, 64, 0, 0, 0),
        new Cube(-1, -6, -10, 2, 6, 12, 0, 220, 53),
        new Cube(-1, -6, 10, 2, 6, 12, 0, 220, 53),
        new Cube(-1, -6, 30, 2, 6, 12, 0, 220, 53));
    private static final Part WING = new Part(-12, 5, 2,
        new Cube(-56, -4, -4, 56, 8, 8, 0, 112, 88),
        new Cube(-56, 0, 2, 56, 0, 56, 0, -56, 88));
    private static final Part WING_TIP = new Part(-56, 0, 0,
        new Cube(-56, -2, -2, 56, 4, 4, 0, 112, 136),
        new Cube(-56, 0, 2, 56, 0, 56, 0, -56, 144));
    private static final Part FRONT_LEG = new Part(-12, 20, 2, new Cube(-4, -4, -4, 8, 24, 8, 0, 112, 104));
    private static final Part FRONT_LEG_TIP = new Part(0, 20, -1, new Cube(-3, -1, -3, 6, 24, 6, 0, 226, 138));
    private static final Part FRONT_FOOT = new Part(0, 23, 0, new Cube(-4, 0, -12, 8, 4, 16, 0, 144, 104));
    private static final Part REAR_LEG = new Part(-16, 16, 42, new Cube(-8, -4, -8, 16, 32, 16, 0, 0, 0));
    private static final Part REAR_LEG_TIP = new Part(0, 32, -4, new Cube(-6, -2, 0, 12, 32, 12, 0, 196, 0));
    private static final Part REAR_FOOT = new Part(0, 31, 4, new Cube(-9, 0, -20, 18, 6, 24, 0, 112, 0));
    static {
        HEAD.children.add(JAW);
        WING.children.add(WING_TIP);
        FRONT_LEG.children.add(FRONT_LEG_TIP);
        FRONT_LEG_TIP.children.add(FRONT_FOOT);
        REAR_LEG.children.add(REAR_LEG_TIP);
        REAR_LEG_TIP.children.add(REAR_FOOT);
    }

    private static float wrap(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return (float) d;
    }

    private static final float RAD = (float) (Math.PI / 180.0), TAU = (float) (Math.PI * 2.0);

    /** ModelDragon.render: the model in the renderer's flipped, pixel-scaled space. */
    static void dragon(EntityDragon dragon, float partialTicks) {
        float texW = AnimalModels.textureWidth, texH = AnimalModels.textureHeight;
        AnimalModels.textureWidth = 256f;
        AnimalModels.textureHeight = 256f;
        glPushMatrix();
        float anim = dragon.prevAnimTime + (dragon.animTime - dragon.prevAnimTime) * partialTicks;
        JAW.rx = (float) (Math.sin(anim * TAU) + 1.0) * 0.2f;
        float bob = (float) (Math.sin(anim * TAU - 1f) + 1.0);
        bob = (bob * bob + bob * 2f) * 0.05f;
        glTranslatef(0f, bob - 2f, -3f);
        glRotatef(bob * 2f, 1f, 0f, 0f);
        float py = 20f, pz = -12f, px = 0f;
        float scale = 1.5f;
        double[] base = dragon.movementOffsets(6, partialTicks);
        float bodyRoll = wrap(dragon.movementOffsets(5, partialTicks)[0] - dragon.movementOffsets(10, partialTicks)[0]);
        float bodyYaw = wrap(dragon.movementOffsets(5, partialTicks)[0] + bodyRoll / 2f);
        float phase = anim * TAU;
        for (int i = 0; i < 5; i++) {
            double[] here = dragon.movementOffsets(5 - i, partialTicks);
            float wave = (float) Math.cos(i * 0.45f + phase) * 0.15f;
            NECK.ry = wrap(here[0] - base[0]) * RAD * scale;
            NECK.rx = wave + (float) (here[1] - base[1]) * RAD * scale * 5f;
            NECK.rz = -wrap(here[0] - bodyYaw) * RAD * scale;
            NECK.px = px; NECK.py = py; NECK.pz = pz;
            py += (float) (Math.sin(NECK.rx) * 10.0);
            pz -= (float) (Math.cos(NECK.ry) * Math.cos(NECK.rx) * 10.0);
            px -= (float) (Math.sin(NECK.ry) * Math.cos(NECK.rx) * 10.0);
            NECK.draw();
        }
        HEAD.px = px; HEAD.py = py; HEAD.pz = pz;
        double[] head = dragon.movementOffsets(0, partialTicks);
        HEAD.ry = wrap(head[0] - base[0]) * RAD;
        HEAD.rx = (float) (head[1] - base[1]) * RAD * scale * 5f;
        HEAD.rz = -wrap(head[0] - bodyYaw) * RAD;
        HEAD.draw();

        glPushMatrix();
        glTranslatef(0f, 1f, 0f);
        glRotatef(-bodyRoll * scale, 0f, 0f, 1f);
        glTranslatef(0f, -1f, 0f);
        BODY.draw();
        for (int side = 0; side < 2; side++) {
            glEnable(GL_CULL_FACE);
            float flap = anim * TAU;
            WING.rx = 0.125f - (float) Math.cos(flap) * 0.2f;
            WING.ry = 0.25f;
            WING.rz = (float) (Math.sin(flap) + 0.125) * 0.8f;
            WING_TIP.rz = -((float) (Math.sin(flap + 2f) + 0.5)) * 0.75f;
            REAR_LEG.rx = 1f + bob * 0.1f;
            REAR_LEG_TIP.rx = 0.5f + bob * 0.1f;
            REAR_FOOT.rx = 0.75f + bob * 0.1f;
            FRONT_LEG.rx = 1.3f + bob * 0.1f;
            FRONT_LEG_TIP.rx = -0.5f - bob * 0.1f;
            FRONT_FOOT.rx = 0.75f + bob * 0.1f;
            WING.draw();
            FRONT_LEG.draw();
            REAR_LEG.draw();
            glScalef(-1f, 1f, 1f);
            if (side == 0) glCullFace(GL_FRONT);
        }
        glPopMatrix();
        glCullFace(GL_BACK);
        glDisable(GL_CULL_FACE);

        float wave = 0f;
        py = 10f; pz = 60f; px = 0f;
        base = dragon.movementOffsets(11, partialTicks);
        for (int k = 0; k < 12; k++) {
            double[] here = dragon.movementOffsets(12 + k, partialTicks);
            wave += (float) (Math.sin(k * 0.45f + phase) * 0.05);
            NECK.ry = (wrap(here[0] - base[0]) * scale + 180f) * RAD;
            NECK.rx = wave + (float) (here[1] - base[1]) * RAD * scale * 5f;
            NECK.rz = wrap(here[0] - bodyYaw) * RAD * scale;
            NECK.px = px; NECK.py = py; NECK.pz = pz;
            py += (float) (Math.sin(NECK.rx) * 10.0);
            pz -= (float) (Math.cos(NECK.ry) * Math.cos(NECK.rx) * 10.0);
            px -= (float) (Math.sin(NECK.ry) * Math.cos(NECK.rx) * 10.0);
            NECK.draw();
        }
        glPopMatrix();
        AnimalModels.textureWidth = texW;
        AnimalModels.textureHeight = texH;
    }

    private static final Part CRYSTAL_CUBE = new Part(0, 0, 0, new Cube(-4, -4, -4, 8, 8, 8, 0, 32, 0));
    private static final Part CRYSTAL_GLASS = new Part(0, 0, 0, new Cube(-4, -4, -4, 8, 8, 8, 0, 0, 0));
    private static final Part CRYSTAL_BASE = new Part(0, 0, 0, new Cube(-6, 0, -6, 12, 4, 12, 0, 0, 16));

    /** ModelEnderCrystal.render: two glass cubes and the core spinning by {@code spin} degrees, raised by {@code bob}. */
    static void crystal(float spin, float bob, boolean showBase) {
        float texW = AnimalModels.textureWidth, texH = AnimalModels.textureHeight;
        AnimalModels.textureWidth = 64f;
        AnimalModels.textureHeight = 32f;
        glPushMatrix();
        glScalef(2f, 2f, 2f);
        glTranslatef(0f, -0.5f, 0f);
        if (showBase) CRYSTAL_BASE.draw();
        glRotatef(spin, 0f, 1f, 0f);
        glTranslatef(0f, 0.8f + bob, 0f);
        glRotatef(60f, 0.7071f, 0f, 0.7071f);
        CRYSTAL_GLASS.draw();
        glScalef(0.875f, 0.875f, 0.875f);
        glRotatef(60f, 0.7071f, 0f, 0.7071f);
        glRotatef(spin, 0f, 1f, 0f);
        CRYSTAL_GLASS.draw();
        glScalef(0.875f, 0.875f, 0.875f);
        glRotatef(60f, 0.7071f, 0f, 0.7071f);
        glRotatef(spin, 0f, 1f, 0f);
        CRYSTAL_CUBE.draw();
        glPopMatrix();
        AnimalModels.textureWidth = texW;
        AnimalModels.textureHeight = texH;
    }
}

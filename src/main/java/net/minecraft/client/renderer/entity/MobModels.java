package net.minecraft.client.renderer.entity;

import net.minecraft.client.renderer.entity.AnimalModels.Cube;
import net.minecraft.client.renderer.entity.AnimalModels.Part;
import net.minecraft.client.renderer.entity.AnimalModels.Pose;

import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * The Nether's mob models and the skeleton (1.8.9 ModelZombie, ModelSkeleton,
 * ModelBlaze, ModelGhast and ModelMagmaCube, HYPOTHESIS: recalled geometry).
 */
public final class MobModels {
    private MobModels() {}

    private static Cube cube(float x, float y, float z, float w, float h, float d, float grow, int u, int v, boolean mirror) {
        return new Cube(x, y, z, w, h, d, grow, u, v, mirror);
    }

    /** Where a biped's right arm ends up, for the held item (ModelBiped.postRenderArm). */
    public record Arm(float px, float py, float pz, float rx, float ry, float rz) {
        public void transform() {
            glTranslatef(px / 16f, py / 16f, pz / 16f);
            if (rz != 0) glRotatef((float) Math.toDegrees(rz), 0, 0, 1);
            if (ry != 0) glRotatef((float) Math.toDegrees(ry), 0, 1, 0);
            if (rx != 0) glRotatef((float) Math.toDegrees(rx), 1, 0, 0);
        }
    }

    /**
     * ModelZombie on a 64x64 texture (the zombie pigman), or ModelSkeleton's
     * thin limbs on 64x32: biped legs swinging, the head turned, and the
     * arms held out in front swaying with age (zombie), or the skeleton's
     * arms, raised to aim when it has a target. Returns the right arm.
     */
    public static Arm biped(Pose pose, float age, float swingProgress, boolean skeleton, boolean aiming, boolean child) {
        AnimalModels.textureWidth = 64f;
        AnimalModels.textureHeight = skeleton ? 32f : 64f;
        Part head = new Part(0, 0, 0, cube(-4, -8, -4, 8, 8, 8, 0, 0, 0, false));
        Part hat = new Part(0, 0, 0, cube(-4, -8, -4, 8, 8, 8, 0.5f, 32, 0, false));
        Part body = new Part(0, 0, 0, cube(-4, 0, -2, 8, 12, 4, 0, 16, 16, false));
        Part rightArm, leftArm, rightLeg, leftLeg;
        if (skeleton) {
            rightArm = new Part(-5, 2, 0, cube(-1, -2, -1, 2, 12, 2, 0, 40, 16, false));
            leftArm = new Part(5, 2, 0, cube(-1, -2, -1, 2, 12, 2, 0, 40, 16, true));
            rightLeg = new Part(-2, 12, 0, cube(-1, 0, -1, 2, 12, 2, 0, 0, 16, false));
            leftLeg = new Part(2, 12, 0, cube(-1, 0, -1, 2, 12, 2, 0, 0, 16, true));
        } else {
            rightArm = new Part(-5, 2, 0, cube(-3, -2, -2, 4, 12, 4, 0, 40, 16, false));
            leftArm = new Part(5, 2, 0, cube(-1, -2, -2, 4, 12, 4, 0, 40, 16, true));
            rightLeg = new Part(-1.9f, 12, 0, cube(-2, 0, -2, 4, 12, 4, 0, 0, 16, false));
            leftLeg = new Part(1.9f, 12, 0, cube(-2, 0, -2, 4, 12, 4, 0, 0, 16, true));
        }
        float yaw = (float) Math.toRadians(pose.headYaw()), pitch = (float) Math.toRadians(pose.pitch());
        head.ry = hat.ry = yaw;
        head.rx = hat.rx = pitch;
        rightLeg.rx = (float) Math.cos(pose.swing() * 0.6662f) * 1.4f * pose.amount();
        leftLeg.rx = (float) Math.cos(pose.swing() * 0.6662f + Math.PI) * 1.4f * pose.amount();
        // ModelBiped's walking arms (holding an item lowers the right one a little).
        rightArm.rx = (float) Math.cos(pose.swing() * 0.6662f + Math.PI) * 2f * pose.amount() * 0.5f;
        leftArm.rx = (float) Math.cos(pose.swing() * 0.6662f) * 2f * pose.amount() * 0.5f;
        rightArm.rx = rightArm.rx * 0.5f - (float) Math.PI / 10f;
        if (!skeleton || aiming) {
            // ModelZombie (and ModelSkeleton aiming): both arms straight out, swaying with age.
            float f = (float) Math.sin(swingProgress * Math.PI);
            float f1 = (float) Math.sin((1f - (1f - swingProgress) * (1f - swingProgress)) * Math.PI);
            rightArm.rz = 0;
            leftArm.rz = 0;
            rightArm.ry = -(0.1f - f * 0.6f);
            leftArm.ry = 0.1f - f * 0.6f;
            rightArm.rx = -(float) Math.PI / 2f;
            leftArm.rx = -(float) Math.PI / 2f;
            rightArm.rx -= f * 1.2f - f1 * 0.4f;
            leftArm.rx -= f * 1.2f - f1 * 0.4f;
            rightArm.rz += (float) Math.cos(age * 0.09f) * 0.05f + 0.05f;
            leftArm.rz -= (float) Math.cos(age * 0.09f) * 0.05f + 0.05f;
            rightArm.rx += (float) Math.sin(age * 0.067f) * 0.05f;
            leftArm.rx -= (float) Math.sin(age * 0.067f) * 0.05f;
        }
        if (child) {
            // ModelBiped.render for children: the head at 3/4 size, the rest at half.
            glPushMatrix();
            glScalef(0.75f, 0.75f, 0.75f);
            glTranslatef(0f, 16f / 16f, 0f);
            head.draw();
            if (!skeleton) hat.draw();
            glPopMatrix();
            glPushMatrix();
            glScalef(0.5f, 0.5f, 0.5f);
            glTranslatef(0f, 24f / 16f, 0f);
            body.draw();
            rightArm.draw();
            leftArm.draw();
            rightLeg.draw();
            leftLeg.draw();
            glPopMatrix();
        } else {
            head.draw();
            if (!skeleton) hat.draw();
            body.draw();
            rightArm.draw();
            leftArm.draw();
            rightLeg.draw();
            leftLeg.draw();
        }
        AnimalModels.textureWidth = 64f;
        AnimalModels.textureHeight = 32f;
        return new Arm(rightArm.px, rightArm.py, rightArm.pz, rightArm.rx, rightArm.ry, rightArm.rz);
    }

    /** ModelBlaze: the head and twelve rods circling in three rings at different speeds. */
    public static void blaze(float age, float headYaw, float pitch) {
        Part head = new Part(0, 0, 0, cube(-4, -4, -4, 8, 8, 8, 0, 0, 0, false));
        head.ry = (float) Math.toRadians(headYaw);
        head.rx = (float) Math.toRadians(pitch);
        head.draw();
        float f = age * (float) Math.PI * -0.1f;
        for (int i = 0; i < 4; i++) {
            rod(-2f + (float) Math.cos((i * 2 + age) * 0.25f), (float) Math.cos(f) * 9f, (float) Math.sin(f) * 9f);
            f++;
        }
        f = (float) Math.PI / 4f + age * (float) Math.PI * 0.03f;
        for (int i = 4; i < 8; i++) {
            rod(2f + (float) Math.cos((i * 2 + age) * 0.25f), (float) Math.cos(f) * 7f, (float) Math.sin(f) * 7f);
            f++;
        }
        f = 0.47123894f + age * (float) Math.PI * -0.05f;
        for (int i = 8; i < 12; i++) {
            rod(11f + (float) Math.cos((i * 1.5f + age) * 0.5f), (float) Math.cos(f) * 5f, (float) Math.sin(f) * 5f);
            f++;
        }
    }

    private static void rod(float y, float x, float z) {
        new Part(x, y, z, cube(0, 0, 0, 2, 8, 2, 0, 0, 16, false)).draw();
    }

    /** ModelGhast's tentacle lengths (8-14 pixels), fixed by Random(1660). */
    private static final int[] TENTACLES = new int[9];
    static {
        Random random = new Random(1660L);
        for (int i = 0; i < 9; i++) TENTACLES[i] = random.nextInt(7) + 8;
    }

    /** ModelGhast: the 16-pixel body and nine dangling, waving tentacles. */
    public static void ghast(float age) {
        new Part(0, 8, 0, cube(-8, -8, -8, 16, 16, 16, 0, 0, 0, false)).draw();
        for (int i = 0; i < 9; i++) {
            float x = ((i % 3 - (i / 3 % 2) * 0.5f + 0.25f) / 2f * 2f - 1f) * 5f;
            float z = (i / 3 / 2f * 2f - 1f) * 5f;
            Part tentacle = new Part(x, 15, z, cube(-1, 0, -1, 2, TENTACLES[i], 2, 0, 0, 0, false));
            tentacle.rx = 0.2f * (float) Math.sin(age * 0.3f + i) + 0.4f;
            tentacle.draw();
        }
    }

    /**
     * ModelWither (64x64, every box grown by {@code grow}: 0.5 for the
     * armour aura): the shoulder bar, the swaying spine with its three ribs,
     * the tail below it, the big middle head turned by the look and the two
     * side heads turned to their own targets ({@code sideYaw} relative to the
     * body, degrees).
     */
    public static void wither(float age, float headYaw, float pitch, float[] sideYaw, float[] sidePitch, float grow) {
        AnimalModels.textureWidth = 64f;
        AnimalModels.textureHeight = 64f;
        new Part(0, 0, 0, cube(-10, 3.9f, -0.5f, 20, 3, 3, grow, 0, 16, false)).draw();
        float f = (float) Math.cos(age * 0.1f);
        Part spine = new Part(-2, 6.9f, -0.5f, cube(0, 0, 0, 3, 10, 3, grow, 0, 22, false),
            cube(-4, 1.5f, 0.5f, 11, 2, 2, grow, 24, 22, false), cube(-4, 4, 0.5f, 11, 2, 2, grow, 24, 22, false),
            cube(-4, 6.5f, 0.5f, 11, 2, 2, grow, 24, 22, false));
        spine.rx = (0.065f + 0.05f * f) * (float) Math.PI;
        spine.draw();
        Part tail = new Part(-2, 6.9f + (float) Math.cos(spine.rx) * 10f, -0.5f + (float) Math.sin(spine.rx) * 10f,
            cube(0, 0, 0, 3, 6, 3, grow, 12, 22, false));
        tail.rx = (0.265f + 0.1f * f) * (float) Math.PI;
        tail.draw();
        Part middle = new Part(0, 0, 0, cube(-4, -4, -4, 8, 8, 8, grow, 0, 0, false));
        middle.ry = (float) Math.toRadians(headYaw);
        middle.rx = (float) Math.toRadians(pitch);
        middle.draw();
        float[] pivots = {-8f, 10f};
        for (int i = 0; i < 2; i++) {
            Part side = new Part(pivots[i], 4, 0, cube(-4, -4, -4, 6, 6, 6, grow, 32, 0, false));
            side.ry = (float) Math.toRadians(sideYaw[i]);
            side.rx = (float) Math.toRadians(sidePitch[i]);
            side.draw();
        }
        AnimalModels.textureWidth = 64f;
        AnimalModels.textureHeight = 32f;
    }

    /** ModelMagmaCube: eight 1-pixel slices that spread apart as it squishes, around a glowing core. */
    public static void magmaCube(float squish) {
        if (squish < 0f) squish = 0f;
        new Part(0, 0, 0, cube(-2, 18, -2, 4, 4, 4, 0, 0, 16, false)).draw();
        for (int i = 0; i < 8; i++) {
            int u = 0, v = i;
            if (i == 2) { u = 24; v = 10; }
            else if (i == 3) { u = 24; v = 19; }
            new Part(0, -(4 - i) * squish * 1.7f, 0, cube(-4, 16 + i, -4, 8, 1, 8, 0, u, v, false)).draw();
        }
    }
}

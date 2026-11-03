package net.minecraft.client.renderer.entity;

import net.minecraft.client.renderer.PlayerModel;

import static org.lwjgl.opengl.GL11.*;

/**
 * The farm animal models (1.8.9 ModelQuadruped, ModelPig, ModelCow,
 * ModelSheep1/2 and ModelChicken, HYPOTHESIS: recalled geometry), in model
 * pixels on 64x32 textures. Each part turns about its pivot in Z, Y, X
 * order like ModelRenderer. Babies draw their head full size (moved by the
 * model's child offsets) and the rest at half size.
 */
public final class AnimalModels {
    private AnimalModels() {}

    /** One box of a part: position and size in pixels, grown by {@code grow}, its texture net at (u, v). */
    record Cube(float x, float y, float z, float w, float h, float d, float grow, int u, int v, boolean mirror) {
        Cube(float x, float y, float z, float w, float h, float d, float grow, int u, int v) { this(x, y, z, w, h, d, grow, u, v, false); }
    }

    /** The texture size the parts are drawn with (64x32 unless a model says otherwise). */
    static float textureWidth = 64f, textureHeight = 32f;

    /** A ModelRenderer: a pivot, rotations in radians and its boxes. */
    static final class Part {
        float px, py, pz, rx, ry, rz;
        final Cube[] cubes;
        Part(float px, float py, float pz, Cube... cubes) {
            this.px = px; this.py = py; this.pz = pz;
            this.cubes = cubes;
        }
        void draw() {
            glPushMatrix();
            glTranslatef(px / 16f, py / 16f, pz / 16f);
            if (rz != 0) glRotatef((float) Math.toDegrees(rz), 0, 0, 1);
            if (ry != 0) glRotatef((float) Math.toDegrees(ry), 0, 1, 0);
            if (rx != 0) glRotatef((float) Math.toDegrees(rx), 1, 0, 0);
            glBegin(GL_QUADS);
            for (Cube c : cubes) {
                if (c.mirror) mirroredBox(c);
                else PlayerModel.box(c.x, c.y, c.z, c.w, c.h, c.d, c.grow, c.u, c.v, textureWidth, textureHeight);
            }
            glEnd();
            glPopMatrix();
        }
    }

    /**
     * A box with ModelRenderer.mirror set (left limbs): the same net flipped
     * left to right, so the sides swap and every face reads mirrored.
     */
    private static void mirroredBox(Cube c) {
        float tw = textureWidth, th = textureHeight;
        float x1 = (c.x - c.grow) / 16f, y1 = (c.y - c.grow) / 16f, z1 = (c.z - c.grow) / 16f;
        float x2 = (c.x + c.w + c.grow) / 16f, y2 = (c.y + c.h + c.grow) / 16f, z2 = (c.z + c.d + c.grow) / 16f;
        float u = c.u, v = c.v, w = c.w, h = c.h, d = c.d;
        face(tw, th, 0, 0, -1, u + d + w, v + d, u + d, v + d + h, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1);
        face(tw, th, 0, 0, 1, u + d + w + d + w, v + d, u + d + w + d, v + d + h, x2, y1, z2, x1, y1, z2, x1, y2, z2, x2, y2, z2);
        face(tw, th, -1, 0, 0, u + d + w + d, v + d, u + d + w, v + d + h, x1, y1, z2, x1, y1, z1, x1, y2, z1, x1, y2, z2);
        face(tw, th, 1, 0, 0, u + d, v + d, u, v + d + h, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1);
        face(tw, th, 0, -1, 0, u + d + w, v, u + d, v + d, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1);
        face(tw, th, 0, 1, 0, u + d + w + w, v, u + d + w, v + d, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2);
    }

    private static void face(float tw, float th, float nx, float ny, float nz, float u0, float v0, float u1, float v1,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        glNormal3f(nx, ny, nz);
        glTexCoord2f(u0 / tw, v0 / th); glVertex3f(ax, ay, az);
        glTexCoord2f(u1 / tw, v0 / th); glVertex3f(bx, by, bz);
        glTexCoord2f(u1 / tw, v1 / th); glVertex3f(cx, cy, cz);
        glTexCoord2f(u0 / tw, v1 / th); glVertex3f(dx, dy, dz);
    }

    /** How the animal is posed this frame: walk cycle, head turn (degrees) and pitch (degrees). */
    public record Pose(float swing, float amount, float headYaw, float pitch) {}

    private static Cube cube(float x, float y, float z, float w, float h, float d, float grow, int u, int v) {
        return new Cube(x, y, z, w, h, d, grow, u, v);
    }

    /** ModelQuadruped's legs swing in pairs: front-left with back-right. */
    private static void legs(Part[] legs, Pose pose) {
        float a = (float) Math.cos(pose.swing * 0.6662f) * 1.4f * pose.amount;
        float b = (float) Math.cos(pose.swing * 0.6662f + Math.PI) * 1.4f * pose.amount;
        legs[0].rx = a;
        legs[1].rx = b;
        legs[2].rx = b;
        legs[3].rx = a;
    }

    private static void head(Part head, Pose pose) {
        head.rx = (float) Math.toRadians(pose.pitch);
        head.ry = (float) Math.toRadians(pose.headYaw);
    }

    /** ModelQuadruped.render: the head (child-offset for babies), then the body and legs (half size for babies). */
    private static void quadruped(Part head, Part body, Part[] legs, boolean child, float childY, float childZ) {
        body.rx = (float) Math.PI / 2f;
        if (child) {
            glPushMatrix();
            glTranslatef(0f, childY / 16f, childZ / 16f);
            head.draw();
            glPopMatrix();
            glPushMatrix();
            glScalef(0.5f, 0.5f, 0.5f);
            glTranslatef(0f, 24f / 16f, 0f);
            body.draw();
            for (Part leg : legs) leg.draw();
            glPopMatrix();
        } else {
            head.draw();
            body.draw();
            for (Part leg : legs) leg.draw();
        }
    }

    private static Part[] quadrupedLegs(int height, float grow, float frontZ, float backZ, float side) {
        float y = 24 - height;
        Cube leg = cube(-2, 0, -2, 4, height, 4, grow, 0, 16);
        return new Part[] {new Part(-side, y, backZ, leg), new Part(side, y, backZ, leg),
            new Part(-side, y, frontZ, leg), new Part(side, y, frontZ, leg)};
    }

    // ---- pig ----------------------------------------------------------------------------------

    /** ModelPig: ModelQuadruped(6) with a snout; babies' heads sit 4 pixels down. */
    public static void pig(Pose pose, boolean child) {
        Part head = new Part(0, 12, -6, cube(-4, -4, -8, 8, 8, 8, 0, 0, 0), cube(-2, 0, -9, 4, 3, 1, 0, 16, 16));
        Part body = new Part(0, 11, 2, cube(-5, -10, -7, 10, 16, 8, 0, 28, 8));
        Part[] legs = quadrupedLegs(6, 0, -5, 7, 3);
        head(head, pose);
        legs(legs, pose);
        quadruped(head, body, legs, child, 4, 4);
    }

    // ---- cow ----------------------------------------------------------------------------------

    /** ModelCow: ModelQuadruped(12) with horns, a bigger body with an udder, legs spread a pixel wider. */
    public static void cow(Pose pose, boolean child) {
        Part head = new Part(0, 4, -8, cube(-4, -4, -6, 8, 8, 6, 0, 0, 0),
            cube(-5, -5, -4, 1, 3, 1, 0, 22, 0), cube(4, -5, -4, 1, 3, 1, 0, 22, 0));
        Part body = new Part(0, 5, 2, cube(-6, -10, -7, 12, 18, 10, 0, 18, 4), cube(-2, 2, -8, 4, 6, 1, 0, 52, 0));
        Part[] legs = quadrupedLegs(12, 0, -6, 7, 4);
        head(head, pose);
        legs(legs, pose);
        quadruped(head, body, legs, child, 8, 6);
    }

    // ---- sheep --------------------------------------------------------------------------------

    /**
     * ModelSheep2 (the shorn body) or ModelSheep1 (the fleece over it):
     * the head drops 9 pixels and nods while grazing.
     */
    public static void sheep(Pose pose, boolean child, boolean fleece, float headDrop, float headAngle) {
        Part head, body;
        Part[] legs;
        if (fleece) {
            head = new Part(0, 6, -8, cube(-3, -4, -4, 6, 6, 6, 0.6f, 0, 0));
            body = new Part(0, 5, 2, cube(-4, -10, -7, 8, 16, 6, 1.75f, 28, 8));
            Cube leg = cube(-2, 0, -2, 4, 6, 4, 0.5f, 0, 16);
            legs = new Part[] {new Part(-3, 12, 7, leg), new Part(3, 12, 7, leg), new Part(-3, 12, -5, leg), new Part(3, 12, -5, leg)};
        } else {
            head = new Part(0, 6, -8, cube(-3, -4, -6, 6, 6, 8, 0, 0, 0));
            body = new Part(0, 5, 2, cube(-4, -10, -7, 8, 16, 6, 0, 28, 8));
            legs = quadrupedLegs(12, 0, -5, 7, 3);
        }
        head(head, pose);
        head.py = 6 + headDrop * 9f;
        head.rx = headAngle;
        legs(legs, pose);
        quadruped(head, body, legs, child, 8, 4);
    }

    // ---- chicken ------------------------------------------------------------------------------

    /** ModelChicken: head, bill and wattle turn together, wings flap out by {@code wings} radians. */
    public static void chicken(Pose pose, boolean child, float wings) {
        Part head = new Part(0, 15, -4, cube(-2, -6, -2, 4, 6, 3, 0, 0, 0));
        Part bill = new Part(0, 15, -4, cube(-2, -4, -4, 4, 2, 2, 0, 14, 0));
        Part chin = new Part(0, 15, -4, cube(-1, -2, -3, 2, 2, 2, 0, 14, 4));
        Part body = new Part(0, 16, 0, cube(-3, -4, -3, 6, 8, 6, 0, 0, 9));
        Part rightLeg = new Part(-2, 19, 1, cube(-1, 0, -3, 3, 5, 3, 0, 26, 0));
        Part leftLeg = new Part(1, 19, 1, cube(-1, 0, -3, 3, 5, 3, 0, 26, 0));
        Part rightWing = new Part(-4, 13, 0, cube(0, 0, -3, 1, 4, 6, 0, 24, 13));
        Part leftWing = new Part(4, 13, 0, cube(-1, 0, -3, 1, 4, 6, 0, 24, 13));
        for (Part part : new Part[] {head, bill, chin}) head(part, pose);
        body.rx = (float) Math.PI / 2f;
        rightLeg.rx = (float) Math.cos(pose.swing * 0.6662f) * 1.4f * pose.amount;
        leftLeg.rx = (float) Math.cos(pose.swing * 0.6662f + Math.PI) * 1.4f * pose.amount;
        rightWing.rz = wings;
        leftWing.rz = -wings;
        if (child) {
            glPushMatrix();
            glTranslatef(0f, 5f / 16f, 2f / 16f);
            head.draw();
            bill.draw();
            chin.draw();
            glPopMatrix();
            glPushMatrix();
            glScalef(0.5f, 0.5f, 0.5f);
            glTranslatef(0f, 24f / 16f, 0f);
        } else {
            head.draw();
            bill.draw();
            chin.draw();
            glPushMatrix();
        }
        body.draw();
        rightLeg.draw();
        leftLeg.draw();
        rightWing.draw();
        leftWing.draw();
        glPopMatrix();
    }
}

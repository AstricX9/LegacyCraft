package net.minecraft.client.renderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * The player model in 1.8.9's 64x64 skin layout (ModelPlayer, wide or slim
 * "Alex" arms, plus the cape; HYPOTHESIS: part sizes, pivots and pose maths
 * are recalled). Model space
 * has y pointing down and the face toward -z, in 1/16-block units; callers
 * draw it the way RendererLivingEntity does (see {@link #setPose}).
 *
 * <p>Each part is a box whose six faces take their texture from the
 * standard skin net: top and bottom in the first row, then the right side,
 * front, left side and back; the outer layer (hat, jacket, sleeves,
 * trousers) is the same box grown by 0.5 or 0.25.
 */
public final class PlayerModel {
    /** A box of the model: position, size, skin offset and how far the outer layer grows. */
    public static final class Part {
        final float x, y, z, width, height, depth;
        final int u, v, overlayU, overlayV;
        final float overlayGrow;
        /** Whether the outer layer is drawn (Skin Customization's model parts). */
        public boolean showOverlay = true;
        public float pivotX, pivotY, pivotZ, angleX, angleY, angleZ;
        final float basePivotX, basePivotY, basePivotZ;

        Part(float x, float y, float z, float width, float height, float depth, int u, int v, int overlayU, int overlayV,
             float overlayGrow, float pivotX, float pivotY, float pivotZ) {
            this.x = x; this.y = y; this.z = z;
            this.width = width; this.height = height; this.depth = depth;
            this.u = u; this.v = v; this.overlayU = overlayU; this.overlayV = overlayV; this.overlayGrow = overlayGrow;
            this.basePivotX = this.pivotX = pivotX;
            this.basePivotY = this.pivotY = pivotY;
            this.basePivotZ = this.pivotZ = pivotZ;
        }

        void reset() {
            pivotX = basePivotX; pivotY = basePivotY; pivotZ = basePivotZ;
            angleX = angleY = angleZ = 0;
        }

        /** Moves the modelview to this part's pivot and rotation (ModelRenderer.postRender), in blocks. */
        public void transform() {
            glTranslatef(pivotX / 16f, pivotY / 16f, pivotZ / 16f);
            if (angleZ != 0) glRotatef((float) Math.toDegrees(angleZ), 0, 0, 1);
            if (angleY != 0) glRotatef((float) Math.toDegrees(angleY), 0, 1, 0);
            if (angleX != 0) glRotatef((float) Math.toDegrees(angleX), 1, 0, 0);
        }

        /** Draws the part and its outer layer (the skin must be bound). */
        public void render() {
            glPushMatrix();
            transform();
            glBegin(GL_QUADS);
            box(x, y, z, width, height, depth, 0, u, v);
            if (showOverlay) box(x, y, z, width, height, depth, overlayGrow, overlayU, overlayV);
            glEnd();
            glPopMatrix();
        }
    }

    public final Part head = new Part(-4, -8, -4, 8, 8, 8, 0, 0, 32, 0, 0.5f, 0, 0, 0);
    public final Part body = new Part(-4, 0, -2, 8, 12, 4, 16, 16, 16, 32, 0.25f, 0, 0, 0);
    public final Part rightArm;
    public final Part leftArm;
    public final Part rightLeg = new Part(-2, 0, -2, 4, 12, 4, 0, 16, 0, 32, 0.25f, -1.9f, 12, 0);
    public final Part leftLeg = new Part(-2, 0, -2, 4, 12, 4, 16, 48, 0, 48, 0.25f, 1.9f, 12, 0);
    private final Part[] parts;
    /** ModelPlayer.bipedCape: a 10x16x1 box on the 64x32 cape texture, hanging from the shoulders. */
    private float capePivotY;
    public final boolean slim;

    /** The classic (Steve) model. */
    public PlayerModel() { this(false); }

    /** {@code slim}: ModelPlayer's small arms, 3 pixels wide with the shoulder pivot half a pixel lower. */
    public PlayerModel(boolean slim) {
        this.slim = slim;
        if (slim) {
            rightArm = new Part(-2, -2, -2, 3, 12, 4, 40, 16, 40, 32, 0.25f, -5, 2.5f, 0);
            leftArm = new Part(-1, -2, -2, 3, 12, 4, 32, 48, 48, 48, 0.25f, 5, 2.5f, 0);
        } else {
            rightArm = new Part(-3, -2, -2, 4, 12, 4, 40, 16, 40, 32, 0.25f, -5, 2, 0);
            leftArm = new Part(-1, -2, -2, 4, 12, 4, 32, 48, 48, 48, 0.25f, 5, 2, 0);
        }
        parts = new Part[] {head, body, rightArm, leftArm, rightLeg, leftLeg};
    }

    /** What the pose depends on (ModelBiped.setRotationAngles and friends). */
    public record Pose(float limbSwing, float limbSwingAmount, float ageInTicks, float headYaw, float headPitch,
                       float swingProgress, boolean sneaking, boolean holdingItem, boolean blocking) {}

    /**
     * Poses the parts: walking swings arms and legs in opposition, the head
     * turns and tilts, holding an item lowers the right arm (blocking raises
     * it across the body: ModelBiped heldItemRight 3), an attack swings
     * it (twisting the body), sneaking bends the body forward, and both arms
     * sway slightly with time.
     */
    public void setPose(Pose pose) {
        for (Part part : parts) part.reset();
        float swing = pose.limbSwing(), amount = pose.limbSwingAmount(), age = pose.ageInTicks();
        head.angleY = (float) Math.toRadians(pose.headYaw());
        head.angleX = (float) Math.toRadians(pose.headPitch());
        rightArm.angleX = (float) Math.cos(swing * 0.6662f + Math.PI) * 2f * amount * 0.5f;
        leftArm.angleX = (float) Math.cos(swing * 0.6662f) * 2f * amount * 0.5f;
        rightLeg.angleX = (float) Math.cos(swing * 0.6662f) * 1.4f * amount;
        leftLeg.angleX = (float) Math.cos(swing * 0.6662f + Math.PI) * 1.4f * amount;
        if (pose.blocking()) {
            rightArm.angleX = rightArm.angleX * 0.5f - (float) Math.PI / 10f * 3f;
            rightArm.angleY = -0.5235988f;
        } else if (pose.holdingItem()) {
            rightArm.angleX = rightArm.angleX * 0.5f - (float) Math.PI / 10f;
        }

        float attack = pose.swingProgress();
        body.angleY = (float) Math.sin(Math.sqrt(attack) * Math.PI * 2) * 0.2f;
        rightArm.pivotZ = (float) Math.sin(body.angleY) * 5f;
        rightArm.pivotX = (float) -Math.cos(body.angleY) * 5f;
        leftArm.pivotZ = (float) -Math.sin(body.angleY) * 5f;
        leftArm.pivotX = (float) Math.cos(body.angleY) * 5f;
        rightArm.angleY += body.angleY;
        leftArm.angleY += body.angleY;
        leftArm.angleX += body.angleY;
        float eased = 1f - attack;
        eased *= eased;
        eased *= eased;
        eased = 1f - eased;
        float lift = (float) Math.sin(eased * Math.PI);
        float reach = (float) Math.sin(attack * Math.PI) * -(head.angleX - 0.7f) * 0.75f;
        rightArm.angleX -= lift * 1.2f + reach;
        rightArm.angleY += body.angleY * 2f;
        rightArm.angleZ += (float) Math.sin(attack * Math.PI) * -0.4f;

        if (pose.sneaking()) {
            body.angleX = 0.5f;
            rightArm.angleX += 0.4f;
            leftArm.angleX += 0.4f;
            rightLeg.pivotZ = leftLeg.pivotZ = 4f;
            rightLeg.pivotY = leftLeg.pivotY = 9f;
            head.pivotY = 1f;
            capePivotY = 2f;
        } else {
            rightLeg.pivotZ = leftLeg.pivotZ = 0.1f;
            capePivotY = 0f;
        }
        rightArm.angleZ += (float) Math.cos(age * 0.09f) * 0.05f + 0.05f;
        leftArm.angleZ -= (float) Math.cos(age * 0.09f) * 0.05f + 0.05f;
        rightArm.angleX += (float) Math.sin(age * 0.067f) * 0.05f;
        leftArm.angleX -= (float) Math.sin(age * 0.067f) * 0.05f;
    }

    /** Draws the whole posed model (sneaking lowers it 0.2 blocks, as ModelBiped.render). */
    public void render(boolean sneaking) {
        glPushMatrix();
        if (sneaking) glTranslatef(0f, 0.2f, 0f);
        for (Part part : parts) part.render();
        glPopMatrix();
    }

    /**
     * The cape box (ModelPlayer.renderCape) in the space LayerCape sets up;
     * the cape texture must be bound. Its texture coordinates assume the
     * 64x32 cape layout, so larger capes of the same shape work too.
     */
    public void renderCape() {
        glPushMatrix();
        glTranslatef(0f, capePivotY / 16f, 0f);
        glBegin(GL_QUADS);
        box(-5, 0, -1, 10, 16, 1, 0, 0, 0, 64f, 32f);
        glEnd();
        glPopMatrix();
    }

    /** The first-person arm: the right arm at rest (idle sway from time 0, i.e. tilted 0.1 radians out). */
    public void renderRightArmAtRest() {
        setPose(new Pose(0, 0, 0, 0, 0, 0, false, false, false));
        rightArm.render();
    }

    /**
     * One box: six outward-facing quads with normals and skin coordinates
     * (the net laid out from the skin offset), grown by {@code grow} on
     * every side without changing the texture area.
     */
    public static void box(float x, float y, float z, float width, float height, float depth, float grow, int u, int v) {
        box(x, y, z, width, height, depth, grow, u, v, 64f, 64f);
    }

    /** {@link #box} on a texture of {@code textureWidth} x {@code textureHeight} pixels (armour is 64x32). */
    public static void box(float x, float y, float z, float width, float height, float depth, float grow, int u, int v,
                    float textureWidth, float textureHeight) {
        float tw = textureWidth, th = textureHeight;
        float x1 = (x - grow) / 16f, y1 = (y - grow) / 16f, z1 = (z - grow) / 16f;
        float x2 = (x + width + grow) / 16f, y2 = (y + height + grow) / 16f, z2 = (z + depth + grow) / 16f;
        float w = width, h = height, d = depth;
        // Front (-z): left of the texture is the model's -x side.
        face(tw, th, 0, 0, -1, u + d, v + d, u + d + w, v + d + h, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1);
        // Back (+z): mirrored across.
        face(tw, th, 0, 0, 1, u + d + w + d, v + d, u + d + w + d + w, v + d + h, x2, y1, z2, x1, y1, z2, x1, y2, z2, x2, y2, z2);
        // Right side (-x): back edge on the left of its area, front edge on the right.
        face(tw, th, -1, 0, 0, u, v + d, u + d, v + d + h, x1, y1, z2, x1, y1, z1, x1, y2, z1, x1, y2, z2);
        // Left side (+x): front edge on the left, back edge on the right.
        face(tw, th, 1, 0, 0, u + d + w, v + d, u + d + w + d, v + d + h, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1);
        // Top (-y, model space is y-down): the back edge at the top of its area, the front edge touching the front.
        face(tw, th, 0, -1, 0, u + d, v, u + d + w, v + d, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1);
        // Bottom (+y): next to the top, the front edge at the top of its area.
        face(tw, th, 0, 1, 0, u + d + w, v, u + d + w + w, v + d, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2);
    }

    /** A quad whose four corners take the texture's top-left, top-right, bottom-right and bottom-left in order. */
    private static void face(float tw, float th, float nx, float ny, float nz, float u0, float v0, float u1, float v1,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        glNormal3f(nx, ny, nz);
        glTexCoord2f(u0 / tw, v0 / th); glVertex3f(ax, ay, az);
        glTexCoord2f(u1 / tw, v0 / th); glVertex3f(bx, by, bz);
        glTexCoord2f(u1 / tw, v1 / th); glVertex3f(cx, cy, cz);
        glTexCoord2f(u0 / tw, v1 / th); glVertex3f(dx, dy, dz);
    }
}

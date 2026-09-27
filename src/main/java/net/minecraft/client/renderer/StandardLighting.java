package net.minecraft.client.renderer;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * The two fixed directional lights 1.8.9 puts on items, entities and the
 * first-person hand (RenderHelper's standard item lighting, HYPOTHESIS:
 * recalled values): lights from (0.2, 1, -0.7) and (-0.2, 1, 0.7) in the
 * frame of whatever modelview is current when {@link #enable} is called,
 * 0.6 diffuse each, 0.4 ambient, no specular, flat shading, and the vertex
 * colour as the material.
 */
public final class StandardLighting {
    private static final float[] LIGHT0 = normalize(0.2f, 1.0f, -0.7f);
    private static final float[] LIGHT1 = normalize(-0.2f, 1.0f, 0.7f);

    private StandardLighting() {}

    public static void enable() {
        glEnable(GL_LIGHTING);
        glEnable(GL_LIGHT0);
        glEnable(GL_LIGHT1);
        glEnable(GL_COLOR_MATERIAL);
        glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);
        glEnable(GL_NORMALIZE);
        light(GL_LIGHT0, LIGHT0);
        light(GL_LIGHT1, LIGHT1);
        glShadeModel(GL_FLAT);
        glLightModelfv(GL_LIGHT_MODEL_AMBIENT, buffer(0.4f, 0.4f, 0.4f, 1f));
    }

    public static void disable() {
        glDisable(GL_LIGHTING);
        glDisable(GL_LIGHT0);
        glDisable(GL_LIGHT1);
        glDisable(GL_COLOR_MATERIAL);
        glDisable(GL_NORMALIZE);
    }

    private static void light(int light, float[] direction) {
        glLightfv(light, GL_POSITION, buffer(direction[0], direction[1], direction[2], 0f));
        glLightfv(light, GL_DIFFUSE, buffer(0.6f, 0.6f, 0.6f, 1f));
        glLightfv(light, GL_AMBIENT, buffer(0f, 0f, 0f, 1f));
        glLightfv(light, GL_SPECULAR, buffer(0f, 0f, 0f, 1f));
    }

    private static FloatBuffer buffer(float a, float b, float c, float d) {
        FloatBuffer buffer = org.lwjgl.BufferUtils.createFloatBuffer(4);
        buffer.put(a).put(b).put(c).put(d).flip();
        return buffer;
    }

    private static float[] normalize(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / length, y / length, z / length};
    }
}

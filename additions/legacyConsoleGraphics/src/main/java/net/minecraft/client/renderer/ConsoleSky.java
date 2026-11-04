package net.minecraft.client.renderer;
import static org.lwjgl.opengl.GL11.*;
final class ConsoleSky {
    /** Native sky geometry, with a smooth colour gradient and no gap below the horizon. */
    public static void render(float[] sky, float[] fog, boolean submerged) {
        glDisable(GL_FOG);
        glDisable(GL_CULL_FACE);
        glShadeModel(GL_SMOOTH);
        float[] zenith = submerged ? fog : sky;
        // The console sky pass keeps its far plane at least 128 blocks, including at 2 chunks.
        for (int ring = 0; ring < 24; ring++) {
            float a0 = (float) (-Math.PI / 2 + ring * Math.PI / 24);
            float a1 = (float) (-Math.PI / 2 + (ring + 1) * Math.PI / 24);
            float y0 = (float) Math.sin(a0), y1 = (float) Math.sin(a1);
            float radius0 = (float) Math.cos(a0) * 100f, radius1 = (float) Math.cos(a1) * 100f;
            float[] lower = LegacyConsoleGraphics.horizon(zenith, fog, y0);
            float[] upper = LegacyConsoleGraphics.horizon(zenith, fog, y1);
            glBegin(GL_QUAD_STRIP);
            for (int segment = 0; segment <= 48; segment++) {
                double turn = segment * Math.PI * 2 / 48;
                float x = (float) Math.cos(turn), z = (float) Math.sin(turn);
                glColor3f(lower[0], lower[1], lower[2]);
                glVertex3f(x * radius0, y0 * 100f, z * radius0);
                glColor3f(upper[0], upper[1], upper[2]);
                glVertex3f(x * radius1, y1 * 100f, z * radius1);
            }
            glEnd();
        }
        glColor4f(1f, 1f, 1f, 1f);
    }

}

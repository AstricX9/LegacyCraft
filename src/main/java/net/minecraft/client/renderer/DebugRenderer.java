package net.minecraft.client.renderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * World-space debug overlays toggled with F3 key combinations.
 *
 * <p>F3+G chunk borders: red vertical lines at the corners of the chunks
 * around the player, yellow lines every 2 blocks on the walls of the chunk
 * the player stands in, and blue lines at every 16-block section boundary.
 * HYPOTHESIS styling (unknowns.yml "F3 visuals").
 */
public final class DebugRenderer {
    private DebugRenderer() {}

    public static void renderChunkBorders(double playerX, double playerY, double playerZ) {
        int chunkX = (int) Math.floor(playerX) >> 4, chunkZ = (int) Math.floor(playerZ) >> 4;
        double baseX = chunkX * 16.0, baseZ = chunkZ * 16.0;
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_FOG);
        glEnable(GL_BLEND);
        glLineWidth(1f);
        glBegin(GL_LINES);
        glColor4f(1, 0, 0, 1);
        for (int dx = -16; dx <= 32; dx += 16)
            for (int dz = -16; dz <= 32; dz += 16) {
                glVertex3d(baseX + dx, 0, baseZ + dz);
                glVertex3d(baseX + dx, 256, baseZ + dz);
            }
        glColor4f(1, 1, 0, 1);
        for (int step = 2; step < 16; step += 2) {
            glVertex3d(baseX + step, 0, baseZ); glVertex3d(baseX + step, 256, baseZ);
            glVertex3d(baseX + step, 0, baseZ + 16); glVertex3d(baseX + step, 256, baseZ + 16);
            glVertex3d(baseX, 0, baseZ + step); glVertex3d(baseX, 256, baseZ + step);
            glVertex3d(baseX + 16, 0, baseZ + step); glVertex3d(baseX + 16, 256, baseZ + step);
        }
        int minY = Math.max(0, (int) playerY - 32), maxY = Math.min(256, (int) playerY + 32);
        for (int y = minY - minY % 2; y <= maxY; y += 2) {
            if (y % 16 == 0) glColor4f(0.25f, 0.25f, 1, 1); else glColor4f(1, 1, 0, 1);
            glVertex3d(baseX, y, baseZ); glVertex3d(baseX + 16, y, baseZ);
            glVertex3d(baseX + 16, y, baseZ); glVertex3d(baseX + 16, y, baseZ + 16);
            glVertex3d(baseX + 16, y, baseZ + 16); glVertex3d(baseX, y, baseZ + 16);
            glVertex3d(baseX, y, baseZ + 16); glVertex3d(baseX, y, baseZ);
        }
        glEnd();
        glColor4f(1, 1, 1, 1);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_FOG);
    }
}

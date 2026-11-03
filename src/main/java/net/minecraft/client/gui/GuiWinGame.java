package net.minecraft.client.gui;

import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.opengl.GL11.*;

/**
 * GuiWinGame (1.8.9, HYPOTHESIS: recalled): after the exit portal, the end
 * poem (texts/end.txt, PLAYERNAME filled in, the scrambled bits drawn
 * obfuscated) and then the credits (texts/credits.txt) scroll up under the
 * logo, 274 wide, over a slowly lightening dirt background and the
 * vignette. It ends by itself, or on Escape, back at the spawn point.
 */
public class GuiWinGame extends GuiScreen {
    private static final ResourceLocation LOGO = new ResourceLocation("minecraft:textures/gui/title/minecraft.png");
    private static final ResourceLocation VIGNETTE = new ResourceLocation("minecraft:textures/misc/vignette.png");
    private static final ResourceLocation END = new ResourceLocation("minecraft:texts/end.txt");
    private static final ResourceLocation CREDITS = new ResourceLocation("minecraft:texts/credits.txt");
    private static final int WIDTH = 274;
    private static final float SPEED = 0.5f;
    /** end.txt marks scrambled words with these codes. */
    private static final String SCRAMBLE = "§f§k§a§b";

    private int time;
    private List<String> lines;
    private int totalHeight;
    private boolean done;

    /**
     * Vanilla doesn't pause but takes the player out of the world while the
     * credits roll; pausing keeps them just as safe at their spawn point.
     */
    @Override public boolean doesGuiPauseGame() { return true; }


    @Override
    public void updateScreen() {
        time++;
        if (time > (totalHeight + height + height + 24) / SPEED) finish();
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        if (key == GLFW_KEY_ESCAPE) finish();
    }

    private void finish() {
        if (done) return;
        done = true;
        mc.displayGuiScreen(null);
    }

    @Override
    public void initGui() {
        if (lines != null) return;
        lines = new ArrayList<>();
        String name = mc.playerName();
        Random random = new Random(8124371L);
        for (String line : read(END)) {
            line = line.replace("PLAYERNAME", name);
            int at;
            while ((at = line.indexOf(SCRAMBLE)) >= 0)
                line = line.substring(0, at) + "§f§k" + "XXXXXXXX".substring(0, random.nextInt(4) + 3) + line.substring(at + SCRAMBLE.length());
            lines.addAll(fontRendererObj.listFormattedStringToWidth(line, WIDTH));
            lines.add("");
        }
        for (int i = 0; i < 8; i++) lines.add("");
        for (String line : read(CREDITS)) {
            lines.addAll(fontRendererObj.listFormattedStringToWidth(line.replace("PLAYERNAME", name).replace("\t", "    "), WIDTH));
            lines.add("");
        }
        totalHeight = lines.size() * 12;
    }

    private List<String> read(ResourceLocation location) {
        List<String> result = new ArrayList<>();
        try (InputStream stream = mc.resources().open(location);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.add(line);
        } catch (IOException missing) {
            // A pack without the text just scrolls nothing.
        }
        return result;
    }

    /** GuiWinGame.drawWinGameScreen: the dirt scrolls at half speed and brightens to 96/255 over the first seconds. */
    private void drawBackgroundScroll(float partialTicks) {
        float t = time + partialTicks;
        float top = -t * 0.5f * SPEED, bottom = height - t * 0.5f * SPEED, scale = 0.015625f;
        float light = t * 0.02f;
        float end = (totalHeight + height + height + 24) / SPEED;
        light = Math.min(light, (end - 20f - t) * 0.005f);
        light = Math.min(light, 1f);
        light = light * light * 96f / 255f;
        glEnable(GL_TEXTURE_2D);
        mc.textures().bind(GuiTextures.OPTIONS_BACKGROUND);
        glColor4f(light, light, light, 1f);
        glBegin(GL_QUADS);
        glTexCoord2f(0, top * scale); glVertex2f(0, height);
        glTexCoord2f(width * scale, top * scale); glVertex2f(width, height);
        glTexCoord2f(width * scale, bottom * scale); glVertex2f(width, 0);
        glTexCoord2f(0, bottom * scale); glVertex2f(0, 0);
        glEnd();
        glColor4f(1, 1, 1, 1);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackgroundScroll(partialTicks);
        int left = width / 2 - WIDTH / 2, top = height + 50;
        float scroll = -(time + partialTicks) * SPEED;
        glPushMatrix();
        glTranslatef(0, scroll, 0);
        mc.textures().bind(LOGO);
        glColor4f(1, 1, 1, 1);
        drawTexturedModalRect(left, top, 0, 0, 155, 44);
        drawTexturedModalRect(left + 155, top, 0, 45, 155, 44);
        int y = top + 200;
        for (int i = 0; i < lines.size(); i++) {
            // The last line stops in the middle of the screen.
            if (i == lines.size() - 1) {
                float over = y + scroll - (height / 2f - 6);
                if (over < 0) glTranslatef(0, -over, 0);
            }
            if (y + scroll + 12 + 8 > 0 && y + scroll < height) {
                String line = lines.get(i);
                if (line.startsWith("[C]")) {
                    String text = line.substring(3);
                    fontRendererObj.drawStringWithShadow(text, left + (WIDTH - fontRendererObj.getStringWidth(text)) / 2f, y, 0xFFFFFF);
                } else fontRendererObj.drawStringWithShadow(line, left, y, 0xFFFFFF);
            }
            y += 12;
        }
        glPopMatrix();
        mc.textures().bind(VIGNETTE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ZERO, GL_ONE_MINUS_SRC_COLOR);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(0, height);
        glTexCoord2f(1, 1); glVertex2f(width, height);
        glTexCoord2f(1, 0); glVertex2f(width, 0);
        glTexCoord2f(0, 0); glVertex2f(0, 0);
        glEnd();
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_BLEND);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

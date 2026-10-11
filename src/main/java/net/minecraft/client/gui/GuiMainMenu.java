package net.minecraft.client.gui;

import net.minecraft.client.Branding;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * The 1.8.9 title screen (master context §66): blurred rotating panorama,
 * the logo (with the rare "Minceraft" misspelling), a pulsing splash, and
 * Singleplayer / Multiplayer / Minecraft Realms / language / Options / Quit.
 *
 * <p>Layout, panorama pass counts and splash maths are HYPOTHESIS until
 * screenshot-compared (unknowns.yml "GUI pixel layout"). Branding text is a
 * documented deviation (DEV-012): LegacyCraft must not present itself as a
 * Mojang product.
 */
public class GuiMainMenu extends GuiScreen {
    private static final ResourceLocation SPLASHES = new ResourceLocation("minecraft:texts/splashes.txt");
    private static final ResourceLocation LOGO = new ResourceLocation("minecraft:textures/gui/title/minecraft.png");
    /** LegacyCraft's own full-resolution logo; drawn instead of the vanilla sheet when a pack provides it. */
    private static final ResourceLocation LEGACYCRAFT_LOGO = new ResourceLocation("minecraft:textures/gui/title/legacycraft.png");
    /** Vanilla's logo is 274x44 GUI units; LegacyCraft's keeps that width. */
    private static final int LOGO_WIDTH = 274, LOGO_HEIGHT = 44;
    private static final ResourceLocation[] PANORAMA = new ResourceLocation[6];
    private static final Random RANDOM = new Random();

    static {
        for (int i = 0; i < 6; i++) PANORAMA[i] = new ResourceLocation("minecraft:textures/gui/title/background/panorama_" + i + ".png");
    }

    private final float minceraftRoll = RANDOM.nextFloat();
    private String splashText = "missingno";
    private int panoramaTimer;
    private int viewportTexture;
    private java.awt.image.BufferedImage logoImage;
    private boolean logoLoaded;
    private int logoTexture, logoTextureWidth, logoTextureHeight;

    public GuiMainMenu() {}

    @Override
    public boolean doesGuiPauseGame() { return false; }

    @Override
    public void keyPressed(int key, int modifiers) {}

    @Override
    public void updateScreen() { panoramaTimer++; }

    @Override
    public void initGui() {
        if (splashText.equals("missingno")) splashText = pickSplash();
        int top = height / 4 + 48;
        buttonList.add(new GuiButton(1, width / 2 - 100, top, I18n.format("menu.singleplayer")));
        buttonList.add(new GuiButton(2, width / 2 - 100, top + 24, I18n.format("menu.multiplayer")));
        buttonList.add(new GuiButton(14, width / 2 - 100, top + 24 * 2, I18n.format("menu.online")));
        buttonList.add(new GuiButton(0, width / 2 - 100, top + 72 + 12, 98, 20, I18n.format("menu.options")));
        buttonList.add(new GuiButton(4, width / 2 + 2, top + 72 + 12, 98, 20, I18n.format("menu.quit")));
        buttonList.add(new GuiButtonLanguage(5, width / 2 - 124, top + 72 + 12));
    }

    /** A random splashes.txt line, replaced by vanilla's date-specific splashes on their days. */
    private String pickSplash() {
        Calendar calendar = Calendar.getInstance();
        int month = calendar.get(Calendar.MONTH) + 1, day = calendar.get(Calendar.DAY_OF_MONTH);
        if (month == 11 && day == 9) return "Happy birthday, ez!";
        if (month == 6 && day == 1) return "Happy birthday, Notch!";
        if (month == 12 && day == 24) return "Merry X-mas!";
        if (month == 1 && day == 1) return "Happy new year!";
        if (month == 10 && day == 31) return "OOoooOOOoooo! Spooky!";
        List<String> lines = new ArrayList<>();
        try (InputStream stream = mc.resources().open(SPLASHES);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) lines.add(line);
            }
        } catch (IOException missing) {
            return "missingno";
        }
        if (lines.isEmpty()) return "missingno";
        String chosen;
        // Vanilla rerolls splashes that contain its own easter-egg hash string.
        do chosen = lines.get(RANDOM.nextInt(lines.size())); while (chosen.hashCode() == 125780783);
        return chosen;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0 -> mc.displayGuiScreen(new GuiOptions(this));
            case 1 -> mc.displayGuiScreen(new GuiSelectWorld(this));
            case 2 -> mc.displayGuiScreen(new GuiMultiplayer(this));
            case 4 -> mc.shutdown();
            case 5 -> mc.displayGuiScreen(new GuiLanguage(this));
            case 14 -> mc.displayGuiScreen(new GuiDisconnected(this, "menu.online",
                "Realms needs Mojang's online services, which LegacyCraft does not connect to.", "gui.back"));
            default -> {}
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        glDisable(GL_ALPHA_TEST);
        renderSkybox(partialTicks);
        glEnable(GL_ALPHA_TEST);
        drawGradientRect(0, 0, width, height, 0x80FFFFFF, 0x00FFFFFF);
        drawGradientRect(0, 0, width, height, 0x00000000, 0x80000000);

        int logoLeft = width / 2 - LOGO_WIDTH / 2, logoTop = 30;
        if (!drawLegacyCraftLogo(logoLeft, logoTop)) drawVanillaLogo(logoLeft, logoTop);

        glPushMatrix();
        glTranslatef(width / 2f + 90, 70, 0);
        glRotatef(-20, 0, 0, 1);
        float pulse = 1.8f - Math.abs((float) Math.sin((System.currentTimeMillis() % 1000L) / 1000f * (float) Math.PI * 2f) * 0.1f);
        pulse = pulse * 100f / (fontRendererObj.getStringWidth(splashText) + 32);
        glScalef(pulse, pulse, pulse);
        drawCenteredString(fontRendererObj, splashText, 0, -8, 0xFFFF00);
        glPopMatrix();

        drawString(fontRendererObj, Branding.TITLE_VERSION, 2, height - 10, 0xFFFFFF);
        drawString(fontRendererObj, Branding.TITLE_NOTICE, width - fontRendererObj.getStringWidth(Branding.TITLE_NOTICE) - 2,
            height - 10, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (mouseX < 2 + fontRendererObj.getStringWidth(Branding.TITLE_VERSION) + 1 && mouseY >= height - 11)
            drawHoveringText(Branding.versionTooltip(), mouseX, mouseY);
    }

    /** Vanilla's two-half logo from minecraft.png (with the rare "Minceraft" misspelling). */
    private void drawVanillaLogo(int logoLeft, int logoTop) {
        mc.textures().bind(LOGO);
        glColor4f(1, 1, 1, 1);
        if (minceraftRoll < 1.0E-4) {
            drawTexturedModalRect(logoLeft, logoTop, 0, 0, 99, 44);
            drawTexturedModalRect(logoLeft + 99, logoTop, 129, 0, 27, 44);
            drawTexturedModalRect(logoLeft + 99 + 26, logoTop, 126, 0, 3, 44);
            drawTexturedModalRect(logoLeft + 99 + 26 + 3, logoTop, 99, 0, 26, 44);
            drawTexturedModalRect(logoLeft + 155, logoTop, 0, 45, 155, 44);
        } else {
            drawTexturedModalRect(logoLeft, logoTop, 0, 0, 155, 44);
            drawTexturedModalRect(logoLeft + 155, logoTop, 0, 45, 155, 44);
        }
    }

    /**
     * Draws legacycraft.png across the logo's 274-unit width, vertically
     * centred in vanilla's 44-unit band. The artwork is far larger than it is
     * drawn, and letting the GPU shrink it by a non-integer factor drops and
     * smears texels (the blurry logo). Instead it is box-filtered on the CPU to
     * exactly the logo's size in real screen pixels, so each texel lands on one
     * pixel; that is redone whenever the GUI scale or window size changes it.
     */
    private boolean drawLegacyCraftLogo(int logoLeft, int logoTop) {
        if (!logoLoaded) {
            logoLoaded = true;
            if (mc.resources().exists(LEGACYCRAFT_LOGO)) {
                try (InputStream stream = mc.resources().open(LEGACYCRAFT_LOGO)) {
                    logoImage = javax.imageio.ImageIO.read(stream);
                } catch (IOException ignored) {
                    logoImage = null;
                }
            }
        }
        if (logoImage == null) return false;
        int logoHeight = Math.min(LOGO_HEIGHT, Math.round(LOGO_WIDTH * (float) logoImage.getHeight() / logoImage.getWidth()));
        int top = logoTop + (LOGO_HEIGHT - logoHeight) / 2;
        float scale = width > 0 ? (float) mc.displayWidth() / width : 1;
        int pixelWidth = Math.max(1, Math.round(LOGO_WIDTH * scale)), pixelHeight = Math.max(1, Math.round(logoHeight * scale));
        if (logoTexture == 0 || logoTextureWidth != pixelWidth || logoTextureHeight != pixelHeight) {
            if (logoTexture != 0) glDeleteTextures(logoTexture);
            logoTexture = GuiTextures.upload(boxDownscale(logoImage, pixelWidth, pixelHeight), false);
            logoTextureWidth = pixelWidth;
            logoTextureHeight = pixelHeight;
        }
        glBindTexture(GL_TEXTURE_2D, logoTexture);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1, 1, 1, 1);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(logoLeft, top + logoHeight);
        glTexCoord2f(1, 1); glVertex2f(logoLeft + LOGO_WIDTH, top + logoHeight);
        glTexCoord2f(1, 0); glVertex2f(logoLeft + LOGO_WIDTH, top);
        glTexCoord2f(0, 0); glVertex2f(logoLeft, top);
        glEnd();
        return true;
    }

    /**
     * Area-averaging resize: every target pixel is the coverage-weighted mean of
     * the source pixels under it, with colour premultiplied by alpha so the
     * transparent surround cannot darken the logo's edges. Enlarging picks the
     * nearest source pixel instead, which keeps pixel art blocky.
     */
    static java.awt.image.BufferedImage boxDownscale(java.awt.image.BufferedImage source, int targetWidth, int targetHeight) {
        int sourceWidth = source.getWidth(), sourceHeight = source.getHeight();
        int[] pixels = source.getRGB(0, 0, sourceWidth, sourceHeight, null, 0, sourceWidth);
        java.awt.image.BufferedImage result = new java.awt.image.BufferedImage(targetWidth, targetHeight,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        double stepX = (double) sourceWidth / targetWidth, stepY = (double) sourceHeight / targetHeight;
        for (int y = 0; y < targetHeight; y++) {
            double y0 = stepY < 1 ? Math.floor(y * stepY) : y * stepY, y1 = stepY < 1 ? y0 + 1 : y0 + stepY;
            for (int x = 0; x < targetWidth; x++) {
                double x0 = stepX < 1 ? Math.floor(x * stepX) : x * stepX, x1 = stepX < 1 ? x0 + 1 : x0 + stepX;
                double alpha = 0, red = 0, green = 0, blue = 0, total = 0;
                for (int sy = (int) y0; sy < Math.min(sourceHeight, Math.ceil(y1)); sy++) {
                    double weightY = Math.min(y1, sy + 1) - Math.max(y0, sy);
                    for (int sx = (int) x0; sx < Math.min(sourceWidth, Math.ceil(x1)); sx++) {
                        double weight = weightY * (Math.min(x1, sx + 1) - Math.max(x0, sx));
                        if (weight <= 0) continue;
                        int argb = pixels[sy * sourceWidth + sx];
                        double covered = (argb >>> 24) / 255.0 * weight;
                        alpha += covered;
                        red += ((argb >> 16) & 0xFF) * covered;
                        green += ((argb >> 8) & 0xFF) * covered;
                        blue += (argb & 0xFF) * covered;
                        total += weight;
                    }
                }
                if (alpha <= 0) continue;
                int a = (int) Math.round(alpha / total * 255);
                int r = (int) Math.round(red / alpha), g = (int) Math.round(green / alpha), b = (int) Math.round(blue / alpha);
                result.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
            }
        }
        return result;
    }

    /**
     * Renders the six panorama faces into a 256x256 corner of the frame with
     * 64 slightly jittered, decreasingly opaque passes, blurs that by copying it
     * into a texture and redrawing it shifted, then stretches it over the screen.
     */
    private void renderSkybox(float partialTicks) {
        if (viewportTexture == 0) {
            viewportTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, viewportTexture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 256, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        }
        glViewport(0, 0, 256, 256);
        drawPanorama(partialTicks);
        for (int i = 0; i < 7; i++) rotateAndBlurSkybox();
        glViewport(0, 0, mc.displayWidth(), mc.displayHeight());

        float scale = width > height ? 120f / width : 120f / height;
        float halfV = height * scale / 256f, halfU = width * scale / 256f;
        glBindTexture(GL_TEXTURE_2D, viewportTexture);
        glColor4f(1, 1, 1, 1);
        glBegin(GL_QUADS);
        glTexCoord2f(0.5f - halfV, 0.5f + halfU); glVertex2f(0, height);
        glTexCoord2f(0.5f - halfV, 0.5f - halfU); glVertex2f(width, height);
        glTexCoord2f(0.5f + halfV, 0.5f - halfU); glVertex2f(width, 0);
        glTexCoord2f(0.5f + halfV, 0.5f + halfU); glVertex2f(0, 0);
        glEnd();
    }

    private void drawPanorama(float partialTicks) {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        double near = 0.05, top = near * Math.tan(Math.toRadians(120) / 2);
        glFrustum(-top, top, -top, top, near, 10);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glColor4f(1, 1, 1, 1);
        glRotatef(180, 1, 0, 0);
        glRotatef(90, 0, 0, 1);
        glEnable(GL_BLEND);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        int grid = 8;
        float time = panoramaTimer + partialTicks;
        for (int pass = 0; pass < grid * grid; pass++) {
            glPushMatrix();
            float dx = ((float) (pass % grid) / grid - 0.5f) / 64f;
            float dy = ((float) (pass / grid) / grid - 0.5f) / 64f;
            glTranslatef(dx, dy, 0);
            glRotatef((float) Math.sin(time / 400f) * 25f + 20f, 1, 0, 0);
            glRotatef(-time * 0.1f, 0, 1, 0);
            for (int face = 0; face < 6; face++) {
                glPushMatrix();
                if (face == 1) glRotatef(90, 0, 1, 0);
                if (face == 2) glRotatef(180, 0, 1, 0);
                if (face == 3) glRotatef(-90, 0, 1, 0);
                if (face == 4) glRotatef(90, 1, 0, 0);
                if (face == 5) glRotatef(-90, 1, 0, 0);
                mc.textures().bind(PANORAMA[face]);
                glColor4f(1, 1, 1, (255 / (pass + 1)) / 255f);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 0); glVertex3f(-1, -1, 1);
                glTexCoord2f(1, 0); glVertex3f(1, -1, 1);
                glTexCoord2f(1, 1); glVertex3f(1, 1, 1);
                glTexCoord2f(0, 1); glVertex3f(-1, 1, 1);
                glEnd();
                glPopMatrix();
            }
            glPopMatrix();
            glColorMask(true, true, true, false);
        }
        glColorMask(true, true, true, true);
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glDepthMask(true);
        glColor4f(1, 1, 1, 1);
    }

    private void rotateAndBlurSkybox() {
        glBindTexture(GL_TEXTURE_2D, viewportTexture);
        glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, 256, 256);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColorMask(true, true, true, false);
        glBegin(GL_QUADS);
        for (int pass = 0; pass < 3; pass++) {
            glColor4f(1, 1, 1, 1f / (pass + 1));
            float shift = (pass - 1) / 256f;
            glTexCoord2f(shift, 1); glVertex2f(width, height);
            glTexCoord2f(1 + shift, 1); glVertex2f(width, 0);
            glTexCoord2f(1 + shift, 0); glVertex2f(0, 0);
            glTexCoord2f(shift, 0); glVertex2f(0, height);
        }
        glEnd();
        glColorMask(true, true, true, true);
        glColor4f(1, 1, 1, 1);
    }

    @Override
    public void onGuiClosed() {
        if (viewportTexture != 0) glDeleteTextures(viewportTexture);
        viewportTexture = 0;
        if (logoTexture != 0) glDeleteTextures(logoTexture);
        logoTexture = 0;
    }

    /** The 20x20 globe button drawn from widgets.png (0,106), hover row +20. */
    static final class GuiButtonLanguage extends GuiButton {
        GuiButtonLanguage(int id, int x, int y) { super(id, x, y, 20, 20, ""); }

        @Override
        public void drawButton(GuiContext context, int mouseX, int mouseY) {
            if (!visible) return;
            context.textures().bind(GuiTextures.WIDGETS);
            glColor4f(1, 1, 1, 1);
            hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
            drawTexturedModalRect(xPosition, yPosition, 0, 106 + (hovered ? 20 : 0), width, height);
        }
    }
}

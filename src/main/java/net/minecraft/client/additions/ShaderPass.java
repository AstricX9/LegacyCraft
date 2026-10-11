package net.minecraft.client.additions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

/**
 * The Shaders addition: after the world is drawn (before the HUD), the
 * picture is copied into a texture and drawn back over the screen through a
 * fragment shader, one of the built-in looks in
 * {@code assets/legacycraft/shaders/} or the Custom File picked from the
 * instance's shaders folder. A shader that doesn't compile is reported once
 * and skipped until the setting or the file changes.
 */
public final class ShaderPass {
    static final String CUSTOM = "Custom File";
    private static final String VERTEX = "#version 120\nvarying vec2 uv;\n"
        + "void main() { uv = gl_MultiTexCoord0.st; gl_Position = gl_Vertex; }\n";

    private final long start = System.nanoTime();
    private int program, texture, textureWidth, textureHeight;
    /** What the program was built from (shader, file, file's modified time); rebuilt when it changes. */
    private String built;
    private long lastCheck;
    private String key = "";

    /** Draws the shader over what is on screen, if the addition is on. Problems go to {@code report} (the chat). */
    public void apply(Additions additions, int width, int height, Consumer<String> report) {
        if (!additions.isOn(Additions.SHADERS) || width <= 0 || height <= 0) return;
        long now = System.nanoTime();
        if (built == null || now - lastCheck > 1_000_000_000L) {
            lastCheck = now;
            key = key(additions);
        }
        if (!key.equals(built)) rebuild(additions, report);
        if (program == 0) return;

        if (texture == 0) texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        if (width != textureWidth || height != textureHeight) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            textureWidth = width;
            textureHeight = height;
        }
        glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);

        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_FOG);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_LIGHTING);
        glEnable(GL_TEXTURE_2D);
        glDepthMask(false);
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "scene"), 0);
        glUniform1f(glGetUniformLocation(program, "strength"), (float) additions.number(Additions.SHADERS, "strength") / 100f);
        glUniform1f(glGetUniformLocation(program, "time"), (System.nanoTime() - start) / 1e9f);
        glUniform2f(glGetUniformLocation(program, "resolution"), width, height);
        glColor4f(1, 1, 1, 1);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(-1, -1);
        glTexCoord2f(1, 0); glVertex2f(1, -1);
        glTexCoord2f(1, 1); glVertex2f(1, 1);
        glTexCoord2f(0, 1); glVertex2f(-1, 1);
        glEnd();
        glUseProgram(0);
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glPopAttrib();
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private static String key(Additions additions) {
        String shader = additions.setting(Additions.SHADERS, "shader");
        if (!CUSTOM.equals(shader)) return shader;
        Path file = customFile(additions);
        long modified = 0;
        try { if (file != null && Files.isRegularFile(file)) modified = Files.getLastModifiedTime(file).toMillis(); }
        catch (IOException ignored) {}
        return shader + "|" + file + "|" + modified;
    }

    private static Path customFile(Additions additions) {
        String name = additions.setting(Additions.SHADERS, "file");
        if (name == null || name.isEmpty()) return null;
        Addition a = additions.find(Additions.SHADERS);
        return additions.folder(a.setting("file")).resolve(name);
    }

    private void rebuild(Additions additions, Consumer<String> report) {
        built = key;
        delete();
        String shader = additions.setting(Additions.SHADERS, "shader"), name, source;
        try {
            if (CUSTOM.equals(shader)) {
                Path file = customFile(additions);
                if (file == null) { report.accept("Shaders: pick a Custom File in the addition's settings."); return; }
                name = file.getFileName().toString();
                if (!Files.isRegularFile(file)) { report.accept("Shaders: can't find " + file); return; }
                source = Files.readString(file, StandardCharsets.UTF_8);
            } else {
                name = shader.toLowerCase(Locale.ROOT).replace(" ", "") + ".fsh";
                try (InputStream in = ShaderPass.class.getResourceAsStream("/assets/legacycraft/shaders/" + name)) {
                    if (in == null) { report.accept("Shaders: there's no built-in shader called " + shader); return; }
                    source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            report.accept("Shaders: couldn't read the shader: " + e.getMessage());
            return;
        }
        program = link(name, source, report);
    }

    private static int link(String name, String fragmentSource, Consumer<String> report) {
        int vertex = compile(GL_VERTEX_SHADER, VERTEX), fragment = compile(GL_FRAGMENT_SHADER, fragmentSource);
        if (glGetShaderi(fragment, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(fragment).trim();
            System.err.println("Shader " + name + " didn't compile:\n" + log);
            report.accept("Shaders: " + name + " didn't compile: " + firstLine(log));
            glDeleteShader(vertex);
            glDeleteShader(fragment);
            return 0;
        }
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program).trim();
            System.err.println("Shader " + name + " didn't link:\n" + log);
            report.accept("Shaders: " + name + " didn't link: " + firstLine(log));
            glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        return shader;
    }

    private static String firstLine(String log) {
        int newline = log.indexOf('\n');
        return newline < 0 ? log : log.substring(0, newline);
    }

    private void delete() {
        if (program != 0) glDeleteProgram(program);
        program = 0;
    }

    public void destroy() {
        delete();
        if (texture != 0) glDeleteTextures(texture);
        texture = 0;
        textureWidth = textureHeight = 0;
        built = null;
    }
}

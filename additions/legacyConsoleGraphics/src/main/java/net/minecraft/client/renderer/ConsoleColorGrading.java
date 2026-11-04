package net.minecraft.client.renderer;

import org.lwjgl.opengl.GL;

import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;

/**
 * The console display transfer, applied to the world before HUD/menu drawing.
 * A GPU texture copy lets the native renderer keep its fog, lightmap and face
 * shading; gamma and contrast then operate on actual textured scene colours.
 * This is built into the renderer and does not load an external shader pack.
 */
public final class ConsoleColorGrading {
    private static final String VERTEX = """
        #version 120
        varying vec2 uv;
        void main() {
            gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);
            uv = gl_MultiTexCoord0.xy;
        }
        """;
    private static final String FRAGMENT = """
        #version 120
        uniform sampler2D scene;
        uniform float gammaExponent;
        uniform float contrastAmount;
        varying vec2 uv;
        void main() {
            vec4 source = texture2D(scene, uv);
            vec3 colour = pow(clamp(source.rgb, 0.0, 1.0), vec3(gammaExponent));
            vec3 curved = colour * colour * (3.0 - 2.0 * colour);
            gl_FragColor = vec4(mix(colour, curved, contrastAmount), source.a);
        }
        """;

    private int program, texture, textureWidth, textureHeight, gammaUniform, contrastUniform;
    private boolean failed;

    /** Must run with the game context current, after the world and before the HUD. */
    public void apply(LegacyConsoleGraphics.Profile profile, int width, int height, Consumer<String> report) {
        if (!profile.enabled() || failed || width <= 0 || height <= 0) return;
        if (!prepare(report)) return;
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int previousUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        try {
            glActiveTexture(GL_TEXTURE0);
            if (texture == 0) texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            if (textureWidth != width || textureHeight != height) {
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
                textureWidth = width;
                textureHeight = height;
            }
            // Copy the currently rendered world, with no CPU readback or framebuffer replacement.
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            glViewport(0, 0, width, height);
            glDisable(GL_DEPTH_TEST);
            glDepthMask(false);
            glDisable(GL_BLEND);
            glDisable(GL_ALPHA_TEST);
            glDisable(GL_FOG);
            glDisable(GL_CULL_FACE);
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_STENCIL_TEST);
            glColorMask(true, true, true, true);
            glUseProgram(program);
            glUniform1f(gammaUniform, LegacyConsoleGraphics.gammaExponent(profile));
            glUniform1f(contrastUniform, LegacyConsoleGraphics.contrastAmount(profile));
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(-1, -1);
            glTexCoord2f(1, 0); glVertex2f(1, -1);
            glTexCoord2f(1, 1); glVertex2f(1, 1);
            glTexCoord2f(0, 1); glVertex2f(-1, 1);
            glEnd();
        } finally {
            glUseProgram(previousProgram);
            glPopAttrib();
            glActiveTexture(previousUnit);
        }
    }

    private boolean prepare(Consumer<String> report) {
        if (program != 0) return true;
        int vertex = 0, fragment = 0;
        try {
            if (!GL.getCapabilities().OpenGL20) throw new IllegalStateException("OpenGL 2.0 is required");
            vertex = compile(GL_VERTEX_SHADER, VERTEX);
            fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT);
            program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
                throw new IllegalStateException(glGetProgramInfoLog(program));
            gammaUniform = glGetUniformLocation(program, "gammaExponent");
            contrastUniform = glGetUniformLocation(program, "contrastAmount");
            // Samplers default to texture unit zero; no GL program state changes during preparation.
            return true;
        } catch (RuntimeException error) {
            if (program != 0) glDeleteProgram(program);
            program = 0;
            failed = true;
            report.accept("Legacy Console Graphics colour controls unavailable: " + error.getMessage());
            return false;
        } finally {
            if (vertex != 0) glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String message = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException(message);
        }
        return shader;
    }

    public void destroy() {
        if (texture != 0) glDeleteTextures(texture);
        if (program != 0) glDeleteProgram(program);
        program = texture = textureWidth = textureHeight = 0;
        failed = false;
    }
}

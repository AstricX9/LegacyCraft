package net.minecraft.client.shaders;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.GL33.glSamplerParameteri;

/**
 * Runs an OptiFine-format shader pack (the Shader Packs addition), the way
 * OptiFine does on 1.8.9: the world is drawn into a set of colour textures
 * (colortex0-15) and depth textures through the pack's gbuffers_* programs,
 * after a shadow pass drawn from the sun; then its deferred, composite and
 * final programs run over the whole screen, and final draws the picture.
 *
 * <p>The game calls {@link #use} at each kind of drawing (sky, terrain,
 * entities, hand...); everything here does nothing unless a pack is running.
 * Problems with a pack are reported once to the chat and the game falls back
 * to normal drawing.
 */
public final class Shaders {
    private Shaders() {}

    /** Game state the uniforms read, filled in by the game each frame before {@link #beginWorld}. */
    public static final class Frame {
        public long worldTime;
        public float celestialAngle, rain, nightVision, blindness, far;
        public int eyeSkyLight, eyeBlockLight, eyeInWater, moonPhase;
        public final float[] fog = new float[3], sky = new float[3];
        public double cameraX, cameraY, cameraZ;
        public String dimension = "world0";
        public boolean hideGui;
        /** The item in the player's hand (null for none) and the light its block gives. */
        public String heldItem;
        public int heldItemLight;
        public float[] look = new float[3];
    }

    public static final Frame frame = new Frame();
    /** Block atlas texture for {@code customTexture.x = minecraft:textures/atlas/blocks.png}. */
    public static int atlasTexture;
    /** Attribute locations the terrain's extra vertex data is bound to. */
    public static final int ENTITY_ATTRIBUTE = 10, MID_TEX_ATTRIBUTE = 11, TANGENT_ATTRIBUTE = 12, MID_BLOCK_ATTRIBUTE = 13;

    private static final int COLORTEX = 16;
    private static final String[] LEGACY = {"gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"};
    /** OptiFine's fallbacks: a pack without gbuffers_water draws water with gbuffers_terrain, and so on. */
    private static final Map<String, String> FALLBACK = Map.ofEntries(
        Map.entry("gbuffers_textured", "gbuffers_basic"), Map.entry("gbuffers_textured_lit", "gbuffers_textured"),
        Map.entry("gbuffers_skybasic", "gbuffers_basic"), Map.entry("gbuffers_skytextured", "gbuffers_textured"),
        Map.entry("gbuffers_clouds", "gbuffers_textured"), Map.entry("gbuffers_terrain", "gbuffers_textured_lit"),
        Map.entry("gbuffers_damagedblock", "gbuffers_terrain"), Map.entry("gbuffers_block", "gbuffers_terrain"),
        Map.entry("gbuffers_beaconbeam", "gbuffers_textured"), Map.entry("gbuffers_item", "gbuffers_textured_lit"),
        Map.entry("gbuffers_entities", "gbuffers_textured_lit"), Map.entry("gbuffers_entities_glowing", "gbuffers_entities"),
        Map.entry("gbuffers_armor_glint", "gbuffers_textured"), Map.entry("gbuffers_spidereyes", "gbuffers_textured"),
        Map.entry("gbuffers_hand", "gbuffers_textured_lit"), Map.entry("gbuffers_weather", "gbuffers_textured_lit"),
        Map.entry("gbuffers_water", "gbuffers_terrain"), Map.entry("gbuffers_hand_water", "gbuffers_hand"),
        // Selection outlines: OptiFine on 1.8.9 has no gbuffers_line (packs' versions are for newer games).
        Map.entry("gbuffers_line", "gbuffers_basic"));

    private static ShaderPack pack;
    private static String loadedKey = "", wantedKey = "";
    private static long lastCheck;
    private static String dimension;
    private static final Map<String, Program> programs = new HashMap<>();
    private static final List<Program> deferred = new ArrayList<>(), composite = new ArrayList<>();
    private static Program finalProgram, shadowProgram, current;
    private static String currentName;
    private static boolean rendering, shadowPass;
    /** Load packs as Iris does (IS_IRIS, Iris's extra programs) rather than as OptiFine. */
    private static boolean iris;
    private static final List<Program> begin = new ArrayList<>(), prepare = new ArrayList<>();

    // Buffers.
    private static int width, height, gbufferFbo, passFbo, shadowFbo;
    private static final int[][] colortex = new int[COLORTEX][2];
    private static final int[] flip = new int[COLORTEX], formats = new int[COLORTEX];
    private static final float[] scale = new float[COLORTEX];
    private static final boolean[] clear = new boolean[COLORTEX], used = new boolean[COLORTEX];
    private static final float[][] clearColor = new float[COLORTEX][];
    private static int depthtex0, depthtex1, depthtex2;
    private static int shadowtex0, shadowtex1, shadowcolor0, shadowcolor1, shadowResolution = 2048;
    private static float shadowDistance = 160f, shadowIntervalSize = 2f, sunPathRotation;
    private static int compareSampler, noiseTexture, noiseResolution = 256, whiteTexture, normalTexture, blackTexture;
    private static final Map<String, Integer> packTextures = new HashMap<>();

    // Matrices (column-major) and camera.
    private static final float[] modelView = new float[16], modelViewInverse = new float[16], projection = new float[16],
        projectionInverse = new float[16], previousModelView = new float[16], previousProjection = new float[16],
        shadowModelView = new float[16], shadowModelViewInverse = new float[16], shadowProjection = new float[16],
        shadowProjectionInverse = new float[16];
    private static final float[] sunPosition = new float[3], moonPosition = new float[3], upPosition = new float[3];
    private static double previousX, previousY, previousZ;
    private static int frameCounter;
    private static long lastFrameNanos;
    private static float frameTime, frameTimeCounter, wetness, eyeBrightnessSmoothX, eyeBrightnessSmoothY;
    private static final Map<String, Double> customValues = new HashMap<>();
    private static final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private static Consumer<String> report = message -> {};
    private static Path debugFolder = Path.of("shaderpacks", "debug");

    /** One linked program and what it declares. */
    private static final class Program {
        final String name;
        int id;
        int[] drawBuffers = {0};
        final List<Uniform> uniforms = new ArrayList<>();
        final List<Sampler> samplers = new ArrayList<>();
        int[] blend;
        boolean blendOff;
        final List<Integer> blendOffBuffers = new ArrayList<>();
        int alphaFunc = -1;
        float alphaRef;
        final boolean[] mipmap = new boolean[COLORTEX];
        Program(String name) { this.name = name; }
    }

    private record Uniform(String name, int location, int type) {}
    private record Sampler(String name, int unit, boolean shadow) {}

    // ---- Loading -------------------------------------------------------------------------------

    /**
     * Picks the pack to run ({@code null} for none); checked about once a
     * second, so swapping the file reloads it. Call each frame with the GL
     * context current.
     */
    public static void select(Path packFile, boolean asIris, Consumer<String> chat) {
        report = chat;
        if (asIris != iris) { iris = asIris; loadedKey = ""; }
        long now = System.nanoTime();
        if (now - lastCheck > 1_000_000_000L) {
            lastCheck = now;
            long modified = 0;
            try { if (packFile != null && Files.exists(packFile)) modified = Files.getLastModifiedTime(packFile).toMillis(); }
            catch (IOException ignored) {}
            wantedKey = packFile == null ? "" : packFile + "|" + modified;
        }
        if (wantedKey.equals(loadedKey) && (pack == null || frame.dimension.equals(dimension))) return;
        boolean sameFile = wantedKey.equals(loadedKey);
        loadedKey = wantedKey;
        destroyPrograms();
        if (!sameFile) {
            destroyBuffers();
            pack = null;
            if (packFile == null) { meshFormatChanged(); return; }
            // Framebuffers with several targets and sampler objects (for shadow lookups) are OpenGL 3.3.
            if (!GL.getCapabilities().OpenGL33) {
                chat.accept("Shader packs need a graphics card with OpenGL 3.3.");
                meshFormatChanged();
                return;
            }
            debugFolder = packFile.resolveSibling("debug");
            try {
                pack = ShaderPack.load(packFile, standardMacros());
            } catch (IOException | RuntimeException e) {
                chat.accept("Shader pack " + packFile.getFileName() + " couldn't be read: " + e.getMessage());
                meshFormatChanged();
                return;
            }
            String missing = missingIrisFeatures();
            if (missing != null) {
                chat.accept("Shader pack " + pack.name + " needs Iris features LegacyCraft doesn't have yet: " + missing);
                pack = null;
                meshFormatChanged();
                return;
            }
        }
        dimension = frame.dimension;
        if (!compileAll()) {
            destroyPrograms();
            pack = null;
        }
        meshFormatChanged();
    }

    /** Iris features a pack may ask for that this pipeline provides. */
    /** Iris's renderStage values, in order (MC_RENDER_STAGE_NONE is 0). */
    private static final String[] RENDER_STAGES = {"NONE", "SKY", "SUNSET", "CUSTOM_SKY", "SUN", "MOON", "STARS", "VOID",
        "TERRAIN_SOLID", "TERRAIN_CUTOUT_MIPPED", "TERRAIN_CUTOUT", "ENTITIES", "BLOCK_ENTITIES", "DESTROY", "OUTLINE",
        "DEBUG", "HAND_SOLID", "TERRAIN_TRANSLUCENT", "TRIPWIRE", "PARTICLES", "CLOUDS", "RAIN_SNOW", "WORLD_BORDER",
        "HAND_TRANSLUCENT"};
    public static final int STAGE_NONE = 0, STAGE_SKY = 1, STAGE_SUNSET = 2, STAGE_SUN = 4, STAGE_MOON = 5, STAGE_STARS = 6, STAGE_VOID = 7;
    private static int renderStage;
    private static int entityId = -1, blockEntityId = -1;

    /** The entity about to be drawn (entityId, from entity.properties), by its 1.8.9 save name; null when done. */
    public static void entity(String name) {
        int id = name == null || pack == null ? -1 : pack.entityId(name);
        if (id == entityId) return;
        entityId = id;
        if (rendering && current != null) uniforms(current);
    }

    /** The block entity about to be drawn (blockEntityId, its block's ID from block.properties); null when done. */
    public static void blockEntity(String block, java.util.function.Function<String, String> property) {
        int id = block == null || pack == null ? -1 : pack.blockId(block, property);
        if (id == 0 && block != null) id = -1;
        if (id == blockEntityId) return;
        blockEntityId = id;
        if (rendering && current != null) uniforms(current);
    }

    /** Tells the pack (renderStage) what is being drawn; packs written for Iris tell the sun, moon and stars apart by it. */
    public static void renderStage(int stage) {
        renderStage = stage;
        if (rendering && !shadowPass && current != null) uniforms(current);
    }

    private static final java.util.Set<String> IRIS_FEATURES = java.util.Set.of("SEPARATE_HARDWARE_SAMPLERS");

    /** In Iris mode, the features the pack requires (iris.features.required) that aren't provided, or null. */
    private static String missingIrisFeatures() {
        String required = iris ? pack.property("iris.features.required") : null;
        if (required == null) return null;
        List<String> missing = new ArrayList<>();
        for (String feature : required.trim().split("\\s+"))
            if (!feature.isEmpty() && !IRIS_FEATURES.contains(feature.toUpperCase(Locale.ROOT))) missing.add(feature);
        return missing.isEmpty() ? null : String.join(", ", missing);
    }

    /** Reads a pack for its settings screen (with the GL context current, for the standard macros). */
    public static ShaderPack readForOptions(Path packFile) throws IOException { return ShaderPack.load(packFile, standardMacros()); }

    /** Loads the pack again on the next frame (after its options changed). */
    public static void reload() { loadedKey = ""; lastCheck = 0; }

    /** Whether a pack is loaded and running. */
    public static boolean active() { return pack != null && finalProgram != null; }

    /** The running pack (what the terrain mesher asks for block IDs), or null. */
    public static ShaderPack pack() { return active() ? pack : null; }

    /** Set by the world renderer: called when terrain must be rebuilt for a different vertex layout. */
    public static Runnable onMeshFormatChange = () -> {};
    private static ShaderPack meshedFor;

    private static void meshFormatChanged() {
        ShaderPack now = pack();
        if (now == meshedFor) return;
        meshedFor = now;
        onMeshFormatChange.run();
    }

    private static Map<String, String> standardMacros() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("MC_VERSION", "10809");
        String version = glGetString(GL_VERSION);
        int gl = 210;
        Matcher v = Pattern.compile("(\\d+)\\.(\\d+)").matcher(version == null ? "" : version);
        if (v.find()) gl = Integer.parseInt(v.group(1)) * 100 + Integer.parseInt(v.group(2)) * 10;
        m.put("MC_GL_VERSION", Integer.toString(gl));
        String glsl = glGetString(GL_SHADING_LANGUAGE_VERSION);
        Matcher s = Pattern.compile("(\\d+)\\.(\\d+)").matcher(glsl == null ? "" : glsl);
        m.put("MC_GLSL_VERSION", s.find() ? Integer.toString(Integer.parseInt(s.group(1)) * 100 + Integer.parseInt(s.group(2))) : "120");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        m.put(os.contains("win") ? "MC_OS_WINDOWS" : os.contains("mac") ? "MC_OS_MAC" : "MC_OS_LINUX", "");
        String vendor = String.valueOf(glGetString(GL_VENDOR)).toLowerCase(Locale.ROOT);
        m.put(vendor.contains("nvidia") ? "MC_GL_VENDOR_NVIDIA" : vendor.contains("ati") || vendor.contains("amd") ? "MC_GL_VENDOR_ATI"
            : vendor.contains("intel") ? "MC_GL_VENDOR_INTEL" : vendor.contains("mesa") ? "MC_GL_VENDOR_MESA" : "MC_GL_VENDOR_OTHER", "");
        m.put("MC_RENDER_QUALITY", "1.0");
        m.put("MC_SHADOW_QUALITY", "1.0");
        m.put("MC_HAND_DEPTH", "0.125");
        if (iris) {
            // Iris announces itself, its version and the optional features it has (iris.features.optional).
            m.put("IS_IRIS", "");
            m.put("IRIS_VERSION", "10800");
            for (String feature : IRIS_FEATURES) m.put("IRIS_FEATURE_" + feature, "");
            for (int i = 0; i < RENDER_STAGES.length; i++) m.put("MC_RENDER_STAGE_" + RENDER_STAGES[i], String.valueOf(i));
        }
        return m;
    }

    private static boolean compileAll() {
        for (int i = 0; i < COLORTEX; i++) {
            formats[i] = GL_RGBA8;
            scale[i] = 1f;
            clear[i] = true;
            clearColor[i] = null;
            used[i] = false;
        }
        String[] names = {"gbuffers_basic", "gbuffers_textured", "gbuffers_textured_lit", "gbuffers_skybasic",
            "gbuffers_skytextured", "gbuffers_clouds", "gbuffers_terrain", "gbuffers_damagedblock", "gbuffers_block",
            "gbuffers_beaconbeam", "gbuffers_item", "gbuffers_entities", "gbuffers_entities_glowing", "gbuffers_armor_glint",
            "gbuffers_spidereyes", "gbuffers_hand", "gbuffers_weather", "gbuffers_water", "gbuffers_hand_water"};
        for (String name : names) programs.put(name, compileOrSkip(name));
        shadowProgram = compileOrSkip("shadow");
        // OptiFine numbers its passes up to 15, Iris up to 99 and adds begin (before the shadow map) and prepare (after it).
        for (int i = 0; i < (iris ? 100 : 16); i++) {
            String suffix = i == 0 ? "" : Integer.toString(i);
            Program p = compileOrSkip("deferred" + suffix);
            if (p != null) deferred.add(p);
            p = compileOrSkip("composite" + suffix);
            if (p != null) composite.add(p);
            if (!iris) continue;
            p = compileOrSkip("begin" + suffix);
            if (p != null) begin.add(p);
            p = compileOrSkip("prepare" + suffix);
            if (p != null) prepare.add(p);
        }
        finalProgram = compileOrSkip("final");
        if (finalProgram == null) {
            report.accept("Shader pack " + pack.name + " has no final program for " + dimension + "; it can't run.");
            return false;
        }
        for (String key : pack.properties.keySet()) {
            Matcher m = Pattern.compile("size\\.buffer\\.(\\w+)").matcher(key);
            if (!m.matches()) continue;
            int index = bufferIndex(m.group(1));
            String[] parts = pack.properties.get(key).trim().split("\\s+");
            try { if (index >= 0) scale[index] = Float.parseFloat(parts[0]); } catch (NumberFormatException ignored) {}
        }
        return true;
    }

    private static boolean enabled(String name) {
        String value = pack.property("program." + dimension + "/" + name + ".enabled");
        if (value == null) value = pack.property("program." + name + ".enabled");
        if (value == null) return true;
        try { return Expression.parse(value).eval(n -> 0) != 0; } catch (RuntimeException e) { return true; }
    }

    private static final Pattern DRAWBUFFERS = Pattern.compile("/\\*\\s*DRAWBUFFERS\\s*:\\s*(\\w+)\\s*\\*/");
    private static final Pattern RENDERTARGETS = Pattern.compile("/\\*\\s*RENDERTARGETS\\s*:\\s*([\\d,\\s]+)\\*/");
    private static final Pattern CONST = Pattern.compile("^\\s*(?:/\\*)?\\s*const\\s+(\\w+)\\s+(\\w+)\\s*=\\s*([^;]+);");

    /** A program that doesn't build is reported and left out, like one the pack doesn't have (OptiFine does the same). */
    private static Program compileOrSkip(String name) {
        try {
            return compile(name);
        } catch (RuntimeException e) {
            report.accept("Shader pack " + pack.name + ": " + e.getMessage());
            return null;
        }
    }

    private static Program compile(String name) {
        if (!enabled(name)) return null;
        String vertex = pack.program(dimension, name, ".vsh"), fragment = pack.program(dimension, name, ".fsh");
        if (vertex == null || fragment == null) return null;
        Program p = new Program(name);
        for (String source : new String[]{vertex, fragment}) {
            for (String line : pack.activeLines(source)) {
                Matcher m = DRAWBUFFERS.matcher(line);
                if (m.find()) p.drawBuffers = m.group(1).chars().map(c -> Character.digit(c, 16)).toArray();
                m = RENDERTARGETS.matcher(line);
                if (m.find()) p.drawBuffers = java.util.Arrays.stream(m.group(1).split(",")).map(String::trim)
                    .filter(t -> !t.isEmpty()).mapToInt(Integer::parseInt).toArray();
                m = CONST.matcher(line);
                if (m.find()) constant(p, m.group(1), m.group(2), m.group(3).trim());
            }
        }
        for (int buffer : p.drawBuffers) if (buffer >= 0 && buffer < COLORTEX) used[buffer] = true;

        int vs = shader(GL_VERTEX_SHADER, vertex, name + ".vsh"), fs = shader(GL_FRAGMENT_SHADER, fragment, name + ".fsh");
        p.id = glCreateProgram();
        glAttachShader(p.id, vs);
        glAttachShader(p.id, fs);
        glBindAttribLocation(p.id, ENTITY_ATTRIBUTE, "mc_Entity");
        glBindAttribLocation(p.id, MID_TEX_ATTRIBUTE, "mc_midTexCoord");
        glBindAttribLocation(p.id, TANGENT_ATTRIBUTE, "at_tangent");
        glBindAttribLocation(p.id, MID_BLOCK_ATTRIBUTE, "at_midBlock");
        glLinkProgram(p.id);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(p.id, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(p.id);
            glDeleteProgram(p.id);
            dump(name + ".link", log);
            throw new RuntimeException(name + " didn't link: " + firstError(log));
        }
        readInterface(p);
        readBlend(p);
        return p;
    }

    private static int shader(int type, String source, String name) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            dump(name, log + "\n----\n" + numbered(source));
            throw new RuntimeException(name + " didn't compile: " + firstError(log));
        }
        return shader;
    }

    /** Writes a failed shader's log and source to shaderpacks/debug/ for whoever is fixing it. */
    private static void dump(String name, String text) {
        try {
            Path dir = debugFolder;
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name + ".txt"), text);
        } catch (IOException ignored) {}
        System.err.println("Shader pack: " + name + "\n" + firstLine(text));
    }

    private static String numbered(String source) {
        StringBuilder out = new StringBuilder();
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) out.append(i + 1).append(": ").append(lines[i]).append('\n');
        return out.toString();
    }

    /** The first line of a compile log that reports an error, skipping the warnings drivers list before it. */
    static String firstError(String log) {
        for (String line : log.split("\n")) if (line.toLowerCase(Locale.ROOT).contains("error")) return line.trim();
        return firstLine(log);
    }

    private static String firstLine(String log) {
        log = log.trim();
        int newline = log.indexOf('\n');
        return newline < 0 ? log : log.substring(0, newline);
    }

    /** The pack-wide settings programs declare as constants (buffer formats, shadow map size...). */
    private static void constant(Program p, String type, String name, String value) {
        Matcher m = Pattern.compile("(colortex\\d+|gcolor|gdepth|gnormal|composite|gaux\\d)(Format|Clear|ClearColor|MipmapEnabled)").matcher(name);
        if (m.matches()) {
            int index = bufferIndex(m.group(1));
            if (index < 0) return;
            switch (m.group(2)) {
                case "Format" -> formats[index] = format(value);
                case "Clear" -> clear[index] = !value.equals("false");
                case "ClearColor" -> clearColor[index] = numbers(value);
                default -> p.mipmap[index] = value.equals("true");
            }
            return;
        }
        try {
            switch (name) {
                case "shadowMapResolution" -> shadowResolution = (int) Double.parseDouble(value);
                case "shadowDistance" -> shadowDistance = (float) Double.parseDouble(value);
                case "shadowIntervalSize" -> shadowIntervalSize = (float) Double.parseDouble(value);
                case "sunPathRotation" -> sunPathRotation = (float) Double.parseDouble(value);
                case "noiseTextureResolution" -> noiseResolution = (int) Double.parseDouble(value);
                default -> {}
            }
        } catch (NumberFormatException ignored) {
            // A value written as an expression of macros: the default stands.
        }
    }

    private static float[] numbers(String value) {
        Matcher m = Pattern.compile("-?[\\d.]+").matcher(value);
        float[] out = {0, 0, 0, 0};
        for (int i = 0; i < 4 && m.find(); i++) out[i] = Float.parseFloat(m.group());
        return out;
    }

    private static int bufferIndex(String name) {
        if (name.startsWith("colortex")) {
            try { int i = Integer.parseInt(name.substring(8)); return i < COLORTEX ? i : -1; } catch (NumberFormatException e) { return -1; }
        }
        for (int i = 0; i < LEGACY.length; i++) if (LEGACY[i].equals(name)) return i;
        return -1;
    }

    /** An internal format name (RGBA16F, R11F_G11F_B10F...) as its GL constant, looked up in LWJGL's GL classes. */
    private static int format(String name) {
        for (Class<?> gl : new Class<?>[]{org.lwjgl.opengl.GL11.class, org.lwjgl.opengl.GL30.class, org.lwjgl.opengl.GL31.class, org.lwjgl.opengl.GL33.class}) {
            try { return gl.getField("GL_" + name).getInt(null); } catch (ReflectiveOperationException ignored) {}
        }
        return GL_RGBA8;
    }


    /** Finds the program's uniforms and gives each sampler its own texture unit. */
    private static void readInterface(Program p) {
        int count = glGetProgrami(p.id, GL_ACTIVE_UNIFORMS);
        IntBuffer size = BufferUtils.createIntBuffer(1), type = BufferUtils.createIntBuffer(1);
        int unit = 2;
        glUseProgram(p.id);
        for (int i = 0; i < count; i++) {
            String name = glGetActiveUniform(p.id, i, size, type);
            if (name.endsWith("[0]")) name = name.substring(0, name.length() - 3);
            int location = glGetUniformLocation(p.id, name);
            if (location < 0 || name.startsWith("gl_")) continue;
            int t = type.get(0);
            if (isSampler(t)) {
                // The texture being drawn with stays on unit 0 and the lightmap on 1, as the game binds them.
                int u = p.name.startsWith("gbuffers_") || p.name.equals("shadow")
                    ? switch (name) { case "tex", "texture", "gtexture" -> 0; case "lightmap" -> 1; default -> unit++; }
                    : unit++;
                if (p.name.startsWith("gbuffers_") && name.equals("gcolor")) u = 0;
                glUniform1i(location, u);
                p.samplers.add(new Sampler(name, u, t == GL_SAMPLER_2D_SHADOW));
            } else {
                p.uniforms.add(new Uniform(name, location, t));
            }
        }
        glUseProgram(0);
    }

    private static boolean isSampler(int type) {
        return switch (type) {
            case GL_SAMPLER_1D, GL_SAMPLER_2D, GL_SAMPLER_3D, GL_SAMPLER_CUBE, GL_SAMPLER_2D_SHADOW, GL_SAMPLER_1D_SHADOW,
                GL_INT_SAMPLER_2D, GL_INT_SAMPLER_3D, GL_UNSIGNED_INT_SAMPLER_2D, GL_UNSIGNED_INT_SAMPLER_3D,
                GL_SAMPLER_2D_ARRAY, GL_SAMPLER_2D_ARRAY_SHADOW -> true;
            default -> false;
        };
    }

    private static void readBlend(Program p) {
        String blend = pack.property("blend." + p.name);
        if (blend != null) {
            if (blend.trim().equalsIgnoreCase("off")) p.blendOff = true;
            else {
                String[] parts = blend.trim().split("\\s+");
                if (parts.length == 4) p.blend = new int[]{blendFactor(parts[0]), blendFactor(parts[1]), blendFactor(parts[2]), blendFactor(parts[3])};
            }
        }
        for (int i = 0; i < p.drawBuffers.length; i++) {
            int buffer = p.drawBuffers[i];
            String off = pack.property("blend." + p.name + ".colortex" + buffer);
            if (off == null && buffer < LEGACY.length) off = pack.property("blend." + p.name + "." + LEGACY[buffer]);
            if (off != null && off.trim().equalsIgnoreCase("off")) p.blendOffBuffers.add(i);
        }
        String alpha = pack.property("alphaTest." + p.name);
        if (alpha != null) {
            String[] parts = alpha.trim().split("\\s+");
            if (parts[0].equalsIgnoreCase("off")) p.alphaFunc = 0;
            else if (parts.length == 2) {
                p.alphaFunc = switch (parts[0].toUpperCase(Locale.ROOT)) {
                    case "NEVER" -> GL_NEVER; case "LESS" -> GL_LESS; case "EQUAL" -> GL_EQUAL; case "LEQUAL" -> GL_LEQUAL;
                    case "NOTEQUAL" -> GL_NOTEQUAL; case "GEQUAL" -> GL_GEQUAL; case "ALWAYS" -> GL_ALWAYS; default -> GL_GREATER;
                };
                p.alphaRef = Float.parseFloat(parts[1]);
            }
        }
    }

    private static int blendFactor(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "ZERO" -> GL_ZERO; case "ONE" -> GL_ONE; case "SRC_COLOR" -> GL_SRC_COLOR;
            case "ONE_MINUS_SRC_COLOR" -> GL_ONE_MINUS_SRC_COLOR; case "DST_COLOR" -> GL_DST_COLOR;
            case "ONE_MINUS_DST_COLOR" -> GL_ONE_MINUS_DST_COLOR; case "SRC_ALPHA" -> GL_SRC_ALPHA;
            case "ONE_MINUS_SRC_ALPHA" -> GL_ONE_MINUS_SRC_ALPHA; case "DST_ALPHA" -> GL_DST_ALPHA;
            case "ONE_MINUS_DST_ALPHA" -> GL_ONE_MINUS_DST_ALPHA; case "SRC_ALPHA_SATURATE" -> GL_SRC_ALPHA_SATURATE;
            default -> GL_ONE;
        };
    }

    private static void destroyPrograms() {
        for (Program p : programs.values()) if (p != null) glDeleteProgram(p.id);
        for (Program p : deferred) glDeleteProgram(p.id);
        for (Program p : composite) glDeleteProgram(p.id);
        for (Program p : begin) glDeleteProgram(p.id);
        for (Program p : prepare) glDeleteProgram(p.id);
        begin.clear();
        prepare.clear();
        if (finalProgram != null) glDeleteProgram(finalProgram.id);
        if (shadowProgram != null) glDeleteProgram(shadowProgram.id);
        programs.clear();
        deferred.clear();
        composite.clear();
        finalProgram = shadowProgram = current = null;
        // Buffer formats come from the programs: make them again.
        destroyBuffers();
    }

    private static void destroyBuffers() {
        for (int[] pair : colortex) for (int i = 0; i < 2; i++) { if (pair[i] != 0) glDeleteTextures(pair[i]); pair[i] = 0; }
        for (int t : new int[]{depthtex0, depthtex1, depthtex2, shadowtex0, shadowtex1, shadowcolor0, shadowcolor1, noiseTexture,
            whiteTexture, normalTexture, blackTexture}) if (t != 0) glDeleteTextures(t);
        for (int t : packTextures.values()) glDeleteTextures(t);
        packTextures.clear();
        for (int f : new int[]{gbufferFbo, passFbo, shadowFbo}) if (f != 0) glDeleteFramebuffers(f);
        depthtex0 = depthtex1 = depthtex2 = shadowtex0 = shadowtex1 = shadowcolor0 = shadowcolor1 = noiseTexture = 0;
        whiteTexture = normalTexture = blackTexture = 0;
        gbufferFbo = passFbo = shadowFbo = 0;
        width = height = 0;
    }

    // ---- Buffers --------------------------------------------------------------------------------

    private static void createBuffers(int w, int h) {
        destroyBuffers();
        width = w;
        height = h;
        // Buffers the programs read are made too, not only those they write.
        for (Program p : allPrograms()) for (Sampler s : p.samplers) { int i = bufferIndex(s.name); if (i >= 0) used[i] = true; }
        used[0] = true;
        for (int i = 0; i < COLORTEX; i++) {
            if (!used[i]) continue;
            for (int k = 0; k < 2; k++) colortex[i][k] = texture(formats[i], bufferWidth(i), bufferHeight(i), GL_LINEAR);
            flip[i] = 0;
        }
        depthtex0 = depthTexture(w, h);
        depthtex1 = depthTexture(w, h);
        depthtex2 = depthTexture(w, h);
        gbufferFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, gbufferFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthtex0, 0);
        passFbo = glGenFramebuffers();

        int res = Math.max(256, Math.min(8192, shadowResolution));
        shadowtex0 = depthTexture(res, res);
        shadowtex1 = depthTexture(res, res);
        shadowcolor0 = texture(GL_RGBA8, res, res, GL_LINEAR);
        shadowcolor1 = texture(GL_RGBA8, res, res, GL_LINEAR);
        shadowFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowtex0, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, shadowcolor0, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, shadowcolor1, 0);
        glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
        glClearColor(1, 1, 1, 1);
        glClearDepth(1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        if (compareSampler == 0) {
            compareSampler = glGenSamplers();
            glSamplerParameteri(compareSampler, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
            glSamplerParameteri(compareSampler, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
            glSamplerParameteri(compareSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glSamplerParameteri(compareSampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glSamplerParameteri(compareSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glSamplerParameteri(compareSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }
        whiteTexture = solid(255, 255, 255, 255);
        blackTexture = solid(0, 0, 0, 0);
        normalTexture = solid(127, 127, 255, 255);
        noiseTexture = loadPackTexture(pack.property("texture.noise"));
        if (noiseTexture == 0) noiseTexture = noise(noiseResolution);
        else packTextures.values().remove(noiseTexture);
    }

    private static int bufferWidth(int i) { return Math.max(1, Math.round(width * scale[i])); }
    private static int bufferHeight(int i) { return Math.max(1, Math.round(height * scale[i])); }

    private static int texture(int format, int w, int h, int filter) {
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, t);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        boolean integer = isInteger(format);
        glTexImage2D(GL_TEXTURE_2D, 0, format, w, h, 0, integer ? GL_RGBA_INTEGER : GL_RGBA, integer ? GL_INT : GL_FLOAT, (ByteBuffer) null);
        glBindTexture(GL_TEXTURE_2D, 0);
        return t;
    }

    private static boolean isInteger(int format) {
        return format == GL_R8UI || format == GL_R16UI || format == GL_R32UI || format == GL_RG8UI || format == GL_RG16UI
            || format == GL_RG32UI || format == GL_RGBA8UI || format == GL_RGBA16UI || format == GL_RGBA32UI || format == GL_R8I
            || format == GL_R16I || format == GL_R32I || format == GL_RGBA8I || format == GL_RGBA16I || format == GL_RGBA32I;
    }

    private static int depthTexture(int w, int h) {
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, t);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, w, h, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        glBindTexture(GL_TEXTURE_2D, 0);
        return t;
    }

    private static int solid(int r, int g, int b, int a) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4).put((byte) r).put((byte) g).put((byte) b).put((byte) a);
        pixel.flip();
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, t);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        glBindTexture(GL_TEXTURE_2D, 0);
        return t;
    }

    private static int noise(int size) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
        Random random = new Random(0);
        for (int i = 0; i < size * size * 4; i++) pixels.put((byte) random.nextInt(256));
        pixels.flip();
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, t);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindTexture(GL_TEXTURE_2D, 0);
        return t;
    }

    /** A PNG from the pack (path relative to its shaders folder), uploaded once; 0 if it isn't there. */
    private static int loadPackTexture(String path) {
        if (path == null) return 0;
        path = path.trim();
        if (path.startsWith("minecraft:textures/atlas/blocks")) return atlasTexture;
        Integer known = packTextures.get(path);
        if (known != null) return known;
        byte[] data = pack.bytes(path);
        int t = 0;
        if (data != null) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                if (image != null) {
                    int w = image.getWidth(), h = image.getHeight();
                    ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                        int argb = image.getRGB(x, y);
                        pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
                    }
                    pixels.flip();
                    t = glGenTextures();
                    glBindTexture(GL_TEXTURE_2D, t);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                    glBindTexture(GL_TEXTURE_2D, 0);
                }
            } catch (IOException e) {
                System.err.println("Shader pack texture " + path + ": " + e.getMessage());
            }
        }
        packTextures.put(path, t);
        return t;
    }

    private static List<Program> allPrograms() {
        List<Program> all = new ArrayList<>();
        for (Program p : programs.values()) if (p != null) all.add(p);
        all.addAll(deferred);
        all.addAll(composite);
        all.addAll(begin);
        all.addAll(prepare);
        if (shadowProgram != null) all.add(shadowProgram);
        if (finalProgram != null) all.add(finalProgram);
        return all;
    }

    // ---- The frame -----------------------------------------------------------------------------

    /**
     * Starts drawing the world into the pack's buffers. The modelview must
     * hold the camera's rotation and the projection the terrain's: they
     * become gbufferModelView and gbufferProjection.
     */
    public static void beginWorld(int w, int h) {
        if (!active()) return;
        if (w != width || h != height || gbufferFbo == 0) createBuffers(w, h);
        System.arraycopy(modelView, 0, previousModelView, 0, 16);
        System.arraycopy(projection, 0, previousProjection, 0, 16);
        read(GL_MODELVIEW_MATRIX, modelView);
        read(GL_PROJECTION_MATRIX, projection);
        invert(modelView, modelViewInverse);
        invert(projection, projectionInverse);
        if (frameCounter == 0) {
            System.arraycopy(modelView, 0, previousModelView, 0, 16);
            System.arraycopy(projection, 0, previousProjection, 0, 16);
        }
        celestialPositions();
        long now = System.nanoTime();
        frameTime = lastFrameNanos == 0 ? 0f : Math.min(1f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        frameTimeCounter = (frameTimeCounter + frameTime) % 3600f;
        frameCounter = (frameCounter + 1) % 720720;
        float k = 1f - (float) Math.pow(0.01, frameTime / 10.0);
        wetness += (frame.rain - wetness) * (frame.rain > wetness ? 1f - (float) Math.pow(0.01, frameTime / 6.0) : k);
        eyeBrightnessSmoothX += (frame.eyeBlockLight * 16 - eyeBrightnessSmoothX) * k;
        eyeBrightnessSmoothY += (frame.eyeSkyLight * 16 - eyeBrightnessSmoothY) * k;
        updateCustomUniforms();
        rendering = true;

        if (shadowProgram != null) shadowMatrices();

        glBindFramebuffer(GL_FRAMEBUFFER, gbufferFbo);
        glViewport(0, 0, width, height);
        for (int i = 0; i < COLORTEX; i++) {
            if (colortex[i][0] == 0 || !clear[i] && frameCounter > 1) continue;
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colortex[i][flip[i]], 0);
            glDrawBuffers(GL_COLOR_ATTACHMENT0);
            float[] c = clearColor[i] != null ? clearColor[i] : i == 0 ? new float[]{frame.fog[0], frame.fog[1], frame.fog[2], 1}
                : i == 1 ? new float[]{1, 1, 1, 1} : new float[]{0, 0, 0, 0};
            glViewport(0, 0, bufferWidth(i), bufferHeight(i));
            if (isInteger(formats[i])) glClearBufferiv(GL_COLOR, 0, new int[]{0, 0, 0, 0});
            else glClearBufferfv(GL_COLOR, 0, c);
        }
        glViewport(0, 0, width, height);
        glDepthMask(true);
        glClearDepth(1);
        glClear(GL_DEPTH_BUFFER_BIT);
        if (!begin.isEmpty()) {
            runPasses(begin);
            glBindFramebuffer(GL_FRAMEBUFFER, gbufferFbo);
        }
        // The lightmap coordinates come as (sky, block); packs read (block, sky) through texture matrix 1.
        glActiveTexture(GL_TEXTURE1);
        glMatrixMode(GL_TEXTURE);
        glLoadMatrixf(new float[]{0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
        glMatrixMode(GL_MODELVIEW);
        glActiveTexture(GL_TEXTURE0);
        use("gbuffers_basic");
    }

    /** Whether the world is being drawn through the pack this frame. */
    public static boolean rendering() { return rendering; }

    /** Whether the shadow map is being drawn (the world renderer skips the camera's culling then). */
    public static boolean shadowPass() { return shadowPass; }

    /** Draws with the pack's program for this kind of drawing ("gbuffers_terrain"...), or its fallback. */
    public static void use(String name) {
        if (!rendering || shadowPass) return;
        Program p = resolve(name);
        currentName = name;
        bind(p, false);
    }

    private static Program resolve(String name) {
        while (name != null) {
            Program p = programs.get(name);
            if (p != null) return p;
            name = FALLBACK.get(name);
        }
        return null;
    }

    private static void bind(Program p, boolean pass) {
        current = p;
        int[] buffers = p == null ? new int[]{0} : p.drawBuffers;
        if (!pass) {
            glBindFramebuffer(GL_FRAMEBUFFER, gbufferFbo);
            attach(buffers, false);
        }
        if (p == null) {
            glUseProgram(0);
            return;
        }
        glUseProgram(p.id);
        uniforms(p);
        samplers(p, pass);
        if (p.blendOff) glDisable(GL_BLEND);
        else if (p.blend != null) {
            glEnable(GL_BLEND);
            glBlendFuncSeparate(p.blend[0], p.blend[1], p.blend[2], p.blend[3]);
        }
        for (int slot : p.blendOffBuffers) glDisablei(GL_BLEND, slot);
        if (p.alphaFunc == 0) glDisable(GL_ALPHA_TEST);
        else if (p.alphaFunc > 0) {
            glEnable(GL_ALPHA_TEST);
            glAlphaFunc(p.alphaFunc, p.alphaRef);
        }
    }

    /** Puts the program's draw buffers on the bound framebuffer's colour slots: the main copy, or the other copy for a pass. */
    private static void attach(int[] buffers, boolean other) {
        int[] slots = new int[buffers.length];
        for (int i = 0; i < buffers.length; i++) {
            int b = buffers[i];
            int texture = b >= 0 && b < COLORTEX ? colortex[b][other ? 1 - flip[b] : flip[b]] : 0;
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, texture, 0);
            slots[i] = GL_COLOR_ATTACHMENT0 + i;
        }
        // Slots a previous program used and this one doesn't are let go.
        for (int i = buffers.length; i < 8; i++) glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, 0, 0);
        glDrawBuffers(slots);
    }

    private static void samplers(Program p, boolean pass) {
        String stage = p.name.startsWith("gbuffers_") ? "gbuffers" : p.name.startsWith("deferred") ? "deferred"
            : p.name.equals("shadow") ? "shadow" : "composite";
        for (Sampler s : p.samplers) {
            if (s.unit < 2) continue;
            int texture = samplerTexture(s.name, stage);
            glActiveTexture(GL_TEXTURE0 + s.unit);
            glBindTexture(GL_TEXTURE_2D, texture);
            glBindSampler(s.unit, s.shadow ? compareSampler : 0);
        }
        glActiveTexture(GL_TEXTURE0);
    }

    private static int samplerTexture(String name, String stage) {
        String custom = pack.property("texture." + stage + "." + name);
        if (custom != null) return loadPackTexture(custom);
        custom = pack.property("customTexture." + name);
        if (custom != null) return loadPackTexture(custom);
        int buffer = bufferIndex(name);
        if (buffer >= 0) return colortex[buffer][flip[buffer]];
        return switch (name) {
            case "depthtex0" -> depthtex0;
            case "depthtex1" -> depthtex1;
            case "depthtex2" -> depthtex2;
            case "shadow", "shadowtex0", "watershadow" -> shadowtex0;
            case "shadowtex1" -> shadowtex1;
            case "shadowcolor", "shadowcolor0" -> shadowcolor0;
            case "shadowcolor1" -> shadowcolor1;
            case "noisetex" -> noiseTexture;
            case "normals" -> normalTexture;
            case "specular" -> blackTexture;
            case "lightmap" -> whiteTexture;
            default -> 0;
        };
    }

    /** After the solid world (terrain, entities): keeps its depth as depthtex1 and runs the deferred programs. */
    public static void endOpaque() {
        if (!rendering) return;
        copyDepth(depthtex1);
        if (!deferred.isEmpty()) runPasses(deferred);
        glBindFramebuffer(GL_FRAMEBUFFER, gbufferFbo);
        glViewport(0, 0, width, height);
        use(currentName == null ? "gbuffers_basic" : currentName);
    }

    /** Before the hand: keeps the depth without it as depthtex2. */
    public static void beforeHand() {
        if (!rendering) return;
        copyDepth(depthtex2);
    }

    private static void copyDepth(int target) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, gbufferFbo);
        glBindTexture(GL_TEXTURE_2D, target);
        glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /** The world is drawn: runs the composite programs and final, which draws the picture to the screen. */
    public static void endWorld() {
        if (!rendering) return;
        runPasses(composite);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);
        fullScreen(finalProgram, true);
        rendering = false;
        glUseProgram(0);
        for (int unit = 2; unit < 32; unit++) glBindSampler(unit, 0);
        glActiveTexture(GL_TEXTURE1);
        glMatrixMode(GL_TEXTURE);
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);
        glActiveTexture(GL_TEXTURE0);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        previousX = frame.cameraX;
        previousY = frame.cameraY;
        previousZ = frame.cameraZ;
    }

    private static void runPasses(List<Program> passes) {
        glBindFramebuffer(GL_FRAMEBUFFER, passFbo);
        for (Program p : passes) {
            attach(p.drawBuffers, true);
            int first = p.drawBuffers.length > 0 ? p.drawBuffers[0] : 0;
            glViewport(0, 0, bufferWidth(first), bufferHeight(first));
            for (int i = 0; i < COLORTEX; i++) {
                if (!p.mipmap[i] || colortex[i][0] == 0) continue;
                glBindTexture(GL_TEXTURE_2D, colortex[i][flip[i]]);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
                glGenerateMipmap(GL_TEXTURE_2D);
            }
            glBindTexture(GL_TEXTURE_2D, 0);
            fullScreen(p, false);
            for (int b : p.drawBuffers) if (b >= 0 && b < COLORTEX && colortex[b][0] != 0) flip[b] = 1 - flip[b];
        }
        glViewport(0, 0, width, height);
    }

    /** Draws a quad over the whole target with {@code p}, OptiFine-style: (0,0)-(1,1) under an orthographic projection. */
    private static void fullScreen(Program p, boolean toScreen) {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, 1, 0, 1, 0, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_BLEND);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_FOG);
        glDisable(GL_LIGHTING);
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
        if (toScreen) glDrawBuffer(GL_BACK);
        bind(p, true);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex3f(0, 0, 0);
        glTexCoord2f(1, 0); glVertex3f(1, 0, 0);
        glTexCoord2f(1, 1); glVertex3f(1, 1, 0);
        glTexCoord2f(0, 1); glVertex3f(0, 1, 0);
        glEnd();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    // ---- Shadow pass ---------------------------------------------------------------------------

    /**
     * Draws the shadow map: {@code drawTerrain} draws the terrain the bound
     * matrices see, calling the runnable it is given between the solid and
     * translucent layers. Run after {@link #beginWorld}.
     */
    public static void shadowPass(Consumer<Runnable> drawTerrain) {
        if (!rendering) return;
        if (shadowProgram != null) drawShadowMap(drawTerrain);
        // Iris's prepare passes run once the shadow map is ready, before the terrain.
        if (prepare.isEmpty() && shadowProgram == null) return;
        if (!prepare.isEmpty()) runPasses(prepare);
        glBindFramebuffer(GL_FRAMEBUFFER, gbufferFbo);
        glViewport(0, 0, width, height);
        use("gbuffers_basic");
    }

    private static void drawShadowMap(Consumer<Runnable> drawTerrain) {
        int res = Math.max(256, Math.min(8192, shadowResolution));
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
        glViewport(0, 0, res, res);
        glClearColor(1, 1, 1, 1);
        glDepthMask(true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadMatrixf(shadowProjection);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadMatrixf(shadowModelView);
        glTranslated(-frame.cameraX, -frame.cameraY, -frame.cameraZ);
        current = shadowProgram;
        glUseProgram(shadowProgram.id);
        uniforms(shadowProgram);
        samplers(shadowProgram, false);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        shadowPass = true;
        try {
            drawTerrain.accept(() -> {
                glBindTexture(GL_TEXTURE_2D, shadowtex1);
                glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, res, res);
                glBindTexture(GL_TEXTURE_2D, 0);
            });
        } finally {
            shadowPass = false;
        }
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    /** OptiFine's shadow camera: looking down the sun (or moon), snapped to a grid so shadows don't shimmer as you move. */
    private static void shadowMatrices() {
        float half = shadowDistance;
        ortho(shadowProjection, -half, half, -half, half, 0.05f, 256f);
        float[] m = identity();
        m = multiply(m, translation(0, 0, -100));
        m = multiply(m, rotation(90, 1, 0, 0));
        float celestial = frame.celestialAngle;
        float angle = celestial * -360f;
        float interval = 0; // shortcut: no shadowAngleInterval stepping; OptiFine's default steps the sun every 2 degrees
        boolean day = sunAngle() <= 0.5f;
        m = multiply(m, rotation(day ? angle - interval : angle + 180f - interval, 0, 0, 1));
        m = multiply(m, rotation(sunPathRotation, 1, 0, 0));
        float step = shadowIntervalSize, halfStep = step / 2f;
        m = multiply(m, translation((float) (frame.cameraX % step) - halfStep, (float) (frame.cameraY % step) - halfStep,
            (float) (frame.cameraZ % step) - halfStep));
        System.arraycopy(m, 0, shadowModelView, 0, 16);
        invert(shadowModelView, shadowModelViewInverse);
        invert(shadowProjection, shadowProjectionInverse);
    }

    private static float sunAngle() {
        float c = frame.celestialAngle;
        return c < 0.75f ? c + 0.25f : c - 0.75f;
    }

    /** Sun, moon and up directions in view space (Shaders.setCelestialPosition). */
    private static void celestialPositions() {
        float[] m = multiply(modelView, rotation(-90, 0, 1, 0));
        m = multiply(m, rotation(sunPathRotation, 0, 0, 1));
        m = multiply(m, rotation(frame.celestialAngle * 360f, 1, 0, 0));
        transform(m, 0, 100, 0, sunPosition);
        transform(m, 0, -100, 0, moonPosition);
        transform(modelView, 0, 100, 0, upPosition);
    }

    // ---- Uniforms ------------------------------------------------------------------------------

    private static void updateCustomUniforms() {
        for (ShaderPack.CustomUniform u : pack.customUniforms) {
            double value;
            try { value = u.expression().eval(Shaders::named); } catch (RuntimeException e) { value = 0; }
            if (u.type().equals("int")) value = (long) value;
            customValues.put(u.name(), value);
        }
    }

    /** A value for the custom-uniform expressions: a standard uniform (or one of its parts), or an earlier custom one. */
    private static double named(String name) {
        Double custom = customValues.get(name);
        if (custom != null) return custom;
        String base = name, part = null;
        int dot = name.indexOf('.');
        if (dot > 0) { base = name.substring(0, dot); part = name.substring(dot + 1); }
        double[] v = value(base);
        if (v == null) return 0;
        int i = part == null ? 0 : switch (part) { case "y", "g" -> 1; case "z", "b" -> 2; case "w", "a" -> 3; default -> 0; };
        return i < v.length ? v[i] : 0;
    }

    private static void uniforms(Program p) {
        for (Uniform u : p.uniforms) {
            Double custom = customValues.get(u.name);
            double[] v = custom != null ? new double[]{custom} : value(u.name);
            if (v == null) continue;
            set(u, v);
        }
    }

    private static void set(Uniform u, double[] v) {
        int l = u.location;
        switch (u.type) {
            case GL_FLOAT -> glUniform1f(l, (float) v[0]);
            case GL_FLOAT_VEC2 -> glUniform2f(l, (float) v[0], (float) at(v, 1));
            case GL_FLOAT_VEC3 -> glUniform3f(l, (float) v[0], (float) at(v, 1), (float) at(v, 2));
            case GL_FLOAT_VEC4 -> glUniform4f(l, (float) v[0], (float) at(v, 1), (float) at(v, 2), (float) at(v, 3));
            case GL_INT, GL_BOOL -> glUniform1i(l, (int) Math.round(v[0]));
            case GL_INT_VEC2, GL_BOOL_VEC2 -> glUniform2i(l, (int) Math.round(v[0]), (int) Math.round(at(v, 1)));
            case GL_INT_VEC3, GL_BOOL_VEC3 -> glUniform3i(l, (int) Math.round(v[0]), (int) Math.round(at(v, 1)), (int) Math.round(at(v, 2)));
            case GL_FLOAT_MAT4 -> {
                if (v.length < 16) return;
                float[] f = new float[16];
                for (int i = 0; i < 16; i++) f[i] = (float) v[i];
                glUniformMatrix4fv(l, false, f);
            }
            default -> {}
        }
    }

    private static double at(double[] v, int i) { return i < v.length ? v[i] : 0; }

    private static double[] d(float... values) {
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) out[i] = values[i];
        return out;
    }

    /** The standard OptiFine uniforms; null for ones this game doesn't provide (left at 0). */
    private static double[] value(String name) {
        Frame f = frame;
        return switch (name) {
            case "gbufferModelView" -> d(modelView);
            case "gbufferModelViewInverse" -> d(modelViewInverse);
            case "gbufferProjection" -> d(projection);
            case "gbufferProjectionInverse" -> d(projectionInverse);
            case "gbufferPreviousModelView" -> d(previousModelView);
            case "gbufferPreviousProjection" -> d(previousProjection);
            case "shadowModelView" -> d(shadowModelView);
            case "shadowModelViewInverse" -> d(shadowModelViewInverse);
            case "shadowProjection" -> d(shadowProjection);
            case "shadowProjectionInverse" -> d(shadowProjectionInverse);
            case "cameraPosition", "eyePosition" -> new double[]{f.cameraX, f.cameraY, f.cameraZ};
            case "previousCameraPosition" -> new double[]{previousX, previousY, previousZ};
            case "cameraPositionInt" -> new double[]{Math.floor(f.cameraX), Math.floor(f.cameraY), Math.floor(f.cameraZ)};
            case "cameraPositionFract" -> new double[]{frac(f.cameraX), frac(f.cameraY), frac(f.cameraZ)};
            case "previousCameraPositionInt" -> new double[]{Math.floor(previousX), Math.floor(previousY), Math.floor(previousZ)};
            case "previousCameraPositionFract" -> new double[]{frac(previousX), frac(previousY), frac(previousZ)};
            case "relativeEyePosition" -> new double[]{0, 0, 0};
            case "playerLookVector" -> d(f.look);
            case "sunPosition" -> d(sunPosition);
            case "moonPosition" -> d(moonPosition);
            case "shadowLightPosition" -> d(sunAngle() <= 0.5f ? sunPosition : moonPosition);
            case "upPosition" -> d(upPosition);
            case "sunAngle" -> new double[]{sunAngle()};
            case "shadowAngle" -> new double[]{sunAngle() <= 0.5f ? sunAngle() : sunAngle() - 0.5f};
            case "worldTime" -> new double[]{f.worldTime % 24000L};
            case "worldDay" -> new double[]{f.worldTime / 24000L};
            case "moonPhase" -> new double[]{f.moonPhase};
            case "frameCounter" -> new double[]{frameCounter};
            case "frameTime" -> new double[]{frameTime};
            case "frameTimeCounter" -> new double[]{frameTimeCounter};
            case "rainStrength" -> new double[]{f.rain};
            case "wetness" -> new double[]{wetness};
            case "viewWidth" -> new double[]{width};
            case "viewHeight" -> new double[]{height};
            case "aspectRatio" -> new double[]{width / (double) Math.max(1, height)};
            case "near" -> new double[]{0.05};
            case "far" -> new double[]{f.far};
            case "fogColor" -> d(f.fog);
            case "skyColor" -> d(f.sky);
            case "eyeAltitude" -> new double[]{f.cameraY};
            case "eyeBrightness" -> new double[]{f.eyeBlockLight * 16, f.eyeSkyLight * 16};
            case "eyeBrightnessSmooth" -> new double[]{eyeBrightnessSmoothX, eyeBrightnessSmoothY};
            case "isEyeInWater" -> new double[]{f.eyeInWater};
            case "nightVision" -> new double[]{f.nightVision};
            case "blindness" -> new double[]{f.blindness};
            case "screenBrightness" -> new double[]{0.5};
            case "hideGUI" -> new double[]{f.hideGui ? 1 : 0};
            case "centerDepthSmooth" -> new double[]{1};
            case "atlasSize", "terrainTextureSize" -> new double[]{1024, 1024};
            case "terrainIconSize" -> new double[]{16};
            case "entityColor" -> new double[]{0, 0, 0, 0};
            case "fogMode" -> new double[]{GL_LINEAR};
            case "fogDensity" -> new double[]{0.1};
            case "fogStart" -> new double[]{f.far * 0.75};
            case "fogEnd" -> new double[]{f.far};
            case "heldItemId" -> new double[]{f.heldItem == null || pack == null ? -1 : pack.itemId(f.heldItem)};
            case "heldItemId2" -> new double[]{-1};
            case "heldBlockLightValue" -> new double[]{f.heldItemLight};
            case "entityId" -> new double[]{entityId};
            case "blockEntityId" -> new double[]{blockEntityId};
            case "shadowMapResolution" -> new double[]{shadowResolution};
            case "renderStage" -> new double[]{renderStage};
            case "heldBlockLightValue2", "currentRenderedItemId",
                "playerMood", "velocity", "darknessFactor", "darknessLightFactor", "isElytraFlying", "is_invisible" -> new double[]{0};
            default -> null;
        };
    }

    private static double frac(double v) { return v - Math.floor(v); }

    // ---- Matrix helpers (column-major, like GL) ------------------------------------------------

    private static void read(int which, float[] out) {
        matrixBuffer.clear();
        glGetFloatv(which, matrixBuffer);
        matrixBuffer.get(out);
    }

    private static float[] identity() { float[] m = new float[16]; m[0] = m[5] = m[10] = m[15] = 1; return m; }

    private static float[] translation(float x, float y, float z) { float[] m = identity(); m[12] = x; m[13] = y; m[14] = z; return m; }

    private static float[] rotation(float degrees, float x, float y, float z) {
        double r = Math.toRadians(degrees);
        float c = (float) Math.cos(r), s = (float) Math.sin(r), t = 1 - c;
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        x /= len; y /= len; z /= len;
        return new float[]{
            t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0,
            t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0,
            t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0,
            0, 0, 0, 1};
    }

    private static void ortho(float[] m, float l, float r, float b, float t, float n, float f) {
        java.util.Arrays.fill(m, 0);
        m[0] = 2 / (r - l);
        m[5] = 2 / (t - b);
        m[10] = -2 / (f - n);
        m[12] = -(r + l) / (r - l);
        m[13] = -(t + b) / (t - b);
        m[14] = -(f + n) / (f - n);
        m[15] = 1;
    }

    static float[] multiply(float[] a, float[] b) {
        float[] out = new float[16];
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) sum += a[k * 4 + r] * b[c * 4 + k];
                out[c * 4 + r] = sum;
            }
        return out;
    }

    private static void transform(float[] m, float x, float y, float z, float[] out) {
        for (int r = 0; r < 3; r++) out[r] = m[r] * x + m[4 + r] * y + m[8 + r] * z;
    }

    /** 4x4 inverse by cofactors; leaves zeros if singular. */
    static void invert(float[] m, float[] out) {
        float[] inv = new float[16];
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
        float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
        if (det == 0) { java.util.Arrays.fill(out, 0); return; }
        for (int i = 0; i < 16; i++) out[i] = inv[i] / det;
    }
}

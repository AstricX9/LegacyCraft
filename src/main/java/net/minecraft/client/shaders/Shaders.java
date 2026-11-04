package net.minecraft.client.shaders;

import net.minecraft.client.additions.api.ShaderMaterial;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

/** Generic optional shader-backend dispatch. No pack loader is bundled with the engine. */
public final class Shaders {
    private Shaders() {}
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

    public static int atlasTexture;
    public static final int ENTITY_ATTRIBUTE=10, MID_TEX_ATTRIBUTE=11, TANGENT_ATTRIBUTE=12, MID_BLOCK_ATTRIBUTE=13;
    public static final int STAGE_NONE=0, STAGE_SKY=1, STAGE_SUNSET=2, STAGE_SUN=4, STAGE_MOON=5, STAGE_STARS=6, STAGE_VOID=7,
        STAGE_TERRAIN_SOLID=8, STAGE_TERRAIN_TRANSLUCENT=17;
    public static Runnable onMeshFormatChange = () -> {};
    public interface Backend {
        default void select(Path path, boolean iris, Consumer<String> report) {}
        default void entity(String name) {}
        default void blockEntity(String name, Function<String,String> property) {}
        default void renderStage(int stage) {}
        default void reload() {}
        default boolean active() { return false; }
        default ShaderMaterial pack() { return null; }
        default void beginWorld(int width,int height,int atlas) {}
        default boolean rendering() { return false; }
        default boolean shadowPass() { return false; }
        default void use(String name) {}
        default void endOpaque() {}
        default void beforeHand() {}
        default void endWorld() {}
        default void shadowPass(Consumer<Runnable> terrain) {}
        default void close() {}
    }
    private static Backend backend = new Backend() {};
    public static void install(Backend value) { backend = value == null ? new Backend() {} : value; }
    public static void select(Path p, boolean i, Consumer<String> r) { backend.select(p,i,r); }
    public static void entity(String n) { backend.entity(n); }
    public static void blockEntity(String n,Function<String,String> p) { backend.blockEntity(n,p); }
    public static void renderStage(int s) { backend.renderStage(s); }
    public static void reload() { backend.reload(); }
    public static boolean active() { return backend.active(); }
    public static ShaderMaterial pack() { return backend.pack(); }
    public static void beginWorld(int w,int h) { backend.beginWorld(w,h,atlasTexture); }
    public static boolean rendering() { return backend.rendering(); }
    public static boolean shadowPass() { return backend.shadowPass(); }
    public static void use(String n) { backend.use(n); }
    public static void endOpaque() { backend.endOpaque(); }
    public static void beforeHand() { backend.beforeHand(); }
    public static void endWorld() { backend.endWorld(); }
    public static void shadowPass(Consumer<Runnable> t) { backend.shadowPass(t); }
    public static void close() { backend.close(); install(null); }
}

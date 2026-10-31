package net.minecraft.client.shaders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShaderPackTest {
    @Test
    void expressionsFollowCPrecedenceAndOptiFineFunctions() {
        assertEquals(7, Expression.parse("1 + 2 * 3").eval(n -> 0));
        assertEquals(1, Expression.parse("2 > 1 && !(3 < 1) || 0").eval(n -> 0));
        assertEquals(5, Expression.parse("if(x < 0, 1, x > 3, 5, 9)").eval(n -> n.equals("x") ? 4 : 0));
        assertEquals(1, Expression.parse("in(b, 2, 4, 6)").eval(n -> 4));
        assertEquals(0.5, Expression.parse("eyeBrightness.y / 240.0").eval(n -> n.equals("eyeBrightness.y") ? 120 : 0));
        assertEquals(3, Expression.parse("frameCounter % 4").eval(n -> 7));
        // smooth() starts at its first value.
        assertEquals(2, Expression.parse("smooth(1, v, 3, 3)").eval(n -> 2));
    }

    @Test
    void preprocessorKeepsOnlyTheLiveBranches() {
        Preprocessor p = new Preprocessor(Map.of("MC_VERSION", "10809"));
        List<String> lines = p.process("""
            #define SHADOW_QUALITY 2 //[0 1 2]
            #if MC_VERSION >= 11300
            modern
            #elif SHADOW_QUALITY > 1 && defined MC_VERSION
            legacy
            #else
            none
            #endif
            #ifndef SHADOW_QUALITY
            missing
            #endif
            """, false);
        assertTrue(lines.contains("legacy"));
        assertFalse(lines.contains("modern"));
        assertFalse(lines.contains("none"));
        assertFalse(lines.contains("missing"));
        assertEquals("2", p.macros.get("SHADOW_QUALITY"));
    }

    @Test
    void loadsIncludesOptionsPropertiesAndBlockIds(@TempDir Path dir) throws Exception {
        Path pack = dir.resolve("Pack");
        Files.createDirectories(pack.resolve("shaders/world0"));
        Files.createDirectories(pack.resolve("shaders/lib"));
        Files.writeString(pack.resolve("shaders/lib/settings.glsl"), "#define SHADOWS 1 //[0 1]\n#define FOG\n");
        Files.writeString(pack.resolve("shaders/world0/final.fsh"), "#version 130\n#include \"/lib/settings.glsl\"\nvoid main() {}\n");
        Files.writeString(pack.resolve("shaders/shaders.properties"), """
            #if SHADOWS == 0
            program.world0/shadow.enabled=false
            #endif
            #ifdef FOG
            clouds=off
            #endif
            uniform.float.framemod2 = frameCounter % 2
            """);
        Files.writeString(pack.resolve("shaders/block.properties"), """
            #if MC_VERSION >= 11300
            block.10005=short_grass
            #else
            block.10005=tallgrass double_plant:half=lower
            #endif
            block.10009=leaves:waterlogged=false
            """);
        // The player's option file, OptiFine's format: shadows off, fog switched off.
        Files.writeString(dir.resolve("Pack.txt"), "SHADOWS=0\nFOG=false\n");

        ShaderPack loaded = ShaderPack.load(pack, Map.of("MC_VERSION", "10809"));
        String source = loaded.program("world0", "final", ".fsh");
        assertTrue(source.startsWith("#version 130\n#define MC_VERSION 10809\n"), source);
        assertTrue(source.contains("#define SHADOWS 0"), source);
        assertTrue(source.contains("//#define FOG"), source);
        assertNull(loaded.program("world0", "composite", ".fsh"));
        assertEquals("false", loaded.property("program.world0/shadow.enabled"));
        assertNull(loaded.property("clouds"));
        assertEquals("framemod2", loaded.customUniforms.get(0).name());

        assertEquals(10005, loaded.blockId("tallgrass", p -> null));
        assertEquals(10005, loaded.blockId("double_plant", p -> "lower"));
        assertEquals(0, loaded.blockId("double_plant", p -> "upper"));
        // 1.8.9 leaves have no waterlogged property; that condition doesn't rule them out.
        assertEquals(10009, loaded.blockId("leaves", p -> { throw new IllegalArgumentException(p); }));
        assertEquals(0, loaded.blockId("stone", p -> null));
    }

    @Test
    void readsAndSavesTheOptionsItsScreensShow(@TempDir Path dir) throws Exception {
        Path pack = dir.resolve("Pack");
        Files.createDirectories(pack.resolve("shaders/world0"));
        Files.writeString(pack.resolve("shaders/world0/final.fsh"), """
            #version 130
            #define QUALITY 2 //[0 1 2 3]
            #define BLOOM
            //#define DOF
            #define INTERNAL 5
            const float shadowDistance = 128.0; //[64.0 128.0 256.0]
            """);
        Files.writeString(pack.resolve("shaders/shaders.properties"),
            "screen=QUALITY BLOOM [MORE]\nscreen.MORE=DOF shadowDistance\nprofile.LOW=QUALITY=0 !BLOOM\n");
        ShaderPack loaded = ShaderPack.load(pack, Map.of());
        Map<String, ShaderPack.Option> options = loaded.options();
        assertEquals(List.of("QUALITY", "BLOOM", "DOF", "shadowDistance"), List.copyOf(options.keySet()));
        assertEquals(List.of("0", "1", "2", "3"), options.get("QUALITY").values());
        assertEquals("true", options.get("BLOOM").defaultValue());
        assertEquals("false", options.get("DOF").defaultValue());
        assertEquals("128.0", options.get("shadowDistance").defaultValue());

        loaded.saveOptions(Map.of("QUALITY", "2", "DOF", "true", "shadowDistance", "256.0"));
        assertEquals("DOF=true\nshadowDistance=256.0\n", Files.readString(dir.resolve("Pack.txt")), "defaults aren't written");
        String source = ShaderPack.load(pack, Map.of()).program("world0", "final", ".fsh");
        assertTrue(source.contains("\n#define DOF"), source);
        assertTrue(source.contains("const float shadowDistance = 256.0;"), source);
        loaded.saveOptions(Map.of());
        assertFalse(Files.exists(dir.resolve("Pack.txt")), "all defaults: no file");
    }

    @Test
    void entityAndItemIdsMatch189Names(@TempDir Path dir) throws Exception {
        Path pack = dir.resolve("Pack");
        Files.createDirectories(pack.resolve("shaders"));
        Files.writeString(pack.resolve("shaders/entity.properties"), """
            #if MC_VERSION >= 11300
            entity.50012=iron_golem
            #else
            entity.50012=villager_golem
            #endif
            entity.50052=magma_cube
            entity.50016=player
            """);
        Files.writeString(pack.resolve("shaders/item.properties"), "item.44002=torch\nitem.45004=minecraft:wooden_sword bow\n");
        ShaderPack loaded = ShaderPack.load(pack, Map.of("MC_VERSION", "10809"));
        assertEquals(50012, loaded.entityId("VillagerGolem"));
        assertEquals(50052, loaded.entityId("LavaSlime"), "1.8.9's name for the magma cube");
        assertEquals(50016, loaded.entityId("player"));
        assertEquals(-1, loaded.entityId("Creeper"));
        assertEquals(44002, loaded.itemId("torch"));
        assertEquals(45004, loaded.itemId("wooden_sword"));
        assertEquals(-1, loaded.itemId("stick"));
    }

    @Test
    void extensionLinesMoveUpToTheVersionLine(@TempDir Path dir) throws Exception {
        Path pack = dir.resolve("Pack");
        Files.createDirectories(pack.resolve("shaders"));
        Files.writeString(pack.resolve("shaders/shadow.vsh"),
            "#version 130\nuniform float a;\n#if 1\n    #extension GL_ARB_shader_image_load_store : enable\n#endif\nvoid main() {}\n");
        String source = ShaderPack.load(pack, Map.of("MC_VERSION", "10809")).program("world0", "shadow", ".vsh");
        assertTrue(source.startsWith("#version 130\n#extension GL_ARB_shader_image_load_store : enable\n#define MC_VERSION 10809\n"), source);
        assertEquals(1, source.split("#extension", -1).length - 1, source);
    }

    @Test
    void compileErrorsSkipTheDriversWarnings() {
        assertEquals("0(70) : error C1503: undefined variable \"MC_RENDER_STAGE_MOON\"", Shaders.firstError(
            "0(1033) : warning C7533: global function texture2DLod is deprecated after version 120\n"
                + "0(70) : error C1503: undefined variable \"MC_RENDER_STAGE_MOON\"\n"));
        assertEquals("just a note", Shaders.firstError("just a note\nmore"));
    }

    @Test
    void matrixInverseUndoesTheMatrix() {
        float[] m = {2, 0, 0, 0, 0, 3, 0, 0, 0, 0, 4, 0, 5, 6, 7, 1};
        float[] inverse = new float[16];
        Shaders.invert(m, inverse);
        float[] product = Shaders.multiply(m, inverse);
        for (int i = 0; i < 16; i++) assertEquals(i % 5 == 0 ? 1f : 0f, product[i], 1e-6f);
    }
}

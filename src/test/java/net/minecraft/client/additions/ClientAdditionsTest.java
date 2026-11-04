package net.minecraft.client.additions;

import net.minecraft.client.additions.api.ClientAddition;
import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.*;

/** Executable discovery, independent third-party code, disable/restore, and fault isolation. */
final class ClientAdditionsTest {
    @Test
    void aNewJarRunsWithoutAnyHardCodedEngineIdAndKeepsSavedChoices(@TempDir Path dir) throws Exception {
        Path folder = Files.createDirectories(dir.resolve("additions"));
        Path jar = compile(dir, "NewModule", "return original * (float)context.number(\"factor\") / 100f;");
        Path installed = folder.resolve("new/addition.jar");
        Files.createDirectories(installed.getParent());
        Files.copy(jar, installed);
        Files.writeString(folder.resolve("new.json"), manifest("new", "NewModule", "new/addition.jar", 1));
        Path settings = dir.resolve("additions.txt");
        Files.writeString(settings, "enabled:true\nnew:true\nnew.factor:50\n");
        assertThrows(ClassNotFoundException.class, () -> Class.forName("example.NewModule"));
        Additions additions = Additions.load(settings);
        assertEquals(1, additions.runtime().loadedCount());
        assertTrue(additions.runtime().failures().isEmpty());
        assertEquals(1f, additions.runtime().fovMultiplier(2f), 0f);
        additions.setSetting("new", "factor", "75");
        assertEquals(1.5f, additions.runtime().fovMultiplier(2f), 0f, "settings apply to the loaded module immediately");
        additions.set("new", false);
        assertEquals(2f, additions.runtime().fovMultiplier(2f), 0f);
        additions.set("new", true);
        additions.setEnabled(false);
        assertEquals(2f, additions.runtime().fovMultiplier(2f), 0f);
        additions.setEnabled(true);
        additions.save();
        additions.closeRuntime();

        Files.delete(installed);
        Additions missing = Additions.load(settings);
        assertEquals(0, missing.runtime().loadedCount());
        assertTrue(missing.runtime().failures().containsKey("new"));
        assertEquals(2f, missing.runtime().fovMultiplier(2f), 0f, "missing code cannot run a hard-coded fallback");
        missing.closeRuntime();
        Files.copy(jar, installed);
        Additions restored = Additions.load(settings);
        assertTrue(restored.isOn("new"));
        assertEquals(1.5f, restored.runtime().fovMultiplier(2f), 0f);
        restored.closeRuntime();
    }

    @Test
    void aBrokenModuleDoesNotPreventOtherModulesFromRunning(@TempDir Path dir) throws Exception {
        Path folder = Files.createDirectories(dir.resolve("additions"));
        Files.copy(compile(dir, "Broken", "throw new IllegalStateException(\"deliberate test failure\");"), folder.resolve("broken.jar"));
        Files.copy(compile(dir, "Healthy", "return original * .5f;"), folder.resolve("healthy.jar"));
        Files.writeString(folder.resolve("broken.json"), manifest("broken", "Broken", "broken.jar", 1));
        Files.writeString(folder.resolve("healthy.json"), manifest("healthy", "Healthy", "healthy.jar", 1));
        Files.writeString(dir.resolve("additions.txt"), "enabled:true\nbroken:true\nhealthy:true\n");
        Additions additions = Additions.load(dir.resolve("additions.txt"));
        assertEquals(1f, additions.runtime().fovMultiplier(2f), 0f);
        assertTrue(additions.runtime().failures().containsKey("broken"));
        assertEquals(1, additions.runtime().loadedCount());
        assertEquals(1f, additions.runtime().fovMultiplier(2f), 0f);
        additions.closeRuntime();
    }

    @Test
    void unsupportedApiAndPathsOutsideTheModFolderNeverExecute(@TempDir Path dir) throws Exception {
        Path folder = Files.createDirectories(dir.resolve("additions"));
        Path jar = compile(dir, "Foreign", "return .125f;");
        Files.copy(jar, folder.resolve("future.jar"));
        Files.copy(jar, dir.resolve("outside.jar"));
        Files.writeString(folder.resolve("future.json"), manifest("future", "Foreign", "future.jar", 2));
        Files.writeString(folder.resolve("escape.json"), manifest("escape", "Foreign", "../outside.jar", 1));
        Files.writeString(dir.resolve("additions.txt"), "enabled:true\nfuture:true\nescape:true\n");
        Additions additions = Additions.load(dir.resolve("additions.txt"));
        assertEquals(0, additions.runtime().loadedCount());
        assertEquals(2, additions.runtime().failures().size());
        assertEquals(2f, additions.runtime().fovMultiplier(2f), 0f);
        additions.closeRuntime();
    }

    @Test
    void builtInModulesUseTheSameHooksAndToggleKeysDoNotConsumeEachOthersPresses() {
        Additions additions = Additions.defaults();
        additions.setEnabled(true);
        assertEquals(10, additions.runtime().loadedCount());
        assertTrue(additions.runtime().failures().isEmpty());
        additions.set(Additions.STEADY_FOV, true);
        additions.set(Additions.NO_HURT_TILT, true);
        additions.set(Additions.FULL_BRIGHT, true);
        additions.setSetting(Additions.FULL_BRIGHT, "brightness", "70");
        assertEquals(1f, additions.runtime().fovMultiplier(1.3f), 0f);
        assertFalse(additions.runtime().hurtTilt());
        assertEquals(.7f, additions.runtime().nightVision(0f), 1e-6f);
        additions.set(Additions.TOGGLE_SPRINT, true);
        additions.set(Additions.TOGGLE_SNEAK, true);
        GameSettings settings = new GameSettings();
        KeyBinding.onTransition(settings.keyBindings, settings.keyBindSprint.getKeyCode(), true);
        KeyBinding.onTransition(settings.keyBindings, settings.keyBindSneak.getKeyCode(), true);
        Player.Input input = additions.runtime().input(Player.Input.NONE, settings);
        assertTrue(input.sprint());
        assertTrue(input.sneak());
        input=additions.runtime().input(Player.Input.NONE,settings);
        assertTrue(input.sprint());
        assertTrue(input.sneak());
        additions.set(Additions.TOGGLE_SPRINT,false);
        additions.set(Additions.TOGGLE_SNEAK,false);
        KeyBinding.onTransition(settings.keyBindings,settings.keyBindSprint.getKeyCode(),true);
        KeyBinding.onTransition(settings.keyBindings,settings.keyBindSneak.getKeyCode(),true);
        input=additions.runtime().input(Player.Input.NONE,settings);
        assertFalse(input.sprint());
        assertFalse(input.sneak());
        additions.set(Additions.TOGGLE_SPRINT,true);
        additions.set(Additions.TOGGLE_SNEAK,true);
        input=additions.runtime().input(Player.Input.NONE,settings);
        assertFalse(input.sprint(),"disabled presses cannot replay when a mod is enabled");
        assertFalse(input.sneak());
        additions.runtime().reset();
        input = additions.runtime().input(Player.Input.NONE, settings);
        assertFalse(input.sprint());
        assertFalse(input.sneak());
        additions.closeRuntime();
    }

    private static String manifest(String id, String entry, String code, int api) {
        return "{\"id\":\"" + id + "\",\"category\":\"graphics\",\"scope\":\"game\",\"name\":\"" + id
            + "\",\"api\":" + api + ",\"entrypoint\":\"example." + entry + "\",\"code\":\"" + code
            + "\",\"settings\":[{\"id\":\"factor\",\"type\":\"slider\",\"min\":0,\"max\":100,\"default\":50}]}";
    }

    private static Path compile(Path dir, String name, String body) throws Exception {
        Path source = dir.resolve(name + ".java");
        Files.writeString(source, "package example; import net.minecraft.client.additions.api.*; public final class " + name
            + " implements ClientAddition { public float fovMultiplier(AdditionContext context,float original) {" + body + "} }");
        Path classes = Files.createDirectories(dir.resolve(name + "-classes"));
        String host = Path.of(ClientAddition.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-classpath", host,
            "-d", classes.toString(), source.toString());
        assertEquals(0, result, "a new mod compiles using just the public host API");
        Path jar = dir.resolve(name + ".jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("example/" + name + ".class"));
            out.write(Files.readAllBytes(classes.resolve("example/" + name + ".class")));
            out.closeEntry();
        }
        return jar;
    }
}

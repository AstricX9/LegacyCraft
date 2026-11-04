package net.legacycraft.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Launcher behaviours come from external module JARs and respect their own switches. */
final class LauncherAdditionsTest {
    @Test
    void externalLauncherModulesOwnTabSelectionAnnouncementsAndInstanceText() {
        Path install = Path.of(".");
        Map<String,Boolean> switches = new LinkedHashMap<>(Map.of("rememberTab",true,"whatsNew",true,"instanceSummary",true));
        try (var runtime = new LauncherAdditions(install,Additions.load(install),switches)) {
            assertEquals("Additions",runtime.initialTab("Additions","News"));
            assertEquals("Additions on",runtime.instanceSummary(true));
            assertEquals("Additions off",runtime.instanceSummary(false));
            var seen = new LinkedHashSet<String>();
            var messages = new ArrayList<String>();
            runtime.started(seen,values->{seen.clear();seen.addAll(values);},(title,html)->messages.add(html));
            assertEquals(1,messages.size());
            assertTrue(messages.get(0).contains("Legacy Console Graphics"));
            assertEquals(13,seen.size());
            runtime.started(seen,values->{seen.clear();seen.addAll(values);},(title,html)->messages.add(html));
            assertEquals(1,messages.size(),"startup announcements are not repeated");
            switches.put("rememberTab",false);
            switches.put("instanceSummary",false);
            assertEquals("News",runtime.initialTab("Additions","News"));
            assertEquals("",runtime.instanceSummary(true));
        }
    }

    @Test
    void deletingALauncherJarRemovesItsBehaviourWithoutAnyBuiltInFallback(@TempDir Path dir) throws Exception {
        Path folder = Files.createDirectories(dir.resolve("additions"));
        Files.copy(Path.of("additions/rememberTab.json"),folder.resolve("rememberTab.json"));
        var catalog = Additions.load(dir);
        try (var missing = new LauncherAdditions(dir,catalog,Map.of("rememberTab",true))) {
            assertEquals("News",missing.initialTab("Additions","News"));
        }
        Path module = Files.createDirectories(folder.resolve("rememberTab")).resolve("addition.jar");
        Files.copy(Path.of("additions/rememberTab/addition.jar"),module);
        try (var restored = new LauncherAdditions(dir,catalog,Map.of("rememberTab",true))) {
            assertEquals("Additions",restored.initialTab("Additions","News"));
        }
    }
}

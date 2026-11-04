package net.legacycraft.launcher;

import net.legacycraft.launcher.api.LauncherAddition;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Independent launcher-JAR discovery and generic lifecycle/UI hook dispatch. */
final class LauncherAdditions implements AutoCloseable {
    private record Loaded(Additions.Entry entry,LauncherAddition module,URLClassLoader loader) {}
    private final List<Loaded> modules=new ArrayList<>();
    private final Map<String,Boolean> switches;
    private final Additions catalog;
    LauncherAdditions(Path install,Additions catalog,Map<String,Boolean> switches) {
        this.catalog=catalog;this.switches=switches;
        Path folder=(install==null?Path.of("."):install).resolve("additions").toAbsolutePath().normalize();
        for(var entry:catalog.entries) {
            if(!entry.launcher() || entry.entrypoint().isEmpty()) continue;
            URLClassLoader loader=null;
            try {
                if(entry.api()!=1) throw new IllegalArgumentException("unsupported addition API: "+entry.api());
                Path jar=folder.resolve(entry.code()).normalize();
                if(entry.code().isEmpty() || !jar.startsWith(folder) || !Files.isRegularFile(jar)) throw new IllegalArgumentException("module JAR missing: "+jar);
                loader=new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()},LauncherAddition.class.getClassLoader());
                var module=Class.forName(entry.entrypoint(),true,loader).asSubclass(LauncherAddition.class).getDeclaredConstructor().newInstance();
                modules.add(new Loaded(entry,module,loader));
            } catch(ReflectiveOperationException|java.io.IOException|RuntimeException|LinkageError error) {
                System.err.println("Launcher addition "+entry.id()+" unavailable: "+error);
                if(loader!=null) try {loader.close();} catch(java.io.IOException ignored) {}
            }
        }
    }
    private boolean enabled(Loaded loaded) {return switches.getOrDefault(loaded.entry.id(),loaded.entry.defaultOn());}
    String initialTab(String saved,String original) {
        String value=original;
        for(var m:modules) if(enabled(m)) try {value=m.module.initialTab(saved,value);} catch(RuntimeException|LinkageError error){System.err.println(error);}
        return value;
    }
    void started(Set<String> seen,Consumer<Set<String>> saveSeen,BiConsumer<String,String> message) {
        var definitions=catalog.entries.stream().map(e->new LauncherAddition.Definition(e.id(),e.name(),e.category().title,e.description())).toList();
        for(var m:modules) try {m.module.started(new LauncherAddition.Context(enabled(m),definitions,Set.copyOf(seen),saveSeen,message));}
            catch(RuntimeException|LinkageError error){System.err.println(error);}
    }
    String instanceSummary(boolean active) {
        StringBuilder text=new StringBuilder();
        for(var m:modules) if(enabled(m)) try {String part=m.module.instanceSummary(active);if(!part.isEmpty()){if(text.length()>0)text.append("   ·   ");text.append(part);}}
            catch(RuntimeException|LinkageError error){System.err.println(error);}
        return text.toString();
    }
    public void close() {
        for(var m:modules) {try {m.module.close();m.loader.close();} catch(Exception error){System.err.println(error);}}
        modules.clear();
    }
}

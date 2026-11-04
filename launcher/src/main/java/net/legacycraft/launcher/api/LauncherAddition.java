package net.legacycraft.launcher.api;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Public entry point for an independent launcher module. */
public interface LauncherAddition extends AutoCloseable {
    record Definition(String id,String name,String category,String description) {}
    record Context(boolean enabled,List<Definition> definitions,Set<String> seen,
        Consumer<Set<String>> saveSeen,BiConsumer<String,String> message) {}
    default String initialTab(String savedTab,String original) { return original; }
    default void started(Context context) {}
    default String instanceSummary(boolean additionsEnabled) { return ""; }
    @Override default void close() {}
}

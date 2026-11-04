package net.minecraft.client.additions;

import net.minecraft.client.additions.api.AdditionContext;
import net.minecraft.client.additions.api.ClientAddition;
import net.minecraft.client.additions.api.GraphicsProfile;
import net.minecraft.client.entity.Player;
import net.minecraft.client.gui.GuiContext;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shaders.Shaders;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Loads independent module JARs and dispatches engine hooks; knows no built-in feature IDs. */
public final class ClientAdditions implements AutoCloseable {
    private record Loaded(AdditionContext context, ClientAddition module, URLClassLoader loader) {}
    private final List<Loaded> modules = new ArrayList<>();
    private final Map<String, String> failures = new LinkedHashMap<>();

    ClientAdditions(Additions additions, Path folder) {
        for (Addition definition : additions.game()) {
            if (definition.entrypoint().isEmpty()) continue;
            URLClassLoader loader = null;
            try {
                if(definition.api()!=1) throw new IllegalArgumentException("unsupported addition API: "+definition.api());
                Path jar = folder.resolve(definition.code()).normalize();
                if (definition.code().isEmpty() || !jar.startsWith(folder) || !Files.isRegularFile(jar))
                    throw new IllegalArgumentException("module JAR is missing: " + jar);
                loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, ClientAddition.class.getClassLoader());
                Class<?> entry = Class.forName(definition.entrypoint(), true, loader);
                ClientAddition module = entry.asSubclass(ClientAddition.class).getDeclaredConstructor().newInstance();
                Loaded loaded = new Loaded(new AdditionContext(additions, definition), module, loader);
                modules.add(loaded);
                Shaders.Backend backend = module.shaderBackend();
                if (backend != null) Shaders.install(backend);
            } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError error) {
                failed(definition.id(), error);
                if (loader != null) try { loader.close(); } catch (java.io.IOException ignored) {}
            }
        }
    }

    public Map<String,String> failures() { return Map.copyOf(failures); }
    public int loadedCount() { return (int)modules.stream().filter(m->!failures.containsKey(m.context.id())).count(); }

    private void failed(String id, Throwable error) {
        failures.put(id, error.toString());
        System.err.println("Addition " + id + " disabled: " + error);
    }

    private <T> T fold(T value, boolean enabledOnly, BiFunction<Loaded,T,T> action) {
        for (Loaded loaded : modules) {
            if (failures.containsKey(loaded.context.id()) || enabledOnly && !loaded.context.enabled()) continue;
            try { value = action.apply(loaded, value); }
            catch (RuntimeException | LinkageError error) { failed(loaded.context.id(), error); }
        }
        return value;
    }

    public Player.Input input(Player.Input input, GameSettings settings) {
        return fold(input, false, (m,v) -> m.module.input(m.context,v,settings));
    }
    public float nightVision(float value) { return fold(value,true,(m,v)->m.module.nightVision(m.context,v)); }
    public float fovMultiplier(float value) { return fold(value,true,(m,v)->m.module.fovMultiplier(m.context,v)); }
    public boolean hurtTilt() { return fold(true,true,(m,v)->m.module.hurtTilt(m.context,v)); }
    public void hud(ClientAddition.Hud hud) { fold(0,true,(m,v)->{m.module.hud(m.context,hud);return v;}); }
    public boolean crosshair(GuiContext gui,int width,int height) {
        return fold(false,true,(m,v)->v || m.module.crosshair(m.context,gui,width,height));
    }
    public GraphicsProfile graphics() {
        return fold(GraphicsProfile.NONE,true,(m,v)->{
            GraphicsProfile p=m.module.graphics(m.context); return p.enabled()?p:v;
        });
    }
    public ClientAddition.ShaderSelection shaderSelection() {
        return fold(null,true,(m,v)->{ var s=m.module.shaderSelection(m.context); return s==null?v:s; });
    }
    public void afterWorld(int width,int height,Consumer<String> report) {
        fold(0,true,(m,v)->{m.module.afterWorld(m.context,width,height,report);return v;});
    }
    public void reset() { fold(0,false,(m,v)->{m.module.reset();return v;}); }
    public String settingsNote(String id) {
        return fold("",false,(m,v)->m.context.id().equals(id)?m.module.settingsNote(m.context):v);
    }
    public List<ClientAddition.SettingsAction> settingsActions(String id,GuiContext gui,GuiScreen parent) {
        return fold(List.of(),false,(m,v)->m.context.id().equals(id)?m.module.settingsActions(m.context,gui,parent):v);
    }
    @Override public void close() {
        for (Loaded m:modules) {
            try { m.module.close(); } catch(RuntimeException|LinkageError error) { failed(m.context.id(),error); }
            try { m.loader.close(); } catch(java.io.IOException ignored) {}
        }
        modules.clear(); Shaders.install(null);
    }
}

package net.minecraft.client.additions.api;

import net.minecraft.client.additions.Addition;
import net.minecraft.client.additions.Additions;
import java.nio.file.Path;

/** Stable per-module access to the framework's switches, typed settings and instance files. */
public record AdditionContext(Additions additions, Addition definition) {
    public String id() { return definition.id(); }
    public boolean enabled() { return additions.isOn(id()); }
    public String setting(String key) { return additions.setting(id(), key); }
    public double number(String key) { return additions.number(id(), key); }
    public boolean settingOn(String key) { return additions.settingOn(id(), key); }
    public Path pickedFile(String key) {
        Addition.Setting setting = definition.setting(key);
        String value = setting(key);
        return setting == null || value == null || value.isEmpty() ? null : additions.folder(setting).resolve(value);
    }
}

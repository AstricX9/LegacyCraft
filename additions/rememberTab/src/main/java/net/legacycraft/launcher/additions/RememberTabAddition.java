package net.legacycraft.launcher.additions;
import net.legacycraft.launcher.api.LauncherAddition;
public final class RememberTabAddition implements LauncherAddition {
    public String initialTab(String saved,String original) {return saved==null || saved.isBlank()?original:saved;}
}

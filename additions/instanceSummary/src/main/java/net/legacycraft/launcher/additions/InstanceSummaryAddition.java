package net.legacycraft.launcher.additions;
import net.legacycraft.launcher.api.LauncherAddition;
public final class InstanceSummaryAddition implements LauncherAddition {
    public String instanceSummary(boolean enabled) {return enabled?"Additions on":"Additions off";}
}

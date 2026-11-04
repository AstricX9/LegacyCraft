package net.legacycraft.launcher.additions;
import net.legacycraft.launcher.api.LauncherAddition;
import java.util.LinkedHashSet;
public final class WhatsNewAddition implements LauncherAddition {
    public void started(Context context) {
        var fresh=context.definitions().stream().filter(e->!context.seen().contains(e.id())).toList();
        if(fresh.isEmpty()) return;
        boolean first=context.seen().isEmpty();
        var seen=new LinkedHashSet<>(context.seen());fresh.forEach(e->seen.add(e.id()));context.saveSeen().accept(seen);
        if(!context.enabled()) return;
        StringBuilder text=new StringBuilder("<html><body style='width:380px'>");
        text.append(first?"<p>LegacyCraft now has <b>Additions</b>, its own mod system: optional changes you can turn on for each instance. They're off until you turn them on, so the game stays plain 1.8.9 until you choose otherwise.</p>":"<p>This update brings new Additions:</p>");
        text.append("<ul>");
        for(var e:fresh) text.append("<li><b>").append(escape(e.name())).append("</b> (").append(escape(e.category())).append("): ").append(escape(e.description())).append("</li>");
        text.append("</ul><p>Turn them on in the <b>Additions</b> tab.</p></body></html>");
        context.message().accept(first?"Additions":"What's new",text.toString());
    }
    private static String escape(String value) {return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}

package net.legacycraft.additions.legacyConsoleGraphics;
import net.minecraft.client.additions.api.*;
import net.minecraft.client.renderer.LegacyConsoleGraphics;
import net.minecraft.client.renderer.ConsoleColorGrading;
public final class LegacyConsoleGraphicsAddition implements ClientAddition {
    private final ConsoleColorGrading grading=new ConsoleColorGrading();
    public GraphicsProfile graphics(AdditionContext context) { return LegacyConsoleGraphics.profile(context.additions()); }
    public String settingsNote(AdditionContext context) { return "Takes priority over Shader Packs while enabled."; }
    public void afterWorld(AdditionContext context,int width,int height,java.util.function.Consumer<String> report) {
        grading.apply(LegacyConsoleGraphics.profile(context.additions()),width,height,report);
    }
    public void close() { grading.destroy(); }
}

package net.legacycraft.additions.shaderPacks;
import net.minecraft.client.additions.api.*;
import net.minecraft.client.entity.Player;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.gui.*;
import static org.lwjgl.opengl.GL11.*;
public final class ShaderPacksAddition implements ClientAddition {
    @Override public ShaderSelection shaderSelection(AdditionContext context) {
        return new ShaderSelection(context.pickedFile("pack"),"Iris".equals(context.setting("loader")));
    }
    @Override public java.util.List<SettingsAction> settingsActions(AdditionContext context,GuiContext gui,GuiScreen parent) {
        var path=context.pickedFile("pack");
        return java.util.List.of(new SettingsAction("Shader Options...",path!=null,()->{
            var screen=net.minecraft.client.gui.GuiShaderOptions.open(parent,path);
            if(screen!=null) gui.displayGuiScreen(screen);
        }));
    }
    @Override public net.minecraft.client.shaders.Shaders.Backend shaderBackend() {
        return new net.minecraft.client.shaders.Shaders.Backend() {
            public void select(java.nio.file.Path p,boolean iris,java.util.function.Consumer<String> report) {
                net.minecraft.client.shaders.PackShaders.onMeshFormatChange=()->net.minecraft.client.shaders.Shaders.onMeshFormatChange.run();
                net.minecraft.client.shaders.PackShaders.select(p,iris,report);
            }
            public void entity(String n) { net.minecraft.client.shaders.PackShaders.entity(n); }
            public void blockEntity(String n,java.util.function.Function<String,String> p) { net.minecraft.client.shaders.PackShaders.blockEntity(n,p); }
            public void renderStage(int s) { net.minecraft.client.shaders.PackShaders.renderStage(s); }
            public void reload() { net.minecraft.client.shaders.PackShaders.reload(); }
            public boolean active() { return net.minecraft.client.shaders.PackShaders.active(); }
            public net.minecraft.client.additions.api.ShaderMaterial pack() { return net.minecraft.client.shaders.PackShaders.pack(); }
            public void beginWorld(int w,int h,int atlas) { net.minecraft.client.shaders.PackShaders.atlasTexture=atlas; net.minecraft.client.shaders.PackShaders.beginWorld(w,h); }
            public boolean rendering() { return net.minecraft.client.shaders.PackShaders.rendering(); }
            public boolean shadowPass() { return net.minecraft.client.shaders.PackShaders.shadowPass(); }
            public void use(String n) { net.minecraft.client.shaders.PackShaders.use(n); }
            public void endOpaque() { net.minecraft.client.shaders.PackShaders.endOpaque(); }
            public void beforeHand() { net.minecraft.client.shaders.PackShaders.beforeHand(); }
            public void endWorld() { net.minecraft.client.shaders.PackShaders.endWorld(); }
            public void shadowPass(java.util.function.Consumer<Runnable> draw) { net.minecraft.client.shaders.PackShaders.shadowPass(draw); }
        };
    }
    @Override public void close() { if(net.minecraft.client.shaders.PackShaders.active()) net.minecraft.client.shaders.PackShaders.shutdown(); }

}

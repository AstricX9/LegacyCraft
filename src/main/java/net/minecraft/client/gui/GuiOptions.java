package net.minecraft.client.gui;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.world.storage.LevelInfo;

import static org.lwjgl.opengl.GL11.*;

/**
 * The "Options" hub (master context §66). FOV slider; Difficulty (with the
 * lock) when in a singleplayer world, Realms Notifications on the title
 * screen; then the sub-screen buttons in 1.8.9's order.
 */
public class GuiOptions extends GuiScreen {
    private final GuiScreen parent;
    private GuiButton difficultyButton;
    private GuiLockIconButton lockButton;

    public GuiOptions(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        GameSettings settings = mc.settings();
        buttonList.add(GuiSlider.forOption(GameSettings.Options.FOV.ordinal(), width / 2 - 155, height / 6 - 12, settings,
            GameSettings.Options.FOV));
        int x = width / 2 - 155 + 160, y = height / 6 - 12;
        LevelInfo level = mc.currentLevel();
        if (level != null) {
            buttonList.add(difficultyButton = new GuiButton(108, x, y, 150, 20, difficultyText(level)));
            if (!level.hardcore) {
                difficultyButton.width -= 20;
                buttonList.add(lockButton = new GuiLockIconButton(109, x + difficultyButton.width, y));
                lockButton.locked = level.difficultyLocked;
                lockButton.enabled = !level.difficultyLocked;
                difficultyButton.enabled = !level.difficultyLocked;
            } else {
                difficultyButton.enabled = false;
            }
        } else {
            buttonList.add(new GuiOptionButton(GameSettings.Options.REALMS_NOTIFICATIONS.ordinal(), x, y,
                GameSettings.Options.REALMS_NOTIFICATIONS, settings.getKeyBinding(GameSettings.Options.REALMS_NOTIFICATIONS)));
        }
        buttonList.add(new GuiButton(110, width / 2 - 155, height / 6 + 48 - 6, 150, 20, I18n.format("options.skinCustomisation")));
        GuiButton secret = new GuiButton(8675309, width / 2 + 5, height / 6 + 48 - 6, 150, 20, "Super Secret Settings...");
        // 1.8.9 cycles a post-processing shader here; LegacyCraft has no shader pipeline yet (DEV-010).
        secret.enabled = false;
        buttonList.add(secret);
        buttonList.add(new GuiButton(106, width / 2 - 155, height / 6 + 72 - 6, 150, 20, I18n.format("options.sounds")));
        buttonList.add(new GuiButton(107, width / 2 + 5, height / 6 + 72 - 6, 150, 20, I18n.format("options.stream")));
        buttonList.add(new GuiButton(101, width / 2 - 155, height / 6 + 96 - 6, 150, 20, I18n.format("options.video")));
        buttonList.add(new GuiButton(100, width / 2 + 5, height / 6 + 96 - 6, 150, 20, I18n.format("options.controls")));
        buttonList.add(new GuiButton(102, width / 2 - 155, height / 6 + 120 - 6, 150, 20, I18n.format("options.language")));
        buttonList.add(new GuiButton(103, width / 2 + 5, height / 6 + 120 - 6, 150, 20, I18n.format("options.chat.title")));
        buttonList.add(new GuiButton(105, width / 2 - 155, height / 6 + 144 - 6, 150, 20, I18n.format("options.resourcepack")));
        buttonList.add(new GuiButton(104, width / 2 + 5, height / 6 + 144 - 6, 150, 20, I18n.format("options.snooper.view")));
        buttonList.add(new GuiButton(200, width / 2 - 100, height / 6 + 168, I18n.format("gui.done")));
    }

    private static String difficultyText(LevelInfo level) {
        String name = level.hardcore ? I18n.format("options.difficulty.hardcore") : I18n.format(GameSettings.DIFFICULTIES[level.difficulty & 3]);
        return I18n.format("options.difficulty") + ": " + name;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button instanceof GuiOptionButton option) {
            option.cycle(mc.settings());
            mc.saveOptions();
            return;
        }
        LevelInfo level = mc.currentLevel();
        switch (button.id) {
            case 108 -> {
                level.difficulty = (level.difficulty + 1) & 3;
                mc.settings().difficulty = level.difficulty;
                difficultyButton.displayString = difficultyText(level);
            }
            case 109 -> mc.displayGuiScreen(new GuiYesNo(this, I18n.format("difficulty.lock.title"),
                I18n.format("difficulty.lock.question", I18n.format(GameSettings.DIFFICULTIES[level.difficulty & 3])), 109));
            case 110 -> { mc.saveOptions(); mc.displayGuiScreen(new GuiCustomizeSkin(this)); }
            case 101 -> { mc.saveOptions(); mc.displayGuiScreen(new GuiVideoSettings(this)); }
            case 100 -> { mc.saveOptions(); mc.displayGuiScreen(new GuiControls(this)); }
            case 102 -> { mc.saveOptions(); mc.displayGuiScreen(new GuiLanguage(this)); }
            case 103 -> { mc.saveOptions(); mc.displayGuiScreen(new ScreenChatOptions(this)); }
            case 104 -> { mc.saveOptions(); mc.displayGuiScreen(new GuiSnooper(this)); }
            case 105 -> { mc.saveOptions(); mc.displayGuiScreen(new GuiScreenResourcePacks(this)); }
            case 106 -> { mc.saveOptions(); mc.displayGuiScreen(new GuiScreenOptionsSounds(this)); }
            case 107 -> { mc.saveOptions(); mc.displayGuiScreen(new GuiStreamUnavailable(this)); }
            case 200 -> { mc.saveOptions(); mc.displayGuiScreen(parent); }
            default -> {}
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        mc.displayGuiScreen(this);
        if (id == 109 && result && mc.currentLevel() != null) {
            mc.currentLevel().difficultyLocked = true;
            lockButton.locked = true;
            lockButton.enabled = false;
            difficultyButton.enabled = false;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        mc.saveOptions();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("options.title"), width / 2, 15, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** The 20x20 padlock next to Difficulty: widgets.png (0|20, 146/166/186) for locked|unlocked, normal/hover/disabled. */
    static final class GuiLockIconButton extends GuiButton {
        boolean locked;

        GuiLockIconButton(int id, int x, int y) { super(id, x, y, 20, 20, ""); }

        @Override
        public void drawButton(GuiContext context, int mouseX, int mouseY) {
            if (!visible) return;
            context.textures().bind(GuiTextures.WIDGETS);
            glColor4f(1, 1, 1, 1);
            hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
            int u = locked ? 0 : 20;
            int v = !enabled ? 186 : hovered ? 166 : 146;
            drawTexturedModalRect(xPosition, yPosition, u, v, width, height);
        }
    }
}

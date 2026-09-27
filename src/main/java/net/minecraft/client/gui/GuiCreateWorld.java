package net.minecraft.client.gui;

import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.SaveFormat;

import java.util.List;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;

/**
 * "Create New World" and its "More World Options..." page, following 1.8.9's
 * flow (master context §13.2, §66):
 * <ul>
 *   <li>Game Mode cycles Survival → Hardcore → Creative; Hardcore locks Allow
 *       Cheats and Bonus Chest off, Creative turns cheats on unless the player
 *       already chose.</li>
 *   <li>World Type cycles the creatable types (Debug Mode only while shift is
 *       held) and resets the generator options; Superflat and Customized show
 *       "Customize"; Debug Mode forces spectator and hides the rest.</li>
 *   <li>The seed follows {@link WorldSettings#parseSeed}; the save folder
 *       follows {@link SaveFormat#makeUsableName}.</li>
 * </ul>
 * Button positions are HYPOTHESIS until screenshot-compared.
 */
public class GuiCreateWorld extends GuiScreen {
    private final GuiScreen parent;
    private GuiTextField worldNameField, worldSeedField;
    private String saveDirName = "";
    private String gameMode = "survival";
    /** The game mode to restore when leaving the Debug world type. */
    private String gameModeBeforeDebug;
    private String gameModeDesc1 = "", gameModeDesc2 = "";
    private boolean mapFeatures = true;
    private boolean allowCheats;
    private boolean allowCheatsWasSetByUser;
    private boolean bonusChest;
    private boolean hardCoreMode;
    private boolean alreadyGenerated;
    private boolean inMoreWorldOptionsDisplay;
    private WorldType worldType = WorldType.DEFAULT;
    /** The Superflat preset or Customized JSON; empty means the type's default. */
    public String chunkProviderSettingsJson = "";
    private String pendingName;
    private String pendingSeed = "";
    private GuiButton btnGameMode, btnMoreOptions, btnMapFeatures, btnMapType, btnAllowCommands, btnBonusItems, btnCustomizeType;

    public GuiCreateWorld(GuiScreen parent) { this.parent = parent; }

    @Override
    public void updateScreen() {
        worldNameField.updateCursorCounter();
        worldSeedField.updateCursorCounter();
    }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(0, width / 2 - 155, height - 28, 150, 20, I18n.format("selectWorld.create")));
        buttonList.add(new GuiButton(1, width / 2 + 5, height - 28, 150, 20, I18n.format("gui.cancel")));
        buttonList.add(btnGameMode = new GuiButton(2, width / 2 - 75, 115, 150, 20, I18n.format("selectWorld.gameMode")));
        buttonList.add(btnMoreOptions = new GuiButton(3, width / 2 - 75, 187, 150, 20, I18n.format("selectWorld.moreWorldOptions")));
        buttonList.add(btnMapFeatures = new GuiButton(4, width / 2 - 155, 100, 150, 20, I18n.format("selectWorld.mapFeatures")));
        btnMapFeatures.visible = false;
        buttonList.add(btnBonusItems = new GuiButton(7, width / 2 + 5, 151, 150, 20, I18n.format("selectWorld.bonusItems")));
        btnBonusItems.visible = false;
        buttonList.add(btnMapType = new GuiButton(5, width / 2 + 5, 100, 150, 20, I18n.format("selectWorld.mapType")));
        btnMapType.visible = false;
        buttonList.add(btnAllowCommands = new GuiButton(6, width / 2 - 155, 151, 150, 20, I18n.format("selectWorld.allowCommands")));
        btnAllowCommands.visible = false;
        buttonList.add(btnCustomizeType = new GuiButton(8, width / 2 + 5, 120, 150, 20, I18n.format("selectWorld.customizeType")));
        btnCustomizeType.visible = false;

        String name = worldNameField != null ? worldNameField.getText() : pendingName != null ? pendingName : I18n.format("selectWorld.newWorld");
        String seed = worldSeedField != null ? worldSeedField.getText() : pendingSeed;
        boolean nameFocused = worldNameField == null || worldNameField.isFocused();
        worldNameField = new GuiTextField(9, fontRendererObj, width / 2 - 100, 60, 200, 20);
        worldNameField.setClipboard(GuiRenameWorld.clipboard(mc));
        worldNameField.setFocused(nameFocused);
        worldNameField.setText(name);
        worldNameField.setOnChange(this::onNameChanged);
        worldSeedField = new GuiTextField(10, fontRendererObj, width / 2 - 100, 60, 200, 20);
        worldSeedField.setClipboard(GuiRenameWorld.clipboard(mc));
        worldSeedField.setText(seed);
        if (hardCoreMode) { btnAllowCommands.enabled = false; btnBonusItems.enabled = false; }
        showMoreWorldOptions(inMoreWorldOptionsDisplay);
        calcSaveDirName();
        updateDisplayState();
    }

    private void onNameChanged() {
        buttonList.get(0).enabled = !worldNameField.getText().isEmpty();
        calcSaveDirName();
    }

    private void calcSaveDirName() {
        saveDirName = SaveFormat.makeUsableName(worldNameField.getText(), mc.saves()::folderExists);
    }

    private void updateDisplayState() {
        btnGameMode.displayString = I18n.format("selectWorld.gameMode") + ": " + I18n.format("selectWorld.gameMode." + gameMode);
        gameModeDesc1 = I18n.format("selectWorld.gameMode." + gameMode + ".line1");
        gameModeDesc2 = I18n.format("selectWorld.gameMode." + gameMode + ".line2");
        btnMapFeatures.displayString = I18n.format("selectWorld.mapFeatures") + " " + onOff(mapFeatures);
        btnBonusItems.displayString = I18n.format("selectWorld.bonusItems") + " " + onOff(bonusChest && !hardCoreMode);
        btnMapType.displayString = I18n.format("selectWorld.mapType") + " " + I18n.format(worldType.getTranslateName());
        btnAllowCommands.displayString = I18n.format("selectWorld.allowCommands") + " " + onOff(allowCheats && !hardCoreMode);
        buttonList.get(0).enabled = !worldNameField.getText().isEmpty();
    }

    private static String onOff(boolean value) { return I18n.format(value ? "options.on" : "options.off"); }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        switch (button.id) {
            case 1 -> mc.displayGuiScreen(parent);
            case 0 -> createWorld();
            case 2 -> {
                switch (gameMode) {
                    case "survival" -> {
                        if (!allowCheatsWasSetByUser) allowCheats = false;
                        hardCoreMode = true;
                        gameMode = "hardcore";
                        btnAllowCommands.enabled = false;
                        btnBonusItems.enabled = false;
                    }
                    case "hardcore" -> {
                        if (!allowCheatsWasSetByUser) allowCheats = true;
                        hardCoreMode = false;
                        gameMode = "creative";
                        btnAllowCommands.enabled = true;
                        btnBonusItems.enabled = true;
                    }
                    default -> {
                        if (!allowCheatsWasSetByUser) allowCheats = false;
                        gameMode = "survival";
                        hardCoreMode = false;
                        btnAllowCommands.enabled = true;
                        btnBonusItems.enabled = true;
                    }
                }
                updateDisplayState();
            }
            case 3 -> showMoreWorldOptions(!inMoreWorldOptionsDisplay);
            case 4 -> { mapFeatures = !mapFeatures; updateDisplayState(); }
            case 7 -> { bonusChest = !bonusChest; updateDisplayState(); }
            case 5 -> {
                worldType = worldType.next(isShiftKeyDown());
                chunkProviderSettingsJson = "";
                updateDisplayState();
                showMoreWorldOptions(inMoreWorldOptionsDisplay);
            }
            case 6 -> {
                allowCheatsWasSetByUser = true;
                allowCheats = !allowCheats;
                updateDisplayState();
            }
            case 8 -> {
                if (worldType == WorldType.FLAT) mc.displayGuiScreen(new GuiCreateFlatWorld(this, chunkProviderSettingsJson));
                else mc.displayGuiScreen(new GuiCustomizeWorldScreen(this, chunkProviderSettingsJson));
            }
            default -> {}
        }
    }

    private void createWorld() {
        if (alreadyGenerated) return;
        alreadyGenerated = true;
        long seed = WorldSettings.parseSeed(worldSeedField.getText(), new Random().nextLong());
        WorldSettings settings = new WorldSettings(seed, WorldSettings.GameType.getByName(gameMode), mapFeatures, hardCoreMode,
            worldType, allowCheats && !hardCoreMode, bonusChest && !hardCoreMode, chunkProviderSettingsJson);
        mc.launchIntegratedServer(saveDirName, worldNameField.getText().trim(), settings);
    }

    private void showMoreWorldOptions(boolean show) {
        inMoreWorldOptionsDisplay = show;
        if (worldType == WorldType.DEBUG_WORLD) {
            btnGameMode.visible = !show;
            btnGameMode.enabled = false;
            if (gameModeBeforeDebug == null) gameModeBeforeDebug = gameMode;
            gameMode = "spectator";
            btnMapFeatures.visible = false;
            btnBonusItems.visible = false;
            btnMapType.visible = show;
            btnAllowCommands.visible = false;
            btnCustomizeType.visible = false;
        } else {
            btnGameMode.visible = !show;
            btnGameMode.enabled = true;
            if (gameModeBeforeDebug != null) {
                gameMode = gameModeBeforeDebug;
                gameModeBeforeDebug = null;
            }
            btnMapFeatures.visible = show && worldType != WorldType.CUSTOMIZED;
            btnBonusItems.visible = show;
            btnMapType.visible = show;
            btnAllowCommands.visible = show;
            btnCustomizeType.visible = show && worldType.isCustomizable();
        }
        updateDisplayState();
        btnMoreOptions.displayString = I18n.format(show ? "gui.done" : "selectWorld.moreWorldOptions");
    }

    /** Fills the screen from an existing world for "Re-Create". */
    public void recreateFrom(String worldName, WorldSettings settings) {
        pendingName = I18n.format("selectWorld.newWorld.copyOf", worldName);
        pendingSeed = Long.toString(settings.seed());
        worldType = settings.worldType() == WorldType.DEFAULT_1_1 ? WorldType.DEFAULT : settings.worldType();
        chunkProviderSettingsJson = settings.generatorOptions();
        mapFeatures = settings.mapFeatures();
        allowCheats = settings.commandsAllowed();
        allowCheatsWasSetByUser = true;
        if (settings.hardcore()) { gameMode = "hardcore"; hardCoreMode = true; }
        else gameMode = switch (settings.gameType()) {
            case CREATIVE -> "creative";
            case SPECTATOR -> "spectator";
            case ADVENTURE -> "adventure";
            default -> "survival";
        };
    }

    public WorldType worldType() { return worldType; }

    @Override
    public void keyPressed(int key, int modifiers) {
        GuiTextField field = inMoreWorldOptionsDisplay ? worldSeedField : worldNameField;
        field.setFocused(true);
        if (field.keyPressed(key, modifiers)) { if (field == worldNameField) onNameChanged(); return; }
        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) { actionPerformed(buttonList.get(0)); return; }
        if (key == GLFW_KEY_ESCAPE) mc.displayGuiScreen(parent);
    }

    @Override
    public void charTyped(char character) {
        GuiTextField field = inMoreWorldOptionsDisplay ? worldSeedField : worldNameField;
        field.setFocused(true);
        field.charTyped(character);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (inMoreWorldOptionsDisplay) worldSeedField.mouseClicked(mouseX, mouseY, button);
        else worldNameField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("selectWorld.create"), width / 2, 20, 0xFFFFFF);
        if (inMoreWorldOptionsDisplay) {
            drawString(fontRendererObj, I18n.format("selectWorld.enterSeed"), width / 2 - 100, 47, 0xA0A0A0);
            drawString(fontRendererObj, I18n.format("selectWorld.seedInfo"), width / 2 - 100, 85, 0xA0A0A0);
            if (btnMapFeatures.visible)
                drawString(fontRendererObj, I18n.format("selectWorld.mapFeatures.info"), width / 2 - 150, 122, 0xA0A0A0);
            if (btnAllowCommands.visible)
                drawString(fontRendererObj, I18n.format("selectWorld.allowCommands.info"), width / 2 - 150, 172, 0xA0A0A0);
            worldSeedField.drawTextBox();
            if (worldType.showWorldInfoNotice()) {
                List<String> lines = fontRendererObj.listFormattedStringToWidth(I18n.format(worldType.getTranslatedInfo()),
                    btnMapType.getButtonWidth());
                int y = btnMapType.yPosition + 22;
                for (String line : lines) {
                    fontRendererObj.drawString(line, btnMapType.xPosition + 2, y, 0xA0A0A0);
                    y += FontRenderer.FONT_HEIGHT;
                }
            }
        } else {
            drawString(fontRendererObj, I18n.format("selectWorld.enterName"), width / 2 - 100, 47, 0xA0A0A0);
            drawString(fontRendererObj, I18n.format("selectWorld.resultFolder") + " " + saveDirName, width / 2 - 100, 85, 0xA0A0A0);
            worldNameField.drawTextBox();
            drawString(fontRendererObj, gameModeDesc1, width / 2 - 100, 137, 0xA0A0A0);
            drawString(fontRendererObj, gameModeDesc2, width / 2 - 100, 149, 0xA0A0A0);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

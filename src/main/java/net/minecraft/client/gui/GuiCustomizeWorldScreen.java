package net.minecraft.client.gui;

import net.minecraft.world.biome.BiomeNames;
import net.minecraft.world.gen.ChunkProviderSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;

/**
 * "Customize World Settings" for the Customized world type: four pages
 * (Basic, Ore, Advanced, Extra Advanced) of two-column sliders/toggles — the
 * last page repeats the advanced values as text boxes — plus Previous/Next
 * Page, Done, Randomize and Defaults (both confirmed first) and Presets.
 * The page titles and labels come from the language file; ranges and layout
 * are HYPOTHESIS (see {@link ChunkProviderSettings}).
 */
public class GuiCustomizeWorldScreen extends GuiScreen {
    private static final int PAGES = 4;
    private final GuiCreateWorld parent;
    private ChunkProviderSettings settings;
    private int page;
    private RowList list;
    private GuiButton previous, next;
    private boolean confirmingDefaults, confirmingRandomize;
    private final Random random = new Random();

    public GuiCustomizeWorldScreen(GuiCreateWorld parent, String json) {
        this.parent = parent;
        this.settings = ChunkProviderSettings.fromJson(json);
    }

    public String getSettingsJson() { return settings.toJson(); }
    public void loadSettings(String json) { settings = ChunkProviderSettings.fromJson(json); }

    @Override
    public void initGui() {
        buttonList.add(previous = new GuiButton(302, 20, 5, 80, 20, I18n.format("createWorld.customize.custom.prev")));
        buttonList.add(next = new GuiButton(303, width - 100, 5, 80, 20, I18n.format("createWorld.customize.custom.next")));
        buttonList.add(new GuiButton(304, width / 2 - 187, height - 27, 90, 20, I18n.format("createWorld.customize.custom.defaults")));
        buttonList.add(new GuiButton(301, width / 2 - 92, height - 27, 90, 20, I18n.format("createWorld.customize.custom.randomize")));
        buttonList.add(new GuiButton(305, width / 2 + 3, height - 27, 90, 20, I18n.format("createWorld.customize.custom.presets")));
        buttonList.add(new GuiButton(300, width / 2 + 98, height - 27, 90, 20, I18n.format("gui.done")));
        buildPage();
    }

    private void buildPage() {
        previous.enabled = page > 0;
        next.enabled = page < PAGES - 1;
        list = new RowList();
        List<ChunkProviderSettings.Field> fields = new ArrayList<>();
        int sourcePage = page == 3 ? 2 : page;
        for (ChunkProviderSettings.Field field : ChunkProviderSettings.FIELDS) if (field.page() == sourcePage) fields.add(field);
        int id = 0;
        if (page == 3) {
            for (ChunkProviderSettings.Field field : fields) {
                GuiTextField text = new GuiTextField(id++, fontRendererObj, 0, 0, 150, 20);
                text.setClipboard(GuiRenameWorld.clipboard(mc));
                text.setMaxStringLength(16);
                text.setText(formatNumber(field, settings.get(field.key())));
                text.setOnChange(() -> {
                    try { settings.set(field, Float.parseFloat(text.getText())); } catch (NumberFormatException ignored) {}
                });
                list.rows.add(new Row(label(field), null, text));
            }
            return;
        }
        GuiButton pending = null;
        for (ChunkProviderSettings.Field field : fields) {
            GuiButton control;
            if (field.kind() == ChunkProviderSettings.Kind.BOOLEAN) {
                control = new GuiButton(id++, 0, 0, 150, 20, caption(field, settings.get(field.key())));
            } else {
                float min = field.min(), max = field.max();
                control = new GuiSlider(id++, 0, 0, 150, 20, (settings.get(field.key()) - min) / (max - min),
                    position -> {
                        float value = (float) (min + (max - min) * position);
                        if (field.kind() != ChunkProviderSettings.Kind.FLOAT) value = Math.round(value);
                        return (value - min) / (max - min);
                    },
                    position -> settings.set(field, (float) (min + (max - min) * position)),
                    position -> caption(field, settings.get(field.key())));
            }
            BoundButton bound = new BoundButton(control, field);
            if (pending == null) pending = bound.button;
            else { list.rows.add(new Row(null, pending, null, bound.button)); pending = null; }
            list.bindings.add(bound);
        }
        if (pending != null) list.rows.add(new Row(null, pending, null, null));
    }

    /** "Sea Level: 63", "Caves: ON", "Biome: All", "Dirt Spawn Size: 33", "Coordinate Scale: 684.412". */
    private String caption(ChunkProviderSettings.Field field, float value) {
        String label = label(field) + ": ";
        return switch (field.kind()) {
            case BOOLEAN -> label + I18n.format(value != 0 ? "options.on" : "options.off");
            case INT -> {
                if (field.key().equals("fixedBiome")) {
                    String name = value < 0 ? I18n.format("gui.all") : BiomeNames.get((int) value);
                    yield label + (name == null ? "?" : name);
                }
                yield label + (int) value;
            }
            case FLOAT -> label + formatNumber(field, value);
        };
    }

    private static String formatNumber(ChunkProviderSettings.Field field, float value) {
        return field.kind() == ChunkProviderSettings.Kind.FLOAT ? String.format(Locale.ROOT, "%5.3f", value).trim()
            : Integer.toString((int) value);
    }

    /** Joins "tile.oreCoal.name|createWorld.customize.custom.size" into "Coal Ore Spawn Size". */
    private static String label(ChunkProviderSettings.Field field) {
        StringBuilder out = new StringBuilder();
        for (String key : field.label().split("\\|")) out.append(I18n.format(key));
        return out.toString();
    }

    private String pageTitle() {
        return I18n.format("createWorld.customize.custom.page" + page);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        switch (button.id) {
            case 300 -> {
                parent.chunkProviderSettingsJson = settings.toJson();
                mc.displayGuiScreen(parent);
            }
            case 301 -> {
                confirmingRandomize = true;
                mc.displayGuiScreen(new GuiYesNo(this, I18n.format("createWorld.customize.custom.confirmTitle"),
                    I18n.format("createWorld.customize.custom.confirm1") + " " + I18n.format("createWorld.customize.custom.confirm2"), 301));
            }
            case 302 -> { page--; rebuild(); }
            case 303 -> { page++; rebuild(); }
            case 304 -> {
                confirmingDefaults = true;
                mc.displayGuiScreen(new GuiYesNo(this, I18n.format("createWorld.customize.custom.confirmTitle"),
                    I18n.format("createWorld.customize.custom.confirm1") + " " + I18n.format("createWorld.customize.custom.confirm2"), 304));
            }
            case 305 -> mc.displayGuiScreen(new GuiCustomizePresets(this));
            default -> {}
        }
    }

    private void rebuild() {
        buttonList.clear();
        initGui();
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (result && confirmingDefaults) settings.setDefaults();
        if (result && confirmingRandomize) randomize();
        confirmingDefaults = confirmingRandomize = false;
        mc.displayGuiScreen(this);
    }

    /** Every value set uniformly within its range (booleans left alone), as a starting point for experiments. */
    private void randomize() {
        for (ChunkProviderSettings.Field field : ChunkProviderSettings.FIELDS) {
            if (field.kind() == ChunkProviderSettings.Kind.BOOLEAN) continue;
            settings.set(field, field.min() + random.nextFloat() * (field.max() - field.min()));
        }
    }

    @Override
    public void keyPressed(int key, int modifiers) {
        for (Row row : list.rows) if (row.field != null && row.field.keyPressed(key, modifiers)) return;
        if (key == GLFW_KEY_ESCAPE) mc.displayGuiScreen(parent);
    }

    @Override
    public void charTyped(char character) {
        if (character != '.' && character != '-' && !Character.isDigit(character)) return;
        for (Row row : list.rows) if (row.field != null && row.field.charTyped(character)) return;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (list.clickControls(mouseX, mouseY, button)) return;
        list.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        list.releaseControls(mouseX, mouseY);
        list.mouseReleased();
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseDragged(int mouseX, int mouseY, int button, long time) { list.mouseDragged(mouseX, mouseY); }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) { list.mouseScrolled(direction); }

    @Override
    public void updateScreen() { for (Row row : list.rows) if (row.field != null) row.field.updateCursorCounter(); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, I18n.format("options.customizeTitle"), width / 2, 2, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Page " + (page + 1) + " of " + PAGES, width / 2, 12, 0xFFFFFF);
        drawCenteredString(fontRendererObj, pageTitle(), width / 2, 22, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** A control and the setting a toggle click flips. */
    private final class BoundButton {
        final GuiButton button;
        final ChunkProviderSettings.Field field;
        BoundButton(GuiButton button, ChunkProviderSettings.Field field) { this.button = button; this.field = field; }
        void click() {
            if (field.kind() != ChunkProviderSettings.Kind.BOOLEAN) return;
            settings.set(field, settings.getBoolean(field.key()) ? 0 : 1);
            button.displayString = caption(field, settings.get(field.key()));
        }
    }

    /** One list row: a label and a text box (last page) or up to two controls. */
    private record Row(String label, GuiButton left, GuiTextField field, GuiButton right) {
        Row(String label, GuiButton left, GuiTextField field) { this(label, left, field, null); }
    }

    private final class RowList extends GuiSlot {
        final List<Row> rows = new ArrayList<>();
        final List<BoundButton> bindings = new ArrayList<>();
        private GuiButton pressed;

        RowList() {
            super(GuiCustomizeWorldScreen.this.mc, GuiCustomizeWorldScreen.this.width, GuiCustomizeWorldScreen.this.height, 32, GuiCustomizeWorldScreen.this.height - 32, 25);
            showSelectionBox = false;
        }

        @Override protected int getSize() { return rows.size(); }
        @Override protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {}
        @Override protected boolean isSelected(int index) { return false; }
        @Override public int getListWidth() { return 400; }
        @Override protected int getScrollBarX() { return width / 2 + 160; }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            Row row = rows.get(index);
            int leftX = width / 2 - 155, rightX = width / 2 + 5;
            if (row.label() != null)
                fontRendererObj.drawStringWithShadow(row.label(), leftX, y + 6, 0xFFFFFF);
            if (row.field() != null) {
                row.field().xPosition = rightX;
                row.field().yPosition = y;
                row.field().drawTextBox();
            }
            if (row.left() != null) {
                row.left().xPosition = leftX;
                row.left().yPosition = y;
                row.left().drawButton(mc, mouseX, mouseY);
            }
            if (row.right() != null) {
                row.right().xPosition = rightX;
                row.right().yPosition = y;
                row.right().drawButton(mc, mouseX, mouseY);
            }
        }

        boolean clickControls(int mouseX, int mouseY, int button) {
            for (Row other : rows) if (other.field() != null) other.field().setFocused(false);
            int index = getSlotIndexFromScreenCoords(mouseX, mouseY);
            if (button != 0 || index < 0) return false;
            {
                Row row = rows.get(index);
                int y = rowTop(index);
                if (row.field() != null) { row.field().xPosition = width / 2 + 5; row.field().yPosition = y; }
                if (row.left() != null) { row.left().xPosition = width / 2 - 155; row.left().yPosition = y; }
                if (row.right() != null) { row.right().xPosition = width / 2 + 5; row.right().yPosition = y; }
                if (row.field() != null) row.field().mouseClicked(mouseX, mouseY, button);
                for (GuiButton control : new GuiButton[]{row.left(), row.right()}) {
                    if (control == null || !control.mousePressed(mc, mouseX, mouseY)) continue;
                    control.playPressSound(mc);
                    pressed = control;
                    for (BoundButton bound : bindings) if (bound.button == control) bound.click();
                    return true;
                }
            }
            return false;
        }

        void releaseControls(int mouseX, int mouseY) {
            if (pressed != null) pressed.mouseReleased(mouseX, mouseY);
            pressed = null;
        }
    }
}

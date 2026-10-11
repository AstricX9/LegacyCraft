package net.legacycraft.launcher;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The Additions tab: LegacyCraft's own mod system, for the instance picked
 * in the bottom bar. At the top, the switch that turns Additions on or off
 * for the instance as a whole (off plays as plain 1.8.9 and takes the
 * Additions button off the title screen); then a section per category where
 * each addition is a row with its name and description on the left and an
 * On/Off switch on the right. An addition with settings also has a settings
 * button (the game's settings.png) that opens its settings under the row.
 * The Launcher section applies to every instance. Changes are saved as
 * they're made: game switches and settings to the instance's additions.txt
 * (the file the game's Additions screen edits too), launcher switches to
 * launcher_profiles.json.
 */
final class AdditionsTab extends JPanel {
    private static final int TEXT_WIDTH = 520;

    private final LauncherData data;
    private final ConsoleTab console;
    private final Runnable onChange;
    private final JLabel title = new JLabel();
    private final JPanel body = new FitWidth();
    private Profile profile;
    private Map<String, Boolean> game;
    private Map<String, String> settings = Map.of();
    /** Additions whose settings are open under their row. */
    private final java.util.Set<String> expanded = new java.util.HashSet<>();

    AdditionsTab(LauncherData data, ConsoleTab console, Runnable onChange) {
        super(new BorderLayout());
        this.data = data;
        this.console = console;
        this.onChange = onChange;
        title.setFont(Theme.BOLD);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);
        body.setBackground(Theme.PANEL);
        JScrollPane scroll = Theme.scroll(body);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        refresh();
    }

    /** A panel as wide as the scroll pane's view, so long rows wrap instead of running off the right. */
    private static final class FitWidth extends JPanel implements javax.swing.Scrollable {
        FitWidth() { super(new GridBagLayout()); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return r.height - 32; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Rebuilds the tab for the instance picked in the bottom bar; onChange (the launcher's refresh) calls it after every change. */
    void refresh() {
        profile = data.selected();
        Additions catalog = data.additions();
        title.setText(profile == null ? "Additions" : "Additions for " + profile.name);
        game = profile == null ? Map.of() : Additions.readInstance(data.gameDirFor(profile));
        settings = profile == null ? Map.of() : Additions.readSettings(data.gameDirFor(profile));
        body.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        if (catalog.entries.isEmpty() || profile == null) {
            JLabel missing = Theme.muted("<html>Couldn't find the list of Additions. Press Play once so the launcher knows "
                + "where LegacyCraft is, then come back here.</html>");
            missing.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            body.add(missing, c);
        } else {
            boolean enabled = catalog.enabled(game);
            body.add(masterRow(catalog, enabled), c);
            body.add(Theme.header("Preset", Theme.muted(enabled ? "" : "Off while Additions are off")), c);
            body.add(presets(catalog, enabled), c);
            for (Additions.Category category : Additions.Category.values()) {
                List<Additions.Entry> entries = catalog.in(category);
                if (entries.isEmpty()) continue;
                boolean launcher = category == Additions.Category.LAUNCHER;
                int on = 0;
                for (Additions.Entry e : entries) if (isOn(e)) on++;
                String count = on + " of " + entries.size() + " on";
                body.add(Theme.header(category.title, Theme.muted(launcher ? "For every instance   ·   " + count
                    : enabled ? count : "Off while Additions are off")), c);
                for (Additions.Entry e : entries) {
                    boolean active = launcher || enabled;
                    body.add(row(catalog, e, active), c);
                    if (active && !e.launcher() && !e.settings().isEmpty() && expanded.contains(e.id())) body.add(settingsPanel(e), c);
                }
            }
        }
        c.weighty = 1;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        body.add(filler, c);
        body.revalidate();
        body.repaint();
    }

    private boolean isOn(Additions.Entry e) {
        return e.launcher() ? data.launcherAddition(e.id()) : game.getOrDefault(e.id(), e.defaultOn());
    }

    /** The switch for Additions as a whole. */
    private JComponent masterRow(Additions catalog, boolean enabled) {
        JPanel row = rowPanel(18);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST; c.weightx = 1;
        JLabel name = new JLabel("Additions are " + (enabled ? "on" : "off") + " for this instance");
        name.setFont(Theme.BOLD.deriveFont(16f));
        name.setForeground(Theme.TEXT);
        row.add(name, c);
        c.gridy = 1; c.insets = new Insets(4, 0, 0, 0);
        row.add(description("LegacyCraft's own mod system: optional changes to 1.8.9, switched on one by one below. "
            + "While Additions are off this instance plays as plain 1.8.9, with no Additions button on the title screen or pause menu."), c);
        JToggleButton toggle = toggle(enabled, 80, 36);
        toggle.addActionListener(ev -> {
            game.put(Additions.ENABLED, toggle.isSelected());
            saveGame(catalog, "Additions turned " + (toggle.isSelected() ? "on" : "off") + " for " + profile.name);
        });
        c.gridx = 1; c.gridy = 0; c.gridheight = 2; c.weightx = 0; c.insets = new Insets(0, 16, 0, 0); c.anchor = GridBagConstraints.CENTER;
        row.add(toggle, c);
        return row;
    }

    /** Faithful / Recommended / Custom: set every game switch at once (Custom is what your own switches make). */
    private JComponent presets(Additions catalog, boolean enabled) {
        JPanel p = rowPanel(12);
        p.setLayout(new BorderLayout(0, 8));
        JPanel buttons = new JPanel(new java.awt.GridLayout(1, 0, 8, 0));
        buttons.setOpaque(false);
        Additions.Look current = catalog.look(game);
        for (Additions.Look look : Additions.Look.values()) {
            boolean picked = look == current;
            JToggleButton b = new JToggleButton(look.title, picked);
            b.setFont(Theme.BOLD);
            b.setPreferredSize(new Dimension(10, 32));
            b.putClientProperty(Theme.FlatButtonUI.PRIMARY, picked && enabled);
            b.setEnabled(enabled && (look != Additions.Look.CUSTOM || picked));
            b.addActionListener(ev -> {
                catalog.apply(look, game);
                saveGame(catalog, look.title + " preset for " + profile.name);
            });
            buttons.add(b);
        }
        p.add(buttons, BorderLayout.NORTH);
        p.add(Theme.muted(current.description), BorderLayout.CENTER);
        return p;
    }

    /** One addition: name over description on the left, its switch on the right. */
    private JComponent row(Additions catalog, Additions.Entry e, boolean active) {
        JPanel row = rowPanel(10);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST; c.weightx = 1;
        JLabel name = new JLabel(e.name());
        name.setFont(Theme.BOLD);
        name.setForeground(active ? Theme.TEXT : Theme.MUTED);
        row.add(name, c);
        c.gridy = 1; c.insets = new Insets(3, 0, 0, 0);
        row.add(description(e.description()), c);
        JToggleButton toggle = toggle(isOn(e), 64, 30);
        toggle.setEnabled(active);
        toggle.addActionListener(ev -> {
            if (e.launcher()) {
                data.launcherAdditions.put(e.id(), toggle.isSelected());
                data.save();
                onChange.run();
            } else {
                game.put(e.id(), toggle.isSelected());
                saveGame(catalog, e.name() + " turned " + (toggle.isSelected() ? "on" : "off") + " for " + profile.name);
            }
        });
        c.gridx = 1; c.gridy = 0; c.gridheight = 2; c.weightx = 0; c.insets = new Insets(0, 16, 0, 0); c.anchor = GridBagConstraints.CENTER;
        if (!e.launcher() && !e.settings().isEmpty()) {
            boolean open = active && expanded.contains(e.id());
            javax.swing.JButton gear = new javax.swing.JButton(settingsIcon());
            gear.setPreferredSize(new Dimension(36, 30));
            gear.setToolTipText(open ? "Hide settings" : "Settings");
            gear.putClientProperty(Theme.FlatButtonUI.PRIMARY, open);
            gear.setEnabled(active);
            gear.addActionListener(ev -> {
                if (!expanded.remove(e.id())) expanded.add(e.id());
                refresh();
            });
            row.add(gear, c);
            c.gridx = 2; c.insets = new Insets(0, 8, 0, 0);
        }
        row.add(toggle, c);
        return row;
    }

    /** An addition's settings, under its row: a line per setting, then a link back to the defaults. */
    private JComponent settingsPanel(Additions.Entry e) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Theme.HEADER);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
            BorderFactory.createEmptyBorder(6, 30, 10, 14)));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        for (Additions.Setting s : e.settings()) {
            c.gridx = 0; c.weightx = 1; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(6, 0, 0, 0);
            JPanel text = new JPanel(new BorderLayout(0, 2));
            text.setOpaque(false);
            JLabel name = new JLabel(s.name());
            name.setFont(Theme.BOLD);
            name.setForeground(Theme.TEXT);
            text.add(name, BorderLayout.NORTH);
            if (!s.description().isEmpty())
                text.add(Theme.muted("<html><div style='width:" + (TEXT_WIDTH - 220) + "px'>" + s.description() + "</div></html>"), BorderLayout.CENTER);
            panel.add(text, c);
            c.gridx = 1; c.weightx = 0; c.anchor = GridBagConstraints.EAST; c.insets = new Insets(6, 16, 0, 0);
            panel.add(control(e, s), c);
            c.gridy++;
        }
        c.gridx = 0; c.gridwidth = 2; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(10, 0, 0, 0);
        panel.add(Theme.link("Reset to defaults", () -> {
            for (Additions.Setting s : e.settings()) saveSetting(e, s, null);
            refresh();
        }), c);
        return panel;
    }

    /** The control for one setting: a switch, a slider with its value, a list of options, or a list of files with a folder link. */
    private JComponent control(Additions.Entry e, Additions.Setting s) {
        String value = Additions.setting(settings, e, s);
        switch (s.type()) {
            case Additions.Setting.TOGGLE -> {
                JToggleButton t = toggle(Boolean.parseBoolean(value), 64, 30);
                t.addActionListener(ev -> {
                    saveSetting(e, s, Boolean.toString(t.isSelected()));
                    t.setText(t.isSelected() ? "On" : "Off");
                    t.putClientProperty(Theme.FlatButtonUI.PRIMARY, t.isSelected());
                    t.repaint();
                });
                return t;
            }
            case Additions.Setting.SLIDER -> {
                int steps = (int) Math.max(1, Math.round((s.max() - s.min()) / (s.step() > 0 ? s.step() : 1)));
                double current = Double.parseDouble(value);
                javax.swing.JSlider slider = new javax.swing.JSlider(0, steps, (int) Math.round((current - s.min()) / (s.max() - s.min()) * steps));
                slider.setOpaque(false);
                slider.setPreferredSize(new Dimension(170, 30));
                JLabel shown = new JLabel(value + s.unit());
                shown.setForeground(Theme.TEXT);
                shown.setPreferredSize(new Dimension(50, 30));
                slider.addChangeListener(ev -> {
                    String v = s.clean(Double.toString(s.min() + (s.max() - s.min()) * slider.getValue() / steps));
                    shown.setText(v + s.unit());
                    if (!slider.getValueIsAdjusting()) saveSetting(e, s, v);
                });
                JPanel p = new JPanel(new BorderLayout(8, 0));
                p.setOpaque(false);
                p.add(slider, BorderLayout.CENTER);
                p.add(shown, BorderLayout.EAST);
                return p;
            }
            case Additions.Setting.CHOICE -> {
                javax.swing.JComboBox<String> box = new javax.swing.JComboBox<>(s.options().toArray(new String[0]));
                box.setSelectedItem(value);
                box.setPreferredSize(new Dimension(228, 30));
                box.addActionListener(ev -> saveSetting(e, s, (String) box.getSelectedItem()));
                return box;
            }
            default -> {
                javax.swing.JComboBox<String> box = new javax.swing.JComboBox<>();
                Path gameDir = data.gameDirFor(profile);
                Runnable fill = () -> {
                    String picked = Additions.setting(Additions.readSettings(gameDir), e, s);
                    java.awt.event.ActionListener[] listeners = box.getActionListeners();
                    for (java.awt.event.ActionListener l : listeners) box.removeActionListener(l);
                    box.removeAllItems();
                    box.addItem(NO_FILE);
                    for (String f : s.files(gameDir)) box.addItem(f);
                    if (!picked.isEmpty() && !s.files(gameDir).contains(picked)) box.addItem(picked);
                    box.setSelectedItem(picked.isEmpty() ? NO_FILE : picked);
                    for (java.awt.event.ActionListener l : listeners) box.addActionListener(l);
                };
                fill.run();
                box.addActionListener(ev -> {
                    Object item = box.getSelectedItem();
                    if (item != null) saveSetting(e, s, NO_FILE.equals(item) ? "" : (String) item);
                });
                // New files in the folder show up the next time the list opens.
                box.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                    @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent ev) { fill.run(); }
                    @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent ev) {}
                    @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent ev) {}
                });
                box.setPreferredSize(new Dimension(160, 30));
                JPanel p = new JPanel(new BorderLayout(10, 0));
                p.setOpaque(false);
                p.add(box, BorderLayout.CENTER);
                p.add(Theme.link("Folder", () -> {
                    Path dir = gameDir.resolve(s.folder());
                    try {
                        java.nio.file.Files.createDirectories(dir);
                        java.awt.Desktop.getDesktop().open(dir.toFile());
                    } catch (IOException | RuntimeException ex) {
                        Windows.error(this, "Additions", "Couldn't open " + dir + ":\n" + ex.getMessage());
                    }
                }), BorderLayout.EAST);
                p.setPreferredSize(new Dimension(228, 30));
                return p;
            }
        }
    }

    private static final String NO_FILE = "None";

    /** Saves one setting to the instance's additions.txt (null puts it back to its default). */
    private void saveSetting(Additions.Entry e, Additions.Setting s, String value) {
        Path dir = data.gameDirFor(profile);
        String key = e.id() + "." + s.id();
        try {
            Additions.writeSetting(dir, key, value);
            settings = Additions.readSettings(dir);
            console.log(e.name() + ": " + s.name() + (value == null ? " reset" : " set to " + (value.isEmpty() ? NO_FILE : value + s.unit()))
                + " for " + profile.name);
        } catch (IOException ex) {
            Windows.error(this, "Additions", "Couldn't save the Additions in " + dir + ":\n" + ex.getMessage());
        }
    }

    /** The game's settings.png from the LegacyCraft folder, at 18x18; a drawn gear if it isn't there. */
    private Icon settingsIcon() {
        Path file = data.installDir == null ? null : data.installDir.resolve(Additions.SETTINGS_ICON);
        if (file != null && java.nio.file.Files.isRegularFile(file)) {
            try {
                java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(file.toFile());
                if (image != null) return new javax.swing.ImageIcon(image.getScaledInstance(18, 18, java.awt.Image.SCALE_FAST));
            } catch (IOException ignored) {}
        }
        return new GearIcon();
    }

    /** A plain gear, in the text colour. */
    private static final class GearIcon implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.isEnabled() ? c.getForeground() : Theme.DISABLED);
            g2.translate(x + 8, y + 8);
            for (int i = 0; i < 8; i++) {
                g2.fillRect(-2, -8, 4, 4);
                g2.rotate(Math.PI / 4);
            }
            g2.setStroke(new java.awt.BasicStroke(3f));
            g2.drawOval(-4, -4, 8, 8);
            g2.dispose();
        }
    }

    private static JPanel rowPanel(int padding) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(Theme.PANEL);
        row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
            BorderFactory.createEmptyBorder(padding, 14, padding, 14)));
        return row;
    }

    private static JLabel description(String text) {
        return Theme.muted("<html><div style='width:" + TEXT_WIDTH + "px'>" + text + "</div></html>");
    }

    /** An On/Off switch: filled when on. */
    private static JToggleButton toggle(boolean on, int width, int height) {
        JToggleButton t = new JToggleButton(on ? "On" : "Off", on);
        t.setPreferredSize(new Dimension(width, height));
        t.putClientProperty(Theme.FlatButtonUI.PRIMARY, on);
        return t;
    }

    private void saveGame(Additions catalog, String log) {
        Path dir = data.gameDirFor(profile);
        try {
            catalog.writeInstance(dir, game);
            console.log(log);
        } catch (IOException e) {
            Windows.error(this, "Additions", "Couldn't save the Additions in " + dir + ":\n" + e.getMessage());
        }
        onChange.run();
    }
}

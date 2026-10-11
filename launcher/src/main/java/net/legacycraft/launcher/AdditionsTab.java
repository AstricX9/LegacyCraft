package net.legacycraft.launcher;

import javax.swing.BorderFactory;
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
 * On/Off switch on the right. The Launcher section applies to every instance.
 * Changes are saved as they're made: game switches to the instance's
 * additions.txt (the file the game's Additions screen edits too), launcher
 * switches to launcher_profiles.json.
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
                for (Additions.Entry e : entries) body.add(row(catalog, e, launcher || enabled), c);
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
            + "While Additions are off this instance plays as plain 1.8.9 and the title screen has no Additions button."), c);
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
        row.add(toggle, c);
        return row;
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

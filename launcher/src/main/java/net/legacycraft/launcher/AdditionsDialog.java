package net.legacycraft.launcher;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An instance's Additions. At the top, the look as three buttons (Faithful,
 * Recommended, Custom) with a line saying what it means; then a section per
 * category where each addition is a row: its name (and a Recommended tag)
 * over its description on the left, an On/Off switch on the right, and the
 * section header counting how many are on. The Launcher section applies to
 * every instance. Game switches go to the instance's additions.txt, the file
 * the game's Additions screen edits too.
 */
final class AdditionsDialog {
    private static final int WIDTH = 640, TEXT_WIDTH = 420;

    private final LauncherData data;
    private final Additions catalog;
    private final Path gameDir;
    private final Windows.Dialog dialog;
    private final Map<String, Boolean> game;
    private final Map<String, Boolean> launcher = new LinkedHashMap<>();
    private final Map<String, JToggleButton> switches = new LinkedHashMap<>();
    private final Map<Additions.Category, JLabel> counts = new EnumMap<>(Additions.Category.class);
    private final Map<Additions.Look, JToggleButton> looks = new EnumMap<>(Additions.Look.class);
    private final JLabel lookHelp = Theme.muted("");
    private boolean saved;

    private AdditionsDialog(Component owner, LauncherData data, Profile profile) {
        this.data = data;
        this.catalog = data.additions();
        this.gameDir = data.gameDirFor(profile);
        this.game = Additions.readInstance(gameDir);
        for (Additions.Entry e : catalog.in(Additions.Category.LAUNCHER)) launcher.put(e.id(), data.launcherAddition(e.id()));
        dialog = new Windows.Dialog(owner, "Additions for " + profile.name);

        JPanel body = new FitWidth();
        body.setBackground(Theme.PANEL);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        if (catalog.entries.isEmpty()) {
            body.add(Theme.header("Additions"), c);
            JLabel missing = Theme.muted("<html>Couldn't find the list of Additions. Press Play once so the launcher knows "
                + "where LegacyCraft is, then open this again.</html>");
            missing.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
            body.add(missing, c);
        } else {
            body.add(Theme.header("Look"), c);
            body.add(lookChoice(), c);
            for (Additions.Category category : Additions.Category.values()) {
                if (catalog.in(category).isEmpty()) continue;
                JLabel count = Theme.muted("");
                counts.put(category, count);
                JComponent right = count;
                if (category == Additions.Category.LAUNCHER) {
                    JPanel both = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                    both.add(Theme.muted("For every instance   ·   "));
                    both.add(count);
                    right = both;
                }
                body.add(Theme.header(category.title, right), c);
                for (Additions.Entry e : catalog.in(category)) body.add(row(e), c);
            }
        }
        c.weighty = 1;
        body.add(new JPanel() {{ setOpaque(false); }}, c);

        JScrollPane scroll = Theme.scroll(body);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(WIDTH, Math.min(600, body.getPreferredSize().height + 2)));
        JPanel content = new JPanel(new BorderLayout());
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons(), BorderLayout.SOUTH);
        dialog.setBody(content);
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

    /** Shows the Additions of an instance; returns true when they were saved. */
    static boolean edit(Component owner, LauncherData data, Profile profile) {
        AdditionsDialog d = new AdditionsDialog(owner, data, profile);
        d.dialog.open(owner);
        return d.saved;
    }

    private JComponent lookChoice() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JPanel buttons = new JPanel(new GridLayout(1, 0, 8, 0));
        buttons.setOpaque(false);
        for (Additions.Look look : Additions.Look.values()) {
            JToggleButton b = new JToggleButton(look.title);
            b.setFont(Theme.BOLD);
            b.setPreferredSize(new Dimension(10, 34));
            looks.put(look, b);
            b.addActionListener(e -> {
                catalog.apply(look, game);
                refresh();
            });
            buttons.add(b);
        }
        looks.get(Additions.Look.CUSTOM).setToolTipText("Switch any addition below to make your own look");
        p.add(buttons, BorderLayout.NORTH);
        lookHelp.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        p.add(lookHelp, BorderLayout.CENTER);
        return p;
    }

    /** One addition: name and tag over the description on the left, its switch on the right. */
    private JComponent row(Additions.Entry e) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(Theme.PANEL);
        row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST; c.weightx = 1;

        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        title.setOpaque(false);
        JLabel name = new JLabel(e.name());
        name.setFont(Theme.BOLD);
        name.setForeground(Theme.TEXT);
        title.add(name);
        if (e.recommended()) {
            JLabel tag = new JLabel("Recommended");
            tag.setFont(Theme.FONT.deriveFont(11f));
            tag.setForeground(Theme.TEXT);
            tag.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.MUTED),
                BorderFactory.createEmptyBorder(0, 5, 0, 5)));
            title.add(javax.swing.Box.createHorizontalStrut(8));
            title.add(tag);
        }
        row.add(title, c);
        c.gridy = 1; c.insets = new Insets(3, 0, 0, 0);
        JLabel description = Theme.muted("<html><div style='width:" + TEXT_WIDTH + "px'>" + e.description() + "</div></html>");
        row.add(description, c);

        JToggleButton toggle = new JToggleButton();
        toggle.setPreferredSize(new Dimension(64, 30));
        toggle.addActionListener(ev -> {
            (e.launcher() ? launcher : game).put(e.id(), toggle.isSelected());
            refresh();
        });
        switches.put(e.id(), toggle);
        c.gridx = 1; c.gridy = 0; c.gridheight = 2; c.weightx = 0; c.insets = new Insets(0, 16, 0, 0);
        c.anchor = GridBagConstraints.CENTER;
        row.add(toggle, c);
        return row;
    }

    private void refresh() {
        for (Additions.Entry e : catalog.entries) {
            JToggleButton t = switches.get(e.id());
            if (t == null) continue;
            boolean on = (e.launcher() ? launcher : game).getOrDefault(e.id(), false);
            t.setSelected(on);
            t.setText(on ? "On" : "Off");
            t.putClientProperty(Theme.FlatButtonUI.PRIMARY, on);
            t.repaint();
        }
        for (Map.Entry<Additions.Category, JLabel> count : counts.entrySet()) {
            List<Additions.Entry> in = catalog.in(count.getKey());
            int on = 0;
            for (Additions.Entry e : in) if ((e.launcher() ? launcher : game).getOrDefault(e.id(), false)) on++;
            count.getValue().setText(on + " of " + in.size() + " on");
        }
        Additions.Look current = catalog.look(game);
        for (Map.Entry<Additions.Look, JToggleButton> look : looks.entrySet()) {
            boolean picked = look.getKey() == current;
            look.getValue().setSelected(picked);
            look.getValue().putClientProperty(Theme.FlatButtonUI.PRIMARY, picked);
            // Custom is where your own switches take you, not something to pick.
            look.getValue().setEnabled(look.getKey() != Additions.Look.CUSTOM || picked);
            look.getValue().repaint();
        }
        lookHelp.setText(current.description);
    }

    private JPanel buttons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");
        save.putClientProperty(Theme.FlatButtonUI.PRIMARY, true);
        save.setFont(Theme.BOLD);
        save.setEnabled(!catalog.entries.isEmpty());
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> save());
        p.add(cancel);
        p.add(save);
        dialog.getRootPane().setDefaultButton(save);
        return p;
    }

    private void save() {
        try {
            catalog.writeInstance(gameDir, game);
        } catch (IOException e) {
            Windows.error(dialog, dialog.getTitle(), "Couldn't save the Additions in " + gameDir + ":\n" + e.getMessage());
            return;
        }
        for (Map.Entry<String, Boolean> e : launcher.entrySet()) {
            // Only remember launcher switches that differ from what they'd be anyway, so new defaults still reach people.
            if (e.getValue() == data.launcherAddition(e.getKey()) && !data.launcherAdditions.containsKey(e.getKey())) continue;
            data.launcherAdditions.put(e.getKey(), e.getValue());
        }
        data.save();
        saved = true;
        dialog.dispose();
    }
}

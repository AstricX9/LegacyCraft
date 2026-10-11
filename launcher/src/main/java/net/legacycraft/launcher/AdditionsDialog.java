package net.legacycraft.launcher;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An instance's Additions: the look (Faithful, Recommended or Custom), a
 * switch for each game addition grouped by category, and the launcher's own
 * additions, which apply to every instance. Game switches go to the
 * instance's additions.txt, the file the game's Additions screen edits too.
 */
final class AdditionsDialog {
    private final LauncherData data;
    private final Additions catalog;
    private final Path gameDir;
    private final Windows.Dialog dialog;
    private final Map<String, Boolean> game;
    private final Map<String, JCheckBox> gameBoxes = new LinkedHashMap<>();
    private final Map<String, JCheckBox> launcherBoxes = new LinkedHashMap<>();
    private final Map<Additions.Look, JRadioButton> looks = new EnumMap<>(Additions.Look.class);
    private boolean saved, updating;

    private AdditionsDialog(Component owner, LauncherData data, Profile profile) {
        this.data = data;
        this.catalog = data.additions();
        this.gameDir = data.gameDirFor(profile);
        this.game = Additions.readInstance(gameDir);
        dialog = new Windows.Dialog(owner, "Additions for " + profile.name);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Theme.PANEL);
        if (catalog.entries.isEmpty()) {
            body.add(section("Additions", null, fields(Theme.muted("<html>Couldn't find the list of Additions. Press Play once so the "
                + "launcher knows where LegacyCraft is, then open this again.</html>"))));
        } else {
            body.add(section("Look", null, lookChoice()));
            for (Additions.Category c : Additions.Category.values()) {
                if (c == Additions.Category.LAUNCHER || catalog.in(c).isEmpty()) continue;
                body.add(section(c.title, null, switches(c, gameBoxes)));
            }
            if (!catalog.in(Additions.Category.LAUNCHER).isEmpty())
                body.add(section("Launcher", Theme.muted("For every instance"), switches(Additions.Category.LAUNCHER, launcherBoxes)));
        }
        JPanel top = new JPanel(new BorderLayout());
        top.add(body, BorderLayout.NORTH);
        JScrollPane scroll = Theme.scroll(top);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(body.getPreferredSize().width + 20, Math.min(560, body.getPreferredSize().height + 4)));
        JPanel content = new JPanel(new BorderLayout());
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons(), BorderLayout.SOUTH);
        dialog.setBody(content);
        refresh();
    }

    /** Shows the Additions of an instance; returns true when they were saved. */
    static boolean edit(Component owner, LauncherData data, Profile profile) {
        AdditionsDialog d = new AdditionsDialog(owner, data, profile);
        d.dialog.open(owner);
        return d.saved;
    }

    private static JComponent section(String title, JComponent right, JComponent fields) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(Theme.header(title, right), BorderLayout.NORTH);
        p.add(fields, BorderLayout.CENTER);
        return p;
    }

    private static JPanel fields(Component... parts) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 10, 12));
        for (Component c : parts) p.add(c);
        return p;
    }

    /** A check box or radio button with its explanation underneath, lined up with its text. */
    private static JComponent option(JComponent control, String description) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        control.setAlignmentX(0);
        control.setOpaque(false);
        p.add(control);
        JLabel text = Theme.muted("<html>" + description + "</html>");
        text.setBorder(BorderFactory.createEmptyBorder(0, 24, 6, 0));
        text.setAlignmentX(0);
        p.add(text);
        p.setAlignmentX(0);
        return p;
    }

    private JPanel lookChoice() {
        JPanel p = fields();
        ButtonGroup group = new ButtonGroup();
        for (Additions.Look look : Additions.Look.values()) {
            JRadioButton b = new JRadioButton(look.title);
            b.setFont(Theme.BOLD);
            group.add(b);
            looks.put(look, b);
            b.addActionListener(e -> {
                if (updating) return;
                catalog.apply(look, game);
                refresh();
            });
            p.add(option(b, look.description));
        }
        looks.get(Additions.Look.CUSTOM).setToolTipText("Switch any addition below to make your own look");
        return p;
    }

    private JPanel switches(Additions.Category category, Map<String, JCheckBox> boxes) {
        JPanel p = fields();
        for (Additions.Entry e : catalog.in(category)) {
            JCheckBox box = new JCheckBox(e.name() + (e.recommended() ? "  (recommended)" : ""));
            boxes.put(e.id(), box);
            if (e.launcher()) box.setSelected(data.launcherAddition(e.id()));
            box.addActionListener(ev -> {
                if (updating || e.launcher()) return;
                game.put(e.id(), box.isSelected());
                refresh();
            });
            p.add(option(box, e.description()));
        }
        return p;
    }

    private void refresh() {
        updating = true;
        for (Map.Entry<String, JCheckBox> e : gameBoxes.entrySet()) e.getValue().setSelected(game.getOrDefault(e.getKey(), false));
        JRadioButton look = looks.get(catalog.look(game));
        if (look != null) look.setSelected(true);
        updating = false;
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
        for (Map.Entry<String, JCheckBox> e : launcherBoxes.entrySet()) {
            boolean on = e.getValue().isSelected();
            // Only remember launcher switches that differ from what they'd be anyway, so new defaults still reach people.
            if (on == data.launcherAddition(e.getKey()) && !data.launcherAdditions.containsKey(e.getKey())) continue;
            data.launcherAdditions.put(e.getKey(), on);
        }
        data.save();
        saved = true;
        dialog.dispose();
    }
}

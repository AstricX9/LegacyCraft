package net.legacycraft.launcher;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates or edits an instance: its name and folder, window and launcher behaviour, and Java settings. */
final class InstanceDialog {
    /** A memory choice in the combo box; null megabytes leaves it to the game's build. */
    private record Memory(Integer mb, String label) {
        @Override public String toString() { return label; }
    }

    private static final Memory[] MEMORY = {
        new Memory(null, "Game default"), new Memory(1024, "1 GB"), new Memory(2048, "2 GB"),
        new Memory(3072, "3 GB"), new Memory(4096, "4 GB"), new Memory(6144, "6 GB"), new Memory(8192, "8 GB"),
    };

    private final LauncherData data;
    private final Profile original;
    private final boolean isNew;
    private final Windows.Dialog dialog;
    private boolean saved;
    private boolean folderEdited;

    private final JTextField name = new JTextField(28);
    private final JTextField folder = new JTextField(28);
    private final JCheckBox resolutionCustom = new JCheckBox("Window size:");
    private final JTextField resolutionWidth = new JTextField(5);
    private final JTextField resolutionHeight = new JTextField(5);
    private final JComboBox<Profile.Visibility> visibility = new JComboBox<>(Profile.Visibility.values());
    private final JComboBox<Memory> memory = new JComboBox<>();
    private final JCheckBox javaPathCustom = new JCheckBox("Java:");
    private final JTextField javaPath = new JTextField(28);
    private final JTextField javaArgs = new JTextField(28);

    private InstanceDialog(Component owner, LauncherData data, Profile profile, boolean isNew) {
        this.data = data;
        this.original = profile;
        this.isNew = isNew;
        dialog = new Windows.Dialog(owner, isNew ? "New instance" : "Edit instance");

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(section("Instance", instanceInfo()));
        body.add(section("Game", gameSettings()));
        body.add(section("Java", javaSettings()));
        JPanel content = new JPanel(new BorderLayout());
        content.add(body, BorderLayout.CENTER);
        content.add(buttons(), BorderLayout.SOUTH);
        dialog.setBody(content);
        fill(profile);
        if (isNew) {
            folder.setText(data.newInstanceDir(profile.name).toString());
            folderEdited = false;
        }
        folder.setCaretPosition(0);
    }

    /** Shows the editor; returns true when the instance was saved. */
    static boolean edit(Component owner, LauncherData data, Profile profile, boolean isNew) {
        InstanceDialog d = new InstanceDialog(owner, data, profile, isNew);
        d.dialog.setResizable(false);
        d.dialog.open(owner);
        return d.saved;
    }

    private static JComponent section(String title, JPanel fields) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(Theme.header(title), BorderLayout.NORTH);
        fields.setBorder(BorderFactory.createEmptyBorder(8, 12, 10, 12));
        p.add(fields, BorderLayout.CENTER);
        return p;
    }

    private static GridBagConstraints at(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x; c.gridy = y;
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private static GridBagConstraints wide(int x, int y) {
        GridBagConstraints c = at(x, y);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        return c;
    }

    private static JPanel row(Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        for (Component c : parts) p.add(c);
        return p;
    }

    private JPanel instanceInfo() {
        JPanel p = new JPanel(new GridBagLayout());
        p.add(new JLabel("Name:"), at(0, 0));
        p.add(name, wide(1, 0));
        p.add(new JLabel("Folder:"), at(0, 1));
        p.add(folder, wide(1, 1));
        GridBagConstraints c = at(1, 2);
        p.add(row(Theme.link("Browse...", this::browse), Box.createHorizontalStrut(14), Theme.link("Open folder", this::openFolder)), c);
        GridBagConstraints hint = at(1, 3);
        p.add(Theme.muted("<html>Saves, resource packs, options and servers live in this folder.</html>"), hint);
        name.getDocument().addDocumentListener(new Changes(() -> {
            if (isNew && !folderEdited) {
                String n = name.getText().trim();
                folder.setText(data.newInstanceDir(n.isEmpty() ? "instance" : n).toString());
                folderEdited = false;
            }
        }));
        folder.getDocument().addDocumentListener(new Changes(() -> folderEdited = true));
        return p;
    }

    private JPanel gameSettings() {
        JPanel p = new JPanel(new GridBagLayout());
        p.add(resolutionCustom, at(0, 0));
        p.add(row(resolutionWidth, new JLabel("  x  "), resolutionHeight), at(1, 0));
        p.add(new JLabel("Launcher:"), at(0, 1));
        p.add(visibility, wide(1, 1));
        resolutionCustom.addItemListener(e -> {
            resolutionWidth.setEnabled(resolutionCustom.isSelected());
            resolutionHeight.setEnabled(resolutionCustom.isSelected());
        });
        return p;
    }

    private JPanel javaSettings() {
        JPanel p = new JPanel(new GridBagLayout());
        for (Memory m : MEMORY) memory.addItem(m);
        p.add(new JLabel("Memory:"), at(0, 0));
        p.add(row(memory), at(1, 0));
        p.add(javaPathCustom, at(0, 1));
        p.add(javaPath, wide(1, 1));
        p.add(new JLabel("JVM arguments:"), at(0, 2));
        p.add(javaArgs, wide(1, 2));
        javaPath.setToolTipText("Java used to run Gradle (sets JAVA_HOME). The game itself runs on the Java 17 toolchain.");
        javaArgs.setToolTipText("Extra arguments for the game's JVM. An -Xmx here overrides Memory.");
        javaPathCustom.addItemListener(e -> javaPath.setEnabled(javaPathCustom.isSelected()));
        return p;
    }

    private JPanel buttons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton(isNew ? "Create" : "Save");
        save.putClientProperty(Theme.FlatButtonUI.PRIMARY, true);
        save.setFont(Theme.BOLD);
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> save());
        p.add(cancel);
        p.add(save);
        dialog.getRootPane().setDefaultButton(save);
        return p;
    }

    private void fill(Profile p) {
        name.setText(p.name);
        folder.setText(data.gameDirFor(p).toString());
        folderEdited = false;
        boolean res = p.resolutionWidth != null && p.resolutionHeight != null;
        resolutionCustom.setSelected(res);
        resolutionWidth.setText(res ? String.valueOf(p.resolutionWidth) : "854");
        resolutionHeight.setText(res ? String.valueOf(p.resolutionHeight) : "480");
        resolutionWidth.setEnabled(res);
        resolutionHeight.setEnabled(res);
        visibility.setSelectedItem(p.effectiveVisibility());
        memory.setSelectedIndex(0);
        for (int i = 0; i < MEMORY.length; i++) if (java.util.Objects.equals(MEMORY[i].mb(), p.memoryMb)) memory.setSelectedIndex(i);
        if (p.memoryMb != null && memory.getSelectedIndex() == 0) {
            Memory custom = new Memory(p.memoryMb, p.memoryMb + " MB");
            memory.addItem(custom);
            memory.setSelectedItem(custom);
        }
        javaPathCustom.setSelected(p.javaDir != null);
        javaPath.setText(p.javaDir != null ? p.javaDir : defaultJava());
        javaPath.setEnabled(p.javaDir != null);
        javaArgs.setText(p.javaArgs != null ? p.javaArgs : Profile.DEFAULT_JAVA_ARGS);
    }

    private static String defaultJava() {
        Path home = Path.of(System.getProperty("java.home"));
        return home.resolve("bin").resolve(LauncherData.isWindows() ? "javaw.exe" : "java").toString();
    }

    private void save() {
        String n = name.getText().trim();
        if (n.isEmpty()) { error("Please enter a name."); return; }
        if (!n.equals(original.name) && data.profiles.containsKey(n)) { error("An instance named \"" + n + "\" already exists."); return; }
        String dirText = folder.getText().trim();
        if (dirText.isEmpty()) { error("Please choose a folder for the instance."); return; }
        Path dir;
        try { dir = Path.of(dirText).toAbsolutePath().normalize(); }
        catch (RuntimeException e) { error("\"" + dirText + "\" isn't a valid folder."); return; }
        Integer w = null, h = null;
        if (resolutionCustom.isSelected()) {
            try {
                w = Integer.parseInt(resolutionWidth.getText().trim());
                h = Integer.parseInt(resolutionHeight.getText().trim());
                if (w < 1 || h < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) { error("The window size must be two positive numbers."); return; }
        }
        if (!isNew) data.profiles.remove(original.name);
        boolean wasSelected = original.name.equals(data.selectedProfile);
        // Profiles from before instances have no folder of their own and play in the LegacyCraft folder; keep that unless it changed.
        boolean unchangedLegacy = original.gameDir == null && dir.equals(data.gameDirFor(new Profile("")));
        original.name = n;
        original.gameDir = unchangedLegacy ? null : dir.toString();
        original.resolutionWidth = w;
        original.resolutionHeight = h;
        Profile.Visibility v = (Profile.Visibility) visibility.getSelectedItem();
        original.visibility = v == Profile.DEFAULT_VISIBILITY && original.visibility == null ? null : v;
        original.memoryMb = ((Memory) memory.getSelectedItem()).mb();
        original.javaDir = javaPathCustom.isSelected() && !javaPath.getText().isBlank() ? javaPath.getText().trim() : null;
        String args = javaArgs.getText().trim();
        original.javaArgs = args.isEmpty() ? null : args;
        data.profiles.put(n, original);
        if (wasSelected || isNew) data.selectedProfile = n;
        data.save();
        saved = true;
        dialog.dispose();
    }

    private void browse() {
        JFileChooser chooser = new Windows.FileChooser(Path.of(folder.getText().trim()).toFile());
        chooser.setDialogTitle("Instance folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showDialog(dialog, "Use this folder") == JFileChooser.APPROVE_OPTION)
            folder.setText(chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
    }

    private void openFolder() {
        try {
            Path dir = Path.of(folder.getText().trim());
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception e) { error("Couldn't open the folder: " + e.getMessage()); }
    }

    private void error(String message) { Windows.error(dialog, dialog.getTitle(), message); }

    /** Runs an action on any edit of a text field. */
    private record Changes(Runnable action) implements DocumentListener {
        @Override public void insertUpdate(DocumentEvent e) { action.run(); }
        @Override public void removeUpdate(DocumentEvent e) { action.run(); }
        @Override public void changedUpdate(DocumentEvent e) { action.run(); }
    }
}

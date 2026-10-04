package net.legacycraft.launcher;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The 1.6-era "Profile Editor" popup: Profile Info, Version Selection and Java Settings (Advanced). */
final class ProfileEditorDialog extends JDialog {
    private static final String LATEST = "Use Latest Version";
    private static final String RELEASE = "release " + LauncherData.GAME_VERSION;

    private final LauncherData data;
    private final Profile original;
    private final boolean isNew;
    private boolean saved;

    private final JTextField profileName = new JTextField();
    private final JCheckBox gameDirCustom = new JCheckBox("Game Directory:");
    private final JTextField gameDirField = new JTextField(32);
    private final JCheckBox resolutionCustom = new JCheckBox("Resolution:");
    private final JTextField resolutionWidth = new JTextField();
    private final JTextField resolutionHeight = new JTextField();
    private final JCheckBox useHopper = new JCheckBox("Automatically ask Mojang for assistance with fixing crashes");
    private final JCheckBox visibilityCustom = new JCheckBox("Launcher Visibility:");
    private final JComboBox<Profile.Visibility> visibility = new JComboBox<>(Profile.Visibility.values());

    private final JCheckBox allowSnapshots = new JCheckBox("Enable experimental development versions (\"snapshots\")");
    private final JCheckBox allowBeta = new JCheckBox("Allow use of old \"Beta\" Minecraft versions (From 2010-2011)");
    private final JCheckBox allowAlpha = new JCheckBox("Allow use of old \"Alpha\" Minecraft versions (From 2010)");
    private final JComboBox<String> version = new JComboBox<>(new String[] {LATEST, RELEASE});

    private final JCheckBox javaPathCustom = new JCheckBox("Executable:");
    private final JTextField javaPathField = new JTextField(32);
    private final JCheckBox javaArgsCustom = new JCheckBox("JVM Arguments:");
    private final JTextField javaArgsField = new JTextField(32);

    private ProfileEditorDialog(JFrame owner, LauncherData data, Profile profile, boolean isNew) {
        super(owner, "Profile Editor", true);
        this.data = data;
        this.original = profile;
        this.isNew = isNew;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JPanel sections = new JPanel();
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
        sections.add(profileInfo());
        sections.add(versionSelection());
        sections.add(javaSettings());
        content.add(sections, BorderLayout.CENTER);
        content.add(buttons(), BorderLayout.SOUTH);
        setContentPane(content);
        fill(profile);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Shows the editor; returns true when the profile was saved. */
    static boolean edit(JFrame owner, LauncherData data, Profile profile, boolean isNew) {
        ProfileEditorDialog dialog = new ProfileEditorDialog(owner, data, profile, isNew);
        dialog.setVisible(true);
        return dialog.saved;
    }

    private JPanel section(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private static GridBagConstraints at(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x; c.gridy = y;
        c.insets = new Insets(2, 2, 2, 2);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private static GridBagConstraints wide(int x, int y) {
        GridBagConstraints c = at(x, y);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        return c;
    }

    private JPanel profileInfo() {
        JPanel p = section("Profile Info");
        p.add(new JLabel("Profile Name:"), at(0, 0));
        p.add(profileName, wide(1, 0));
        p.add(gameDirCustom, at(0, 1));
        p.add(gameDirField, wide(1, 1));
        p.add(resolutionCustom, at(0, 2));
        JPanel res = new JPanel(new GridBagLayout());
        GridBagConstraints w = wide(0, 0); w.insets = new Insets(0, 0, 0, 0);
        res.add(resolutionWidth, w);
        res.add(Box.createHorizontalStrut(5), at(1, 0));
        res.add(new JLabel("x"), at(2, 0));
        res.add(Box.createHorizontalStrut(5), at(3, 0));
        GridBagConstraints h = wide(4, 0); h.insets = new Insets(0, 0, 0, 0);
        res.add(resolutionHeight, h);
        p.add(res, wide(1, 2));
        GridBagConstraints hop = at(0, 3); hop.gridwidth = 2;
        p.add(useHopper, hop);
        useHopper.setEnabled(false);
        useHopper.setToolTipText("Not available in offline mode");
        p.add(visibilityCustom, at(0, 4));
        p.add(visibility, wide(1, 4));

        gameDirCustom.addItemListener(e -> gameDirField.setEnabled(gameDirCustom.isSelected()));
        resolutionCustom.addItemListener(e -> {
            resolutionWidth.setEnabled(resolutionCustom.isSelected());
            resolutionHeight.setEnabled(resolutionCustom.isSelected());
        });
        visibilityCustom.addItemListener(e -> visibility.setEnabled(visibilityCustom.isSelected()));
        return p;
    }

    private JPanel versionSelection() {
        JPanel p = section("Version Selection");
        GridBagConstraints c = at(0, 0); c.gridwidth = 2;
        p.add(allowSnapshots, c);
        c = at(0, 1); c.gridwidth = 2;
        p.add(allowBeta, c);
        c = at(0, 2); c.gridwidth = 2;
        p.add(allowAlpha, c);
        p.add(new JLabel("Use version:"), at(0, 3));
        p.add(version, wide(1, 3));
        allowSnapshots.addActionListener(e -> {
            if (allowSnapshots.isSelected())
                JOptionPane.showMessageDialog(this,
                    "Are you sure you want to enable development builds?\n"
                        + "They are not guaranteed to be stable and may corrupt your world.\n"
                        + "You are advised to run this in a separate directory or run regular backups.\n\n"
                        + "LegacyCraft only provides " + LauncherData.GAME_VERSION + ", so no snapshots will be listed.",
                    "Snapshots", JOptionPane.WARNING_MESSAGE);
        });
        return p;
    }

    private JPanel javaSettings() {
        JPanel p = section("Java Settings (Advanced)");
        p.add(javaPathCustom, at(0, 0));
        p.add(javaPathField, wide(1, 0));
        p.add(javaArgsCustom, at(0, 1));
        p.add(javaArgsField, wide(1, 1));
        javaPathField.setToolTipText("Java used to run Gradle (sets JAVA_HOME). The game itself runs on the Java 17 toolchain.");
        javaPathCustom.addItemListener(e -> javaPathField.setEnabled(javaPathCustom.isSelected()));
        javaArgsCustom.addItemListener(e -> javaArgsField.setEnabled(javaArgsCustom.isSelected()));
        return p;
    }

    private JPanel buttons() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        JButton cancel = new JButton("Cancel");
        JButton openDir = new JButton("Open Game Dir");
        JButton save = new JButton("Save Profile");
        cancel.addActionListener(e -> dispose());
        openDir.addActionListener(e -> openGameDir());
        save.addActionListener(e -> save());
        JPanel right = new JPanel(new java.awt.GridLayout(1, 2, 5, 0));
        right.add(openDir);
        right.add(save);
        p.add(cancel, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        getRootPane().setDefaultButton(save);
        return p;
    }

    private void fill(Profile p) {
        profileName.setText(p.name);
        Path defaultDir = data.gameDirFor(new Profile(""));
        gameDirCustom.setSelected(p.gameDir != null);
        gameDirField.setText(p.gameDir != null ? p.gameDir : defaultDir.toString());
        gameDirField.setEnabled(p.gameDir != null);
        boolean res = p.resolutionWidth != null && p.resolutionHeight != null;
        resolutionCustom.setSelected(res);
        resolutionWidth.setText(String.valueOf(res ? p.resolutionWidth : 854));
        resolutionHeight.setText(String.valueOf(res ? p.resolutionHeight : 480));
        resolutionWidth.setEnabled(res);
        resolutionHeight.setEnabled(res);
        useHopper.setSelected(p.useHopperCrashService);
        visibilityCustom.setSelected(p.visibility != null);
        visibility.setSelectedItem(p.effectiveVisibility());
        visibility.setEnabled(p.visibility != null);
        allowSnapshots.setSelected(p.allowedReleaseTypes.contains("snapshot"));
        allowBeta.setSelected(p.allowedReleaseTypes.contains("old_beta"));
        allowAlpha.setSelected(p.allowedReleaseTypes.contains("old_alpha"));
        version.setSelectedItem(p.lastVersionId == null ? LATEST : RELEASE);
        javaPathCustom.setSelected(p.javaDir != null);
        javaPathField.setText(p.javaDir != null ? p.javaDir : defaultJava());
        javaPathField.setEnabled(p.javaDir != null);
        javaArgsCustom.setSelected(p.javaArgs != null);
        javaArgsField.setText(p.javaArgs != null ? p.javaArgs : Profile.DEFAULT_JAVA_ARGS);
        javaArgsField.setEnabled(p.javaArgs != null);
    }

    private static String defaultJava() {
        Path home = Path.of(System.getProperty("java.home"));
        return home.resolve("bin").resolve(LauncherData.isWindows() ? "javaw.exe" : "java").toString();
    }

    private void save() {
        String name = profileName.getText().trim();
        if (name.isEmpty()) { error("Please enter a profile name."); return; }
        if (!name.equals(original.name) && data.profiles.containsKey(name)) { error("A profile named \"" + name + "\" already exists."); return; }
        Integer w = null, h = null;
        if (resolutionCustom.isSelected()) {
            try {
                w = Integer.parseInt(resolutionWidth.getText().trim());
                h = Integer.parseInt(resolutionHeight.getText().trim());
                if (w < 1 || h < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) { error("The resolution must be two positive numbers."); return; }
        }
        if (!isNew) data.profiles.remove(original.name);
        boolean wasSelected = original.name.equals(data.selectedProfile);
        original.name = name;
        original.gameDir = gameDirCustom.isSelected() && !gameDirField.getText().isBlank() ? gameDirField.getText().trim() : null;
        original.resolutionWidth = w;
        original.resolutionHeight = h;
        original.useHopperCrashService = useHopper.isSelected();
        original.visibility = visibilityCustom.isSelected() ? (Profile.Visibility) visibility.getSelectedItem() : null;
        original.allowedReleaseTypes.clear();
        if (allowSnapshots.isSelected()) original.allowedReleaseTypes.add("snapshot");
        if (allowBeta.isSelected()) original.allowedReleaseTypes.add("old_beta");
        if (allowAlpha.isSelected()) original.allowedReleaseTypes.add("old_alpha");
        if (!original.allowedReleaseTypes.isEmpty()) original.allowedReleaseTypes.add("release");
        original.lastVersionId = LATEST.equals(version.getSelectedItem()) ? null : LauncherData.GAME_VERSION;
        original.javaDir = javaPathCustom.isSelected() && !javaPathField.getText().isBlank() ? javaPathField.getText().trim() : null;
        original.javaArgs = javaArgsCustom.isSelected() ? javaArgsField.getText().trim() : null;
        data.profiles.put(name, original);
        if (wasSelected || isNew) data.selectedProfile = name;
        data.save();
        saved = true;
        dispose();
    }

    private void openGameDir() {
        Path dir = gameDirCustom.isSelected() ? Path.of(gameDirField.getText().trim()) : data.gameDirFor(new Profile(""));
        try {
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception e) { error("Couldn't open " + dir + ": " + e.getMessage()); }
    }

    private void error(String message) { JOptionPane.showMessageDialog(this, message, "Profile Editor", JOptionPane.ERROR_MESSAGE); }
}

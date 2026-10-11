package net.legacycraft.launcher;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An offline launcher laid out like the 1.6-era Minecraft Launcher that
 * shipped alongside 1.8.9: the Update Notes / Development Console / Profile
 * Editor tabs above a dirt bottom bar with the profile selector, the Play
 * button and the player panel. Logging in is replaced by an offline name, and
 * a Skins tab manages the skin handed to the game.
 */
public final class LauncherApp {
    static final String LAUNCHER_VERSION = "1.6.93";

    private final LauncherData data = new LauncherData();
    private JFrame frame;
    private JTabbedPane tabs;
    private ConsoleTab console;
    private WorldsTab worlds;
    private ProfileTableModel profileTable;
    private final JComboBox<Profile> profileCombo = new JComboBox<>();
    private final JButton playButton = new JButton("Play");
    private final JLabel welcome = new JLabel("", SwingConstants.CENTER);
    private final JLabel versionLabel = new JLabel("", SwingConstants.CENTER);
    private boolean updatingCombo;
    private Process game;

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new LauncherApp().start());
    }

    private void start() {
        console = new ConsoleTab();
        data.load();
        Textures.init(data.dirtTexture());
        console.log("Minecraft Launcher " + LAUNCHER_VERSION + " (LegacyCraft offline) started on "
            + (LauncherData.isWindows() ? "windows" : System.getProperty("os.name").toLowerCase()) + "...");
        console.log("Current time is " + new java.util.Date());
        console.log("System.getProperty('os.name') == '" + System.getProperty("os.name") + "'");
        console.log("System.getProperty('os.version') == '" + System.getProperty("os.version") + "'");
        console.log("System.getProperty('os.arch') == '" + System.getProperty("os.arch") + "'");
        console.log("System.getProperty('java.version') == '" + System.getProperty("java.version") + "'");
        console.log("System.getProperty('java.vendor') == '" + System.getProperty("java.vendor") + "'");
        console.log("Launcher data directory: " + data.dataDir);
        if (data.installDir != null) console.log("LegacyCraft found at " + data.installDir);
        else console.warn("Couldn't find the LegacyCraft folder (with " + LauncherData.wrapperName() + "); you'll be asked for it when you press Play.");

        frame = new JFrame("Minecraft Launcher " + LAUNCHER_VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImages(icons());
        JPanel root = new JPanel(new BorderLayout());
        root.add(createTabs(), BorderLayout.CENTER);
        root.add(createBottomBar(), BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.setPreferredSize(new Dimension(900, 580));
        frame.pack();
        frame.setLocationRelativeTo(null);
        refreshProfiles();
        refreshPlayer();
        frame.setVisible(true);
    }

    // ------------------------------------------------------------------ tabs

    private JTabbedPane createTabs() {
        tabs = new JTabbedPane();
        tabs.addTab("Update Notes", createNews());
        tabs.addTab("Launcher Log", console);
        tabs.addTab("Profile Editor", createProfileList());
        tabs.addTab("Skins & Capes", new SkinTab(data, this::refreshPlayer));
        worlds = new WorldsTab(() -> data.gameDirFor(data.selected()), console);
        tabs.addTab("Worlds", worlds);
        return tabs;
    }

    private JScrollPane createNews() {
        JEditorPane page = new JEditorPane();
        page.setEditable(false);
        page.setOpaque(false);
        page.setEditorKit(new HTMLEditorKit());
        page.addHyperlinkListener(e -> { if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) openLink(e); });
        try {
            Path custom = data.dataDir.resolve("news.html");
            URL url = Files.isRegularFile(custom) ? custom.toUri().toURL() : LauncherApp.class.getResource("news.html");
            if (url == null) throw new IOException("news.html is missing from the launcher");
            page.setPage(url);
        } catch (IOException e) {
            page.setText("<html><body style='color:#e0d0d0; font-family:sans-serif; padding:16px'>"
                + "<h1 style='color:white'>Minecraft News</h1><p>Couldn't load the news: " + e.getMessage() + "</p></body></html>");
        }
        JScrollPane scroll = new JScrollPane();
        scroll.setViewport(new Textures.TiledViewport());
        scroll.setViewportView(page);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private void openLink(HyperlinkEvent e) {
        String link = e.getDescription();
        try {
            switch (link) {
                case "launcher:skins" -> tabs.setSelectedIndex(3);
                case "launcher:worlds" -> tabs.setSelectedIndex(4);
                case "launcher:profiles" -> tabs.setSelectedIndex(2);
                case "launcher:console" -> tabs.setSelectedIndex(1);
                case "launcher:gamedir" -> openFolder(data.gameDirFor(data.selected()));
                case "launcher:skinsdir" -> openFolder(data.skinsDir);
                case "launcher:capesdir" -> openFolder(data.capesDir);
                case "launcher:datadir" -> openFolder(data.dataDir);
                case "launcher:savesdir" -> openFolder(WorldImporter.savesDir(data.gameDirFor(data.selected())));
                default -> {
                    URI uri = e.getURL() != null ? e.getURL().toURI() : URI.create(link);
                    Desktop.getDesktop().browse(uri);
                }
            }
        } catch (Exception ex) {
            console.warn("Couldn't open link " + link + ": " + ex);
        }
    }

    private void openFolder(Path dir) throws IOException {
        Files.createDirectories(dir);
        Desktop.getDesktop().open(dir.toFile());
    }

    private JScrollPane createProfileList() {
        profileTable = new ProfileTableModel();
        JTable table = new JTable(profileTable);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem add = new JMenuItem("Add Profile"), copy = new JMenuItem("Copy Profile"), delete = new JMenuItem("Delete Profile");
        menu.add(add); menu.add(copy); menu.add(delete);
        add.addActionListener(e -> newProfile(null));
        copy.addActionListener(e -> { Profile p = profileTable.at(table.getSelectedRow()); if (p != null) newProfile(p); });
        delete.addActionListener(e -> { Profile p = profileTable.at(table.getSelectedRow()); if (p != null) deleteProfile(p); });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    Profile p = profileTable.at(table.rowAtPoint(e.getPoint()));
                    if (p != null) editProfile(p);
                }
            }
            @Override public void mousePressed(MouseEvent e) { popup(e); }
            @Override public void mouseReleased(MouseEvent e) { popup(e); }
            private void popup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) table.setRowSelectionInterval(row, row); else table.clearSelection();
                copy.setEnabled(row >= 0);
                delete.setEnabled(row >= 0 && data.profiles.size() > 1);
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        scroll.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { show(e); }
            @Override public void mouseReleased(MouseEvent e) { show(e); }
            private void show(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                table.clearSelection(); copy.setEnabled(false); delete.setEnabled(false);
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        return scroll;
    }

    private final class ProfileTableModel extends AbstractTableModel {
        private List<Profile> rows = new ArrayList<>();

        void refresh() { rows = data.sortedProfiles(); fireTableDataChanged(); }
        Profile at(int row) { return row >= 0 && row < rows.size() ? rows.get(row) : null; }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int c) { return c == 0 ? "Version name" : "Version"; }
        @Override public Object getValueAt(int r, int c) {
            Profile p = rows.get(r);
            if (c == 0) return p.name;
            return p.lastVersionId == null ? "(Latest version)" : p.lastVersionId;
        }
    }

    // ------------------------------------------------------------ bottom bar

    private JPanel createBottomBar() {
        JPanel bar = new JPanel(new GridLayout(1, 3));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        bar.add(createProfilePanel());
        bar.add(createPlayPanel());
        bar.add(createPlayerPanel());
        return bar;
    }

    private JPanel createProfilePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.anchor = GridBagConstraints.WEST;
        JLabel label = new JLabel("Profile: ");
        c.gridx = 0; c.gridy = 0;
        p.add(label, c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        p.add(profileCombo, c);
        profileCombo.addActionListener(e -> {
            if (updatingCombo || !(profileCombo.getSelectedItem() instanceof Profile p2)) return;
            data.selectedProfile = p2.name;
            data.save();
            refreshPlayer();
            worlds.refresh();
        });
        JPanel buttons = new JPanel(new GridLayout(1, 2, 2, 0));
        buttons.setOpaque(false);
        JButton newProfile = new JButton("New Profile"), editProfile = new JButton("Edit Profile");
        newProfile.addActionListener(e -> newProfile(data.selected()));
        editProfile.addActionListener(e -> { if (data.selected() != null) editProfile(data.selected()); });
        buttons.add(newProfile);
        buttons.add(editProfile);
        c.gridx = 0; c.gridy = 1; c.gridwidth = 2;
        p.add(buttons, c);
        return p;
    }

    private JPanel createPlayPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1; c.weighty = 1;
        c.insets = new Insets(4, 0, 4, 0);
        playButton.setFont(playButton.getFont().deriveFont(Font.BOLD, playButton.getFont().getSize() + 2));
        playButton.addActionListener(e -> play());
        p.add(playButton, c);
        return p;
    }

    private JPanel createPlayerPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        c.gridy = 0; p.add(welcome, c);
        c.gridy = 1; p.add(versionLabel, c);
        JButton switchUser = new JButton("Switch User");
        switchUser.addActionListener(e -> switchUser());
        c.gridy = 2; c.fill = GridBagConstraints.NONE; c.insets = new Insets(2, 0, 0, 0);
        p.add(switchUser, c);
        return p;
    }

    private void refreshProfiles() {
        updatingCombo = true;
        List<Profile> sorted = data.sortedProfiles();
        profileCombo.setModel(new DefaultComboBoxModel<>(sorted.toArray(new Profile[0])));
        profileCombo.setSelectedItem(data.selected());
        updatingCombo = false;
        if (profileTable != null) profileTable.refresh();
        if (worlds != null) worlds.refresh();
        refreshPlayer();
    }

    private void refreshPlayer() {
        welcome.setText("<html>Welcome, <b>" + data.username + "</b></html>");
        versionLabel.setText("Ready to play Minecraft " + LauncherData.GAME_VERSION);
        LauncherData.CapeEntry cape = data.selectedCapeEntry();
        welcome.setToolTipText("Skin: " + data.selectedSkinEntry().name + (cape == null ? "" : ", cape: " + cape.name));
    }

    private void switchUser() {
        while (true) {
            Object name = JOptionPane.showInputDialog(frame,
                "Enter a name to play with (no account needed).\nNames are 1-16 letters, digits or underscores.",
                "Switch User", JOptionPane.PLAIN_MESSAGE, null, null, data.username);
            if (name == null) return;
            String n = name.toString().trim();
            if (n.matches("[A-Za-z0-9_]{1,16}")) {
                data.username = n;
                data.save();
                console.log("Switched user to " + n);
                refreshPlayer();
                return;
            }
            JOptionPane.showMessageDialog(frame, "\"" + n + "\" isn't a valid name.", "Switch User", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -------------------------------------------------------------- profiles

    private void newProfile(Profile template) {
        Profile p = template == null ? new Profile(data.uniqueProfileName("New Profile"))
                                     : template.copy(data.uniqueProfileName("Copy of " + template.name));
        if (ProfileEditorDialog.edit(frame, data, p, true)) refreshProfiles();
    }

    private void editProfile(Profile p) {
        if (ProfileEditorDialog.edit(frame, data, p, false)) refreshProfiles();
    }

    private void deleteProfile(Profile p) {
        if (data.profiles.size() <= 1) return;
        if (JOptionPane.showConfirmDialog(frame, "Are you sure you want to delete " + p.name + "?", "Profile Confirmation",
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        data.profiles.remove(p.name);
        if (p.name.equals(data.selectedProfile)) data.selectedProfile = data.sortedProfiles().get(0).name;
        data.save();
        refreshProfiles();
    }

    // ------------------------------------------------------------------ play

    private void play() {
        if (game != null && game.isAlive()) return;
        Profile profile = data.selected();
        if (profile == null) return;
        if (!LauncherData.isInstall(data.installDir) && !chooseInstall()) return;
        Path gameDir = data.gameDirFor(profile);
        try {
            Files.createDirectories(gameDir);
            List<String> args = new ArrayList<>();
            args.add("--gamedir=" + gameDir);
            args.add("--username=" + data.username);
            LauncherData.SkinEntry skin = data.selectedSkinEntry();
            if (!LauncherData.STEVE.equals(skin.id)) {
                Path source = data.skinFile(skin);
                if (source == null) throw new IOException("Can't find the texture for skin " + skin.name);
                Path target = gameDir.resolve(".legacycraft-launcher").resolve("skin.png");
                SkinImages.write(SkinImages.read(source).image(), target);
                args.add("--skin=" + target);
            }
            args.add("--skinModel=" + (skin.slim() ? "slim" : "classic"));
            LauncherData.CapeEntry cape = data.selectedCapeEntry();
            if (cape != null) {
                Path target = gameDir.resolve(".legacycraft-launcher").resolve("cape.png");
                SkinImages.write(SkinImages.readCape(data.capeFile(cape)), target);
                args.add("--cape=" + target);
            }
            if (profile.resolutionWidth != null && profile.resolutionHeight != null) {
                args.add("--width=" + profile.resolutionWidth);
                args.add("--height=" + profile.resolutionHeight);
            }
            args.add("--version=" + LauncherData.GAME_VERSION);
            launch(profile, args);
        } catch (IOException e) {
            console.warn("Couldn't launch: " + e.getMessage());
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Can't start game", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean chooseInstall() {
        JFileChooser chooser = new JFileChooser(data.installDir == null ? null : data.installDir.toFile());
        chooser.setDialogTitle("Where is LegacyCraft? (the folder with " + LauncherData.wrapperName() + ")");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        while (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            Path dir = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            if (LauncherData.isInstall(dir)) {
                data.installDir = dir;
                data.save();
                worlds.refresh();
                Textures.init(data.dirtTexture());
                frame.repaint();
                console.log("LegacyCraft found at " + dir);
                return true;
            }
            JOptionPane.showMessageDialog(frame, dir + " doesn't contain " + LauncherData.wrapperName() + ".",
                "Not a LegacyCraft folder", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    /** Gradle's --args splits on spaces and honours single quotes. */
    private static String gradleArgs(List<String> args) {
        StringBuilder out = new StringBuilder();
        for (String a : args) {
            if (out.length() > 0) out.append(' ');
            int eq = a.indexOf('=');
            if (a.contains(" ") && eq > 0) out.append(a, 0, eq + 1).append('\'').append(a.substring(eq + 1)).append('\'');
            else out.append(a);
        }
        return out.toString();
    }

    private void launch(Profile profile, List<String> args) throws IOException {
        Path wrapper = data.installDir.resolve(LauncherData.wrapperName());
        String argString = gradleArgs(args);
        ProcessBuilder pb = LauncherData.isWindows()
            ? new ProcessBuilder("cmd.exe", "/d", "/s", "/c", "\"\"" + wrapper + "\" run \"--args=" + argString + "\"\"")
            : new ProcessBuilder(wrapper.toString(), "run", "--args=" + argString);
        pb.directory(data.installDir.toFile());
        Map<String, String> env = pb.environment();
        if (profile.javaDir != null) {
            Path exe = Path.of(profile.javaDir);
            Path home = exe.getParent() != null && exe.getParent().getFileName() != null
                && exe.getParent().getFileName().toString().equalsIgnoreCase("bin") ? exe.getParent().getParent() : exe;
            if (home != null) env.put("JAVA_HOME", home.toString());
        }
        if (profile.javaArgs != null && !profile.javaArgs.isBlank()) env.put("LEGACYCRAFT_JVM_ARGS", profile.javaArgs);
        else env.remove("LEGACYCRAFT_JVM_ARGS");

        Profile.Visibility visibility = profile.effectiveVisibility();
        Path outputLog = data.gameDirFor(profile).resolve("logs").resolve("launcher-game-output.log");
        if (visibility == Profile.Visibility.CLOSE_ON_START) {
            Files.createDirectories(outputLog.getParent());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.to(outputLog.toFile()));
        } else {
            pb.redirectErrorStream(true);
        }

        console.log("Launching game");
        LauncherData.CapeEntry cape = data.selectedCapeEntry();
        console.log("Profile: " + profile.name + ", user: " + data.username + ", skin: " + data.selectedSkinEntry().name
            + ", cape: " + (cape == null ? "none" : cape.name));
        console.log("Half command: " + String.join(" ", pb.command()));
        game = pb.start();
        playButton.setEnabled(false);
        playButton.setText("Playing");

        if (visibility == Profile.Visibility.CLOSE_ON_START) {
            console.log("Closing launcher as requested by the profile (game output goes to " + outputLog + ")");
            frame.dispose();
            System.exit(0);
            return;
        }
        if (visibility == Profile.Visibility.HIDE_LAUNCHER) frame.setVisible(false);

        Process process = game;
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) console.client(line);
            } catch (IOException ignored) {}
            int code;
            try { code = process.waitFor(); } catch (InterruptedException e) { return; }
            int exit = code;
            SwingUtilities.invokeLater(() -> gameEnded(exit));
        }, "Game output");
        reader.setDaemon(true);
        reader.start();
    }

    private void gameEnded(int exitCode) {
        if (exitCode == 0) console.log("Game ended with no troubles detected (exit code 0)");
        else console.warn("Game ended with bad state (exit code " + exitCode + ")");
        playButton.setText("Play");
        playButton.setEnabled(true);
        worlds.refresh();
        if (!frame.isVisible()) frame.setVisible(true);
        if (exitCode != 0) {
            tabs.setSelectedIndex(1);
            frame.toFront();
        }
    }

    private static List<java.awt.Image> icons() {
        List<java.awt.Image> list = new ArrayList<>();
        try (InputStream in = LauncherApp.class.getResourceAsStream("icon.png")) {
            if (in != null) {
                BufferedImage img = ImageIO.read(in);
                for (int size : new int[] {16, 32, 64}) list.add(img.getScaledInstance(size, size, java.awt.Image.SCALE_FAST));
            }
        } catch (IOException ignored) {}
        return list;
    }
}

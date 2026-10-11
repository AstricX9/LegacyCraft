package net.legacycraft.launcher;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
 * An offline launcher for LegacyCraft 1.8.9 with an instance manager. Flat
 * and black on white (see {@link Theme}): a tab strip that doubles as the
 * title bar (News, Instances, Skins &amp; Capes, Launcher Log) above a grey
 * bottom bar with the instance selector, the Play button and the player name.
 * Logging in is replaced by an offline name.
 */
public final class LauncherApp {
    static final String LAUNCHER_VERSION = "1.6.93";

    private final LauncherData data;
    private JFrame frame;
    private JTabbedPane tabs;
    private ConsoleTab console;
    private InstancesTab instances;
    private AdditionsTab additions;
    private SkinTab skins;
    private WorldsTab worlds;
    private final JComboBox<Profile> profileCombo = new JComboBox<>();
    private final JButton playButton = Theme.primary("Play");
    private final JLabel welcome = new JLabel();
    private final JLabel wearing = Theme.muted("");
    private boolean updatingCombo;
    private Process game;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LauncherData data = new LauncherData();
            data.load();
            Theme.install(data.darkMode);
            new LauncherApp(data).start(null);
        });
    }

    private LauncherApp(LauncherData data) { this.data = data; }

    /** Switches between light and dark: the palette changes, so the window is rebuilt in the same place, on the same tab. */
    private void toggleDarkMode() {
        if (game != null && game.isAlive()) {
            Windows.message(frame, "Dark mode", "Switch modes after the game has closed.");
            return;
        }
        data.darkMode = !data.darkMode;
        data.save();
        Theme.install(data.darkMode);
        LauncherApp next = new LauncherApp(data);
        next.start(this);
        frame.dispose();
    }

    /** Builds the window; `previous` is the window being replaced when switching modes (its log, place and tab carry over). */
    private void start(LauncherApp previous) {
        console = new ConsoleTab();
        if (previous != null) {
            console.restore(previous.console.text());
            buildWindow(previous);
            return;
        }
        console.log("LegacyCraft Launcher " + LAUNCHER_VERSION + " started on "
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

        buildWindow(null);
    }

    private void buildWindow(LauncherApp previous) {
        frame = new JFrame("LegacyCraft Launcher");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        List<java.awt.Image> icons = icons();
        frame.setIconImages(icons);
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createLineBorder(Theme.OUTLINE));
        root.add(new TabStrip(createTabs(), frame, icons.isEmpty() ? null : icons.get(icons.size() - 1), this::toggleDarkMode), BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        root.add(createBottomBar(), BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.setMinimumSize(new Dimension(760, 480));
        frame.setPreferredSize(new Dimension(960, 620));
        frame.pack();
        if (previous != null) {
            frame.setBounds(previous.frame.getBounds());
            frame.setExtendedState(previous.frame.getExtendedState());
        } else {
            frame.setLocationRelativeTo(null);
        }
        Windows.resizable(frame);
        refreshProfiles();
        if (data.launcherAddition(Additions.REMEMBER_TAB)) {
            for (int i = 0; i < tabs.getTabCount(); i++) if (tabs.getTitleAt(i).equals(data.lastTab)) tabs.setSelectedIndex(i);
        }
        if (previous != null) tabs.setSelectedIndex(previous.tabs.getSelectedIndex());
        tabs.addChangeListener(e -> {
            String title = tabs.getTitleAt(tabs.getSelectedIndex());
            if (title.equals(data.lastTab)) return;
            data.lastTab = title;
            data.save();
        });
        frame.setVisible(true);
        if (previous == null) SwingUtilities.invokeLater(this::whatsNew);
    }

    /** The What's New addition: lists Additions the launcher hasn't told you about yet, once. */
    private void whatsNew() {
        List<Additions.Entry> fresh = new ArrayList<>();
        for (Additions.Entry e : data.additions().entries) if (!data.seenAdditions.contains(e.id())) fresh.add(e);
        if (fresh.isEmpty()) return;
        boolean firstTime = data.seenAdditions.isEmpty();
        for (Additions.Entry e : fresh) data.seenAdditions.add(e.id());
        data.save();
        if (!data.launcherAddition(Additions.WHATS_NEW)) return;
        StringBuilder text = new StringBuilder("<html><body style='width:380px'>");
        text.append(firstTime
            ? "<p>LegacyCraft now has <b>Additions</b>, its own mod system: optional changes you can turn on for each instance. "
                + "They're off until you turn them on, so the game stays plain 1.8.9 until you choose otherwise.</p>"
            : "<p>This update brings new Additions:</p>");
        text.append("<ul>");
        for (Additions.Entry e : fresh)
            text.append("<li><b>").append(e.name()).append("</b> (").append(e.category().title).append("): ").append(e.description()).append("</li>");
        text.append("</ul><p>Turn them on in the <b>Additions</b> tab.</p></body></html>");
        Windows.message(frame, firstTime ? "Additions" : "What's new", text.toString());
    }

    // ------------------------------------------------------------------ tabs

    private JTabbedPane createTabs() {
        tabs = new JTabbedPane();
        instances = new InstancesTab(data, console, this::refreshProfiles, this::play, () -> tabs.setSelectedComponent(additions));
        additions = new AdditionsTab(data, console, this::refreshProfiles);
        skins = new SkinTab(data, this::refreshPlayer);
        tabs.addTab("News", createNews());
        tabs.addTab("Instances", instances);
        tabs.addTab("Additions", additions);
        worlds = new WorldsTab(() -> data.gameDirFor(data.selected()), console);
        tabs.addTab("Worlds", worlds);
        tabs.addTab("Skins & Capes", skins);
        tabs.addTab("Launcher Log", console);
        return tabs;
    }

    private JScrollPane createNews() {
        JEditorPane page = new JEditorPane();
        page.setEditable(false);
        page.setBackground(Theme.PANEL);
        page.setEditorKit(new HTMLEditorKit());
        page.addHyperlinkListener(e -> { if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) openLink(e); });
        try {
            Path custom = data.dataDir.resolve("news.html");
            URL url = Files.isRegularFile(custom) ? custom.toUri().toURL() : LauncherApp.class.getResource("news.html");
            if (url == null) throw new IOException("news.html is missing from the launcher");
            String html;
            try (InputStream in = url.openStream()) { html = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
            page.setText(recolour(html));
            page.setCaretPosition(0);
        } catch (IOException e) {
            page.setText("<html><body style='font-family:sans-serif; padding:16px; color:" + Theme.hex(Theme.TEXT)
                + "; background-color:" + Theme.hex(Theme.PANEL) + "'>"
                + "<h1>News</h1><p>Couldn't load the news: " + e.getMessage() + "</p></body></html>");
        }
        return Theme.scroll(page);
    }

    /** The news page is written in the light palette's colours; this swaps them for the current palette's. */
    private static String recolour(String html) {
        String[][] map = {
            {"#ffffff", Theme.hex(Theme.PANEL)}, {"#000000", Theme.hex(Theme.TEXT)}, {"#0b57b0", Theme.hex(Theme.LINK)},
            {"#45403c", Theme.hex(Theme.MUTED)}, {"#d5cfc9", Theme.hex(Theme.HEADER)},
        };
        for (String[] m : map) html = html.replaceAll("(?i)" + m[0], m[1]);
        return html;
    }

    private void openLink(HyperlinkEvent e) {
        String link = e.getDescription();
        try {
            switch (link) {
                case "launcher:skins" -> tabs.setSelectedComponent(skins);
                case "launcher:worlds" -> tabs.setSelectedComponent(worlds);
                case "launcher:additions" -> tabs.setSelectedComponent(additions);
                case "launcher:instances", "launcher:profiles" -> tabs.setSelectedComponent(instances);
                case "launcher:console" -> tabs.setSelectedComponent(console);
                case "launcher:newinstance" -> instances.create(null);
                case "launcher:gamedir" -> openFolder(data.gameDirFor(data.selected()));
                case "launcher:instancesdir" -> openFolder(data.instancesDir);
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

    // ------------------------------------------------------------ bottom bar

    private JPanel createBottomBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBackground(Theme.HEADER);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.weightx = 1; c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; bar.add(createInstancePanel(), c);
        c.gridx = 1; c.weightx = 0; c.anchor = GridBagConstraints.CENTER; c.insets = new Insets(0, 16, 0, 16);
        playButton.setPreferredSize(new Dimension(220, 46));
        playButton.addActionListener(e -> play());
        bar.add(playButton, c);
        c.gridx = 2; c.weightx = 1; c.anchor = GridBagConstraints.EAST; c.insets = new Insets(0, 0, 0, 0);
        bar.add(createPlayerPanel(), c);
        return bar;
    }

    private JComponent createInstancePanel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel label = new JLabel("Instance");
        label.setFont(Theme.BOLD);
        label.setAlignmentX(0);
        p.add(label);
        p.add(Box.createVerticalStrut(4));
        profileCombo.setPreferredSize(new Dimension(230, 26));
        profileCombo.setMaximumSize(new Dimension(230, 26));
        profileCombo.setAlignmentX(0);
        profileCombo.addActionListener(e -> {
            if (updatingCombo || !(profileCombo.getSelectedItem() instanceof Profile p2)) return;
            data.selectedProfile = p2.name;
            data.save();
            refreshProfiles();
        });
        p.add(profileCombo);
        p.add(Box.createVerticalStrut(4));
        JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        links.setOpaque(false);
        links.setAlignmentX(0);
        links.add(Theme.link("Edit", () -> { if (data.selected() != null) instances.edit(data.selected()); }));
        links.add(Box.createHorizontalStrut(12));
        links.add(Theme.link("Additions", () -> tabs.setSelectedComponent(additions)));
        links.add(Box.createHorizontalStrut(12));
        links.add(Theme.link("New", () -> instances.create(null)));
        links.add(Box.createHorizontalStrut(12));
        links.add(Theme.link("Folder", () -> {
            try { openFolder(data.gameDirFor(data.selected())); } catch (IOException ex) { console.warn("Couldn't open the folder: " + ex.getMessage()); }
        }));
        links.add(Box.createHorizontalStrut(12));
        links.add(Theme.link("All instances", () -> tabs.setSelectedComponent(instances)));
        p.add(links);
        return p;
    }

    private JComponent createPlayerPanel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        welcome.setAlignmentX(1);
        wearing.setAlignmentX(1);
        p.add(welcome);
        p.add(Box.createVerticalStrut(4));
        p.add(wearing);
        p.add(Box.createVerticalStrut(4));
        JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        links.setOpaque(false);
        links.setAlignmentX(1);
        links.add(Theme.link("Change name", this::switchUser));
        links.add(Box.createHorizontalStrut(12));
        links.add(Theme.link("Change skin", () -> tabs.setSelectedComponent(skins)));
        p.add(links);
        return p;
    }

    private void refreshProfiles() {
        updatingCombo = true;
        List<Profile> sorted = data.sortedProfiles();
        profileCombo.setModel(new DefaultComboBoxModel<>(sorted.toArray(new Profile[0])));
        profileCombo.setSelectedItem(data.selected());
        updatingCombo = false;
        if (instances != null) instances.refresh();
        if (worlds != null) worlds.refresh();
        if (additions != null) additions.refresh();
        refreshPlayer();
    }

    private void refreshPlayer() {
        welcome.setText("<html>Playing as <b>" + data.username + "</b></html>");
        LauncherData.CapeEntry cape = data.selectedCapeEntry();
        wearing.setText(data.selectedSkinEntry().name + (cape == null ? "" : " with " + cape.name)
            + "   ·   Minecraft " + LauncherData.GAME_VERSION);
    }

    private void switchUser() {
        while (true) {
            String name = Windows.input(frame, "Change name",
                "Enter a name to play with (no account needed).\nNames are 1-16 letters, digits or underscores.", data.username);
            if (name == null) return;
            String n = name.trim();
            if (n.matches("[A-Za-z0-9_]{1,16}")) {
                data.username = n;
                data.save();
                console.log("Switched user to " + n);
                refreshPlayer();
                return;
            }
            Windows.error(frame, "Change name", "\"" + n + "\" isn't a valid name.");
        }
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
            profile.lastPlayed = System.currentTimeMillis();
            data.save();
            instances.refresh();
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
            Windows.error(frame, "Can't start game", e.getMessage());
        }
    }

    private boolean chooseInstall() {
        JFileChooser chooser = new Windows.FileChooser(data.installDir == null ? null : data.installDir.toFile());
        chooser.setDialogTitle("Where is LegacyCraft? (the folder with " + LauncherData.wrapperName() + ")");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        while (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            Path dir = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            if (LauncherData.isInstall(dir)) {
                data.installDir = dir;
                data.save();
                if (worlds != null) worlds.refresh();
                console.log("LegacyCraft found at " + dir);
                return true;
            }
            Windows.error(frame, "Not a LegacyCraft folder", dir + " doesn't contain " + LauncherData.wrapperName() + ".");
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
        String jvmArgs = profile.jvmArgs();
        if (!jvmArgs.isBlank()) env.put("LEGACYCRAFT_JVM_ARGS", jvmArgs);
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
        console.log("Instance: " + profile.name + ", user: " + data.username + ", skin: " + data.selectedSkinEntry().name
            + ", cape: " + (cape == null ? "none" : cape.name));
        console.log("Half command: " + String.join(" ", pb.command()));
        game = pb.start();
        playButton.setEnabled(false);
        playButton.setText("Playing");

        if (visibility == Profile.Visibility.CLOSE_ON_START) {
            console.log("Closing launcher as requested by the instance (game output goes to " + outputLog + ")");
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
            tabs.setSelectedComponent(console);
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

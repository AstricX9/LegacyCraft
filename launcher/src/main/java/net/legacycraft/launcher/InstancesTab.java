package net.legacycraft.launcher;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The instance manager: every instance with its folder and settings, and
 * links to play, edit, copy, open or delete it. The selected instance is the
 * one the Play button starts.
 */
final class InstancesTab extends JPanel {
    /** What a copied instance takes from the original's folder. */
    private static final String[] COPIED = {"saves", "resourcepacks", "options.txt", "servers.dat", Additions.INSTANCE_FILE};

    private final LauncherData data;
    private final ConsoleTab console;
    private final Runnable onChange;
    private final Runnable play;
    private final Runnable showAdditions;
    private final JPanel rows = new JPanel();

    InstancesTab(LauncherData data, ConsoleTab console, Runnable onChange, Runnable play, Runnable showAdditions) {
        super(new BorderLayout());
        this.data = data;
        this.console = console;
        this.onChange = onChange;
        this.play = play;
        this.showAdditions = showAdditions;
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        actions.add(Theme.link("New instance", () -> create(null)));
        actions.add(Theme.link("Open instances folder", () -> open(data.instancesDir)));
        add(Theme.header("Instances", actions), BorderLayout.NORTH);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBackground(Theme.PANEL);
        JPanel top = new JPanel(new BorderLayout());
        top.add(rows, BorderLayout.NORTH);
        add(Theme.scroll(top), BorderLayout.CENTER);
        refresh();
    }

    void refresh() {
        rows.removeAll();
        for (Profile p : data.sortedProfiles()) rows.add(new Row(p));
        rows.revalidate();
        rows.repaint();
    }

    private final class Row extends JPanel {
        private final Profile profile;

        Row(Profile p) {
            super(new BorderLayout(16, 0));
            this.profile = p;
            boolean selected = p.name.equals(data.selectedProfile);
            setBackground(selected ? Theme.SELECTED : Theme.PANEL);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel title = Theme.link(p.name, () -> select());
            title.setFont(Theme.underlined(Theme.FONT.deriveFont(17f)));
            text.add(title);
            text.add(Box.createVerticalStrut(4));
            text.add(Theme.muted(data.gameDirFor(p).toString()));
            text.add(Box.createVerticalStrut(2));
            text.add(Theme.muted(details(p)));
            add(text, BorderLayout.CENTER);

            JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
            links.setOpaque(false);
            links.add(Theme.link("Play", () -> { select(); play.run(); }));
            links.add(Theme.link("Edit", () -> edit(p)));
            links.add(Theme.link("Additions", () -> additions(p)));
            links.add(Theme.link("Copy", () -> create(p)));
            links.add(Theme.link("Folder", () -> open(data.gameDirFor(p))));
            JLabel delete = Theme.link("Delete", () -> delete(p));
            delete.setEnabled(data.profiles.size() > 1);
            if (!delete.isEnabled()) delete.setToolTipText("The last instance can't be deleted");
            links.add(delete);
            JPanel right = new JPanel(new BorderLayout());
            right.setOpaque(false);
            right.add(links, BorderLayout.NORTH);
            add(right, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    select();
                    if (e.getClickCount() == 2) play.run();
                }
            });
        }

        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (profile.name.equals(data.selectedProfile)) {
                g.setColor(Theme.ACCENT);
                g.fillRect(0, 0, 4, getHeight() - 1);
            }
        }

        private void select() {
            if (profile.name.equals(data.selectedProfile)) return;
            data.selectedProfile = profile.name;
            data.save();
            onChange.run();
        }
    }

    private String details(Profile p) {
        StringBuilder s = new StringBuilder("Minecraft ").append(LauncherData.GAME_VERSION);
        s.append("   ·   Memory ").append(p.memoryMb == null ? "default" : p.memoryMb % 1024 == 0 ? p.memoryMb / 1024 + " GB" : p.memoryMb + " MB");
        if (p.resolutionWidth != null && p.resolutionHeight != null) s.append("   ·   ").append(p.resolutionWidth).append('x').append(p.resolutionHeight);
        if (data.launcherAddition(Additions.INSTANCE_SUMMARY) && !data.additions().entries.isEmpty()) {
            Map<String, Boolean> on = Additions.readInstance(data.gameDirFor(p));
            s.append("   ·   ").append(data.additions().enabled(on) ? "Additions on (" + data.additions().switchedOn(on) + ")" : "Additions off");
        }
        s.append("   ·   ").append(p.lastPlayed == null ? "Never played" : "Played " + ago(p.lastPlayed));
        return s.toString();
    }

    static String ago(long millis) {
        long minutes = Math.max(0, (System.currentTimeMillis() - millis) / 60_000);
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        if (days < 30) return days + (days == 1 ? " day ago" : " days ago");
        return new java.text.SimpleDateFormat("d MMM yyyy").format(new java.util.Date(millis));
    }

    // ---------------------------------------------------------------- actions

    /** New instance, or a copy of `template` (settings plus its worlds, resource packs and options). */
    void create(Profile template) {
        Profile p;
        if (template == null) {
            p = new Profile(data.uniqueProfileName("New instance"));
            p.memoryMb = Profile.DEFAULT_MEMORY_MB;
        } else {
            p = template.copy(data.uniqueProfileName(template.name + " (copy)"));
        }
        if (!InstanceDialog.edit(this, data, p, true)) return;
        Path to = data.gameDirFor(p);
        try { Files.createDirectories(to); } catch (IOException e) { console.warn("Couldn't create " + to + ": " + e.getMessage()); }
        console.log("Created instance " + p.name + " in " + to);
        onChange.run();
        if (template != null) copyGameData(data.gameDirFor(template), to);
    }

    private void copyGameData(Path from, Path to) {
        if (from.equals(to)) return;
        Thread copier = new Thread(() -> {
            try {
                for (String name : COPIED) {
                    Path src = from.resolve(name);
                    if (Files.exists(src)) copyTree(src, to.resolve(name));
                }
                console.log("Copied worlds, resource packs and options from " + from + " to " + to);
            } catch (IOException e) {
                console.warn("Couldn't copy everything from " + from + ": " + e.getMessage());
                SwingUtilities.invokeLater(() -> Windows.error(this, "Copy instance", "Couldn't copy everything from " + from + ":\n" + e.getMessage()));
            }
        }, "Instance copy");
        copier.setDaemon(true);
        copier.start();
    }

    private static void copyTree(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                Files.copy(file, dst.resolve(src.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    void edit(Profile p) {
        if (InstanceDialog.edit(this, data, p, false)) onChange.run();
    }

    /** Picks the instance and opens the Additions tab on it. */
    void additions(Profile p) {
        data.selectedProfile = p.name;
        data.save();
        onChange.run();
        showAdditions.run();
    }

    void delete(Profile p) {
        if (data.profiles.size() <= 1) return;
        Path dir = data.gameDirFor(p);
        boolean shared = data.profiles.values().stream().anyMatch(o -> o != p && data.gameDirFor(o).equals(dir));
        JCheckBox alsoFolder = new JCheckBox("Also delete its folder (" + worldCount(dir) + ")");
        Object message = data.managedDir(dir) && Files.isDirectory(dir) && !shared
            ? new Object[] {"Delete the instance \"" + p.name + "\"?", Box.createVerticalStrut(6), alsoFolder}
            : "Delete the instance \"" + p.name + "\"? Its folder stays where it is.";
        if (!Windows.confirm(this, "Delete instance", message)) return;
        data.profiles.remove(p.name);
        if (p.name.equals(data.selectedProfile)) data.selectedProfile = data.sortedProfiles().get(0).name;
        data.save();
        console.log("Deleted instance " + p.name);
        if (alsoFolder.isSelected()) {
            try {
                try (Stream<Path> walk = Files.walk(dir)) {
                    for (Path f : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) Files.delete(f);
                }
                console.log("Deleted " + dir);
            } catch (IOException e) {
                console.warn("Couldn't delete " + dir + ": " + e.getMessage());
                Windows.error(this, "Delete instance", "Couldn't delete everything in " + dir + ":\n" + e.getMessage());
            }
        }
        onChange.run();
    }

    private static String worldCount(Path dir) {
        int n = 0;
        try (Stream<Path> saves = Files.list(dir.resolve("saves"))) { n = (int) saves.filter(Files::isDirectory).count(); }
        catch (IOException ignored) {}
        return n == 0 ? "no worlds" : n == 1 ? "1 world" : n + " worlds";
    }

    private void open(Path dir) {
        try {
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception e) { console.warn("Couldn't open " + dir + ": " + e.getMessage()); }
    }
}

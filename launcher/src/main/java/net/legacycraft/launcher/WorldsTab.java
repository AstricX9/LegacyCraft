package net.legacycraft.launcher;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

/**
 * Worlds (not part of the 1.6 launcher): the selected profile's saves, and
 * importing worlds from a vanilla {@code .minecraft/saves} folder or a .zip.
 * Imported vanilla worlds are converted by the game the first time they're
 * opened from Singleplayer.
 */
final class WorldsTab extends JPanel {
    private final Supplier<Path> gameDir;
    private final ConsoleTab console;
    private final WorldTableModel model = new WorldTableModel();
    private final JTable table = new JTable(model);
    private final JButton importFolder = new JButton("Import World Folder...");
    private final JButton importZip = new JButton("Import .zip...");
    private final JLabel location = new JLabel();
    private boolean busy;

    WorldsTab(Supplier<Path> gameDir, ConsoleTab console) {
        super(new BorderLayout());
        this.gameDir = gameDir;
        this.console = console;
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        TransferHandler drop = new DropHandler();
        table.setTransferHandler(drop);
        scroll.setTransferHandler(drop);
        add(scroll, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton open = new JButton("Open Saves Folder"), refresh = new JButton("Refresh");
        importFolder.addActionListener(e -> chooseAndImport(true));
        importZip.addActionListener(e -> chooseAndImport(false));
        open.addActionListener(e -> openSaves());
        refresh.addActionListener(e -> refresh());
        buttons.add(importFolder);
        buttons.add(importZip);
        buttons.add(open);
        buttons.add(refresh);
        bar.add(buttons, BorderLayout.WEST);
        location.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
        bar.add(location, BorderLayout.CENTER);
        add(bar, BorderLayout.SOUTH);
        refresh();
    }

    private Path saves() { return WorldImporter.savesDir(gameDir.get()); }

    void refresh() {
        Path saves = saves();
        location.setText("Saves: " + saves);
        model.set(WorldImporter.list(saves));
    }

    private void openSaves() {
        try {
            Files.createDirectories(saves());
            Desktop.getDesktop().open(saves().toFile());
        } catch (IOException | RuntimeException e) {
            console.warn("Couldn't open " + saves() + ": " + e);
        }
    }

    private void chooseAndImport(boolean folder) {
        JFileChooser chooser = new JFileChooser(defaultSource());
        chooser.setDialogTitle(folder ? "Choose a world folder (the one with level.dat)" : "Choose a world .zip");
        if (folder) {
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        } else {
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setFileFilter(new FileFilter() {
                @Override public boolean accept(File f) { return f.isDirectory() || f.getName().toLowerCase().endsWith(".zip"); }
                @Override public String getDescription() { return "Zipped worlds (*.zip)"; }
            });
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) importWorld(chooser.getSelectedFile().toPath());
    }

    /** Vanilla's saves folder when there is one, since that's where most imports come from. */
    private static File defaultSource() {
        String appData = System.getenv("APPDATA");
        String home = System.getProperty("user.home");
        List<Path> candidates = new ArrayList<>();
        if (appData != null) candidates.add(Path.of(appData, ".minecraft", "saves"));
        candidates.add(Path.of(home, "Library", "Application Support", "minecraft", "saves"));
        candidates.add(Path.of(home, ".minecraft", "saves"));
        for (Path p : candidates) if (Files.isDirectory(p)) return p.toFile();
        return null;
    }

    private void importWorld(Path source) {
        if (busy) return;
        Path saves = saves();
        setBusy(true);
        console.log("Importing world from " + source);
        new SwingWorker<WorldImporter.Staged, Void>() {
            @Override protected WorldImporter.Staged doInBackground() throws IOException { return WorldImporter.stage(source, saves); }

            @Override protected void done() {
                WorldImporter.Staged staged;
                try {
                    staged = get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    console.warn("Couldn't import " + source + ": " + cause.getMessage());
                    JOptionPane.showMessageDialog(WorldsTab.this, "Couldn't import that world:\n" + cause.getMessage(),
                        "Import World", JOptionPane.ERROR_MESSAGE);
                    setBusy(false);
                    return;
                }
                finish(staged, saves);
                setBusy(false);
            }
        }.execute();
    }

    private void finish(WorldImporter.Staged staged, Path saves) {
        WorldImporter.World world = staged.world();
        String problem = world.problem();
        if (problem != null) {
            WorldImporter.discard(staged);
            console.warn("Not importing " + world.name() + ": " + problem);
            JOptionPane.showMessageDialog(this, "LegacyCraft can't open \"" + world.name() + "\" because " + problem,
                "Import World", JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<String> warnings = world.warnings();
        if (!warnings.isEmpty()) {
            String text = "Import \"" + world.name() + "\"?\n\n- " + String.join("\n- ", warnings)
                + "\n\nThe original world is not changed.";
            if (JOptionPane.showConfirmDialog(this, text, "Import World", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
                WorldImporter.discard(staged);
                console.log("Import of " + world.name() + " cancelled");
                return;
            }
        }
        try {
            String folder = WorldImporter.commit(staged, saves);
            console.log("Imported " + world.name() + " into " + saves.resolve(folder));
            refresh();
            for (int row = 0; row < model.getRowCount(); row++)
                if (model.at(row).folderName().equals(folder)) table.setRowSelectionInterval(row, row);
            JOptionPane.showMessageDialog(this, "Imported \"" + world.name() + "\" as saves/" + folder + "."
                + (world.legacyCraft() ? "" : "\nIt's converted to LegacyCraft's format the first time you open it in Singleplayer."),
                "Import World", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            WorldImporter.discard(staged);
            console.warn("Couldn't import " + world.name() + ": " + e);
            JOptionPane.showMessageDialog(this, "Couldn't copy the world into saves:\n" + e.getMessage(),
                "Import World", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        importFolder.setEnabled(!busy);
        importZip.setEnabled(!busy);
        importFolder.setText(busy ? "Importing..." : "Import World Folder...");
    }

    /** Dropping a world folder or .zip onto the list imports it. */
    private final class DropHandler extends TransferHandler {
        @Override public boolean canImport(TransferSupport support) {
            return !busy && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                if (files.isEmpty()) return false;
                if (files.size() > 1) console.warn("Dropped " + files.size() + " items; importing only " + files.get(0).getName());
                importWorld(files.get(0).toPath());
                return true;
            } catch (Exception e) {
                console.warn("Couldn't read the dropped files: " + e);
                return false;
            }
        }
    }

    private static final class WorldTableModel extends AbstractTableModel {
        private final SimpleDateFormat date = new SimpleDateFormat();
        private List<WorldImporter.World> rows = new ArrayList<>();

        void set(List<WorldImporter.World> worlds) { rows = worlds; fireTableDataChanged(); }
        WorldImporter.World at(int row) { return rows.get(row); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 4; }
        @Override public String getColumnName(int c) { return new String[] {"World", "Folder", "Last played", "Status"}[c]; }
        @Override public Object getValueAt(int r, int c) {
            WorldImporter.World w = rows.get(r);
            return switch (c) {
                case 0 -> w.name();
                case 1 -> w.folderName();
                case 2 -> w.lastPlayed() > 0 ? date.format(new Date(w.lastPlayed())) : "";
                default -> w.status();
            };
        }
    }
}

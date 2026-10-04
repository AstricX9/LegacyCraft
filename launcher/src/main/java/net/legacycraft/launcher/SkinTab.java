package net.legacycraft.launcher;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Skins and capes (not part of the 1.6 launcher, where skins were changed on
 * minecraft.net and capes were never user-chosen). The selected entry in each
 * list is what you wear; Play hands both to the game.
 */
final class SkinTab extends JPanel {
    private static final LauncherData.CapeEntry NO_CAPE = new LauncherData.CapeEntry("", "(No cape)", null);

    private final LauncherData data;
    private final Runnable onChange;
    private final DefaultListModel<LauncherData.SkinEntry> skinModel = new DefaultListModel<>();
    private final JList<LauncherData.SkinEntry> skinList = new JList<>(skinModel);
    private final DefaultListModel<LauncherData.CapeEntry> capeModel = new DefaultListModel<>();
    private final JList<LauncherData.CapeEntry> capeList = new JList<>(capeModel);
    private final SkinPreview preview = new SkinPreview();
    private final JRadioButton classic = new JRadioButton("Classic");
    private final JRadioButton slim = new JRadioButton("Slim");
    private final JButton renameSkin = new JButton("Rename"), removeSkin = new JButton("Remove");
    private final JButton renameCape = new JButton("Rename"), removeCape = new JButton("Remove");
    private final Map<String, BufferedImage> cache = new HashMap<>();
    private final Map<String, ImageIcon> icons = new HashMap<>();
    private boolean reloading;
    private File lastFolder;

    SkinTab(LauncherData data, Runnable onChange) {
        super(new BorderLayout(4, 0));
        this.data = data;
        this.onChange = onChange;
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        skinList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        skinList.setCellRenderer(new Renderer());
        skinList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && !reloading) wearSkin(); });
        capeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        capeList.setCellRenderer(new Renderer());
        capeList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && !reloading) wearCape(); });

        JButton addSkin = new JButton("Add...");
        addSkin.addActionListener(e -> choose("Add Skin", "Minecraft skins (64x64 or 64x32 PNG)", this::importSkins));
        renameSkin.addActionListener(e -> renameSkin());
        removeSkin.addActionListener(e -> removeSkin());
        ButtonGroup group = new ButtonGroup();
        group.add(classic);
        group.add(slim);
        classic.addActionListener(e -> setArmModel("classic"));
        slim.addActionListener(e -> setArmModel("slim"));
        JPanel arms = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        arms.add(new JLabel("Arms:"));
        arms.add(classic);
        arms.add(slim);
        JPanel skinBox = section("Skin", skinList, row(addSkin, renameSkin, removeSkin), arms);

        JButton addCape = new JButton("Add...");
        addCape.addActionListener(e -> choose("Add Cape", "Minecraft capes (64x32 PNG)", this::importCapes));
        renameCape.addActionListener(e -> renameCape());
        removeCape.addActionListener(e -> removeCape());
        JPanel capeBox = section("Cape", capeList, row(addCape, renameCape, removeCape), null);

        JPanel left = new JPanel(new GridLayout(2, 1, 0, 4));
        left.setPreferredSize(new Dimension(270, 100));
        left.add(skinBox);
        left.add(capeBox);

        JCheckBox outer = new JCheckBox("Show second layer", true);
        outer.addActionListener(e -> preview.setOuterLayer(outer.isSelected()));
        JLabel hint = new JLabel("Drag to turn. Drop PNG files on a list to add them.");
        hint.setEnabled(false);
        JPanel under = new JPanel(new BorderLayout());
        under.add(outer, BorderLayout.WEST);
        under.add(hint, BorderLayout.EAST);
        JPanel right = new JPanel(new BorderLayout(0, 2));
        preview.setBorder(BorderFactory.createLoweredBevelBorder());
        right.add(preview, BorderLayout.CENTER);
        right.add(under, BorderLayout.SOUTH);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);

        skinList.setTransferHandler(dropper(this::importSkins));
        capeList.setTransferHandler(dropper(this::importCapes));
        preview.setTransferHandler(dropper(this::importSkins));
        reload();
    }

    private static JPanel section(String title, JList<?> list, JComponent buttons, JComponent extra) {
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title),
            BorderFactory.createEmptyBorder(0, 3, 3, 3)));
        p.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        south.add(buttons);
        if (extra != null) {
            extra.setAlignmentX(LEFT_ALIGNMENT);
            south.add(Box.createVerticalStrut(2));
            south.add(extra);
        }
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private static JPanel row(JButton... buttons) {
        JPanel p = new JPanel(new GridLayout(1, buttons.length, 3, 0));
        for (JButton b : buttons) { b.setMargin(new java.awt.Insets(2, 2, 2, 2)); p.add(b); }
        return p;
    }

    private TransferHandler dropper(Consumer<File[]> importer) {
        return new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) { return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }
            @Override public boolean importData(TransferSupport s) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    importer.accept(files.toArray(new File[0]));
                    return true;
                } catch (Exception e) { return false; }
            }
        };
    }

    void reload() {
        reloading = true;
        skinModel.clear();
        for (LauncherData.SkinEntry s : data.skins) skinModel.addElement(s);
        skinList.setSelectedValue(data.selectedSkinEntry(), true);
        capeModel.clear();
        capeModel.addElement(NO_CAPE);
        for (LauncherData.CapeEntry c : data.capes) capeModel.addElement(c);
        LauncherData.CapeEntry cape = data.selectedCapeEntry();
        capeList.setSelectedValue(cape == null ? NO_CAPE : cape, true);
        reloading = false;
        refresh();
    }

    private void refresh() {
        LauncherData.SkinEntry s = data.selectedSkinEntry();
        renameSkin.setEnabled(!s.builtIn());
        removeSkin.setEnabled(!s.builtIn());
        classic.setEnabled(!s.builtIn());
        slim.setEnabled(!s.builtIn());
        (s.slim() ? slim : classic).setSelected(true);
        BufferedImage img = skinImage(s);
        preview.setSkin(img, s.slim(), img == null ? "Can't find this skin's texture" : null);
        LauncherData.CapeEntry c = data.selectedCapeEntry();
        renameCape.setEnabled(c != null);
        removeCape.setEnabled(c != null);
        preview.setCape(c == null ? null : capeImage(c));
        skinList.repaint();
        capeList.repaint();
    }

    private void wearSkin() {
        LauncherData.SkinEntry s = skinList.getSelectedValue();
        if (s == null || s.id.equals(data.selectedSkin)) return;
        data.selectedSkin = s.id;
        data.save();
        refresh();
        onChange.run();
    }

    private void wearCape() {
        LauncherData.CapeEntry c = capeList.getSelectedValue();
        String id = c == null || c == NO_CAPE ? null : c.id;
        if (java.util.Objects.equals(id, data.selectedCape)) return;
        data.selectedCape = id;
        data.save();
        refresh();
        onChange.run();
    }

    private void setArmModel(String m) {
        LauncherData.SkinEntry s = data.selectedSkinEntry();
        if (s.builtIn() || m.equals(s.model)) return;
        s.model = m;
        data.save();
        refresh();
    }

    // ---- images ----

    private BufferedImage skinImage(LauncherData.SkinEntry s) {
        BufferedImage img = cache.get("skin:" + s.id);
        if (img != null) return img;
        Path file = data.skinFile(s);
        if (file == null) return null;
        try {
            BufferedImage raw = ImageIO.read(file.toFile());
            if (raw == null) return null;
            img = SkinImages.process(raw);
            cache.put("skin:" + s.id, img);
        } catch (IOException ignored) {}
        return img;
    }

    private BufferedImage capeImage(LauncherData.CapeEntry c) {
        BufferedImage img = cache.get("cape:" + c.id);
        if (img != null) return img;
        try {
            img = SkinImages.readCape(data.capeFile(c));
            cache.put("cape:" + c.id, img);
        } catch (IOException ignored) {}
        return img;
    }

    /** The skin's face (with hat), or the cape's outer face, as a list icon. */
    private ImageIcon icon(Object value) {
        String key = value instanceof LauncherData.SkinEntry s ? "skin:" + s.id : "cape:" + ((LauncherData.CapeEntry) value).id;
        return icons.computeIfAbsent(key, k -> {
            BufferedImage out = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
            Graphics g = out.getGraphics();
            if (value instanceof LauncherData.SkinEntry s) {
                BufferedImage img = skinImage(s);
                if (img != null) {
                    g.drawImage(img.getSubimage(8, 8, 8, 8).getScaledInstance(24, 24, Image.SCALE_FAST), 0, 0, null);
                    g.drawImage(img.getSubimage(40, 8, 8, 8).getScaledInstance(24, 24, Image.SCALE_FAST), 0, 0, null);
                }
            } else if (value != NO_CAPE) {
                BufferedImage img = capeImage((LauncherData.CapeEntry) value);
                if (img != null) {
                    int u = img.getWidth() / 64;
                    g.drawImage(img.getSubimage(u, u, 10 * u, 16 * u).getScaledInstance(15, 24, Image.SCALE_FAST), 4, 0, null);
                }
            }
            g.dispose();
            return new ImageIcon(out);
        });
    }

    // ---- adding, renaming, removing ----

    private void choose(String title, String filter, Consumer<File[]> importer) {
        JFileChooser chooser = new JFileChooser(lastFolder);
        chooser.setDialogTitle(title);
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(filter, "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        lastFolder = chooser.getCurrentDirectory();
        importer.accept(chooser.getSelectedFiles());
    }

    private interface Importer { String add(Path file) throws IOException; }

    private void importAll(File[] files, Importer importer, Consumer<String> wear, String what) {
        String last = null;
        StringBuilder errors = new StringBuilder();
        for (File f : files) {
            try { last = importer.add(f.toPath()); }
            catch (IOException e) { errors.append(f.getName()).append(": ").append(e.getMessage()).append('\n'); }
        }
        if (last != null) {
            wear.accept(last);
            data.save();
            reload();
            onChange.run();
        }
        if (errors.length() > 0)
            JOptionPane.showMessageDialog(this, errors.toString().trim(), "Can't add " + what, JOptionPane.ERROR_MESSAGE);
    }

    private void importSkins(File[] files) {
        importAll(files, f -> data.importSkin(f, null).id, id -> data.selectedSkin = id, "skin");
    }

    private void importCapes(File[] files) {
        importAll(files, f -> data.importCape(f).id, id -> data.selectedCape = id, "cape");
    }

    private String askName(String title, String current) {
        Object name = JOptionPane.showInputDialog(this, "Name:", title, JOptionPane.PLAIN_MESSAGE, null, null, current);
        return name == null || name.toString().isBlank() ? null : name.toString().trim();
    }

    private boolean confirmRemove(String name) {
        return JOptionPane.showConfirmDialog(this, "Are you sure you want to remove " + name + "?", "Remove",
            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void renameSkin() {
        LauncherData.SkinEntry s = data.selectedSkinEntry();
        String name = s.builtIn() ? null : askName("Rename Skin", s.name);
        if (name == null) return;
        s.name = name;
        data.save();
        refresh();
        onChange.run();
    }

    private void removeSkin() {
        LauncherData.SkinEntry s = data.selectedSkinEntry();
        if (s.builtIn() || !confirmRemove(s.name)) return;
        data.removeSkin(s);
        cache.remove("skin:" + s.id);
        reload();
        onChange.run();
    }

    private void renameCape() {
        LauncherData.CapeEntry c = data.selectedCapeEntry();
        String name = c == null ? null : askName("Rename Cape", c.name);
        if (name == null) return;
        c.name = name;
        data.save();
        refresh();
        onChange.run();
    }

    private void removeCape() {
        LauncherData.CapeEntry c = data.selectedCapeEntry();
        if (c == null || !confirmRemove(c.name)) return;
        data.removeCape(c);
        cache.remove("cape:" + c.id);
        reload();
        onChange.run();
    }

    private final class Renderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
            super.getListCellRendererComponent(l, value, index, sel, focus);
            setIcon(icon(value));
            setIconTextGap(6);
            setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
            return this;
        }
    }
}

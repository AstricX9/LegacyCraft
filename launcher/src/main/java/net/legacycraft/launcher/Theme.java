package net.legacycraft.launcher;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.Map;

/**
 * The launcher's look: flat and square, in the style of World of Mods.
 * Grey section headers, underlined blue links, a green Play button, in a
 * light (black on white) or dark (white on charcoal) palette. Built on Metal with flat
 * painters so it needs no libraries and looks the same on every OS.
 */
final class Theme {
    // The palette in use; install() sets it. Light is near-black on white, dark is near-white on charcoal.
    static Color PAGE, PANEL, HEADER, STRIP, LINE, HOVER, SELECTED, TEXT, MUTED, DISABLED;
    static Color EDGE;                         // button, field and check box outlines
    static Color OUTLINE;                      // the 1px line around windows
    static Color TAB_HOVER, THUMB, THUMB_HOVER;
    static Color LINK, LINK_HOVER, ACCENT;     // links, the selected tab bar and the selected instance bar
    static Color PLAY, PLAY_HOVER, PLAY_PRESSED;
    static Color DANGER;
    static boolean dark;

    static {
        palette(false);
    }

    private static void palette(boolean darkMode) {
        dark = darkMode;
        if (!darkMode) {
            PAGE = new Color(0xE9E5E1); PANEL = Color.WHITE; HEADER = new Color(0xD5CFC9); STRIP = new Color(0xC6BFB8);
            LINE = new Color(0xA39C95); HOVER = new Color(0xEDEAE6); SELECTED = new Color(0xDAD4CE);
            TEXT = Color.BLACK; MUTED = new Color(0x45403C); DISABLED = new Color(0x7D7770);
            EDGE = new Color(0x5E5852); OUTLINE = new Color(0x3D3833);
            TAB_HOVER = new Color(0xDAD4CE); THUMB = new Color(0x9C958E); THUMB_HOVER = new Color(0x3D3833);
            LINK = new Color(0x0B57B0); LINK_HOVER = new Color(0x063A78); ACCENT = new Color(0x0B57B0);
            PLAY = new Color(0x237A35); PLAY_HOVER = new Color(0x2A8F3F); PLAY_PRESSED = new Color(0x1B5E29);
            DANGER = new Color(0xB00020);
        } else {
            PAGE = new Color(0x18181A); PANEL = new Color(0x202023); HEADER = new Color(0x34343A); STRIP = new Color(0x2A2A2F);
            LINE = new Color(0x55555D); HOVER = new Color(0x2B2B30); SELECTED = new Color(0x3A3A42);
            TEXT = new Color(0xF2F2F2); MUTED = new Color(0xC2C2CA); DISABLED = new Color(0x7C7C85);
            EDGE = new Color(0x9A9AA3); OUTLINE = new Color(0x606068);
            TAB_HOVER = new Color(0x36363C); THUMB = new Color(0x5C5C64); THUMB_HOVER = new Color(0xA5A5AE);
            LINK = new Color(0x8AB4FF); LINK_HOVER = new Color(0xBFD5FF); ACCENT = new Color(0x8AB4FF);
            PLAY = new Color(0x2E8B3E); PLAY_HOVER = new Color(0x37A24A); PLAY_PRESSED = new Color(0x246F31);
            DANGER = new Color(0xFF6B7A);
        }
    }

    /** "#rrggbb" for a palette colour, for the news page's HTML. */
    static String hex(Color c) { return String.format("#%06x", c.getRGB() & 0xFFFFFF); }

    static final Font FONT = new Font("SansSerif", Font.PLAIN, 13);
    static final Font BOLD = FONT.deriveFont(Font.BOLD);

    private Theme() {}

    /** Sets the palette and the Swing defaults; call before building any component (again to switch modes). */
    static void install(boolean darkMode) {
        palette(darkMode);
        MetalLookAndFeel.setCurrentTheme(new Flat());
        try { UIManager.setLookAndFeel(new MetalLookAndFeel()); } catch (Exception ignored) {}
        System.setProperty("awt.useSystemAAFontSettings", "on");

        FontUIResource font = new FontUIResource(FONT);
        for (Object key : UIManager.getLookAndFeelDefaults().keySet().toArray())
            if (key instanceof String s && s.endsWith(".font")) UIManager.put(key, font);

        Border line = new BorderUIResource(BorderFactory.createLineBorder(LINE));
        Border field = new BorderUIResource(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE), BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        for (String k : new String[] {"TextField", "FormattedTextField", "PasswordField", "Spinner"})
            UIManager.put(k + ".border", field);
        UIManager.put("TextArea.margin", new InsetsUIResource(4, 6, 4, 6));
        UIManager.put("ScrollPane.border", line);
        UIManager.put("Table.scrollPaneBorder", line);
        UIManager.put("ComboBox.border", line);
        UIManager.put("PopupMenu.border", line);
        UIManager.put("ToolTip.border", line);
        UIManager.put("ToolTip.background", new ColorUIResource(PANEL));
        UIManager.put("TitledBorder.border", line);
        UIManager.put("List.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder(1, 1, 1, 1));
        UIManager.put("TableHeader.cellBorder", BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 1, LINE), BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        UIManager.put("TableHeader.background", new ColorUIResource(HEADER));
        UIManager.put("Table.gridColor", new ColorUIResource(LINE));
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
        UIManager.put("OptionPane.errorIcon", null);
        UIManager.put("OptionPane.warningIcon", null);
        UIManager.put("OptionPane.informationIcon", null);
        UIManager.put("OptionPane.questionIcon", null);
        UIManager.put("OptionPane.background", new ColorUIResource(PANEL));
        ColorUIResource panel = new ColorUIResource(PANEL), text = new ColorUIResource(TEXT);
        ColorUIResource selection = new ColorUIResource(dark ? new Color(0x2F4F80) : new Color(0xBCD3F2));
        ColorUIResource disabled = new ColorUIResource(DISABLED);
        for (String k : new String[] {"Panel", "Label", "CheckBox", "RadioButton", "Button", "ToggleButton", "Viewport",
                "ScrollPane", "TabbedPane", "OptionPane", "TextField", "FormattedTextField", "PasswordField", "TextArea",
                "EditorPane", "TextPane", "List", "Table", "ComboBox", "Spinner", "PopupMenu", "MenuItem", "Menu",
                "Tree", "FileChooser", "ToolTip", "SplitPane", "Separator"}) {
            UIManager.put(k + ".background", panel);
            UIManager.put(k + ".foreground", text);
            UIManager.put(k + ".caretForeground", text);
            UIManager.put(k + ".selectionBackground", selection);
            UIManager.put(k + ".selectionForeground", text);
            UIManager.put(k + ".inactiveForeground", disabled);
            UIManager.put(k + ".disabledText", disabled);
            UIManager.put(k + ".disabledForeground", disabled);
        }
        UIManager.put("List.selectionBackground", new ColorUIResource(SELECTED));
        UIManager.put("Table.selectionBackground", new ColorUIResource(SELECTED));
        UIManager.put("ComboBox.selectionBackground", new ColorUIResource(SELECTED));
        UIManager.put("MenuItem.selectionBackground", new ColorUIResource(SELECTED));
        UIManager.put("ComboBox.disabledBackground", panel);
        UIManager.put("TextField.inactiveBackground", panel);
        UIManager.put("TextField.disabledBackground", panel);
        UIManager.put("TableHeader.foreground", text);
        UIManager.put("Label.disabledForeground", disabled);
        UIManager.put("CheckBox.icon", new BoxIcon(false));
        UIManager.put("RadioButton.icon", new BoxIcon(true));
        UIManager.put("Button.margin", new InsetsUIResource(5, 14, 5, 14));
        UIManager.put("ScrollBar.width", 12);
        for (String k : new String[] {"yes", "no", "ok", "cancel"}) UIManager.put("OptionPane." + k + "ButtonMnemonic", "0");

        UIManager.put("ButtonUI", FlatButtonUI.class.getName());
        UIManager.put("ToggleButtonUI", FlatButtonUI.class.getName());
        UIManager.put(FlatButtonUI.class.getName(), FlatButtonUI.class);
        UIManager.put("ComboBoxUI", FlatComboBoxUI.class.getName());
        UIManager.put(FlatComboBoxUI.class.getName(), FlatComboBoxUI.class);
        UIManager.put("ScrollBarUI", FlatScrollBarUI.class.getName());
        UIManager.put(FlatScrollBarUI.class.getName(), FlatScrollBarUI.class);
    }

    /** Metal's colours swapped for flat greys; selection is a light grey, not blue. */
    private static final class Flat extends DefaultMetalTheme {
        @Override public String getName() { return "LegacyCraft flat"; }
        @Override protected javax.swing.plaf.ColorUIResource getPrimary1() { return new ColorUIResource(TEXT); }
        @Override protected javax.swing.plaf.ColorUIResource getPrimary2() { return new ColorUIResource(LINE); }
        @Override protected javax.swing.plaf.ColorUIResource getPrimary3() { return new ColorUIResource(SELECTED); }
        @Override protected javax.swing.plaf.ColorUIResource getSecondary1() { return new ColorUIResource(LINE); }
        @Override protected javax.swing.plaf.ColorUIResource getSecondary2() { return new ColorUIResource(LINE); }
        @Override protected javax.swing.plaf.ColorUIResource getSecondary3() { return new ColorUIResource(PANEL); }
        @Override protected javax.swing.plaf.ColorUIResource getBlack() { return new ColorUIResource(TEXT); }
        @Override protected javax.swing.plaf.ColorUIResource getWhite() { return new ColorUIResource(PANEL); }
        @Override public javax.swing.plaf.ColorUIResource getInactiveControlTextColor() { return new ColorUIResource(DISABLED); }
        @Override public javax.swing.plaf.ColorUIResource getInactiveSystemTextColor() { return new ColorUIResource(DISABLED); }
        @Override public javax.swing.plaf.ColorUIResource getHighlightedTextColor() { return new ColorUIResource(TEXT); }
        @Override public javax.swing.plaf.ColorUIResource getMenuSelectedForeground() { return new ColorUIResource(TEXT); }
        @Override public javax.swing.plaf.ColorUIResource getMenuSelectedBackground() { return new ColorUIResource(SELECTED); }
        @Override public javax.swing.plaf.ColorUIResource getTextHighlightColor() { return new ColorUIResource(dark ? new Color(0x2F4F80) : new Color(0xBCD3F2)); }
        @Override public javax.swing.plaf.ColorUIResource getFocusColor() { return new ColorUIResource(LINE); }
        @Override public FontUIResource getControlTextFont() { return new FontUIResource(FONT); }
        @Override public FontUIResource getSystemTextFont() { return new FontUIResource(FONT); }
        @Override public FontUIResource getUserTextFont() { return new FontUIResource(FONT); }
        @Override public FontUIResource getMenuTextFont() { return new FontUIResource(FONT); }
        @Override public FontUIResource getWindowTitleFont() { return new FontUIResource(BOLD); }
        @Override public FontUIResource getSubTextFont() { return new FontUIResource(FONT.deriveFont(11f)); }
    }

    // ------------------------------------------------------------ factories

    /** A grey section header bar with bold dark text, like WoM's "Subcategories". */
    static JComponent header(String text) { return header(text, null); }

    /** A section header with components (usually links) on its right. */
    static JComponent header(String text, JComponent right) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(HEADER);
        bar.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        JLabel label = new JLabel(text);
        label.setFont(BOLD);
        label.setForeground(TEXT);
        bar.add(label, BorderLayout.WEST);
        if (right != null) { right.setOpaque(false); bar.add(right, BorderLayout.EAST); }
        return bar;
    }

    /** A plain underlined text link. */
    static JLabel link(String text, Runnable action) {
        JLabel l = new JLabel(text);
        l.setForeground(LINK);
        l.setFont(underlined(FONT));
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (l.isEnabled() && e.getButton() == MouseEvent.BUTTON1) action.run(); }
            @Override public void mouseEntered(MouseEvent e) { if (l.isEnabled()) l.setForeground(LINK_HOVER); }
            @Override public void mouseExited(MouseEvent e) { l.setForeground(LINK); }
        });
        return l;
    }

    static Font underlined(Font f) {
        return f.deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON));
    }

    /** The big green button (Play). */
    static JButton primary(String text) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatButtonUI.PRIMARY, true);
        b.setFont(BOLD.deriveFont(16f));
        return b;
    }

    static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        return l;
    }

    /** A scroll pane without its own border, scrolling at a sensible speed. */
    static JScrollPane scroll(Component view) {
        JScrollPane s = new JScrollPane(view);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.getVerticalScrollBar().setUnitIncrement(16);
        s.getViewport().setBackground(PANEL);
        return s;
    }

    // ------------------------------------------------------------ painters

    /** Square flat buttons: panel-coloured with an outline, or solid green when PRIMARY is set. */
    public static final class FlatButtonUI extends BasicButtonUI {
        static final String PRIMARY = "legacycraft.primary";
        private static final FlatButtonUI INSTANCE = new FlatButtonUI();

        public static ComponentUI createUI(JComponent c) { return INSTANCE; }

        @Override public void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setOpaque(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
            b.setRolloverEnabled(true);
            b.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
        }

        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();
            boolean primary = Boolean.TRUE.equals(b.getClientProperty(PRIMARY));
            Color fill, edge;
            if (primary) {
                fill = !b.isEnabled() ? (dark ? new Color(0x3E4A40) : new Color(0x8A9A8D)) : m.isPressed() ? PLAY_PRESSED : m.isRollover() ? PLAY_HOVER : PLAY;
                edge = fill;
            } else {
                boolean on = m.isPressed() || m.isSelected();
                fill = on ? SELECTED : m.isRollover() && b.isEnabled() ? HOVER : PANEL;
                edge = b.isEnabled() ? EDGE : LINE;
            }
            g.setColor(fill);
            g.fillRect(0, 0, c.getWidth(), c.getHeight());
            g.setColor(edge);
            g.drawRect(0, 0, c.getWidth() - 1, c.getHeight() - 1);
            if (b.isFocusOwner() && !primary) {
                g.setColor(TEXT);
                g.drawRect(0, 0, c.getWidth() - 1, c.getHeight() - 1);
            }
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            super.paint(g, c);
        }

        @Override protected void paintText(Graphics g, AbstractButton b, Rectangle r, String text) {
            boolean primary = Boolean.TRUE.equals(b.getClientProperty(PRIMARY));
            g.setColor(primary ? Color.WHITE : b.isEnabled() ? TEXT : DISABLED);
            g.setFont(b.getFont());
            java.awt.FontMetrics fm = g.getFontMetrics();
            int mnemonic = b.getDisplayedMnemonicIndex();
            javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(b, (Graphics2D) g, text, mnemonic, r.x, r.y + fm.getAscent());
        }
    }

    /** A square combo box with a plain chevron. */
    public static final class FlatComboBoxUI extends BasicComboBoxUI {
        public static ComponentUI createUI(JComponent c) { return new FlatComboBoxUI(); }

        @Override protected void installDefaults() {
            super.installDefaults();
            comboBox.setBorder(BorderFactory.createLineBorder(EDGE));
            comboBox.setForeground(TEXT);
            comboBox.setBackground(PANEL);
        }

        @Override protected JButton createArrowButton() {
            JButton b = new BasicArrowButton(SwingConstants.SOUTH) {
                @Override public void paint(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(PANEL);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(TEXT);
                    g2.setStroke(new BasicStroke(1.5f));
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
                    g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
                    g2.dispose();
                }
                @Override public Dimension getPreferredSize() { return new Dimension(22, 22); }
            };
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setName("ComboBox.arrowButton");
            return b;
        }

        @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            g.setColor(PANEL);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            super.paintCurrentValue(g, new Rectangle(bounds.x + 4, bounds.y, bounds.width - 4, bounds.height), false);
        }
    }

    /** A thin grey thumb, no arrow buttons. */
    public static final class FlatScrollBarUI extends BasicScrollBarUI {
        public static ComponentUI createUI(JComponent c) { return new FlatScrollBarUI(); }

        @Override protected void configureScrollBarColors() {
            thumbColor = THUMB;
            trackColor = PAGE;
        }

        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }

        private static JButton zero() {
            JButton b = new JButton();
            Dimension none = new Dimension(0, 0);
            b.setPreferredSize(none); b.setMinimumSize(none); b.setMaximumSize(none);
            return b;
        }

        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            g.setColor(isThumbRollover() || isDragging ? THUMB_HOVER : thumbColor);
            if (scrollbar.getOrientation() == JScrollBar.VERTICAL) g.fillRect(r.x + 3, r.y + 2, r.width - 6, r.height - 4);
            else g.fillRect(r.x + 2, r.y + 3, r.width - 4, r.height - 6);
        }
    }

    /** Square check boxes with a tick, and square radio buttons with a filled centre. */
    private static final class BoxIcon implements Icon, javax.swing.plaf.UIResource {
        private final boolean radio;

        BoxIcon(boolean radio) { this.radio = radio; }

        @Override public int getIconWidth() { return 14; }
        @Override public int getIconHeight() { return 14; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            ButtonModel m = ((AbstractButton) c).getModel();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(m.isPressed() ? SELECTED : PANEL);
            g2.fillRect(x, y, 13, 13);
            g2.setColor(c.isEnabled() ? EDGE : LINE);
            g2.drawRect(x, y, 13, 13);
            if (m.isSelected()) {
                g2.setColor(c.isEnabled() ? ACCENT : DISABLED);
                if (radio) g2.fillRect(x + 4, y + 4, 6, 6);
                else {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawLine(x + 3, y + 7, x + 6, y + 10);
                    g2.drawLine(x + 6, y + 10, x + 11, y + 3);
                }
            }
            g2.dispose();
        }
    }
}

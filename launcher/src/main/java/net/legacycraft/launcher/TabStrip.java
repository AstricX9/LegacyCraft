package net.legacycraft.launcher;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The top bar, which is also the window's title bar: the launcher icon, flat
 * tabs (the selected one white, joined to the page below it, the others grey
 * with a thin divider, like World of Mods' game strip), empty space to drag
 * the window by, and the window buttons. The tabs mirror a JTabbedPane whose
 * own tab row is hidden, so code keeps using addTab and setSelectedComponent.
 */
final class TabStrip extends JPanel {
    private final JTabbedPane tabs;
    private final JPanel cells = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    TabStrip(JTabbedPane tabs, JFrame frame, Image icon) {
        super(new BorderLayout());
        this.tabs = tabs;
        setBackground(Theme.STRIP);
        tabs.setUI(new HiddenTabsUI());
        tabs.setBorder(BorderFactory.createEmptyBorder());

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        if (icon != null) {
            JLabel logo = new JLabel(new ImageIcon(icon.getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
            logo.setPreferredSize(new Dimension(52, 44));
            logo.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Theme.LINE));
            logo.setToolTipText("LegacyCraft Launcher " + LauncherApp.LAUNCHER_VERSION);
            Windows.dragBy(frame, logo);
            left.add(logo, BorderLayout.WEST);
        }
        cells.setOpaque(false);
        left.add(cells, BorderLayout.CENTER);
        add(left, BorderLayout.WEST);

        JComponent filler = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.LINE);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        Windows.dragBy(frame, filler);
        add(filler, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Theme.LINE);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        controls.setOpaque(false);
        controls.add(new Windows.Control(Windows.Control.Kind.MINIMIZE, () -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED)));
        controls.add(new Windows.Control(Windows.Control.Kind.MAXIMIZE, () -> Windows.toggleMaximized(frame)));
        controls.add(new Windows.Control(Windows.Control.Kind.CLOSE, () -> frame.dispatchEvent(
            new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING))));
        for (java.awt.Component c : controls.getComponents()) c.setPreferredSize(new Dimension(46, 44));
        add(controls, BorderLayout.EAST);

        tabs.addChangeListener(e -> cells.repaint());
        tabs.addContainerListener(new ContainerListener() {
            @Override public void componentAdded(ContainerEvent e) { SwingUtilities.invokeLater(TabStrip.this::rebuild); }
            @Override public void componentRemoved(ContainerEvent e) { SwingUtilities.invokeLater(TabStrip.this::rebuild); }
        });
        rebuild();
    }

    private void rebuild() {
        cells.removeAll();
        for (int i = 0; i < tabs.getTabCount(); i++) cells.add(new Cell(i));
        cells.revalidate();
        cells.repaint();
    }

    private final class Cell extends JComponent {
        private final int index;
        private boolean hover;

        Cell(int index) {
            this.index = index;
            setFont(Theme.BOLD.deriveFont(14f));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) tabs.setSelectedIndex(index); }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        private String title() { return index < tabs.getTabCount() ? tabs.getTitleAt(index) : ""; }

        @Override public Dimension getPreferredSize() {
            return new Dimension(getFontMetrics(getFont()).stringWidth(title()) + 40, 44);
        }

        @Override protected void paintComponent(Graphics raw) {
            Graphics2D g = (Graphics2D) raw.create();
            boolean selected = tabs.getSelectedIndex() == index;
            g.setColor(selected ? Theme.PANEL : hover ? new Color(0xDAD4CE) : Theme.STRIP);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Theme.LINE);
            g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            if (!selected) g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            else { g.setColor(Theme.TEXT); g.fillRect(0, 0, getWidth() - 1, 3); }
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont());
            g.setColor(selected ? Theme.TEXT : new Color(0x1A1A1A));
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.drawString(title(), (getWidth() - fm.stringWidth(title())) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    /** A JTabbedPane look with no tab row and no content border: the strip above stands in for the tabs. */
    private static final class HiddenTabsUI extends BasicTabbedPaneUI {
        @Override protected int calculateTabAreaHeight(int placement, int runs, int maxTabHeight) { return 0; }
        @Override protected int calculateTabHeight(int placement, int index, int fontHeight) { return 0; }
        @Override protected Insets getContentBorderInsets(int placement) { return new Insets(0, 0, 0, 0); }
        @Override protected Insets getTabAreaInsets(int placement) { return new Insets(0, 0, 0, 0); }
        @Override protected void paintTabArea(Graphics g, int placement, int selected) {}
        @Override protected void paintContentBorder(Graphics g, int placement, int selected) {}
        @Override public int tabForCoordinate(JTabbedPane pane, int x, int y) { return -1; }
        @Override public Rectangle getTabBounds(JTabbedPane pane, int i) { return new Rectangle(); }
    }
}

package net.legacycraft.launcher;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Square, undecorated windows. The launcher draws its own title bars so
 * Windows 11 can't round the corners (it only rounds windows with a system
 * frame), and so dialogs match the flat look: a grey title strip, a 1px
 * outline, and nothing else.
 */
final class Windows {
    static final Color OUTLINE = new Color(0x3D3833);
    private static final int EDGE = 5;

    private Windows() {}

    /** Lets the window be moved by dragging `handle` (and toggled maximised by double-clicking it, for frames). */
    static void dragBy(Window window, Component handle) {
        MouseAdapter drag = new MouseAdapter() {
            private Point offset;

            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || Resizer.active) { offset = null; return; }
                if (window instanceof Frame f && (f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) { offset = null; return; }
                Point p = e.getLocationOnScreen();
                offset = new Point(p.x - window.getX(), p.y - window.getY());
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (offset == null) return;
                Point p = e.getLocationOnScreen();
                window.setLocation(p.x - offset.x, p.y - offset.y);
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && window instanceof JFrame f) toggleMaximized(f);
            }
        };
        handle.addMouseListener(drag);
        handle.addMouseMotionListener(drag);
    }

    static void toggleMaximized(JFrame f) {
        if ((f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) { f.setExtendedState(Frame.NORMAL); return; }
        // An undecorated window would otherwise cover the taskbar when maximised.
        GraphicsConfiguration gc = f.getGraphicsConfiguration();
        Rectangle screen = gc.getBounds();
        Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        f.setMaximizedBounds(new Rectangle(screen.x + in.left, screen.y + in.top,
            screen.width - in.left - in.right, screen.height - in.top - in.bottom));
        f.setExtendedState(Frame.MAXIMIZED_BOTH);
    }

    /** Edge and corner resizing for an undecorated frame, wherever the pointer is within a few pixels of its border. */
    static void resizable(JFrame frame) {
        Toolkit.getDefaultToolkit().addAWTEventListener(new Resizer(frame)::handle, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    private static final class Resizer {
        static boolean active;
        private final JFrame frame;
        private int edge = Cursor.DEFAULT_CURSOR;
        private Rectangle start;
        private Point from;

        Resizer(JFrame frame) { this.frame = frame; }

        void handle(AWTEvent event) {
            if (!(event instanceof MouseEvent e) || !(e.getSource() instanceof Component c)) return;
            if (SwingUtilities.getWindowAncestor(c) != frame && c != frame) return;
            if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) return;
            switch (e.getID()) {
                case MouseEvent.MOUSE_MOVED -> {
                    int now = edgeAt(SwingUtilities.convertPoint(c, e.getPoint(), frame));
                    if (now != edge) {
                        edge = now;
                        frame.setCursor(Cursor.getPredefinedCursor(now));
                    }
                }
                case MouseEvent.MOUSE_PRESSED -> {
                    if (edge != Cursor.DEFAULT_CURSOR && SwingUtilities.isLeftMouseButton(e)) {
                        active = true;
                        start = frame.getBounds();
                        from = e.getLocationOnScreen();
                    }
                }
                case MouseEvent.MOUSE_DRAGGED -> { if (active) resize(e.getLocationOnScreen()); }
                case MouseEvent.MOUSE_RELEASED -> active = false;
                case MouseEvent.MOUSE_EXITED -> {
                    if (!active && c == frame.getRootPane()) { edge = Cursor.DEFAULT_CURSOR; frame.setCursor(null); }
                }
                default -> {}
            }
        }

        private int edgeAt(Point p) {
            boolean w = p.x < EDGE, e = p.x >= frame.getWidth() - EDGE, n = p.y < EDGE, s = p.y >= frame.getHeight() - EDGE;
            if (n && w) return Cursor.NW_RESIZE_CURSOR;
            if (n && e) return Cursor.NE_RESIZE_CURSOR;
            if (s && w) return Cursor.SW_RESIZE_CURSOR;
            if (s && e) return Cursor.SE_RESIZE_CURSOR;
            if (w) return Cursor.W_RESIZE_CURSOR;
            if (e) return Cursor.E_RESIZE_CURSOR;
            if (n) return Cursor.N_RESIZE_CURSOR;
            if (s) return Cursor.S_RESIZE_CURSOR;
            return Cursor.DEFAULT_CURSOR;
        }

        private void resize(Point p) {
            int dx = p.x - from.x, dy = p.y - from.y;
            Rectangle r = new Rectangle(start);
            Dimension min = frame.getMinimumSize();
            boolean west = edge == Cursor.W_RESIZE_CURSOR || edge == Cursor.NW_RESIZE_CURSOR || edge == Cursor.SW_RESIZE_CURSOR;
            boolean east = edge == Cursor.E_RESIZE_CURSOR || edge == Cursor.NE_RESIZE_CURSOR || edge == Cursor.SE_RESIZE_CURSOR;
            boolean north = edge == Cursor.N_RESIZE_CURSOR || edge == Cursor.NW_RESIZE_CURSOR || edge == Cursor.NE_RESIZE_CURSOR;
            boolean south = edge == Cursor.S_RESIZE_CURSOR || edge == Cursor.SW_RESIZE_CURSOR || edge == Cursor.SE_RESIZE_CURSOR;
            if (east) r.width = Math.max(min.width, start.width + dx);
            if (south) r.height = Math.max(min.height, start.height + dy);
            if (west) { r.width = Math.max(min.width, start.width - dx); r.x = start.x + start.width - r.width; }
            if (north) { r.height = Math.max(min.height, start.height - dy); r.y = start.y + start.height - r.height; }
            frame.setBounds(r);
            frame.validate();
        }
    }

    /** A flat title-bar button: minimise, maximise or close. */
    static final class Control extends JComponent {
        enum Kind { MINIMIZE, MAXIMIZE, CLOSE }

        private final Kind kind;
        private boolean hover;

        Control(Kind kind, Runnable action) {
            this.kind = kind;
            setPreferredSize(new Dimension(42, 30));
            setToolTipText(switch (kind) { case MINIMIZE -> "Minimise"; case MAXIMIZE -> "Maximise"; case CLOSE -> "Close"; });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) action.run(); }
            });
        }

        @Override protected void paintComponent(Graphics raw) {
            Graphics2D g = (Graphics2D) raw.create();
            if (hover) {
                g.setColor(kind == Kind.CLOSE ? Color.BLACK : new Color(0x00000018, true));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            g.setColor(hover && kind == Kind.CLOSE ? Color.WHITE : Theme.TEXT);
            g.setStroke(new BasicStroke(1f));
            int cx = getWidth() / 2, cy = getHeight() / 2;
            switch (kind) {
                case MINIMIZE -> g.drawLine(cx - 5, cy, cx + 5, cy);
                case MAXIMIZE -> g.drawRect(cx - 5, cy - 5, 10, 10);
                case CLOSE -> {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                    g.drawLine(cx - 5, cy + 5, cx + 5, cy - 5);
                }
            }
            g.dispose();
        }
    }

    // ---------------------------------------------------------------- dialogs

    /** A modal, undecorated dialog with a grey title strip and a close cross. */
    static final class Dialog extends JDialog {
        private final JPanel root = new JPanel(new BorderLayout());

        Dialog(Component parent, String title) {
            super(windowOf(parent), title, ModalityType.APPLICATION_MODAL);
            setUndecorated(true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            root.setBorder(BorderFactory.createLineBorder(OUTLINE));
            root.setBackground(Theme.PANEL);
            JPanel bar = new JPanel(new BorderLayout());
            bar.setBackground(Theme.STRIP);
            bar.setPreferredSize(new Dimension(10, 34));
            JLabel label = new JLabel(title);
            label.setFont(Theme.BOLD);
            label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            bar.add(label, BorderLayout.CENTER);
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            controls.setOpaque(false);
            Control close = new Control(Control.Kind.CLOSE, this::dispose);
            close.setPreferredSize(new Dimension(40, 34));
            controls.add(close);
            bar.add(controls, BorderLayout.EAST);
            dragBy(this, bar);
            dragBy(this, label);
            root.add(bar, BorderLayout.NORTH);
            setContentPane(root);
            getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        void setBody(Component body) {
            root.add(body, BorderLayout.CENTER);
        }

        void open(Component parent) {
            pack();
            setLocationRelativeTo(parent);
            setVisible(true);
        }
    }

    static Window windowOf(Component c) {
        if (c == null) return null;
        return c instanceof Window w ? w : SwingUtilities.getWindowAncestor(c);
    }

    /** Shows a JOptionPane inside a flat dialog; returns the pane's value (UNINITIALIZED_VALUE when closed). */
    private static Object show(Component parent, String title, JOptionPane pane) {
        Dialog d = new Dialog(parent, title);
        pane.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, e -> {
            if (e.getNewValue() != JOptionPane.UNINITIALIZED_VALUE) d.dispose();
        });
        d.setBody(pane);
        d.pack();
        d.setMinimumSize(new Dimension(Math.max(320, d.getWidth()), d.getHeight()));
        d.setLocationRelativeTo(parent);
        pane.selectInitialValue();
        d.setVisible(true);
        return pane.getValue();
    }

    static void message(Component parent, String title, Object message) {
        show(parent, title, new JOptionPane(message, JOptionPane.PLAIN_MESSAGE));
    }

    static void error(Component parent, String title, Object message) {
        show(parent, title, new JOptionPane(message, JOptionPane.ERROR_MESSAGE));
    }

    static boolean confirm(Component parent, String title, Object message) {
        Object value = show(parent, title, new JOptionPane(message, JOptionPane.PLAIN_MESSAGE, JOptionPane.YES_NO_OPTION));
        return value instanceof Integer i && i == JOptionPane.YES_OPTION;
    }

    /** Asks for a line of text; null when cancelled. */
    static String input(Component parent, String title, Object message, String initial) {
        JOptionPane pane = new JOptionPane(message, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        pane.setWantsInput(true);
        pane.setInitialSelectionValue(initial);
        Object value = show(parent, title, pane);
        if (!(value instanceof Integer i) || i != JOptionPane.OK_OPTION) return null;
        Object text = pane.getInputValue();
        return text == null || text == JOptionPane.UNINITIALIZED_VALUE ? null : text.toString();
    }

    /** A file chooser that opens in a flat dialog instead of a system-framed one. */
    static final class FileChooser extends JFileChooser {
        FileChooser(java.io.File dir) { super(dir); }

        @Override protected JDialog createDialog(Component parent) throws HeadlessException {
            String title = getDialogTitle() != null ? getDialogTitle() : getUI().getDialogTitle(this);
            Dialog d = new Dialog(parent, title);
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            d.setBody(this);
            d.pack();
            d.setLocationRelativeTo(parent);
            return d;
        }
    }
}

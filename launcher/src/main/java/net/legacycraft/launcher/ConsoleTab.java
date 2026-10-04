package net.legacycraft.launcher;

import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.Date;

/** The "Development Console" tab: launcher log lines and the game's output prefixed with "Client>". */
final class ConsoleTab extends JScrollPane {
    private static final int MAX_CHARS = 2_000_000;
    private final JTextArea text = new JTextArea();
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss");

    ConsoleTab() {
        text.setEditable(false);
        text.setMargin(null);
        text.setLineWrap(true);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        setViewportView(text);
        setBorder(null);
    }

    /** A launcher message in the launcher's log format. */
    void log(String message) { append("[" + time.format(new Date()) + " INFO]: " + message); }

    void warn(String message) { append("[" + time.format(new Date()) + " WARN]: " + message); }

    void client(String line) { append("Client> " + line); }

    private void append(String line) {
        System.out.println(line);
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = getVerticalScrollBar();
            boolean atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 4;
            text.append(line + "\n");
            int excess = text.getDocument().getLength() - MAX_CHARS;
            if (excess > 0) text.replaceRange("", 0, excess);
            if (atBottom) text.setCaretPosition(text.getDocument().getLength());
        });
    }
}

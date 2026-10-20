package net.runelite.client.plugins.microbot.nirigrotesqueguardians;

import net.runelite.client.plugins.microbot.statemachine.StateSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;

final class GrotesqueGuardiansDiagnosticsWindow {
    private final GrotesqueGuardiansScript script;
    private final GrotesqueGuardiansConfig config;
    private final Runnable onDisposed;
    private JFrame frame;
    private JTextArea output;
    private Timer refreshTimer;

    GrotesqueGuardiansDiagnosticsWindow(GrotesqueGuardiansScript script,
                                        GrotesqueGuardiansConfig config,
                                        Runnable onDisposed) {
        this.script = script;
        this.config = config;
        this.onDisposed = onDisposed;
    }

    void showWindow() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) buildWindow();
            frame.setAlwaysOnTop(config.diagnosticsAlwaysOnTop());
            frame.setVisible(true);
            frame.toFront();
            refresh();
        });
    }

    void close() {
        SwingUtilities.invokeLater(() -> {
            if (refreshTimer != null) refreshTimer.stop();
            if (frame != null) frame.dispose();
        });
    }

    private void buildWindow() {
        frame = new JFrame("Grotesque Guardians — Live Diagnostics");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(620, 500));
        frame.setSize(760, 760);
        frame.setLocationByPlatform(true);

        output = new JTextArea();
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        output.setLineWrap(false);
        output.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton copy = new JButton("Copy snapshot");
        copy.addActionListener(event -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(output.getText()), null));
        JButton refresh = new JButton("Refresh now");
        refresh.addActionListener(event -> refresh());
        JPanel controls = new JPanel();
        controls.add(refresh);
        controls.add(copy);

        frame.add(new JScrollPane(output), BorderLayout.CENTER);
        frame.add(controls, BorderLayout.SOUTH);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (refreshTimer != null) refreshTimer.stop();
                frame = null;
                output = null;
                refreshTimer = null;
                onDisposed.run();
            }
        });

        refreshTimer = new Timer(config.diagnosticsRefreshMs(), event -> {
            refreshTimer.setDelay(config.diagnosticsRefreshMs());
            frame.setAlwaysOnTop(config.diagnosticsAlwaysOnTop());
            refresh();
        });
        refreshTimer.start();
    }

    private void refresh() {
        if (output == null) return;
        StateSnapshot<GrotesqueGuardiansState> snapshot = script.getSnapshot();
        if (snapshot == null) {
            output.setText("Waiting for the Grotesque Guardians state machine...\n");
            return;
        }
        StringBuilder text = new StringBuilder(4096);
        appendValue(text, snapshot.toMap(), 0);
        output.setText(text.toString());
        output.setCaretPosition(0);
    }

    private static void appendValue(StringBuilder text, Object value, int depth) {
        if (value instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                indent(text, depth).append(entry.getKey()).append(':');
                Object child = entry.getValue();
                if (child instanceof Map<?, ?> || child instanceof List<?>) {
                    text.append('\n');
                    appendValue(text, child, depth + 1);
                } else {
                    text.append(' ').append(String.valueOf(child)).append('\n');
                }
            }
        } else if (value instanceof List<?>) {
            List<?> values = (List<?>) value;
            if (values.isEmpty()) {
                indent(text, depth).append("[]\n");
            } else {
                for (Object child : values) {
                    indent(text, depth).append("- ");
                    if (child instanceof Map<?, ?> || child instanceof List<?>) {
                        text.append('\n');
                        appendValue(text, child, depth + 1);
                    } else {
                        text.append(String.valueOf(child)).append('\n');
                    }
                }
            }
        }
    }

    private static StringBuilder indent(StringBuilder text, int depth) {
        for (int i = 0; i < depth; i++) text.append("  ");
        return text;
    }
}

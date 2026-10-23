package net.runelite.client.plugins.microbot.nirigrotesqueguardiansbanking;

import net.runelite.client.plugins.microbot.statemachine.StateSnapshot;
import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.List;
import java.util.Map;
import java.util.Collections;

final class BankingGuardiansDiagnosticsWindow {
    private final BankingGuardiansScript script;
    private final BankingGuardiansConfig config;
    private final Runnable onDisposed;
    private JFrame frame;
    private Dashboard dashboard;
    private Timer refreshTimer;

    BankingGuardiansDiagnosticsWindow(BankingGuardiansScript script,
                                        BankingGuardiansConfig config, Runnable onDisposed) {
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
        frame = new JFrame("Grotesque Guardians Banking — Diagnostics");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(320, 300));
        frame.setSize(820, 760);
        frame.setLocationByPlatform(true);
        dashboard = new Dashboard();
        frame.add(dashboard, BorderLayout.CENTER);
        JPanel controls = new JPanel(new GridLayout(1, 2, 8, 0));
        controls.setBackground(Dashboard.BACKGROUND);
        controls.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        JButton refresh = new JButton("Refresh now");
        refresh.addActionListener(event -> refresh());
        JButton copy = new JButton("Copy snapshot");
        copy.addActionListener(event -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(dashboard.snapshotText), null);
                copy.setText("Copied");
            } catch (IllegalStateException | SecurityException ex) {
                copy.setText("Clipboard unavailable");
            }
            Timer reset = new Timer(1500, ignored -> copy.setText("Copy snapshot"));
            reset.setRepeats(false);
            reset.start();
        });
        controls.add(refresh);
        controls.add(copy);
        frame.add(controls, BorderLayout.SOUTH);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                if (refreshTimer != null) refreshTimer.stop();
                frame = null;
                dashboard = null;
                refreshTimer = null;
                onDisposed.run();
            }
        });
        refreshTimer = new Timer(Math.max(100, config.diagnosticsRefreshMs()), event -> {
            if (frame == null) return;
            refreshTimer.setDelay(Math.max(100, config.diagnosticsRefreshMs()));
            frame.setAlwaysOnTop(config.diagnosticsAlwaysOnTop());
            refresh();
        });
        refreshTimer.start();
    }

    private void refresh() {
        if (dashboard == null) return;
        StateSnapshot<BankingGuardiansState> snapshot = script.getSnapshot();
        dashboard.update(snapshot == null ? Collections.emptyMap() : snapshot.toMap(), script.getKillCount());
    }

    /** Also renderable offscreen for layout checks; contains no game/client calls. */
    static final class Dashboard extends JPanel {
        static final Color BACKGROUND = new Color(19, 25, 35);
        private final JTabbedPane tabs = new JTabbedPane();
        private final JEditorPane overview = editor();
        private final JEditorPane details = editor();
        private final JTextArea raw = new JTextArea();
        private final JScrollPane overviewScroll = scroll(overview);
        private final JScrollPane detailsScroll = scroll(details);
        private final JScrollPane rawScroll = scroll(raw);
        private Map<String, Object> data = Collections.emptyMap();
        private int kills;
        private String snapshotText = "Waiting for the state machine...";

        Dashboard() {
            super(new BorderLayout(0, 10));
            setBackground(BACKGROUND);
            setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
            JLabel title = new JLabel("GUARDIANS / BANKING");
            title.setForeground(new Color(117, 211, 192));
            title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
            title.setBorder(BorderFactory.createEmptyBorder(2, 2, 6, 2));
            add(title, BorderLayout.NORTH);
            raw.setEditable(false);
            raw.setLineWrap(true);
            raw.setWrapStyleWord(true);
            raw.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            raw.setBackground(BACKGROUND);
            raw.setForeground(new Color(216, 225, 237));
            raw.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            ((DefaultCaret) raw.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            tabs.setBackground(new Color(32, 44, 61));
            tabs.setForeground(new Color(216, 225, 237));
            tabs.addTab("Overview", overviewScroll);
            tabs.addTab("All details", detailsScroll);
            tabs.addTab("Raw snapshot", rawScroll);
            tabs.addChangeListener(event -> render());
            overviewScroll.getViewport().addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent event) { render(); }
            });
            add(tabs, BorderLayout.CENTER);
            update(Collections.emptyMap(), 0);
        }

        void update(Map<String, Object> snapshot, int killCount) {
            data = snapshot;
            kills = killCount;
            StringBuilder plain = new StringBuilder();
            appendPlain(plain, snapshot, 0);
            snapshotText = snapshot.isEmpty() ? "Waiting for the state machine..." : plain.toString();
            render();
        }

        private void render() {
            if (tabs.getSelectedIndex() == 2) {
                if ((!raw.isFocusOwner() || raw.getSelectionStart() == raw.getSelectionEnd())
                        && !raw.getText().equals(snapshotText)) {
                    Point position = rawScroll.getViewport().getViewPosition();
                    raw.setText(snapshotText);
                    restore(rawScroll, position);
                }
                return;
            }
            JEditorPane target = tabs.getSelectedIndex() == 1 ? details : overview;
            if (target.isFocusOwner() && target.getSelectionStart() != target.getSelectionEnd()) return;
            StringBuilder html = new StringBuilder("<html><head><style>body {font-family:Dialog;font-size:11pt;color:#d8e1ed;background:#131923;margin:14px;} h1 {font-size:20pt;color:#ffffff;margin-bottom:8px;} h2 {font-size:13pt;color:#75d3c0;margin-top:20px;margin-bottom:8px;} h3 {font-size:11pt;color:#aebed4;} p {margin-top:6px;margin-bottom:8px;} td {padding:10px;background:#202c3d;} .muted {color:#aebed4;} .value {font-size:20pt;color:#ffffff;} </style></head><body>");
            if (data.isEmpty()) {
                html.append("<h1>Waiting for a snapshot</h1><p class='muted'>Live diagnostics will appear when the script starts.</p>");
            } else if (target == details) {
                appendHtml(html, data, 2);
            } else {
                Map<?, ?> diagnostics = map(data.get("diagnostics"));
                Map<?, ?> player = map(diagnostics.get("player"));
                Map<?, ?> encounter = map(diagnostics.get("encounter"));
                html.append("<p class='muted'>LIVE / ").append(escape(encounter.get("pluginVersion"))).append("</p><h1>")
                        .append(escape(label(String.valueOf(data.get("currentState")))))
                        .append("</h1><p>").append(escape(diagnostics.get("status"))).append("</p>");
                int columns = overviewScroll.getViewport().getWidth() < 560 ? 2 : 4;
                String[] names = {"Hitpoints", "Prayer", "Special energy", "Kills"};
                Object[] values = {value(player, "hitpoints") + " / " + value(player, "maxHitpoints"),
                        value(player, "prayer") + " / " + value(player, "maxPrayer"),
                        value(player, "specEnergy") + "%", kills};
                html.append("<table width='100%' cellspacing='5'>");
                for (int i = 0; i < names.length; i++) {
                    if (i % columns == 0) html.append("<tr>");
                    html.append("<td><span class='muted'>").append(names[i]).append("</span><br><span class='value'>")
                            .append(escape(values[i])).append("</span></td>");
                    if ((i + 1) % columns == 0) html.append("</tr>");
                }
                html.append("</table>");
                section(html, "Last action", diagnostics.get("lastAction"));
                section(html, "Banking", diagnostics.get("banking"));
                section(html, "Equipment & supplies", diagnostics.get("supplies"));
                html.append("<h2>Encounter</h2>");
                for (String key : new String[]{"hazardCount", "evading", "escapingPrison", "prisonTicksRemaining", "gameTick", "loopRunning"})
                    field(html, key, encounter.get(key));
                section(html, "Last transition", data.get("lastTransitionReason"));
                html.append("<p class='muted'>Open All details for mechanics, events, tuning and transition history.</p>");
            }
            html.append("</body></html>");
            String rendered = html.toString();
            if (rendered.equals(target.getClientProperty("renderedSnapshot"))) return;
            target.putClientProperty("renderedSnapshot", rendered);
            JScrollPane pane = target == details ? detailsScroll : overviewScroll;
            Point position = pane.getViewport().getViewPosition();
            target.setText(rendered);
            restore(pane, position);
        }

        private static void restore(JScrollPane pane, Point position) {
            SwingUtilities.invokeLater(() -> {
                Dimension view = pane.getViewport().getViewSize();
                Dimension extent = pane.getViewport().getExtentSize();
                pane.getViewport().setViewPosition(new Point(0, Math.min(position.y, Math.max(0, view.height - extent.height))));
            });
        }
        private static JEditorPane editor() {
            JEditorPane pane = new JEditorPane() {
                @Override public boolean getScrollableTracksViewportWidth() { return true; }
            };
            pane.setContentType("text/html");
            pane.setEditable(false);
            pane.setBackground(BACKGROUND);
            pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            ((DefaultCaret) pane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            return pane;
        }
        private static JScrollPane scroll(JComponent content) {
            JScrollPane pane = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            pane.setBorder(BorderFactory.createEmptyBorder());
            pane.getViewport().setBackground(BACKGROUND);
            pane.getVerticalScrollBar().setUnitIncrement(20);
            return pane;
        }
        private static Map<?, ?> map(Object value) { return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap(); }
        private static Object value(Map<?, ?> map, String key) { return map.containsKey(key) ? map.get(key) : "—"; }
        private static void section(StringBuilder html, String title, Object value) {
            html.append("<h2>").append(escape(title)).append("</h2>");
            appendHtml(html, value, 3);
        }
        private static void field(StringBuilder html, Object name, Object value) {
            html.append("<p><span class='muted'>").append(escape(label(String.valueOf(name))))
                    .append(": </span>");
            if (value instanceof Boolean) html.append((Boolean) value ? "<font color='#75d3c0'>Yes</font>" : "<font color='#aebed4'>No</font>");
            else html.append(escape(value));
            html.append("</p>");
        }
        private static void appendHtml(StringBuilder html, Object value, int depth) {
            if (value instanceof Map<?, ?>) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    Object child = entry.getValue();
                    if (child instanceof Map || child instanceof List) {
                        int heading = Math.min(3, depth);
                        html.append("<h").append(heading).append(">").append(escape(label(String.valueOf(entry.getKey()))))
                                .append("</h").append(heading).append(">");
                        appendHtml(html, child, depth + 1);
                    } else field(html, entry.getKey(), child);
                }
            } else if (value instanceof List<?>) {
                List<?> items = (List<?>) value;
                if (items.isEmpty()) html.append("<p class='muted'>None</p>");
                for (Object item : items) {
                    html.append("<hr>");
                    appendHtml(html, item, depth);
                }
            } else html.append("<p>").append(escape(value)).append("</p>");
        }
        private static String label(String name) {
            String text = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
            return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
        }
        private static String escape(Object value) {
            String text = value == null ? "—" : String.valueOf(value);
            // Swing HTML has no overflow-wrap: add break opportunities to long identifiers.
            text = text.replaceAll("(\\S{32})(?=\\S)", "$1 ");
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }
        private static void appendPlain(StringBuilder text, Object value, int depth) {
            if (value instanceof Map<?, ?>) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    text.append(String.join("", Collections.nCopies(depth, "  "))).append(entry.getKey()).append(':');
                    if (entry.getValue() instanceof Map || entry.getValue() instanceof List) {
                        text.append('\n'); appendPlain(text, entry.getValue(), depth + 1);
                    } else text.append(' ').append(entry.getValue()).append('\n');
                }
            } else if (value instanceof List<?>) {
                List<?> values = (List<?>) value;
                if (values.isEmpty()) text.append(String.join("", Collections.nCopies(depth, "  "))).append("[]\n");
                for (Object child : values) {
                    text.append(String.join("", Collections.nCopies(depth, "  "))).append("-\n");
                    appendPlain(text, child, depth + 1);
                }
            } else text.append(String.join("", Collections.nCopies(depth, "  "))).append(value).append('\n');
        }
    }
}


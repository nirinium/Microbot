package net.runelite.client.plugins.microbot.niriaraxxor.dashboard;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local, read-only view of Araxxor diagnostics from the RuneLite client log. */
public final class AraxxorDashboardServer
{
    private static final int DEFAULT_PORT = 8082;
    private static final long MAX_LOG_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_EVENTS = 400;
    private static final Pattern TRACE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} [^ ]+) .*?ARAXXOR_TRACE tick=(\\d+) ([A-Z_]+) (.*)$");
    private static final Pattern METRICS = Pattern.compile("Araxxor metrics: Kill (\\d+): (\\d+)s, dmg (\\d+), food (\\d+), moves (\\d+), attacks (\\d+), acid (\\d+)t, prayer misses (\\d+), attack-ready movement (\\d+)t");
    private static final String RESOURCE = "/net/runelite/client/plugins/microbot/niriaraxxor/dashboard/index.html";

    private AraxxorDashboardServer()
    {
    }

    public static void main(String[] args) throws Exception
    {
        int port = DEFAULT_PORT;
        boolean openBrowser = true;
        for (String arg : args)
        {
            if ("--no-open".equals(arg))
            {
                openBrowser = false;
            }
            else if (arg.startsWith("--port="))
            {
                port = Integer.parseInt(arg.substring("--port=".length()));
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", AraxxorDashboardServer::serveIndex);
        server.createContext("/api/data", AraxxorDashboardServer::serveData);
        server.setExecutor(Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "araxxor-dashboard");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();

        URI uri = URI.create("http://127.0.0.1:" + port + "/");
        System.out.println("Araxxor dashboard: " + uri);
        System.out.println("Reading: " + logPath());
        System.out.println("Press Ctrl+C to stop.");
        if (openBrowser && Desktop.isDesktopSupported())
        {
            try
            {
                Desktop.getDesktop().browse(uri);
            }
            catch (IOException ignored)
            {
                // The URL printed above remains available when desktop integration is unavailable.
            }
        }
    }

    private static void serveIndex(HttpExchange exchange) throws IOException
    {
        if (!isAllowed(exchange) || !"/".equals(exchange.getRequestURI().getPath()))
        {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        try (InputStream input = AraxxorDashboardServer.class.getResourceAsStream(RESOURCE))
        {
            if (input == null)
            {
                send(exchange, 500, "text/plain; charset=utf-8", "Dashboard resource missing");
                return;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, count);
            }
            send(exchange, 200, "text/html; charset=utf-8", output.toByteArray());
        }
    }

    private static void serveData(HttpExchange exchange) throws IOException
    {
        if (!isAllowed(exchange))
        {
            send(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"forbidden\"}");
            return;
        }
        try
        {
            send(exchange, 200, "application/json; charset=utf-8", parseLog());
        }
        catch (IOException ex)
        {
            send(exchange, 200, "application/json; charset=utf-8",
                "{\"available\":false,\"message\":\"client.log is not available yet\",\"events\":[]}");
        }
    }

    private static boolean isAllowed(HttpExchange exchange)
    {
        if (!"GET".equals(exchange.getRequestMethod()) || !exchange.getRemoteAddress().getAddress().isLoopbackAddress())
        {
            return false;
        }
        String host = exchange.getRequestHeaders().getFirst("Host");
        return host != null && (host.startsWith("127.0.0.1:") || host.startsWith("localhost:"));
    }

    private static String parseLog() throws IOException
    {
        List<String> lines = readTail(logPath());
        List<Event> events = new ArrayList<>();
        Metrics metrics = null;
        String latestSnapshot = "Waiting for Araxxor telemetry";
        for (String line : lines)
        {
            Matcher metricMatcher = METRICS.matcher(line);
            if (metricMatcher.find())
            {
                metrics = new Metrics(metricMatcher);
            }
            Matcher traceMatcher = TRACE.matcher(line);
            if (traceMatcher.find())
            {
                Event event = new Event(traceMatcher.group(1), Integer.parseInt(traceMatcher.group(2)),
                    traceMatcher.group(3), traceMatcher.group(4));
                events.add(event);
                if ("SNAPSHOT".equals(event.type))
                {
                    latestSnapshot = event.detail;
                }
            }
        }
        if (events.size() > MAX_EVENTS)
        {
            events = new ArrayList<>(events.subList(events.size() - MAX_EVENTS, events.size()));
        }
        Collections.reverse(events);

        StringBuilder json = new StringBuilder(32768);
        json.append("{\"available\":true,\"updatedAt\":").append(System.currentTimeMillis());
        json.append(",\"snapshot\":\"").append(escape(latestSnapshot)).append('"');
        json.append(",\"metrics\":");
        if (metrics == null)
        {
            json.append("null");
        }
        else
        {
            json.append(metrics.toJson());
        }
        json.append(",\"events\":[");
        for (int i = 0; i < events.size(); i++)
        {
            if (i > 0)
            {
                json.append(',');
            }
            json.append(events.get(i).toJson());
        }
        return json.append("]}").toString();
    }

    private static List<String> readTail(Path path) throws IOException
    {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r"))
        {
            long start = Math.max(0L, file.length() - MAX_LOG_BYTES);
            file.seek(start);
            if (start > 0)
            {
                file.readLine();
            }
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = file.readLine()) != null)
            {
                lines.add(new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
            }
            return lines;
        }
    }

    private static Path logPath()
    {
        return Paths.get(System.getProperty("user.home"), ".runelite", "logs", "client.log");
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException
    {
        send(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException
    {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String escape(String value)
    {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++)
        {
            char character = value.charAt(i);
            switch (character)
            {
                case '\\': escaped.append("\\\\"); break;
                case '"': escaped.append("\\\""); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (character < 0x20)
                    {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    }
                    else
                    {
                        escaped.append(character);
                    }
            }
        }
        return escaped.toString();
    }

    private static final class Event
    {
        private final String time;
        private final int tick;
        private final String type;
        private final String detail;

        private Event(String time, int tick, String type, String detail)
        {
            this.time = time;
            this.tick = tick;
            this.type = type;
            this.detail = detail;
        }

        private String toJson()
        {
            return "{\"time\":\"" + escape(time) + "\",\"tick\":" + tick + ",\"type\":\""
                + escape(type) + "\",\"detail\":\"" + escape(detail) + "\"}";
        }
    }

    private static final class Metrics
    {
        private final int[] values = new int[9];

        private Metrics(Matcher matcher)
        {
            for (int i = 0; i < values.length; i++)
            {
                values[i] = Integer.parseInt(matcher.group(i + 1));
            }
        }

        private String toJson()
        {
            return String.format(Locale.ROOT,
                "{\"kill\":%d,\"seconds\":%d,\"damage\":%d,\"food\":%d,\"moves\":%d,\"attacks\":%d,\"acidTicks\":%d,\"prayerMisses\":%d,\"attackReadyMovement\":%d}",
                values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8]);
        }
    }
}

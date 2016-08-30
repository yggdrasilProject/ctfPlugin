package ru.linachan.ctf;

import com.mongodb.client.MongoCollection;
import com.sun.net.httpserver.*;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.bson.Document;
import org.json.simple.JSONObject;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.common.Queue;
import ru.linachan.yggdrasil.plugin.YggdrasilPluginManager;
import ru.linachan.yggdrasil.service.YggdrasilService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;

public class CTFHTTPFlagListener implements HttpHandler, YggdrasilService {

    private HttpServer flagServer;
    private static final Pattern REQUEST_PATTERN = Pattern.compile(
        "^/flag/((?<type>hi|no|lo)/(?<flag>[a-fA-F0-9]{32}=)|(?<static>status|status\\.json))$"
    );

    private Queue<String> flagQueue;

    @Override
    @SuppressWarnings("unchecked")
    public void onInit() {
        flagQueue = (Queue<String>) YggdrasilCore.INSTANCE.getQueue("ctfFlags");

        String host = YggdrasilCore.INSTANCE.getConfig().getString("ctf.acceptor.host", "0.0.0.0");
        int port = YggdrasilCore.INSTANCE.getConfig().getInt("ctf.acceptor.http.port", 9999);

        try {
            flagServer = HttpServer.create(new InetSocketAddress(host, port), 0);

            HttpContext apiContext = flagServer.createContext("/", this);

            flagServer.start();
        } catch (IOException e) {
            logger.error("Unable to start APIServer: {}", e.getMessage());
        }
    }

    @Override
    public void onShutdown() {
        if (flagServer != null) {
            flagServer.stop(0);
        }
    }

    @Override
    public void run() { }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(HttpExchange exchange) throws IOException {
        Matcher matcher = REQUEST_PATTERN.matcher(exchange.getRequestURI().toString());
        OutputStream os;

        if (matcher.matches()) {
            if (matcher.group("type") != null) {
                switch (matcher.group("type")) {
                    case "hi":
                        flagQueue.push(String.format("%s:%d", matcher.group("flag"), 2));
                        break;
                    case "no":
                        flagQueue.push(String.format("%s:%d", matcher.group("flag"), 1));
                        break;
                    case "lo":
                        flagQueue.push(String.format("%s:%d", matcher.group("flag"), 0));
                        break;
                }

                byte[] bytes = "OK".getBytes();
                exchange.sendResponseHeaders(200, bytes.length);

                os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            } else if (matcher.group("static") != null) {
                MongoCollection<Document> flags = YggdrasilCore.INSTANCE
                    .getManager(YggdrasilPluginManager.class).get(CTFPlugin.class)
                    .getDB().getCollection("flags");

                switch (matcher.group("static")) {
                    case "status":
                        ByteArrayOutputStream template = new ByteArrayOutputStream();
                        IOUtils.copy(
                            this.getClass().getResourceAsStream("/status.html"),
                            template
                        );

                        exchange.getResponseHeaders().add("Content-Type", "text/html");
                        exchange.sendResponseHeaders(200, template.size());

                        os = exchange.getResponseBody();
                        os.write(template.toByteArray());
                        os.close();
                        break;
                    case "status.json":
                        JSONObject stats = new JSONObject();
                        JSONObject queued = new JSONObject();

                        queued.put("high", flags.count(new Document("state", 0).append("priority", 2)));
                        queued.put("normal", flags.count(new Document("state", 0).append("priority", 1)));
                        queued.put("low", flags.count(new Document("state", 0).append("priority", 0)));

                        stats.put("processing", flagQueue.list().size());
                        stats.put("queued", queued);
                        stats.put("sent", flags.count(new Document("state", 1)));
                        stats.put("invalid", flags.count(new Document("state", 2)));
                        stats.put("total", flags.count());

                        byte[] statsBytes = stats.toJSONString().getBytes();

                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, statsBytes.length);

                        os = exchange.getResponseBody();
                        os.write(statsBytes);
                        os.close();
                        break;
                }
            }
        } else {
            byte[] bytes = "BADASS".getBytes();
            exchange.sendResponseHeaders(400, bytes.length);

            os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}

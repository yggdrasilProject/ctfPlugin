package ru.linachan.ctf;

import com.sun.net.httpserver.*;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.common.Queue;
import ru.linachan.yggdrasil.service.YggdrasilService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CTFHTTPFlagListener implements HttpHandler, YggdrasilService {

    private HttpServer flagServer;
    private static final Pattern REQUEST_PATTERN = Pattern.compile(
        "^/flag/(?<type>hi|no|lo)/(?<flag>[a-fA-F0-9]{32}=)$"
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
    public void handle(HttpExchange exchange) throws IOException {
        Matcher matcher = REQUEST_PATTERN.matcher(exchange.getRequestURI().toString());
        if (matcher.matches()) {
            byte[] bytes = "OK".getBytes();
            exchange.sendResponseHeaders(200, bytes.length);

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

            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } else {
            byte[] bytes = "BADASS".getBytes();
            exchange.sendResponseHeaders(400, bytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}

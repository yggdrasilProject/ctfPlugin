package ru.linachan.ctf;

import com.mongodb.client.MongoCollection;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.util.CharsetUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.bson.Document;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import ru.linachan.ctf.common.AbstractServer;
import ru.linachan.ctf.common.WebSocketMessageHandler;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.common.Queue;
import ru.linachan.yggdrasil.plugin.YggdrasilPluginManager;
import ru.linachan.yggdrasil.service.YggdrasilService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CTFFlagListener implements YggdrasilService {

    private Queue<String> flagQueue;

    private AbstractServer hiServer;
    private AbstractServer noServer;
    private AbstractServer loServer;

    private AbstractServer httpServer;

    private MongoCollection<Document> flags;
    private static final Pattern REQUEST_PATTERN = Pattern.compile(
        "^/flag/((?<type>hi|no|lo)/(?<flag>[a-fA-F0-9]{32}=)|(?<static>status))$"
    );

    private ExecutorService threadPool = Executors.newWorkStealingPool();

    @ChannelHandler.Sharable
    private class FlagHandler extends ChannelInboundHandlerAdapter {

        private int priority;

        public FlagHandler(int priority) {
            this.priority = priority;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            flagQueue.push(String.format("%s:%d", msg, priority));
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("Unable to process request: [{}]: {}", cause.getClass().getSimpleName(), cause.getMessage());
            ctx.close();
        }
    }

    @SuppressWarnings("unchecked")
    private class FlagWSHandler implements WebSocketMessageHandler {

        @Override
        public String handleMessage(ChannelHandlerContext ctx, String frameText) {
            JSONObject response = new JSONObject();
            try {
                JSONObject request = (JSONObject) new JSONParser().parse(frameText);
                switch ((String) request.getOrDefault("action", null)) {
                    case "get_stats":
                        response.put("status", "ok");
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

                        response.put("stats", stats);
                        break;
                    default:
                        response.put("status", "error");
                        response.put("errorMessage", "Unknown action");
                        break;
                }

            } catch (ParseException e) {
                response.put("status", "error");
                response.put("errorMessage", String.format("Invalid request: %s", e.getMessage()));
            }

            return response.toJSONString();
        }
    }

    @ChannelHandler.Sharable
    private class HttpHandler extends SimpleChannelInboundHandler<Object>  {

        private WebSocketServerHandshaker handShaker;
        private StringBuilder frameBuffer = null;

        private WebSocketMessageHandler wsMessageHandler = new FlagWSHandler();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof FullHttpRequest) {
                handleHttpRequest(ctx, (FullHttpRequest)msg);
            } else if (msg instanceof WebSocketFrame) {
                handleWebSocketFrame(ctx, (WebSocketFrame)msg);
            }
        }

        private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
            logger.debug("Received incoming frame [{}]", frame.getClass().getName());
            // Check for closing frame
            if (frame instanceof CloseWebSocketFrame) {
                if (frameBuffer != null) {
                    handleMessageCompleted(ctx, frameBuffer.toString());
                }
                handShaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
                return;
            }

            if (frame instanceof PingWebSocketFrame) {
                ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
                return;
            }

            if (frame instanceof PongWebSocketFrame) {
                logger.info("Pong frame received");
                return;
            }

            if (frame instanceof TextWebSocketFrame) {
                frameBuffer = new StringBuilder();
                frameBuffer.append(((TextWebSocketFrame)frame).text());
            } else if (frame instanceof ContinuationWebSocketFrame) {
                if (frameBuffer != null) {
                    frameBuffer.append(((ContinuationWebSocketFrame)frame).text());
                } else {
                    logger.warn("Continuation frame received without initial frame.");
                }
            } else {
                throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
            }

            // Check if Text or Continuation Frame is final fragment and handle if needed.
            if (frame.isFinalFragment()) {
                handleMessageCompleted(ctx, frameBuffer.toString());
                frameBuffer = null;
            }
        }

        private void handleMessageCompleted(ChannelHandlerContext ctx, String frameText) {
            String response = wsMessageHandler.handleMessage(ctx, frameText);
            if (response != null) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame(response));
            }
        }

        public void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(status + "\r\n", CharsetUtil.UTF_8)
            );
            response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");

            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        public void sendFile(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
            Matcher matcher = REQUEST_PATTERN.matcher(req.uri());

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
                    sendError(ctx, HttpResponseStatus.OK);
                } else if (matcher.group("static") != null) {
                    ByteArrayOutputStream template = new ByteArrayOutputStream();
                    IOUtils.copy(
                        this.getClass().getResourceAsStream("/status.html"),
                        template
                    );

                    FullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                        Unpooled.copiedBuffer(template.toString(), CharsetUtil.UTF_8)
                    );
                    response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");

                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                }
            } else {
                sendError(ctx, HttpResponseStatus.FORBIDDEN);
            }
        }

        private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
            if (!req.decoderResult().isSuccess()) {
                sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }

            if (req.method() != HttpMethod.GET) {
                sendError(ctx, HttpResponseStatus.FORBIDDEN);
                return;
            }

            String upgradeHeader = req.headers().get("Upgrade");
            if (upgradeHeader != null && "websocket".equalsIgnoreCase(upgradeHeader)) {
                String url = "ws://" + req.headers().get("Host") + "/ws";
                WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(url, null, false);
                handShaker = wsFactory.newHandshaker(req);
                if (handShaker == null) {
                    WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
                } else {
                    handShaker.handshake(ctx.channel(), req);
                }
            } else {
                sendFile(ctx, req);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onInit() {
        flags = YggdrasilCore.INSTANCE
            .getManager(YggdrasilPluginManager.class).get(CTFPlugin.class)
            .getDB().getCollection("flags");

        flagQueue = (Queue<String>) YggdrasilCore.INSTANCE.getQueue("ctfFlags");

        String host = YggdrasilCore.INSTANCE.getConfig().getString("ctf.acceptor.host", "0.0.0.0");
        int hiPort = YggdrasilCore.INSTANCE.getConfig().getInt("ctf.acceptor.tcp.hi_port", 6666);
        int noPort = YggdrasilCore.INSTANCE.getConfig().getInt("ctf.acceptor.tcp.no_port", 5555);
        int loPort = YggdrasilCore.INSTANCE.getConfig().getInt("ctf.acceptor.tcp.lo_port", 4444);

        int httpPort = YggdrasilCore.INSTANCE.getConfig().getInt("ctf.acceptor.http.port", 9999);

        hiServer = new AbstractServer(host, hiPort);
        noServer = new AbstractServer(host, noPort);
        loServer = new AbstractServer(host, loPort);

        httpServer = new AbstractServer(host, httpPort);

        ChannelInitializer<SocketChannel> tcpChannelInitializer = new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(
                    new LineBasedFrameDecoder(32768),
                    new StringDecoder(),
                    new FlagHandler(2)
                );
            }
        };

        ChannelInitializer<SocketChannel> httpChannelInitializer = new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast("encoder", new HttpResponseEncoder());
                p.addLast("decoder", new HttpRequestDecoder());
                p.addLast("aggregator", new HttpObjectAggregator(65536));
                p.addLast("handler", new HttpHandler());
            }
        };

        hiServer.setChannelHandler(tcpChannelInitializer);
        noServer.setChannelHandler(tcpChannelInitializer);
        loServer.setChannelHandler(tcpChannelInitializer);

        httpServer.setChannelHandler(httpChannelInitializer);

        threadPool.submit(hiServer);
        threadPool.submit(noServer);
        threadPool.submit(loServer);

        threadPool.submit(httpServer);
    }

    @Override
    public void onShutdown() {
        hiServer.stop();
        noServer.stop();
        loServer.stop();

        httpServer.stop();

        threadPool.shutdown();
    }

    @Override
    public void run() {}
}

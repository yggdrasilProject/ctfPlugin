package ru.linachan.ctf.handlers;

import com.mongodb.client.MongoCollection;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.linachan.ctf.CTFPlugin;
import ru.linachan.ctf.common.Flag;
import ru.linachan.ctf.common.WebSocketMessageHandler;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.common.Queue;
import ru.linachan.yggdrasil.plugin.YggdrasilPluginManager;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ChannelHandler.Sharable
public class HttpHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker handShaker;
    private StringBuilder frameBuffer = null;

    private String templateFolder;

    private Queue<String> flagQueue;
    private MongoCollection<Document> flags;

    private WebSocketMessageHandler wsMessageHandler = new FlagWSHandler();
    private static Logger logger = LoggerFactory.getLogger(HttpHandler.class);

    private static final Pattern REQUEST_PATTERN = Pattern.compile(
        "^(/flag/((?<type>hi|no|lo)/(?<flag>[a-fA-F0-9]{32}=)|(?<status>status))|/static/(?<static>.*?))$"
    );

    @SuppressWarnings("unchecked")
    public HttpHandler() {
        flags = YggdrasilCore.INSTANCE
            .getManager(YggdrasilPluginManager.class).get(CTFPlugin.class)
            .getDB().getCollection("flags");

        flagQueue = (Queue<String>) YggdrasilCore.INSTANCE.getQueue("ctfFlags");

        templateFolder = YggdrasilCore.INSTANCE.getConfig().getString("ctf.templates", "./templates");
    }

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
            } else if (matcher.group("status") != null) {
                VelocityEngine templateRenderer = new VelocityEngine();
                Properties props = new Properties();
                props.setProperty("file.resource.loader.path", templateFolder);
                templateRenderer.init(props);

                Template template = templateRenderer.getTemplate("dashboard.vm");

                VelocityContext context = new VelocityContext();

                context.internalPut("PAGE_TITLE", "v0rt3x: Dashboard");
                context.internalPut("BASE_URL", "http://flag.nemesis/");

                context.internalPut("PROCESSING", flagQueue.list().size());
                context.internalPut("ACCEPTED", flags.count(new Document("state", 1)));
                context.internalPut("REJECTED", flags.count(new Document("state", 2)));

                context.internalPut("QUEUED_HI", flags.count(new Document("state", 0).append("priority", 2)));
                context.internalPut("QUEUED_NO", flags.count(new Document("state", 0).append("priority", 1)));
                context.internalPut("QUEUED_LO", flags.count(new Document("state", 0).append("priority", 0)));

                context.internalPut("TIME_STAMP", new Date(System.currentTimeMillis()).toString());

                List<Flag> flagList = new ArrayList<>();

                for (Document flag: flags.find().sort(new Document("updateTime", -1)).limit(10)) {
                    flagList.add(new Flag(flag));
                }

                context.internalPut("FLAGS", flagList);

                StringWriter renderResult = new StringWriter();
                template.merge(context, renderResult);

                FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.copiedBuffer(renderResult.toString(), CharsetUtil.UTF_8)
                );

                response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");
                ctx.writeAndFlush(response);
            }
        } else {
            sendError(ctx, HttpResponseStatus.FORBIDDEN);
        }
        ctx.close();
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

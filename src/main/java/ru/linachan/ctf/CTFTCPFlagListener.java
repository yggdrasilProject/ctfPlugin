package ru.linachan.ctf;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import ru.linachan.ctf.common.AbstractServer;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.common.Queue;
import ru.linachan.yggdrasil.service.YggdrasilService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CTFTCPFlagListener implements YggdrasilService {

    private Queue<String> flagQueue;

    private AbstractServer hiServer;
    private AbstractServer noServer;
    private AbstractServer loServer;

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

    @Override
    @SuppressWarnings("unchecked")
    public void onInit() {
        flagQueue = (Queue<String>) YggdrasilCore.INSTANCE.getQueue("ctfFlags");

        String host = YggdrasilCore.INSTANCE.getConfig().getString("ctf.acceptor.host", "0.0.0.0");
        int hiPort = YggdrasilCore.INSTANCE.getConfig().getInt("ctf.acceptor.tcp.hi_port", 6666);
        int noPort = YggdrasilCore.INSTANCE.getConfig().getInt("ctf.acceptor.tcp.no_port", 5555);
        int loPort = YggdrasilCore.INSTANCE.getConfig().getInt("ctf.acceptor.tcp.lo_port", 4444);

        hiServer = new AbstractServer(host, hiPort);
        noServer = new AbstractServer(host, noPort);
        loServer = new AbstractServer(host, loPort);

        hiServer.setChannelHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(
                    new LineBasedFrameDecoder(32768),
                    new StringDecoder(),
                    new FlagHandler(2)
                );
            }
        });

        noServer.setChannelHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(
                    new LineBasedFrameDecoder(32768),
                    new StringDecoder(),
                    new FlagHandler(1)
                );
            }
        });

        loServer.setChannelHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(
                    new LineBasedFrameDecoder(32768),
                    new StringDecoder(),
                    new FlagHandler(0)
                );
            }
        });

        threadPool.submit(hiServer);
        threadPool.submit(noServer);
        threadPool.submit(loServer);
    }

    @Override
    public void onShutdown() {
        hiServer.stop();
        noServer.stop();
        loServer.stop();

        threadPool.shutdown();
    }

    @Override
    public void run() {}
}

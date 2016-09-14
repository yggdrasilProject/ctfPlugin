package ru.linachan.ctf;

import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.string.StringDecoder;
import ru.linachan.ctf.common.AbstractServer;
import ru.linachan.ctf.handlers.FlagHandler;
import ru.linachan.ctf.handlers.HttpHandler;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.service.YggdrasilService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CTFFlagListener implements YggdrasilService {

    private AbstractServer hiServer;
    private AbstractServer noServer;
    private AbstractServer loServer;

    private AbstractServer httpServer;

    private ExecutorService threadPool = Executors.newWorkStealingPool();

    @Override
    @SuppressWarnings("unchecked")
    public void onInit() {
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

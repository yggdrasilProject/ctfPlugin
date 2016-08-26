package ru.linachan.ctf.common;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractServer implements Runnable {

    private String serverHost;
    private int serverPort;

    private ChannelHandler channelHandler;

    private EventLoopGroup master = new NioEventLoopGroup();
    private EventLoopGroup worker = new NioEventLoopGroup();

    private static Logger logger = LoggerFactory.getLogger(AbstractServer.class);

    public AbstractServer(String bindHost, int bindPort) {
        serverHost = bindHost;
        serverPort = bindPort;
    }

    @Override
    public void run() {
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(master, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(channelHandler)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(serverHost, serverPort).sync();
            logger.info("AbstractServer started on {}:{}", serverHost, serverPort);

            f.channel().closeFuture().sync();
        } catch (InterruptedException ignored) {}
    }

    public void setChannelHandler(ChannelHandler channelHandler) {
        this.channelHandler = channelHandler;
    }

    public void stop() {
        logger.info("AbstractServer on {}:{} is going to shutdown", serverHost, serverPort);
        worker.shutdownGracefully();
        master.shutdownGracefully();
    }
}
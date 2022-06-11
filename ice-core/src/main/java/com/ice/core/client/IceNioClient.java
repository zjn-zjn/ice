package com.ice.core.client;

import com.ice.core.utils.IceAddressUtils;
import com.ice.core.utils.IceExecutor;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Slf4j
public class IceNioClient {

    private final int app;
    private String server;
    private String host;
    private int port;
    private Bootstrap bootstrap;
    private int maxFrameLength = 16 * 1024 * 1024;
    private EventLoopGroup worker;
    private int parallelism = -1;
    private final String address;

    public IceNioClient(int app, String server, int parallelism, int maxFrameLength) {
        this.app = app;
        this.setServer(server);
        this.parallelism = parallelism;
        this.maxFrameLength = maxFrameLength;
        this.address = IceAddressUtils.getAddress(app);
        init();
    }

    public IceNioClient(int app, String server) {
        this.app = app;
        this.setServer(server);
        this.address = IceAddressUtils.getAddress(app);
        init();
    }

    private void setServer(String server) {
        String[] serverHostPort = server.split(":");
        try {
            this.host = serverHostPort[0];
            this.port = Integer.parseInt(serverHostPort[1]);
        } catch (Exception e) {
            throw new RuntimeException("ice server config error conf:" + server);
        }
        this.server = server;
    }

    private void init() {
        bootstrap = new Bootstrap();
        worker = new NioEventLoopGroup();
        bootstrap.group(worker)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new IdleStateHandler(5, 0, 0, TimeUnit.SECONDS));
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(maxFrameLength, 0, 4, 0, 4));
                        ch.pipeline().addLast(new IceNioClientHandler(app, IceNioClient.this));
                    }
                });
        if (parallelism <= 0) {
            IceExecutor.setExecutor(new ForkJoinPool());
        } else {
            IceExecutor.setExecutor(new ForkJoinPool(parallelism));
        }
    }

    public void destroy() {
        if (worker != null) {
            worker.shutdownGracefully();
        }
    }

    public void connect() {
        try {
            bootstrap.connect(host, port).sync();
        } catch (Exception e) {
            if (!IceNioClientHandler.init) {
                //ice client not init just shutdown it
                if (worker != null) {
                    worker.shutdownGracefully();
                }
                throw new RuntimeException("ice connect server error server:" + server, e);
            }
        }
    }

    public void reconnect() throws InterruptedException {
        ChannelFuture cf = bootstrap.connect(host, port);
        cf.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                //the reconnection handed over by backend thread
                future.channel().eventLoop().schedule(() -> {
                    try {
                        reconnect();
                    } catch (Exception e) {
                        log.warn("ice nio client connected error", e);
                    }
                }, 2000, TimeUnit.MILLISECONDS);
            } else {
                log.info("ice nio client reconnected");
            }
        });
        cf.channel().closeFuture().sync();
    }

    public String getAddress() {
        return address;
    }
}

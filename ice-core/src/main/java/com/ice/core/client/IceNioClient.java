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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * @author waitmoon
 */
@Slf4j
public class IceNioClient {

    private final int app;
    private String server;
    private String host;
    private int port;
    private Bootstrap bootstrap;
    private final int maxFrameLength;
    private static final int DEFAULT_MAX_FRAME_LENGTH = 16 * 1024 * 1024;
    private EventLoopGroup worker;
    private final int parallelism;
    private final String address;
    //combine main package and config scan packages
    private Set<String> scanPackages;
    private static volatile Throwable initCause;

    public IceNioClient(int app, String server, int parallelism, int maxFrameLength, Set<String> scan) {
        this.app = app;
        this.setServer(server);
        this.parallelism = parallelism;
        this.maxFrameLength = maxFrameLength;
        this.address = IceAddressUtils.getAddress(app);
        this.setScan(scan);
        init();
    }

    public IceNioClient(int app, String server, Set<String> scan) {
        this(app, server, -1, DEFAULT_MAX_FRAME_LENGTH, scan);
    }

    public IceNioClient(int app, String server) {
        this(app, server, -1, DEFAULT_MAX_FRAME_LENGTH, null);
    }

    private void setScan(Set<String> scan) {
        this.scanPackages = new HashSet<>();
        String mainClassPackage = getMainPackageName();
        if (mainClassPackage != null && !mainClassPackage.isEmpty()) {
            this.scanPackages.add(mainClassPackage);
        }
        if (scan != null && !scan.isEmpty()) {
            this.scanPackages.addAll(scan);
        }
    }

    private static String getMainPackageName() {
        String mainCassName = System.getProperty("sun.java.command");
        if (mainCassName != null && !mainCassName.isEmpty()) {
            try {
                return Class.forName(mainCassName).getPackage().getName();
            } catch (ClassNotFoundException e) {
                //ignore
            }
        }
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement element = stackTrace[stackTrace.length - 1];
        if ("main".equals(element.getMethodName())) {
            return element.getClass().getPackage().getName();
        }
        return null;
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
        initCause = null;
        if (worker != null) {
            worker.shutdownGracefully();
        }
    }

    /**
     * connect to ice nio server
     */
    public void connect() {
        new Thread(() -> {
            try {
                bootstrap.connect(host, port).sync();
            } catch (Throwable t) {
                if (!IceNioClientHandler.init) {
                    //ice client not init just shutdown it
                    if (worker != null) {
                        worker.shutdownGracefully();
                    }
                    initCause = t;
                }
            }
        }).start();
        //waiting for client init
        while (!IceNioClientHandler.init) {
            if (initCause != null) {
                throw new RuntimeException("ice connect server error server:" + server, initCause);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                //
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

    public Set<String> getScanPackages() {
        return scanPackages;
    }
}

package com.ice.server.nio;

import com.ice.server.config.IceServerProperties;
import com.ice.server.nio.ha.IceNioServerHa;
import com.ice.server.service.IceServerService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author waitmoon
 */
@Slf4j
public class IceNioServer {

    private final IceServerProperties properties;

    private EventLoopGroup bossEventLoop;

    private EventLoopGroup workEventLoop;

    private final IceServerService serverService;

    private final IceNioServerHa nioServerZk;

    public IceNioServer(IceServerProperties properties, IceServerService serverService, IceNioServerHa nioServerZk) {
        this.properties = properties;
        this.serverService = serverService;
        this.nioServerZk = nioServerZk;
    }

    public void start() throws Exception {
        if (nioServerZk == null && StringUtils.hasLength(properties.getHa().getAddress())) {
            throw new RuntimeException("lost dependency of curator-recipes to start server with zk");
        }
        bossEventLoop = new NioEventLoopGroup();
        workEventLoop = new NioEventLoopGroup();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossEventLoop, workEventLoop)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        socketChannel.pipeline().addLast(new IdleStateHandler(properties.getReaderIdleTime(), 0, 0, TimeUnit.SECONDS));
                        socketChannel.pipeline().addLast(new LengthFieldBasedFrameDecoder(properties.getMaxFrameLength(), 0, 4, 0, 4));
                        socketChannel.pipeline().addLast(new IceNioServerHandler(serverService));
                    }
                });
        ChannelFuture channelFuture = serverBootstrap.bind(properties.getHost(), properties.getPort()).sync();
        new Thread(() -> {
            try {
                channelFuture.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                log.error("interrupted", e);
            } finally {
                if (bossEventLoop != null) {
                    bossEventLoop.shutdownGracefully();
                }
                if (workEventLoop != null) {
                    workEventLoop.shutdownGracefully();
                }
            }
        }).start();
        if (nioServerZk != null) {
            //register server to zk for HA
            nioServerZk.register();
        }
        log.info("ice nio server start success");
    }

    public void destroy() throws IOException {
        if (bossEventLoop != null) {
            bossEventLoop.shutdownGracefully();
        }
        if (workEventLoop != null) {
            workEventLoop.shutdownGracefully();
        }
        if (nioServerZk != null) {
            nioServerZk.destroy();
        }
    }
}
package com.ice.server.nio;

import com.ice.core.utils.IceAddressUtils;
import com.ice.server.config.IceServerProperties;
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
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
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

    private CuratorFramework curatorFramework;

    private static LeaderLatch leaderLatch;

    private final IceServerService serverService;

    private final int serverPort;

    public static volatile boolean leader = false;

    private final static String LATCH_PATH = "/com.waitmoon.ice.server";

    public IceNioServer(IceServerProperties properties, IceServerService serverService, int serverPort) {
        this.properties = properties;
        this.serverService = serverService;
        this.serverPort = serverPort;
    }

    public void start() throws Exception {
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
        registerZookeeper();
        log.info("ice nio server start success");
    }

    /**
     * register to zk for HA
     */
    private void registerZookeeper() throws Exception {
        if (StringUtils.hasLength(properties.getZk().getAddress())) {
            curatorFramework = CuratorFrameworkFactory.builder()
                    .connectString(properties.getZk().getAddress())
                    .retryPolicy(new ExponentialBackoffRetry(properties.getZk().getBaseSleepTimeMs(), properties.getZk().getMaxRetries(), properties.getZk().getMaxSleepMs()))
                    .connectionTimeoutMs(properties.getZk().getConnectionTimeoutMs()).build();
            String host = properties.getZk().getHost() == null ? IceAddressUtils.getAddress() : properties.getZk().getHost();
            if (!StringUtils.hasLength(host)) {
                throw new RuntimeException("ice server failed register zk, get host null");
            }
            //host:nio-port,host:web-port
            String id = host + ":" + properties.getPort() + "," + host + ":" + serverPort;
            log.info("server:" + id + " will register to zk for HA");
            leaderLatch = new LeaderLatch(curatorFramework, LATCH_PATH, id, LeaderLatch.CloseMode.NOTIFY_LEADER);
            leaderLatch.addListener(new LeaderLatchListener() {
                @Override
                public void isLeader() {
                    leader = true;
                    /*server becomes to leader refresh local cache from database*/
                    serverService.refresh();
                    log.info(id + " is server leader now");
                }

                @Override
                public void notLeader() {
                    leader = false;
                    serverService.clean();
                    log.info(id + " off server leader");
                }
            });
            curatorFramework.start();
            leaderLatch.start();
        } else {
            leader = true;
        }
    }

    public void destroy() throws IOException {
        if (bossEventLoop != null) {
            bossEventLoop.shutdownGracefully();
        }
        if (workEventLoop != null) {
            workEventLoop.shutdownGracefully();
        }
        if (leaderLatch != null) {
            leaderLatch.close();
        }
        if (curatorFramework != null) {
            curatorFramework.close();
        }
    }

    public static String getLeaderWebAddress() throws Exception {
        return leaderLatch == null ? null : leaderLatch.getLeader().getId().split(",")[1];
    }
}
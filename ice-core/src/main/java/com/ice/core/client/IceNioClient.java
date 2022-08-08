package com.ice.core.client;

import com.ice.common.dto.IceTransferDto;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.model.LeafNodeInfo;
import com.ice.core.annotation.IceField;
import com.ice.core.annotation.IceNode;
import com.ice.core.leaf.base.BaseLeafFlow;
import com.ice.core.leaf.base.BaseLeafNone;
import com.ice.core.leaf.base.BaseLeafResult;
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
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * @author waitmoon
 */
@Slf4j
public final class IceNioClient {
    /*base config*/
    private final int app;
    private String server;
    /*with default*/
    private final int maxFrameLength;
    private final int parallelism;
    private final int initRetryTimes;
    private final int initRetrySleepMs;
    /*derive*/
    private String zkAddress;
    private String host;
    private int port;
    private Bootstrap bootstrap;
    private EventLoopGroup worker;
    private final String iceAddress;
    //combine main package and config scan packages
    private List<LeafNodeInfo> leafNodes;

    //start error cause
    private volatile Throwable startCause;
    //destroy sign
    private volatile boolean destroy = false;
    //start data ready from ice server
    private volatile boolean startDataReady = false;
    //ice client started
    private volatile boolean started = false;
    private final Object startDataLock = new Object();
    private final Object startedLock = new Object();
    //start data
    private volatile IceTransferDto startData;
    /*default*/
    private static final int DEFAULT_MAX_FRAME_LENGTH = 16 * 1024 * 1024; //16M
    private static final int DEFAULT_INIT_RETRY_TIMES = 10;
    private static final int DEFAULT_INIT_RETRY_SLEEP_MS = 2000;
    private static final int DEFAULT_PARALLELISM = -1;
    private static final String ZK_PREFIX = "zookeeper:";
    private final static String LATCH_PATH = "/com.waitmoon.ice.server";

    private String serverLeaderNioAddress;
    //zk curator framework
    private CuratorFramework curatorFramework;
    //zk leader latch to find server leader
    private LeaderLatch leaderLatch;

    public IceNioClient(int app, String server, int parallelism, int maxFrameLength, Set<String> scanPackages, int initRetryTimes, int initRetrySleepMs) throws IOException {
        this.initRetryTimes = initRetryTimes < 0 ? DEFAULT_INIT_RETRY_TIMES : initRetryTimes;
        this.initRetrySleepMs = initRetrySleepMs < 0 ? DEFAULT_INIT_RETRY_SLEEP_MS : initRetrySleepMs;
        this.app = app;
        this.setServer(server);
        this.parallelism = parallelism;
        this.maxFrameLength = maxFrameLength;
        this.iceAddress = IceAddressUtils.getAddress(app);
        scanLeafNodes(scanPackages);
        prepare();
    }

    public IceNioClient(int app, String server, Set<String> scanPackages) throws IOException {
        this(app, server, DEFAULT_PARALLELISM, DEFAULT_MAX_FRAME_LENGTH, scanPackages, DEFAULT_INIT_RETRY_TIMES, DEFAULT_INIT_RETRY_SLEEP_MS);
    }

    public IceNioClient(int app, String server, String scan) throws IOException {
        this(app, server, DEFAULT_PARALLELISM, DEFAULT_MAX_FRAME_LENGTH, new HashSet<>(Arrays.asList(scan.split(","))), DEFAULT_INIT_RETRY_TIMES, DEFAULT_INIT_RETRY_SLEEP_MS);
    }

    public IceNioClient(int app, String server) throws IOException {
        this(app, server, DEFAULT_PARALLELISM, DEFAULT_MAX_FRAME_LENGTH, null, DEFAULT_INIT_RETRY_TIMES, DEFAULT_INIT_RETRY_SLEEP_MS);
    }

    private void setServer(String server) {
        this.server = server;
        if (server.startsWith(ZK_PREFIX)) {
            //HA from zk
            zkAddress = server.substring(ZK_PREFIX.length());
        } else {
            //stand-alone
            setServerHostPort(server);
        }
    }

    private void setServerHostPort(String hostPort) {
        String[] serverHostPort = hostPort.split(":");
        try {
            this.host = serverHostPort[0];
            this.port = Integer.parseInt(serverHostPort[1]);
        } catch (Exception e) {
            throw new RuntimeException("ice server config error conf:" + hostPort);
        }
    }

    private void prepare() {
        worker = new NioEventLoopGroup();
        bootstrap = new Bootstrap().group(worker)
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
        startCause = null;
        startDataReady = false;
        started = false;
        destroy = true;
        if (worker != null) {
            worker.shutdownGracefully();
        }
        if (curatorFramework != null) {
            curatorFramework.close();
        }
    }

    public boolean isDestroy() {
        return destroy;
    }

    /**
     * wait for started
     */
    public void waitStarted() {
        if (!started) {
            synchronized (startedLock) {
                if (!started) {
                    try {
                        startedLock.wait();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }
        }
    }

    /**
     * start ice client
     * 1.connect ice server
     * 2.get start data from server
     * 3.init start data
     */
    public void start() throws Exception {
        destroy = false;
        long start = System.currentTimeMillis();
        for (int i = 0; i < initRetryTimes; i++) {
            try {
                if (zkAddress != null) {
                    if (curatorFramework == null) {
                        curatorFramework = CuratorFrameworkFactory.builder()
                                .connectString(zkAddress)
                                .retryPolicy(new ExponentialBackoffRetry(2000, 3))
                                .connectionTimeoutMs(5000).build();
                    }
                    if (leaderLatch == null) {
                        leaderLatch = new LeaderLatch(curatorFramework, LATCH_PATH);
                    }
                    if (curatorFramework.getState() != CuratorFrameworkState.STARTED) {
                        curatorFramework.start();
                    }
                    String serverLeader = leaderLatch.getLeader().getId();
                    if (serverLeader == null || serverLeader.isEmpty()) {
                        throw new RuntimeException("can not get ice server leader from zk:" + zkAddress);
                    }
                    this.serverLeaderNioAddress = serverLeader.split(",")[0];
                    this.setServerHostPort(serverLeaderNioAddress);
                }
                new Thread(() -> {
                    try {
                        bootstrap.connect(host, port).sync();
                    } catch (Throwable t) {
                        if (!this.startDataReady) {
                            //ice client not started, just shutdown it
                            if (worker != null) {
                                worker.shutdownGracefully();
                            }
                            startCause = t;
                            synchronized (startDataLock) {
                                startDataLock.notifyAll();
                            }
                        }
                    }
                }).start();
                //waiting for client start data ready
                synchronized (startDataLock) {
                    if (!startDataReady && startCause == null) {
                        startDataLock.wait();
                    }
                }
                if (startDataReady) {
                    //ready to init
                    break;
                }
                if (i < initRetryTimes - 1) {
                    Thread.sleep(initRetrySleepMs);
                }
            } catch (Exception e) {
                log.error("client init error for retry:{}, sleep:{}", i, initRetrySleepMs, e);
                startCause = e;
                Thread.sleep(initRetrySleepMs);
            }
        }
        if (!this.startDataReady) {
            this.destroy();
            if (serverLeaderNioAddress == null || serverLeaderNioAddress.isEmpty()) {
                throw new RuntimeException("ice connect server error server:" + server, startCause);
            } else {
                throw new RuntimeException("ice connect server error server:" + serverLeaderNioAddress, startCause);
            }
        }
        //start data ready, starting cache
        IceUpdate.update(startData);
        startData = null;
        synchronized (startedLock) {
            //started
            started = true;
            startedLock.notifyAll();
        }
        if (serverLeaderNioAddress == null || serverLeaderNioAddress.isEmpty()) {
            log.info("ice client init app:{} address:{} success:{}ms", app, iceAddress, System.currentTimeMillis() - start);
        } else {
            log.info("ice client init app:{} address:{} success:{}ms leader:{}", app, iceAddress, System.currentTimeMillis() - start, serverLeaderNioAddress);
        }
    }

    /**
     * init data ready from ice server
     *
     * @param initData init data from server
     */
    public void initDataReady(IceTransferDto initData) {
        if (started) {
            //already start, just update
            IceUpdate.update(initData);
        } else {
            synchronized (startedLock) {
                if (started) {
                    IceUpdate.update(initData);
                    return;
                }
                if (startDataReady) {
                    try {
                        //start data ready, wait for started
                        startedLock.wait();
                    } catch (InterruptedException e) {
                        //ignore
                    }
                    //already start, update
                    IceUpdate.update(initData);
                } else {
                    synchronized (startDataLock) {
                        //first start data ready
                        this.startData = initData;
                        startDataReady = true;
                        startDataLock.notifyAll();
                    }
                }
            }
        }
    }

    /**
     * reconnect to ice server
     * 2 second each
     *
     * @throws InterruptedException e
     */
    public void reconnect() throws InterruptedException {
        if (!destroy) {
            if (zkAddress != null) {
                while (true) {
                    //get server leader
                    if (curatorFramework == null) {
                        curatorFramework = CuratorFrameworkFactory.builder()
                                .connectString(zkAddress)
                                .retryPolicy(new ExponentialBackoffRetry(2000, 3))
                                .connectionTimeoutMs(5000).build();
                    }
                    if (leaderLatch == null) {
                        leaderLatch = new LeaderLatch(curatorFramework, LATCH_PATH);
                    }
                    if (curatorFramework.getState() != CuratorFrameworkState.STARTED) {
                        curatorFramework.start();
                    }
                    String serverLeader;
                    try {
                        serverLeader = leaderLatch.getLeader().getId();
                    } catch (Exception e) {
                        //ignore
                        continue;
                    }
                    if (serverLeader == null || serverLeader.isEmpty()) {
                        continue;
                    }
                    serverLeaderNioAddress = serverLeader.split(",")[0];
                    this.setServerHostPort(serverLeaderNioAddress);
                    break;
                }
            }
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
                    }, 2, TimeUnit.SECONDS);
                } else {
                    if (serverLeaderNioAddress == null || serverLeaderNioAddress.isEmpty()) {
                        log.info("ice nio client reconnected");
                    } else {
                        log.info("ice nio client reconnected leader:{}", serverLeaderNioAddress);
                    }
                }
            });
            cf.channel().closeFuture().sync();
        }
    }

    //scan leaf node from packages
    private void scanLeafNodes(Set<String> scanPackages) throws IOException {
        long start = System.currentTimeMillis();
        Set<Class<?>> leafClasses;
        if (scanPackages == null || scanPackages.isEmpty()) {
            //default scan all
            leafClasses = IceLeafScanner.scanPackage(null);
        } else {
            leafClasses = new HashSet<>();
            for (String packageName : scanPackages) {
                leafClasses.addAll(IceLeafScanner.scanPackage(packageName));
            }
        }
        log.info("ice scan leaf node, packages:{} {}ms cnt:{}", scanPackages, System.currentTimeMillis() - start, leafClasses.size());
        if (leafClasses.isEmpty()) {
            return;
        }
        leafNodes = new ArrayList<>(leafClasses.size());
        for (Class<?> leafClass : leafClasses) {
            LeafNodeInfo leafNodeInfo = new LeafNodeInfo();
            leafNodeInfo.setClazz(leafClass.getName());
            IceNode nodeAnnotation = leafClass.getAnnotation(IceNode.class);
            if (nodeAnnotation != null) {
                leafNodeInfo.setName(nodeAnnotation.name());
                leafNodeInfo.setDesc(nodeAnnotation.desc());
            }
            Field[] leafFields = leafClass.getDeclaredFields();
            List<LeafNodeInfo.FieldInfo> annotationFields = new ArrayList<>(leafFields.length);
            for (Field field : leafFields) {
                IceField fieldAnnotation = field.getAnnotation(IceField.class);
                if (fieldAnnotation != null) {
                    LeafNodeInfo.FieldInfo fieldInfo = new LeafNodeInfo.FieldInfo();
                    fieldInfo.setField(field.getName());
                    fieldInfo.setName(fieldAnnotation.name());
                    fieldInfo.setDesc(fieldAnnotation.desc());
                    annotationFields.add(fieldInfo);
                }
            }
            if (!annotationFields.isEmpty()) {
                leafNodeInfo.setFields(annotationFields);
            }
            if (BaseLeafFlow.class.isAssignableFrom(leafClass)) {
                leafNodeInfo.setType(NodeTypeEnum.LEAF_FLOW.getType());
                leafNodes.add(leafNodeInfo);
                continue;
            }
            if (BaseLeafResult.class.isAssignableFrom(leafClass)) {
                leafNodeInfo.setType(NodeTypeEnum.LEAF_RESULT.getType());
                leafNodes.add(leafNodeInfo);
                continue;
            }
            if (BaseLeafNone.class.isAssignableFrom(leafClass)) {
                leafNodeInfo.setType(NodeTypeEnum.LEAF_NONE.getType());
                leafNodes.add(leafNodeInfo);
            }
        }
    }

    public String getIceAddress() {
        return iceAddress;
    }

    public List<LeafNodeInfo> getLeafNodes() {
        return leafNodes;
    }
}
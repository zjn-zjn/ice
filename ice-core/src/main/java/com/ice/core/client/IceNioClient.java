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

    private final int app;
    private String server;
    private String host;
    private int port;
    private final int maxFrameLength;
    private final int parallelism;

    private Bootstrap bootstrap;
    private static final int DEFAULT_MAX_FRAME_LENGTH = 16 * 1024 * 1024;
    private EventLoopGroup worker;
    private final String address;
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
    private volatile IceTransferDto startData;

    public IceNioClient(int app, String server, int parallelism, int maxFrameLength, Set<String> scanPackages) throws IOException {
        this.app = app;
        this.setServer(server);
        this.parallelism = parallelism;
        this.maxFrameLength = maxFrameLength;
        this.address = IceAddressUtils.getAddress(app);
        scanLeafNodes(scanPackages);
        prepare();
    }

    public IceNioClient(int app, String server, Set<String> scanPackages) throws IOException {
        this(app, server, -1, DEFAULT_MAX_FRAME_LENGTH, scanPackages);
    }

    public IceNioClient(int app, String server, String scan) throws IOException {
        this(app, server, -1, DEFAULT_MAX_FRAME_LENGTH, new HashSet<>(Arrays.asList(scan.split(","))));
    }

    public IceNioClient(int app, String server) throws IOException {
        this(app, server, -1, DEFAULT_MAX_FRAME_LENGTH, null);
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

    private void prepare() {
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
        startCause = null;
        startDataReady = false;
        started = false;
        destroy = true;
        if (worker != null) {
            worker.shutdownGracefully();
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
     * start connect to ice nio server
     */
    public void start() throws InterruptedException {
        destroy = false;
        long start = System.currentTimeMillis();
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
        if (!this.startDataReady && startCause != null) {
            throw new RuntimeException("ice connect server error server:" + server, startCause);
        }
        //start data ready, starting cache
        IceUpdate.update(startData);
        startData = null;
        synchronized (startedLock) {
            //started
            started = true;
            startedLock.notifyAll();
        }
        log.info("ice client init app:{} address:{} success:{}ms", app, address, System.currentTimeMillis() - start);
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
                    //first start data ready
                    this.startData = initData;
                    startDataReady = true;
                    startDataLock.notifyAll();
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
                    log.info("ice nio client reconnected");
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

    public String getAddress() {
        return address;
    }

    public List<LeafNodeInfo> getLeafNodes() {
        return leafNodes;
    }
}
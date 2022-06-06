package com.ice.server.nio;

import com.ice.core.nio.IceNioModel;
import com.ice.core.nio.NioType;
import com.ice.core.utils.IceNioUtils;
import com.ice.server.config.IceServerProperties;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class IceNioServer implements InitializingBean, DisposableBean {

    public static Map<String, Object> lockMap = new ConcurrentHashMap<>();

    public static Map<String, IceNioModel> resultMap = new ConcurrentHashMap<>();

    @Resource
    private IceServerService serverService;

    @Resource
    private IceServerProperties properties;

    private ServerSocketChannel ssc;

    @Resource
    private IceNioClientManager iceNioClientManager;

    private Thread nioThread;

    private Selector selector;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("create ice nio server service...");
        ssc = ServerSocketChannel.open();
        ssc.socket().bind(new InetSocketAddress(properties.getPort()));
        ssc.configureBlocking(false);

        selector = Selector.open();
        ssc.register(selector, SelectionKey.OP_ACCEPT);
        nioThread = new Thread(() -> {
            long lastClientScCleanTime = System.currentTimeMillis();
            while (true) {
                try {
                    if (Thread.interrupted()) {
                        return;
                    }
                    int readyChannels = selector.select(5000);
                    if (Thread.interrupted()) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastClientScCleanTime > 5000) {
                        //5 seconds to clean client socket channel
                        iceNioClientManager.cleanClientSc(now - 5000);
                        lastClientScCleanTime = now;
                    }
                    if (readyChannels == 0) {
                        continue;
                    }
                    Iterator<SelectionKey> selectionKeyIterator = selector.selectedKeys().iterator();
                    while (selectionKeyIterator.hasNext()) {
                        SelectionKey key = selectionKeyIterator.next();
                        selectionKeyIterator.remove();
                        if (key.isValid() && key.isAcceptable()) {
                            SocketChannel sc = ssc.accept();
                            sc.configureBlocking(false);
                            sc.register(selector, SelectionKey.OP_READ);
                        } else if (key.isValid() && key.isReadable()) {
                            SocketChannel sc = (SocketChannel) key.channel();
                            IceNioModel nioModel = IceNioUtils.getNioModel(sc);
                            if (nioModel != null) {
                                if (nioModel.getType() == NioType.REQ) {
                                    switch (nioModel.getOps()) {
                                        case SLAP:
                                            iceNioClientManager.register(nioModel.getApp(), sc, nioModel.getAddress());
                                            break;
                                        case DESTROY:
                                            iceNioClientManager.unregister(nioModel.getApp(), sc);
                                            sc.close();
                                            break;
                                        case INIT:
                                            iceNioClientManager.register(nioModel.getApp(), sc, nioModel.getAddress());
                                            IceNioModel response = new IceNioModel();
                                            response.setType(NioType.RSP);
                                            response.setInitDto(serverService.getInitConfig(nioModel.getApp()));
                                            IceNioUtils.writeNioModel(sc, response);
                                            break;
                                        default:
                                            break;
                                    }
                                } else if (nioModel.getType() == NioType.RSP) {
                                    String id = nioModel.getId();
                                    if (id != null) {
                                        Object lock = lockMap.get(id);
                                        if (lock != null) {
                                            synchronized (lock) {
                                                if (lockMap.containsKey(id)) {
                                                    resultMap.put(id, nioModel);
                                                    lock.notifyAll();
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("server socket channel run warn", e);
                }
            }
        });
        nioThread.start();
        //waiting for client heartbeat after restart
        Thread.sleep(3000);
        log.info("create ice nio server service...success");
    }

    @Override
    public void destroy() {
        if (nioThread != null) {
            nioThread.interrupt();
        }
        if (selector != null) {
            try {
                selector.close();
            } catch (IOException e) {
                //ignore
            }
        }
        if (ssc != null) {
            try {
                ssc.close();
            } catch (Exception e) {
                //ignore
            }
        }
    }
}

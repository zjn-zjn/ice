package com.ice.core.nio;

import com.alibaba.fastjson.JSON;
import com.ice.common.exception.IceException;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.Pair;
import com.ice.common.utils.IceAddressUtils;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceExecutor;
import com.ice.core.utils.IceNioUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * @author zjn
 */
@Slf4j
public final class IceNioClient {

    private static volatile IceNioClient client;

    private final int app;

    private Thread thread;

    private Selector selector;

    private String address;

    private String serverHost;

    private int serverPort;

    private SocketChannel sc;

    private IceNioClient(int app, String server) {
        this.app = app;
        this.setServer(server);
    }

    private void setServer(String server) {
        String[] serverHostPort = server.split(":");
        try {
            this.serverHost = serverHostPort[0];
            this.serverPort = Integer.parseInt(serverHostPort[1]);
        } catch (Exception e) {
            throw new RuntimeException("ice server config error conf:" + server);
        }
    }

    public static IceNioClient open(int app, String server) throws IOException {
        return open(app, server, -1);
    }

    public static IceNioClient open(int app, String server, int parallelism) throws IOException {
        if (client != null) {
            return client;
        }
        synchronized (IceNioClient.class) {
            if (client != null) {
                return client;
            }
            client = getClient(app, server, parallelism);
            return client;
        }
    }

    private static IceNioClient getClient(int app, String server, int parallelism) throws IOException {
        if (app <= 0) {
            throw new IceException("invalid app:" + app);
        }
        IceNioClient client = new IceNioClient(app, server);
        if (parallelism <= 0) {
            IceExecutor.setExecutor(new ForkJoinPool());
        } else {
            IceExecutor.setExecutor(new ForkJoinPool(parallelism));
        }
        log.info("ice init start");
        try {
            client.sc = SocketChannel.open(new InetSocketAddress(client.serverHost, client.serverPort));
        } catch (IOException e) {
            throw new IceException("ice connect server error, server:" + server);
        }
        String initJson;
        try {
            //init ice client address
            client.address = IceAddressUtils.getAddress(client.sc);
            IceNioModel initRequest = new IceNioModel();
            initRequest.setOps(NioOps.INIT);
            initRequest.setType(NioType.REQ);
            initRequest.setApp(app);
            initRequest.setAddress(client.address);
            IceNioUtils.writeNioModel(client.sc, initRequest);
            initJson = IceNioUtils.getNioModelJson(client.sc);
        } catch (IOException e) {
            throw new IceException("ice init error, maybe server is down app:" + app + " " + server);
        }
        if (initJson == null) {
            throw new IceException("ice init error, maybe server is down app:" + app + " " + server);
        }
        IceNioModel initResponse = JSON.parseObject(initJson, IceNioModel.class);
        log.info("ice client init content:{}", initJson);
        IceUpdate.update(initResponse.getInitDto());
        log.info("ice client init iceEnd success");
        client.selector = Selector.open();
        client.sc.configureBlocking(false);
        client.sc.register(client.selector, SelectionKey.OP_READ);
        client.thread = new Thread(new NioClientHandle(client));
        client.thread.start();
        return client;
    }

    public void destroy() {
        if (thread != null) {
            thread.interrupt();
        }
        if (sc != null) {
            IceNioModel destroy = new IceNioModel();
            destroy.setType(NioType.REQ);
            destroy.setOps(NioOps.DESTROY);
            destroy.setApp(app);
            destroy.setAddress(address);
            try {
                IceNioUtils.writeNioModel(sc, destroy);
                sc.close();
            } catch (IOException e) {
                //ignore
            }
        }
        if (selector != null) {
            try {
                selector.close();
            } catch (IOException e) {
                //ignore
            }
        }
        client = null;
    }

    public final static class NioClientHandle implements Runnable {

        private final IceNioClient client;

        public NioClientHandle(IceNioClient client) {
            this.client = client;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    int readyChannels = client.selector.select(2000);
                    if (Thread.interrupted()) {
                        return;
                    }
                    if (readyChannels == 0) {
                        IceNioModel heartBeat = new IceNioModel();
                        heartBeat.setType(NioType.REQ);
                        heartBeat.setOps(NioOps.SLAP);
                        heartBeat.setApp(client.app);
                        heartBeat.setAddress(client.address);
                        IceNioUtils.writeNioModel(client.sc, heartBeat);
                        continue;
                    }
                    Iterator<SelectionKey> selectionKeyIterator = client.selector.selectedKeys().iterator();
                    while (selectionKeyIterator.hasNext()) {
                        SelectionKey key = selectionKeyIterator.next();
                        selectionKeyIterator.remove();
                        if (key.isValid() && key.isReadable()) {
                            SocketChannel sc = (SocketChannel) key.channel();
                            IceNioModel request = IceNioUtils.getNioModel(sc);
                            if (request != null) {
                                IceNioModel response = new IceNioModel();
                                response.setType(NioType.RSP);
                                response.setId(request.getId());
                                switch (request.getOps()) {
                                    case CLAZZ_CHECK:
                                        Pair<Integer, String> checkResult = IceNioClientService.confClazzCheck(request.getClazz(), request.getNodeType());
                                        response.setClazzCheck(checkResult);
                                        break;
                                    case UPDATE:
                                        List<String> errors = IceNioClientService.update(request.getUpdateDto());
                                        response.setUpdateErrors(errors);
                                        break;
                                    case SHOW_CONF:
                                        IceShowConf conf = IceNioClientService.getShowConf(request.getConfId());
                                        response.setShowConf(conf);
                                        break;
                                    case MOCK:
                                        List<IceContext> mockResults = IceNioClientService.mock(request.getPack());
                                        response.setMockResults(mockResults);
                                        break;
                                    case PING:
                                        response.setApp(client.app);
                                        response.setAddress(client.address);
                                        break;
                                    default:
                                        break;
                                }
                                IceNioUtils.writeNioModel(sc, response);
                            }
                        }
                    }
                } catch (Exception e) {
                    while (true) {
                        if (Thread.interrupted()) {
                            return;
                        }
                        try {
                            client.sc = SocketChannel.open(new InetSocketAddress(client.serverHost, client.serverPort));
                            client.sc.configureBlocking(false);
                            client.sc.register(client.selector, SelectionKey.OP_READ);
                            log.info("ice reconnected!");
                            break;
                        } catch (Exception ioe) {
                            log.warn("ice reconnecting...");
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (Exception se) {
                            //ignore
                        }
                    }
                }

            }
        }
    }
}

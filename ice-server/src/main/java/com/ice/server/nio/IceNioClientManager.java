package com.ice.server.nio;

import com.ice.common.dto.IceTransferDto;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.Pair;
import com.ice.common.utils.UUIDUtils;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.nio.IceNioModel;
import com.ice.core.nio.NioOps;
import com.ice.core.nio.NioType;
import com.ice.core.utils.IceNioUtils;
import com.ice.server.config.IceServerProperties;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author zjn
 * manager ice nio clients
 * 1.get real config from client
 * 2.mock to client
 * 3.release the update to client
 * 4.check class from client
 */
@Slf4j
@Service
public final class IceNioClientManager implements InitializingBean {

    private static final Map<Integer, Map<String, SocketChannel>> appAddressScMap = new ConcurrentHashMap<>();
    private static final Map<SocketChannel, Pair<Long, String>> scInfoMap = new ConcurrentHashMap<>();
    private static final Map<Integer, TreeMap<Long, Set<SocketChannel>>> appScTimeTreeMap = new TreeMap<>();

    @Resource
    private IceServerProperties properties;

    private static ExecutorService executor;

    public synchronized void unregister(int app, SocketChannel sc) {
        Pair<Long, String> timeAddress = scInfoMap.get(sc);
        if (timeAddress != null) {
            String address = timeAddress.getValue();
            Long originTime = timeAddress.getKey();
            scInfoMap.remove(sc);
            Map<String, SocketChannel> channelMap = appAddressScMap.get(app);
            if (!CollectionUtils.isEmpty(channelMap)) {
                channelMap.remove(address);
            }
            TreeMap<Long, Set<SocketChannel>> treeMap = appScTimeTreeMap.get(app);
            if (!CollectionUtils.isEmpty(treeMap)) {
                Set<SocketChannel> channels = treeMap.get(originTime);
                if (!CollectionUtils.isEmpty(channels)) {
                    channels.remove(sc);
                }
                if (CollectionUtils.isEmpty(channels)) {
                    treeMap.remove(originTime);
                }
            }
            log.info("ice nio app:{} client:{} offline", app, address);
        }
    }

    /**
     * clean client socket channel: slap before cleanTime
     * @param cleanTime less time
     */
    public synchronized void cleanClientSc(long cleanTime) {
        for (Map.Entry<Integer, TreeMap<Long, Set<SocketChannel>>> scTimeTreeEntry : appScTimeTreeMap.entrySet()) {
            SortedMap<Long, Set<SocketChannel>> cleanMap = scTimeTreeEntry.getValue().headMap(cleanTime);
            if (!CollectionUtils.isEmpty(cleanMap)) {
                for (Set<SocketChannel> cleanScSet : cleanMap.values()) {
                    if (!CollectionUtils.isEmpty(cleanScSet)) {
                        for (SocketChannel cleanSc : cleanScSet) {
                            unregister(scTimeTreeEntry.getKey(), cleanSc);
                        }
                    }
                }
            }
        }
    }

    public synchronized void register(int app, SocketChannel sc, String address) {
        Long now = System.currentTimeMillis();
        Pair<Long, String> timeAddress = scInfoMap.get(sc);
        if (timeAddress != null) {
            Long originTime = timeAddress.getKey();
            TreeMap<Long, Set<SocketChannel>> socketTimeTreeMap = appScTimeTreeMap.get(app);
            if (socketTimeTreeMap != null) {
                Set<SocketChannel> originTimeObject = socketTimeTreeMap.get(originTime);
                if (originTimeObject != null) {
                    originTimeObject.remove(sc);
                }
                if (CollectionUtils.isEmpty(originTimeObject)) {
                    socketTimeTreeMap.remove(originTime);
                }
            }
            timeAddress.setKey(now);
        } else {
            appAddressScMap.computeIfAbsent(app, k -> new ConcurrentHashMap<>()).put(address, sc);
            scInfoMap.put(sc, new Pair<>(now, address));
            log.info("ice nio app:{} client:{} online", app, address);
        }
        appScTimeTreeMap.computeIfAbsent(app, k -> new TreeMap<>()).computeIfAbsent(now, k -> new HashSet<>()).add(sc);
    }

    public synchronized Set<String> getRegisterClients(int app) {
        Map<String, SocketChannel> clientInfoMap = appAddressScMap.get(app);
        if (!CollectionUtils.isEmpty(clientInfoMap)) {
            return Collections.unmodifiableSet(clientInfoMap.keySet());
        }
        return null;
    }

    private synchronized SocketChannel getClientSocketChannel(int app, String address) {
        if (address != null) {
            Map<String, SocketChannel> clientMap = appAddressScMap.get(app);
            if (CollectionUtils.isEmpty(clientMap)) {
                return null;
            }
            return clientMap.get(address);
        }
        TreeMap<Long, Set<SocketChannel>> treeMap = appScTimeTreeMap.get(app);
        if (CollectionUtils.isEmpty(treeMap)) {
            return null;
        }
        Set<SocketChannel> socketChannels = treeMap.lastEntry().getValue();
        if (CollectionUtils.isEmpty(socketChannels)) {
            return null;
        }
        return socketChannels.iterator().next();
    }

    public Pair<Integer, String> confClazzCheck(int app, String clazz, byte type) {
        SocketChannel sc = getClientSocketChannel(app, null);
        if (sc == null) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        IceNioModel request = new IceNioModel();
        request.setClazz(clazz);
        request.setNodeType(type);
        request.setApp(app);
        request.setId(UUIDUtils.generateMost22UUID());
        request.setType(NioType.REQ);
        request.setOps(NioOps.CLAZZ_CHECK);
        IceNioModel response = getResult(sc, request);
        return response == null ? null : response.getClazzCheck();
    }

    public List<String> release(int app, IceTransferDto dto) {
        if (dto == null) {
            return null;
        }
        Map<String, SocketChannel> clientMap = appAddressScMap.get(app);
        if (!CollectionUtils.isEmpty(clientMap)) {
            for (Map.Entry<String, SocketChannel> entry : clientMap.entrySet()) {
                submitRelease(app, entry.getValue(), dto, entry.getKey());
            }
        }
        return null;
    }

    /**
     * submit release to update client config
     *
     * @param app     client app
     * @param sc      client socket channel
     * @param dto     update data
     * @param address client address
     */
    private void submitRelease(int app, SocketChannel sc, IceTransferDto dto, String address) {
        executor.submit(() -> {
            try {
                IceNioModel request = new IceNioModel();
                request.setUpdateDto(dto);
                request.setApp(app);
                request.setOps(NioOps.UPDATE);
                request.setType(NioType.REQ);
                IceNioUtils.writeNioModel(sc, request);
            } catch (Exception e) {
                unregister(app, sc);
                log.warn("remote client update failed app:{} address:{}", app, address);
            }
        });
    }

    public IceShowConf getClientShowConf(int app, Long confId, String address) {
        SocketChannel sc = getClientSocketChannel(app, address);
        if (sc == null) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        IceNioModel request = new IceNioModel();
        request.setConfId(confId);
        request.setId(UUIDUtils.generateMost22UUID());
        request.setApp(app);
        request.setOps(NioOps.SHOW_CONF);
        IceNioModel result = getResult(sc, request);
        return result == null ? null : result.getShowConf();
    }

    private IceNioModel getResult(SocketChannel sc, IceNioModel request) {
        Object lock = new Object();
        String id = request.getId();
        IceNioServer.lockMap.put(id, lock);
        IceNioModel result;
        try {
            IceNioUtils.writeNioModel(sc, request);
            synchronized (lock) {
                //wait for response from client
                lock.wait(2000);
            }
            result = IceNioServer.resultMap.get(id);
            IceNioServer.lockMap.remove(id);
            IceNioServer.resultMap.remove(id);
            if (result == null) {
                throw new ErrorCodeException(ErrorCode.TIMEOUT);
            }
        } catch (IOException e) {
            synchronized (lock) {
                IceNioServer.lockMap.remove(id);
                IceNioServer.resultMap.remove(id);
            }
            unregister(request.getApp(), sc);
            throw new ErrorCodeException(ErrorCode.CLIENT_CLOSED);
        } catch (InterruptedException e) {
            synchronized (lock) {
                IceNioServer.lockMap.remove(id);
                IceNioServer.resultMap.remove(id);
            }
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR);
        }
        return result;
    }

    public List<IceContext> mock(int app, IcePack pack) {
        SocketChannel sc = getClientSocketChannel(app, null);
        if (sc == null) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        IceNioModel request = new IceNioModel();
        request.setPack(pack);
        request.setType(NioType.REQ);
        request.setId(UUIDUtils.generateMost22UUID());
        request.setApp(app);
        request.setOps(NioOps.MOCK);
        IceNioModel response = getResult(sc, request);
        return response == null ? null : response.getMockResults();
    }

    @Override
    public void afterPropertiesSet() {
        executor = new ThreadPoolExecutor(properties.getPool().getCoreSize(), properties.getPool().getMaxSize(),
                properties.getPool().getKeepAliveSeconds(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getPool().getQueueCapacity()), new ThreadPoolExecutor.CallerRunsPolicy());
    }
}

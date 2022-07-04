package com.ice.server.nio;

import com.ice.common.dto.IceTransferDto;
import com.ice.common.model.IceChannelInfo;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.LeafNodeInfo;
import com.ice.common.utils.UUIDUtils;
import com.ice.core.client.IceNioModel;
import com.ice.core.client.NioOps;
import com.ice.core.client.NioType;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.utils.IceNioUtils;
import com.ice.server.config.IceServerProperties;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author waitmoon
 * manager ice nio clients
 * 1.get real config from client
 * 2.mock to client
 * 3.release the update to client
 * 4.check class from client
 */
@Slf4j
@Service
public final class IceNioClientManager implements InitializingBean {

    private static final Map<Integer, Map<String, Channel>> appAddressChannelMap = new ConcurrentHashMap<>();
    private static final Map<Channel, IceChannelInfo> channelInfoMap = new ConcurrentHashMap<>();
    private static final Map<Integer, TreeMap<Long, Set<Channel>>> appChannelTimeTreeMap = new TreeMap<>();
    private static final Map<Integer, Map<String, Map<String, LeafNodeInfo>>> appAddressLeafClazzMap = new ConcurrentHashMap<>();
    private static Map<Integer, Map<Byte, Map<String, LeafNodeInfo>>> appNodeLeafClazzMap = new ConcurrentHashMap<>();

    @Resource
    private IceServerProperties properties;

    private static ExecutorService executor;

    public static synchronized void unregister(Channel channel) {
        IceChannelInfo info = channelInfoMap.get(channel);
        if (info != null) {
            String address = info.getAddress();
            Long originTime = info.getLastUpdateTime();
            int app = info.getApp();
            channelInfoMap.remove(channel);
            Map<String, Channel> channelMap = appAddressChannelMap.get(app);
            if (!CollectionUtils.isEmpty(channelMap)) {
                channelMap.remove(address);
                if (CollectionUtils.isEmpty(channelMap)) {
                    appAddressChannelMap.remove(app);
                }
            }
            TreeMap<Long, Set<Channel>> treeMap = appChannelTimeTreeMap.get(app);
            if (!CollectionUtils.isEmpty(treeMap)) {
                Set<Channel> channels = treeMap.get(originTime);
                if (!CollectionUtils.isEmpty(channels)) {
                    channels.remove(channel);
                }
                if (CollectionUtils.isEmpty(channels)) {
                    treeMap.remove(originTime);
                }
                if (CollectionUtils.isEmpty(treeMap)) {
                    appChannelTimeTreeMap.remove(app);
                }
            }
            //remove client leaf class
            Map<String, Map<String, LeafNodeInfo>> addressNodeClassMap = appAddressLeafClazzMap.get(app);
            if (!CollectionUtils.isEmpty(addressNodeClassMap)) {
                addressNodeClassMap.remove(address);
                if (CollectionUtils.isEmpty(addressNodeClassMap)) {
                    //not have any available client
                    appAddressLeafClazzMap.remove(app);
                    appNodeLeafClazzMap = new ConcurrentHashMap<>();
                } else {
                    //reorganize app leaf class map
                    Map<Integer, Map<Byte, Map<String, LeafNodeInfo>>> appNodeLeafClazzMapTmp = new ConcurrentHashMap<>();
                    for (Map<String, LeafNodeInfo> leafTypeClassMap : addressNodeClassMap.values()) {
                        for (LeafNodeInfo leafNodeInfo : leafTypeClassMap.values()) {
                            appNodeLeafClazzMapTmp.computeIfAbsent(app, k -> new ConcurrentHashMap<>()).computeIfAbsent(leafNodeInfo.getType(), k -> new ConcurrentHashMap<>()).put(leafNodeInfo.getClazz(), leafNodeInfo);
                        }
                    }
                    appNodeLeafClazzMap = appNodeLeafClazzMapTmp;
                }
            }
            log.info("ice client app:{} client:{} offline", app, address);
        }
    }

    /**
     * clean client channel with expire time
     *
     * @param expireTime less time
     */
    public synchronized void cleanClientChannel(long expireTime) {
        for (Map.Entry<Integer, TreeMap<Long, Set<Channel>>> channelTimeTreeEntry : appChannelTimeTreeMap.entrySet()) {
            TreeMap<Long, Set<Channel>> treeMap = channelTimeTreeEntry.getValue();
            if (treeMap != null) {
                SortedMap<Long, Set<Channel>> cleanMap = treeMap.headMap(expireTime);
                if (!CollectionUtils.isEmpty(cleanMap)) {
                    Collection<Set<Channel>> cleanChannelSetList = cleanMap.values();
                    for (Set<Channel> cleanChannelSet : cleanChannelSetList) {
                        if (!CollectionUtils.isEmpty(cleanChannelSet)) {
                            for (Channel cleanChannel : cleanChannelSet) {
                                unregister(cleanChannel);
                            }
                        }
                    }
                }
            }
        }
    }

    public static synchronized void register(int app, Channel channel, String address) {
        //slap
        register(app, channel, address, null);
    }

    public static synchronized void register(int app, Channel channel, String address, List<LeafNodeInfo> leafNodes) {
        long now = System.currentTimeMillis();
        IceChannelInfo info = channelInfoMap.get(channel);
        if (info != null) {
            Long originTime = info.getLastUpdateTime();
            TreeMap<Long, Set<Channel>> socketTimeTreeMap = appChannelTimeTreeMap.get(app);
            if (socketTimeTreeMap != null) {
                Set<Channel> originTimeObject = socketTimeTreeMap.get(originTime);
                if (originTimeObject != null) {
                    originTimeObject.remove(channel);
                }
                if (CollectionUtils.isEmpty(originTimeObject)) {
                    socketTimeTreeMap.remove(originTime);
                }
            }
            info.setLastUpdateTime(now);
        } else {
            appAddressChannelMap.computeIfAbsent(app, k -> new ConcurrentHashMap<>()).put(address, channel);
            channelInfoMap.put(channel, new IceChannelInfo(app, address, now));
            log.info("ice client app:{} client:{} online", app, address);
        }
        appChannelTimeTreeMap.computeIfAbsent(app, k -> new TreeMap<>()).computeIfAbsent(now, k -> new HashSet<>()).add(channel);
        if (!CollectionUtils.isEmpty(leafNodes)) {
            for (LeafNodeInfo leafNodeInfo : leafNodes) {
                appAddressLeafClazzMap.computeIfAbsent(app, k -> new ConcurrentHashMap<>()).computeIfAbsent(address, k -> new ConcurrentHashMap<>()).put(leafNodeInfo.getClazz(), leafNodeInfo);
                appNodeLeafClazzMap.computeIfAbsent(app, k -> new ConcurrentHashMap<>()).computeIfAbsent(leafNodeInfo.getType(), k -> new ConcurrentHashMap<>()).put(leafNodeInfo.getClazz(), leafNodeInfo);
            }
        }
    }

    public synchronized Set<String> getRegisterClients(int app) {
        Map<String, Channel> clientInfoMap = appAddressChannelMap.get(app);
        if (!CollectionUtils.isEmpty(clientInfoMap)) {
            return Collections.unmodifiableSet(clientInfoMap.keySet());
        }
        return null;
    }

    private synchronized Channel getClientSocketChannel(int app, String address) {
        if (address != null) {
            Map<String, Channel> clientMap = appAddressChannelMap.get(app);
            if (CollectionUtils.isEmpty(clientMap)) {
                return null;
            }
            return clientMap.get(address);
        }
        TreeMap<Long, Set<Channel>> treeMap = appChannelTimeTreeMap.get(app);
        if (CollectionUtils.isEmpty(treeMap)) {
            return null;
        }
        Set<Channel> socketChannels = treeMap.lastEntry().getValue();
        if (CollectionUtils.isEmpty(socketChannels)) {
            return null;
        }
        return socketChannels.iterator().next();
    }

    public synchronized void confClazzCheck(int app, String clazz, byte type) {
        Map<Byte, Map<String, LeafNodeInfo>> addressClazzInfoMap = appNodeLeafClazzMap.get(app);
        if (!CollectionUtils.isEmpty(addressClazzInfoMap)) {
            Map<String, LeafNodeInfo> clazzInfoMap = addressClazzInfoMap.get(type);
            if (!CollectionUtils.isEmpty(clazzInfoMap)) {
                if (clazzInfoMap.containsKey(clazz)) {
                    //one of available client have this clazz
                    return;
                }
            }
        }
        throw new ErrorCodeException(ErrorCode.CLIENT_CLASS_NOT_FOUND, clazz, type, app);
    }

    public synchronized Set<String> getLeafTypeClasses(int app, byte type) {
        Map<Byte, Map<String, LeafNodeInfo>> addressClazzInfoMap = appNodeLeafClazzMap.get(app);
        if (!CollectionUtils.isEmpty(addressClazzInfoMap)) {
            Map<String, LeafNodeInfo> clazzInfoMap = addressClazzInfoMap.get(type);
            if (!CollectionUtils.isEmpty(clazzInfoMap)) {
                return clazzInfoMap.keySet();
            }
        }
        return null;
    }

    public List<String> release(int app, IceTransferDto dto) {
        if (dto == null) {
            return null;
        }
        Map<String, Channel> clientMap = appAddressChannelMap.get(app);
        if (!CollectionUtils.isEmpty(clientMap)) {
            for (Map.Entry<String, Channel> entry : clientMap.entrySet()) {
                submitRelease(app, entry.getValue(), dto);
            }
        }
        return null;
    }

    /**
     * submit release to update client config
     *
     * @param app     client app
     * @param channel client socket channel
     * @param dto     update data
     */
    private void submitRelease(int app, Channel channel, IceTransferDto dto) {
        executor.submit(() -> {
            IceNioModel request = new IceNioModel();
            request.setUpdateDto(dto);
            request.setApp(app);
            request.setOps(NioOps.UPDATE);
            request.setType(NioType.REQ);
            IceNioUtils.writeNioModel(channel, request);
        });
    }

    public IceShowConf getClientShowConf(int app, Long confId, String address) {
        Channel channel = getClientSocketChannel(app, address);
        if (channel == null) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app + ":" + address);
        }
        IceNioModel request = new IceNioModel();
        request.setConfId(confId);
        request.setId(UUIDUtils.generateUUID22());
        request.setApp(app);
        request.setType(NioType.REQ);
        request.setOps(NioOps.SHOW_CONF);
        IceNioModel result = getResult(channel, request);
        return result == null ? null : result.getShowConf();
    }

    private IceNioModel getResult(Channel channel, IceNioModel request) {
        Object lock = new Object();
        String id = request.getId();
        IceNioServerHandler.lockMap.put(id, lock);
        IceNioModel result;
        IceNioUtils.writeNioModel(channel, request);
        try {
            synchronized (lock) {
                //wait for response from client
                lock.wait(properties.getClientRspTimeOut());
            }
            result = IceNioServerHandler.resultMap.get(id);
            if (result == null) {
                throw new ErrorCodeException(ErrorCode.TIMEOUT);
            }
        } catch (InterruptedException e) {
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR);
        } finally {
            synchronized (lock) {
                IceNioServerHandler.lockMap.remove(id);
                IceNioServerHandler.resultMap.remove(id);
            }
        }
        return result;
    }

    public List<IceContext> mock(int app, IcePack pack) {
        Channel channel = getClientSocketChannel(app, null);
        if (channel == null) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        IceNioModel request = new IceNioModel();
        request.setPack(pack);
        request.setType(NioType.REQ);
        request.setId(UUIDUtils.generateUUID22());
        request.setApp(app);
        request.setOps(NioOps.MOCK);
        IceNioModel response = getResult(channel, request);
        return response == null ? null : response.getMockResults();
    }

    @Override
    public void afterPropertiesSet() {
        executor = new ThreadPoolExecutor(properties.getPool().getCoreSize(), properties.getPool().getMaxSize(),
                properties.getPool().getKeepAliveSeconds(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getPool().getQueueCapacity()), new ThreadPoolExecutor.CallerRunsPolicy());
    }
}

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
import com.ice.core.utils.JacksonUtils;
import com.ice.server.config.IceServerProperties;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import org.springframework.beans.factory.annotation.Autowired;
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

    private static Map<Integer, Map<String, Channel>> appAddressChannelMap = new ConcurrentHashMap<>();
    private static Map<Channel, IceChannelInfo> channelInfoMap = new ConcurrentHashMap<>();
    private static Map<Integer, TreeMap<Long, Set<Channel>>> appChannelTimeTreeMap = new TreeMap<>();
    private static Map<Integer, Map<String, Map<String, LeafNodeInfo>>> appAddressLeafClazzMap = new ConcurrentHashMap<>();
    //remain app`s last client leaf info
    private static Map<Integer, Map<Byte, Map<String, LeafNodeInfo>>> appNodeLeafClazzMap = new ConcurrentHashMap<>();

    @Autowired
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
                    //not have any available client, but remain last appNodeLeafClazzMap
                    appAddressLeafClazzMap.remove(app);
                } else {
                    //reorganize app leaf class map
                    Map<Byte, Map<String, LeafNodeInfo>> nodeLeafClazzMapTmp = new ConcurrentHashMap<>();
                    for (Map<String, LeafNodeInfo> leafTypeClassMap : addressNodeClassMap.values()) {
                        for (LeafNodeInfo leafNodeInfo : leafTypeClassMap.values()) {
                            nodeLeafClazzMapTmp.computeIfAbsent(leafNodeInfo.getType(), k -> new ConcurrentHashMap<>()).put(leafNodeInfo.getClazz(), leafNodeInfo);
                        }
                    }
                    appNodeLeafClazzMap.put(app, nodeLeafClazzMapTmp);
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

    public synchronized Channel getClientSocketChannel(int app, String address) {
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

    public synchronized Map<String, LeafNodeInfo> getLeafTypeClasses(int app, byte type) {
        Map<Byte, Map<String, LeafNodeInfo>> addressClazzInfoMap = appNodeLeafClazzMap.get(app);
        if (!CollectionUtils.isEmpty(addressClazzInfoMap)) {
            Map<String, LeafNodeInfo> clazzInfoMap = addressClazzInfoMap.get(type);
            if (!CollectionUtils.isEmpty(clazzInfoMap)) {
                return clazzInfoMap;
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
            IceNioModel updateModel = new IceNioModel();
            updateModel.setUpdateDto(dto);
            updateModel.setApp(app);
            updateModel.setOps(NioOps.UPDATE);
            updateModel.setType(NioType.REQ);
            byte[] updateModelBytes = JacksonUtils.toJsonBytes(updateModel);
            for (Map.Entry<String, Channel> entry : clientMap.entrySet()) {
                submitRelease(entry.getValue(), updateModelBytes);
            }
        }
        return null;
    }

    /**
     * submit release to update client config
     *
     * @param channel    client socket channel
     * @param modelBytes update data
     */
    private void submitRelease(Channel channel, byte[] modelBytes) {
        executor.submit(() -> {
            try {
                //synchronized with IceNioServerHandler client init
                synchronized (channel) {
                    IceNioUtils.writeModel(channel, modelBytes);
                }
            } catch (Throwable t) {
                //write failed closed client, client will get update from reconnect
                channel.close();
            }
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

    public IceNioModel getResult(Channel channel, IceNioModel request) {
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

    public synchronized void cleanChannelCache() {
        appNodeLeafClazzMap = new ConcurrentHashMap<>();
        appAddressLeafClazzMap = new ConcurrentHashMap<>();
        appChannelTimeTreeMap = new ConcurrentHashMap<>();
        appAddressChannelMap = new ConcurrentHashMap<>();
        try {
            if (!CollectionUtils.isEmpty(channelInfoMap)) {
                for (Channel channel : channelInfoMap.keySet()) {
                    channel.close();
                }
            }
        } catch (Exception e) {
            //ignore
        }
        channelInfoMap = new ConcurrentHashMap<>();
    }

    public LeafNodeInfo getNodeInfo(int app, String address, String clazz, Byte type) {
        if (address == null) {
            return getNodeInfoFromAllClient(app, clazz, type);
        }
        Map<String, Map<String, LeafNodeInfo>> addressLeafClazzMap = appAddressLeafClazzMap.get(app);
        if (addressLeafClazzMap == null) {
            return getNodeInfoFromAllClient(app, clazz, type);
        }
        Map<String, LeafNodeInfo> clazzMap = addressLeafClazzMap.get(address);
        if (clazzMap == null) {
            return getNodeInfoFromAllClient(app, clazz, type);
        }
        return nodeInfoCopy(clazzMap.get(clazz));
    }

    private LeafNodeInfo getNodeInfoFromAllClient(int app, String clazz, Byte type) {
        Map<Byte, Map<String, LeafNodeInfo>> nodeLeafClazzMap = appNodeLeafClazzMap.get(app);
        if (nodeLeafClazzMap == null) {
            return null;
        }
        Map<String, LeafNodeInfo> clazzMap = nodeLeafClazzMap.get(type);
        if (clazzMap == null) {
            return null;
        }
        return nodeInfoCopy(clazzMap.get(clazz));
    }

    private static LeafNodeInfo nodeInfoCopy(LeafNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return null;
        }
        LeafNodeInfo result = new LeafNodeInfo();
        result.setName(nodeInfo.getName());
        result.setClazz(nodeInfo.getClazz());
        result.setType(nodeInfo.getType());
        result.setDesc(nodeInfo.getDesc());
        result.setIceFields(fieldInfoListCopy(nodeInfo.getIceFields()));
        result.setHideFields(fieldInfoListCopy(nodeInfo.getHideFields()));
        return result;
    }

    private static List<LeafNodeInfo.IceFieldInfo> fieldInfoListCopy(List<LeafNodeInfo.IceFieldInfo> fieldInfoList) {
        if (fieldInfoList == null) {
            return null;
        }
        List<LeafNodeInfo.IceFieldInfo> results = new ArrayList<>(fieldInfoList.size());
        for (LeafNodeInfo.IceFieldInfo fieldInfo : fieldInfoList) {
            LeafNodeInfo.IceFieldInfo result = new LeafNodeInfo.IceFieldInfo();
            result.setField(fieldInfo.getField());
            result.setValue(fieldInfo.getValue());
            result.setType(fieldInfo.getType());
            result.setName(fieldInfo.getName());
            result.setDesc(fieldInfo.getDesc());
            results.add(result);
        }
        return results;
    }

}

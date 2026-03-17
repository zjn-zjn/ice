package com.ice.server.storage;

import com.ice.common.dto.IceClientInfo;
import com.ice.common.model.LeafNodeInfo;
import com.ice.server.config.IceServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 客户端管理器
 * 负责巡查失活客户端并清理，同时保护最后一个节点的leafNodes信息
 * 
 * 设计原则：
 * 1. 不使用内存缓存，使用 _latest.json 文件存储最新客户端信息，保证多server一致性
 * 2. 获取leafNodes时，直接读取 _latest.json，O(1)操作，支持百万客户端
 * 3. 客户端上报心跳时自动更新 _latest.json
 * 4. 清理时如果 _latest.json 中的客户端已失活，更新为一个活跃的；保留最后一个客户端文件
 *
 * @author waitmoon
 */
@Slf4j
@Service
public class IceClientManager {

    private final IceServerProperties properties;
    private final IceFileStorageService storageService;

    public IceClientManager(IceServerProperties properties, IceFileStorageService storageService) {
        this.properties = properties;
        this.storageService = storageService;
    }

    /**
     * 获取指定app的所有在线客户端（主干）
     */
    public List<IceClientInfo> getActiveClients(int app) throws IOException {
        return getActiveClients(app, null);
    }

    /**
     * 获取指定app指定泳道的所有在线客户端
     */
    public List<IceClientInfo> getActiveClients(int app, String lane) throws IOException {
        List<IceClientInfo> clients = storageService.listClients(app, lane);
        long timeout = properties.getClientTimeout() * 1000L;
        long now = System.currentTimeMillis();

        return clients.stream()
                .filter(c -> c.getLastHeartbeat() != null && (now - c.getLastHeartbeat()) < timeout)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定app的所有注册客户端地址
     */
    public Set<String> getRegisterClients(int app) {
        try {
            List<IceClientInfo> clients = getActiveClients(app);
            if (CollectionUtils.isEmpty(clients)) {
                return Collections.emptySet();
            }
            return clients.stream()
                    .map(IceClientInfo::getAddress)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            log.error("failed to get register clients for app:{}", app, e);
            return Collections.emptySet();
        }
    }

    /**
     * 获取有效的 _latest.json 客户端信息
     */
    private IceClientInfo getValidLatestClient(int app) throws IOException {
        return getValidLatestClient(app, null);
    }

    private IceClientInfo getValidLatestClient(int app, String lane) throws IOException {
        IceClientInfo latestClient = storageService.getLatestClient(app, lane);
        if (latestClient == null) {
            return null;
        }

        IceClientInfo currentClient = storageService.getClient(app, lane, latestClient.getAddress());
        if (currentClient != null && isClientActive(currentClient)) {
            return latestClient;
        }

        IceClientInfo newActiveClient = storageService.findFirstActiveClientWithLeafNodes(app, lane,
                properties.getClientTimeout() * 1000L);
        if (newActiveClient != null) {
            storageService.updateLatestClient(app, lane, newActiveClient);
            log.info("updated _latest.json for app:{} lane:{} from inactive {} to active {}",
                    app, lane, latestClient.getAddress(), newActiveClient.getAddress());
            return newActiveClient;
        }

        return latestClient;
    }

    private boolean isClientActive(IceClientInfo client) {
        if (client == null || client.getLastHeartbeat() == null) {
            return false;
        }
        long timeout = properties.getClientTimeout() * 1000L;
        return (System.currentTimeMillis() - client.getLastHeartbeat()) < timeout;
    }

    /**
     * 获取指定app的叶子节点类信息（主干 + 可选泳道合并）
     * lane == null: 只返回主干
     * lane != null: 主干 + 该泳道合并，同名类泳道覆盖主干
     */
    public Map<String, LeafNodeInfo> getLeafTypeClasses(int app, byte type) {
        return getLeafTypeClasses(app, type, null);
    }

    public Map<String, LeafNodeInfo> getLeafTypeClasses(int app, byte type, String lane) {
        try {
            Map<String, LeafNodeInfo> result = new HashMap<>();

            // 先加载主干的 leafNodes
            IceClientInfo trunkClient = getValidLatestClient(app, null);
            if (trunkClient != null && !CollectionUtils.isEmpty(trunkClient.getLeafNodes())) {
                for (LeafNodeInfo nodeInfo : trunkClient.getLeafNodes()) {
                    if (nodeInfo.getType() == type) {
                        result.put(nodeInfo.getClazz(), nodeInfo);
                    }
                }
            }

            // 如果指定了泳道，加载泳道的 leafNodes 并覆盖同名类
            if (lane != null && !lane.isEmpty()) {
                IceClientInfo laneClient = getValidLatestClient(app, lane);
                if (laneClient != null && !CollectionUtils.isEmpty(laneClient.getLeafNodes())) {
                    for (LeafNodeInfo nodeInfo : laneClient.getLeafNodes()) {
                        if (nodeInfo.getType() == type) {
                            result.put(nodeInfo.getClazz(), nodeInfo);
                        }
                    }
                }
            }

            return result.isEmpty() ? null : result;
        } catch (IOException e) {
            log.error("failed to get leaf type classes for app:{} lane:{}", app, lane, e);
            return null;
        }
    }

    /**
     * 获取指定class的LeafNodeInfo（主干 + 可选泳道合并）
     */
    public LeafNodeInfo getNodeInfo(int app, String address, String clazz, Byte type) {
        return getNodeInfo(app, address, clazz, type, null);
    }

    public LeafNodeInfo getNodeInfo(int app, String address, String clazz, Byte type, String lane) {
        try {
            // 如果指定了泳道，优先从泳道查找
            if (lane != null && !lane.isEmpty()) {
                LeafNodeInfo laneResult = findNodeInfoInClient(app, lane, clazz, type);
                if (laneResult != null) {
                    return laneResult;
                }
            }

            // 从主干查找
            return findNodeInfoInClient(app, null, clazz, type);
        } catch (IOException e) {
            log.error("failed to get node info for app:{} clazz:{} lane:{}", app, clazz, lane, e);
            return null;
        }
    }

    private LeafNodeInfo findNodeInfoInClient(int app, String lane, String clazz, Byte type) throws IOException {
        IceClientInfo latestClient = getValidLatestClient(app, lane);
        if (latestClient == null || CollectionUtils.isEmpty(latestClient.getLeafNodes())) {
            return null;
        }

        for (LeafNodeInfo nodeInfo : latestClient.getLeafNodes()) {
            if (clazz.equals(nodeInfo.getClazz()) && (type == null || type.equals(nodeInfo.getType()))) {
                return copyNodeInfo(nodeInfo);
            }
        }
        return null;
    }

    /**
     * 列出指定app下的所有泳道
     */
    public List<String> listLanes(int app) {
        try {
            return storageService.listLanes(app);
        } catch (IOException e) {
            log.error("failed to list lanes for app:{}", app, e);
            return Collections.emptyList();
        }
    }

    private LeafNodeInfo copyNodeInfo(LeafNodeInfo source) {
        if (source == null) {
            return null;
        }
        LeafNodeInfo result = new LeafNodeInfo();
        result.setName(source.getName());
        result.setClazz(source.getClazz());
        result.setType(source.getType());
        result.setDesc(source.getDesc());
        result.setOrder(source.getOrder());
        result.setIceFields(copyFieldInfoList(source.getIceFields()));
        result.setHideFields(copyFieldInfoList(source.getHideFields()));
        return result;
    }

    private List<LeafNodeInfo.IceFieldInfo> copyFieldInfoList(List<LeafNodeInfo.IceFieldInfo> source) {
        if (source == null) {
            return null;
        }
        List<LeafNodeInfo.IceFieldInfo> result = new ArrayList<>(source.size());
        for (LeafNodeInfo.IceFieldInfo fieldInfo : source) {
            LeafNodeInfo.IceFieldInfo copy = new LeafNodeInfo.IceFieldInfo();
            copy.setField(fieldInfo.getField());
            copy.setType(fieldInfo.getType());
            copy.setName(fieldInfo.getName());
            copy.setDesc(fieldInfo.getDesc());
            result.add(copy);
        }
        return result;
    }

    /**
     * 定时清理失活客户端
     */
    @Scheduled(fixedDelayString = "${ice.client-timeout:60}000")
    public void cleanInactiveClients() {
        try {
            List<Integer> apps = getAllApps();
            for (Integer app : apps) {
                // 清理主干
                cleanInactiveClientsForApp(app, null);
                // 清理所有泳道
                List<String> lanes = storageService.listLanes(app);
                for (String lane : lanes) {
                    cleanInactiveClientsForApp(app, lane);
                }
            }
        } catch (Exception e) {
            log.error("failed to clean inactive clients", e);
        }
    }

    private List<Integer> getAllApps() {
        try {
            return storageService.listApps().stream()
                    .map(a -> a.getId())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("failed to list apps", e);
            return Collections.emptyList();
        }
    }

    private void cleanInactiveClientsForApp(int app, String lane) {
        try {
            List<IceClientInfo> clients = storageService.listClients(app, lane);
            if (CollectionUtils.isEmpty(clients)) {
                // 泳道下没有客户端文件，尝试删除空泳道目录
                if (lane != null) {
                    storageService.deleteEmptyLaneDir(app, lane);
                }
                return;
            }

            long timeout = properties.getClientTimeout() * 1000L;
            long now = System.currentTimeMillis();

            List<IceClientInfo> activeClients = new ArrayList<>();
            List<IceClientInfo> inactiveClients = new ArrayList<>();

            for (IceClientInfo client : clients) {
                if (client.getLastHeartbeat() != null && (now - client.getLastHeartbeat()) < timeout) {
                    activeClients.add(client);
                } else {
                    inactiveClients.add(client);
                }
            }

            if (CollectionUtils.isEmpty(inactiveClients)) {
                return;
            }

            updateLatestClientIfNeeded(app, lane, activeClients, inactiveClients);

            int deleteCount = 0;
            for (int i = 0; i < inactiveClients.size(); i++) {
                if (activeClients.isEmpty() && i == inactiveClients.size() - 1) {
                    // 泳道下不保留最后一个失活客户端，全部删除后删目录
                    if (lane != null) {
                        storageService.deleteClient(app, lane, inactiveClients.get(i).getAddress());
                        deleteCount++;
                    } else {
                        log.info("preserved last inactive client for app:{}, address:{}", app, inactiveClients.get(i).getAddress());
                    }
                    continue;
                }

                IceClientInfo client = inactiveClients.get(i);
                storageService.deleteClient(app, lane, client.getAddress());
                deleteCount++;
                log.info("cleaned inactive client for app:{} lane:{}, address:{}", app, lane, client.getAddress());
            }

            if (deleteCount > 0) {
                log.info("cleaned {} inactive clients for app:{} lane:{}", deleteCount, app, lane);
            }

            // 泳道清理完后，如果泳道目录为空则删除
            if (lane != null) {
                storageService.deleteEmptyLaneDir(app, lane);
            }
        } catch (IOException e) {
            log.error("failed to clean inactive clients for app:{} lane:{}", app, lane, e);
        }
    }

    private void updateLatestClientIfNeeded(int app, String lane, List<IceClientInfo> activeClients,
                                            List<IceClientInfo> inactiveClients) throws IOException {
        IceClientInfo latestClient = storageService.getLatestClient(app, lane);

        boolean needUpdate = false;
        if (latestClient == null) {
            needUpdate = true;
        } else {
            String latestAddress = latestClient.getAddress();
            boolean isInactive = inactiveClients.stream()
                    .anyMatch(c -> latestAddress.equals(c.getAddress()));
            if (isInactive) {
                needUpdate = true;
            }
        }

        if (needUpdate) {
            IceClientInfo newLatest = activeClients.stream()
                    .filter(c -> !CollectionUtils.isEmpty(c.getLeafNodes()))
                    .findFirst()
                    .orElse(null);

            if (newLatest != null) {
                storageService.updateLatestClient(app, lane, newLatest);
                log.info("updated _latest.json for app:{} lane:{} to active client:{}", app, lane, newLatest.getAddress());
            } else if (!activeClients.isEmpty()) {
                storageService.updateLatestClient(app, lane, activeClients.get(0));
                log.info("updated _latest.json for app:{} lane:{} to active client (no leafNodes):{}", app, lane, activeClients.get(0).getAddress());
            }
        }
    }
}

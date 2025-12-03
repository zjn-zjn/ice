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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 客户端管理器
 * 负责巡查失活客户端并清理，同时保护最后一个节点的leafNodes信息
 *
 * @author waitmoon
 */
@Slf4j
@Service
public class IceClientManager {

    private final IceServerProperties properties;
    private final IceFileStorageService storageService;

    // 缓存每个app最后一个客户端的leafNodes信息，用于在所有客户端下线后仍能提供class信息
    private final Map<Integer, List<LeafNodeInfo>> lastLeafNodesCache = new ConcurrentHashMap<>();

    public IceClientManager(IceServerProperties properties, IceFileStorageService storageService) {
        this.properties = properties;
        this.storageService = storageService;
    }

    /**
     * 获取指定app的所有在线客户端
     */
    public List<IceClientInfo> getActiveClients(int app) throws IOException {
        List<IceClientInfo> clients = storageService.listClients(app);
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
     * 获取指定app的叶子节点类信息
     * 优先从活跃客户端获取，如果没有活跃客户端则使用缓存
     */
    public Map<String, LeafNodeInfo> getLeafTypeClasses(int app, byte type) {
        try {
            List<IceClientInfo> clients = getActiveClients(app);
            Map<String, LeafNodeInfo> result = new HashMap<>();

            if (!CollectionUtils.isEmpty(clients)) {
                // 从活跃客户端收集leafNodes
                for (IceClientInfo client : clients) {
                    if (!CollectionUtils.isEmpty(client.getLeafNodes())) {
                        for (LeafNodeInfo nodeInfo : client.getLeafNodes()) {
                            if (nodeInfo.getType() == type) {
                                result.put(nodeInfo.getClazz(), nodeInfo);
                            }
                        }
                    }
                }

                // 更新缓存
                List<LeafNodeInfo> allLeafNodes = clients.stream()
                        .filter(c -> !CollectionUtils.isEmpty(c.getLeafNodes()))
                        .flatMap(c -> c.getLeafNodes().stream())
                        .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(allLeafNodes)) {
                    lastLeafNodesCache.put(app, allLeafNodes);
                }
            } else {
                // 使用缓存
                List<LeafNodeInfo> cached = lastLeafNodesCache.get(app);
                if (!CollectionUtils.isEmpty(cached)) {
                    for (LeafNodeInfo nodeInfo : cached) {
                        if (nodeInfo.getType() == type) {
                            result.put(nodeInfo.getClazz(), nodeInfo);
                        }
                    }
                }
            }

            return result.isEmpty() ? null : result;
        } catch (IOException e) {
            log.error("failed to get leaf type classes for app:{}", app, e);
            return null;
        }
    }

    /**
     * 获取指定class的LeafNodeInfo
     */
    public LeafNodeInfo getNodeInfo(int app, String address, String clazz, Byte type) {
        try {
            List<IceClientInfo> clients;
            if (address != null) {
                // 从指定客户端获取
                clients = getActiveClients(app).stream()
                        .filter(c -> address.equals(c.getAddress()))
                        .collect(Collectors.toList());
            } else {
                clients = getActiveClients(app);
            }

            // 从客户端中查找
            for (IceClientInfo client : clients) {
                if (!CollectionUtils.isEmpty(client.getLeafNodes())) {
                    for (LeafNodeInfo nodeInfo : client.getLeafNodes()) {
                        if (clazz.equals(nodeInfo.getClazz()) && (type == null || type.equals(nodeInfo.getType()))) {
                            return copyNodeInfo(nodeInfo);
                        }
                    }
                }
            }

            // 从缓存中查找
            List<LeafNodeInfo> cached = lastLeafNodesCache.get(app);
            if (!CollectionUtils.isEmpty(cached)) {
                for (LeafNodeInfo nodeInfo : cached) {
                    if (clazz.equals(nodeInfo.getClazz()) && (type == null || type.equals(nodeInfo.getType()))) {
                        return copyNodeInfo(nodeInfo);
                    }
                }
            }

            return null;
        } catch (IOException e) {
            log.error("failed to get node info for app:{} clazz:{}", app, clazz, e);
            return null;
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
            // 获取所有app目录
            List<Integer> apps = getAllApps();

            for (Integer app : apps) {
                cleanInactiveClientsForApp(app);
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

    private void cleanInactiveClientsForApp(int app) {
        try {
            List<IceClientInfo> clients = storageService.listClients(app);
            if (CollectionUtils.isEmpty(clients)) {
                return;
            }

            long timeout = properties.getClientTimeout() * 1000L;
            long now = System.currentTimeMillis();

            // 分离活跃和失活客户端
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

            // 保护最后一个节点
            if (activeClients.isEmpty() && inactiveClients.size() == 1) {
                // 只剩最后一个失活客户端，保留其leafNodes信息到缓存但不删除文件
                IceClientInfo lastClient = inactiveClients.get(0);
                if (!CollectionUtils.isEmpty(lastClient.getLeafNodes())) {
                    lastLeafNodesCache.put(app, new ArrayList<>(lastClient.getLeafNodes()));
                    log.info("preserved last client's leafNodes for app:{}, address:{}", app, lastClient.getAddress());
                }
                return;
            }

            // 如果还有活跃客户端，更新缓存并删除失活客户端
            if (!activeClients.isEmpty()) {
                List<LeafNodeInfo> allLeafNodes = activeClients.stream()
                        .filter(c -> !CollectionUtils.isEmpty(c.getLeafNodes()))
                        .flatMap(c -> c.getLeafNodes().stream())
                        .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(allLeafNodes)) {
                    lastLeafNodesCache.put(app, allLeafNodes);
                }
            }

            // 删除失活客户端（但保留最后一个以保护leafNodes信息）
            int deleteCount = 0;
            for (int i = 0; i < inactiveClients.size(); i++) {
                // 如果没有活跃客户端，保留最后一个失活客户端
                if (activeClients.isEmpty() && i == inactiveClients.size() - 1) {
                    IceClientInfo lastClient = inactiveClients.get(i);
                    if (!CollectionUtils.isEmpty(lastClient.getLeafNodes())) {
                        lastLeafNodesCache.put(app, new ArrayList<>(lastClient.getLeafNodes()));
                    }
                    log.info("preserved last inactive client's leafNodes for app:{}, address:{}", app, lastClient.getAddress());
                    continue;
                }

                IceClientInfo client = inactiveClients.get(i);
                storageService.deleteClient(app, client.getAddress());
                deleteCount++;
                log.info("cleaned inactive client for app:{}, address:{}", app, client.getAddress());
            }

            if (deleteCount > 0) {
                log.info("cleaned {} inactive clients for app:{}", deleteCount, app);
            }
        } catch (IOException e) {
            log.error("failed to clean inactive clients for app:{}", app, e);
        }
    }
}


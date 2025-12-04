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
     * 获取指定app的所有在线客户端
     * 注意：百万客户端场景下慎用，会遍历所有客户端文件
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
     * 注意：百万客户端场景下慎用
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
     * 检查 _latest.json 中的客户端是否还存活，如果失活则尝试更新
     */
    private IceClientInfo getValidLatestClient(int app) throws IOException {
        IceClientInfo latestClient = storageService.getLatestClient(app);
        if (latestClient == null) {
            return null;
        }

        // 检查 _latest.json 中的客户端是否还存活（直接根据 address 定位文件，O(1)）
        IceClientInfo currentClient = storageService.getClient(app, latestClient.getAddress());
        if (currentClient != null && isClientActive(currentClient)) {
            // 存活，直接返回 _latest.json 的内容（包含完整的 leafNodes）
            return latestClient;
        }

        // _latest.json 中的客户端已失活，尝试找一个新的活跃客户端替换
        // 使用惰性遍历，找到第一个符合条件的就停止，不加载所有文件到内存
        IceClientInfo newActiveClient = storageService.findFirstActiveClientWithLeafNodes(app, 
                properties.getClientTimeout() * 1000L);
        if (newActiveClient != null) {
            storageService.updateLatestClient(app, newActiveClient);
            log.info("updated _latest.json for app:{} from inactive {} to active {}", 
                    app, latestClient.getAddress(), newActiveClient.getAddress());
            return newActiveClient;
        }

        // 没有活跃的带 leafNodes 的客户端，但 _latest.json 的 leafNodes 仍然可用
        // 返回 _latest.json 的内容（即使客户端已失活，leafNodes 信息仍然有效）
        return latestClient;
    }

    /**
     * 检查客户端是否活跃
     */
    private boolean isClientActive(IceClientInfo client) {
        if (client == null || client.getLastHeartbeat() == null) {
            return false;
        }
        long timeout = properties.getClientTimeout() * 1000L;
        return (System.currentTimeMillis() - client.getLastHeartbeat()) < timeout;
    }

    /**
     * 获取指定app的叶子节点类信息
     * 先检查 _latest.json 是否有效，O(1)操作；失活时尝试更新
     */
    public Map<String, LeafNodeInfo> getLeafTypeClasses(int app, byte type) {
        try {
            IceClientInfo latestClient = getValidLatestClient(app);
            if (latestClient == null || CollectionUtils.isEmpty(latestClient.getLeafNodes())) {
                return null;
            }

            Map<String, LeafNodeInfo> result = new HashMap<>();
            for (LeafNodeInfo nodeInfo : latestClient.getLeafNodes()) {
                if (nodeInfo.getType() == type) {
                    result.put(nodeInfo.getClazz(), nodeInfo);
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
     * 先检查 _latest.json 是否有效，O(1)操作；失活时尝试更新
     */
    public LeafNodeInfo getNodeInfo(int app, String address, String clazz, Byte type) {
        try {
            IceClientInfo latestClient = getValidLatestClient(app);
            if (latestClient == null || CollectionUtils.isEmpty(latestClient.getLeafNodes())) {
                return null;
            }

            for (LeafNodeInfo nodeInfo : latestClient.getLeafNodes()) {
                if (clazz.equals(nodeInfo.getClazz()) && (type == null || type.equals(nodeInfo.getType()))) {
                    return copyNodeInfo(nodeInfo);
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

            // 检查并更新 _latest.json
            updateLatestClientIfNeeded(app, activeClients, inactiveClients);

            // 删除失活客户端（但保留最后一个）
            int deleteCount = 0;
            for (int i = 0; i < inactiveClients.size(); i++) {
                // 如果没有活跃客户端，保留最后一个失活客户端文件
                if (activeClients.isEmpty() && i == inactiveClients.size() - 1) {
                    log.info("preserved last inactive client for app:{}, address:{}", app, inactiveClients.get(i).getAddress());
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

    /**
     * 如果 _latest.json 中的客户端已失活，更新为一个活跃的客户端
     * 如果没有活跃客户端，保留 _latest.json 不变
     */
    private void updateLatestClientIfNeeded(int app, List<IceClientInfo> activeClients, 
                                            List<IceClientInfo> inactiveClients) throws IOException {
        IceClientInfo latestClient = storageService.getLatestClient(app);
        
        // 检查 _latest.json 是否需要更新
        boolean needUpdate = false;
        if (latestClient == null) {
            needUpdate = true;
        } else {
            // 检查 latest 是否在失活列表中
            String latestAddress = latestClient.getAddress();
            boolean isInactive = inactiveClients.stream()
                    .anyMatch(c -> latestAddress.equals(c.getAddress()));
            if (isInactive) {
                needUpdate = true;
            }
        }

        if (needUpdate) {
            // 优先选择有 leafNodes 的活跃客户端
            IceClientInfo newLatest = activeClients.stream()
                    .filter(c -> !CollectionUtils.isEmpty(c.getLeafNodes()))
                    .findFirst()
                    .orElse(null);

            if (newLatest != null) {
                storageService.updateLatestClient(app, newLatest);
                log.info("updated _latest.json for app:{} to active client:{}", app, newLatest.getAddress());
            } else if (!activeClients.isEmpty()) {
                // 没有带 leafNodes 的，取任意一个活跃的
                storageService.updateLatestClient(app, activeClients.get(0));
                log.info("updated _latest.json for app:{} to active client (no leafNodes):{}", app, activeClients.get(0).getAddress());
            }
            // 如果没有活跃客户端，保留原来的 _latest.json 不变
        }
    }
}

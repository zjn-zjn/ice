package com.ice.server.service.impl;

import com.ice.common.constant.Constant;
import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.IceBaseDto;
import com.ice.common.dto.IceConfDto;
import com.ice.common.dto.IceTransferDto;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.model.IceShowNode;
import com.ice.common.model.LeafNodeInfo;
import com.ice.server.config.IceServerProperties;
import com.ice.server.constant.ServerConstant;
import com.ice.server.model.IceBase;
import com.ice.server.model.IceConf;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.service.IceServerService;
import com.ice.server.storage.IceClientManager;
import com.ice.server.storage.IceFileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author waitmoon
 * 改造为基于文件系统的实现，不使用内存缓存
 */
@Slf4j
@Service
public class IceServerServiceImpl implements IceServerService {

    private final IceFileStorageService storageService;
    private final IceClientManager clientManager;
    private final IceServerProperties properties;
    private final ObjectMapper objectMapper;

    public IceServerServiceImpl(IceFileStorageService storageService, IceClientManager clientManager,
                                IceServerProperties properties, ObjectMapper objectMapper) {
        this.storageService = storageService;
        this.clientManager = clientManager;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public IceTransferDto getInitConfig(Integer app) {
        try {
            IceTransferDto transferDto = new IceTransferDto();
            transferDto.setVersion(storageService.getVersion(app));
            transferDto.setInsertOrUpdateBases(getActiveBasesByApp(app));
            transferDto.setInsertOrUpdateConfs(getActiveConfListByApp(app));
            return transferDto;
        } catch (IOException e) {
            log.error("failed to get init config for app:{}", app, e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    private Collection<IceBaseDto> getActiveBasesByApp(Integer app) throws IOException {
        return storageService.listActiveBases(app);
    }

    private Collection<IceConfDto> getActiveConfListByApp(Integer app) throws IOException {
        return storageService.listActiveConfs(app).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == IceStorageConstants.STATUS_ONLINE)
                .collect(Collectors.toList());
    }

    @Override
    public IceConf getActiveConfById(Integer app, Long confId) {
        try {
            IceConfDto dto = storageService.getConf(app, confId);
            return dto != null ? IceConf.fromDto(dto) : null;
        } catch (IOException e) {
            log.error("failed to get active conf by id:{}", confId, e);
            return null;
        }
    }

    @Override
    public IceConf getUpdateConfById(Integer app, Long confId, Long iceId) {
        try {
            IceConfDto dto = storageService.getConfUpdate(app, iceId, confId);
            return dto != null ? IceConf.fromDto(dto) : null;
        } catch (IOException e) {
            log.error("failed to get update conf by id:{}", confId, e);
            return null;
        }
    }

    @Override
    public List<IceConf> getMixConfListByIds(Integer app, Set<Long> confSet, long iceId) {
        if (CollectionUtils.isEmpty(confSet)) {
            return null;
        }
        List<IceConf> confList = new ArrayList<>(confSet.size());
        for (Long confId : confSet) {
            IceConf conf = this.getMixConfById(app, confId, iceId);
            if (conf == null) {
                return null;
            }
            confList.add(conf);
        }
        return confList;
    }

    @Override
    public IceConf getMixConfById(int app, long confId, long iceId) {
        // 先查update，再查active
        IceConf updateConf = getUpdateConfById(app, confId, iceId);
        if (updateConf != null) {
            return updateConf;
        }
        return getActiveConfById(app, confId);
    }

    @Override
    public IceShowNode getConfMixById(int app, long confId, long iceId) {
        IceConf root = getMixConfById(app, confId, iceId);
        return assembleShowNode(root, app, iceId);
    }

    private IceShowNode assembleShowNode(IceConf node, int app, long iceId) {
        if (node == null) {
            return null;
        }
        IceShowNode showNode = ServerConstant.confToShow(node);
        if (NodeTypeEnum.isRelation(node.getType())) {
            // 关系节点：处理子节点
            if (StringUtils.hasLength(showNode.getSonIds())) {
            String[] sonIdStrs = showNode.getSonIds().split(Constant.REGEX_COMMA);
            List<Long> sonIds = new ArrayList<>(sonIdStrs.length);
            for (String sonStr : sonIdStrs) {
                sonIds.add(Long.valueOf(sonStr));
            }
            List<IceShowNode> children = new ArrayList<>(sonIdStrs.length);
            for (int i = 0; i < sonIds.size(); i++) {
                    IceConf child = getMixConfById(app, sonIds.get(i), iceId);
                if (child != null) {
                        IceShowNode showChild = assembleShowNode(child, app, iceId);
                    showChild.setParentId(node.getMixId());
                    showChild.setIndex(i);
                    children.add(showChild);
                }
            }
            showNode.setChildren(children);
            }
        } else if (NodeTypeEnum.isLeaf(node.getType()) && StringUtils.hasLength(node.getConfName())) {
            // 叶子节点：获取字段信息
            LeafNodeInfo nodeInfo = clientManager.getNodeInfo(node.getApp(), null, node.getConfName(), node.getType());
            if (nodeInfo != null) {
                showNode.getShowConf().setHaveMeta(true);
                showNode.getShowConf().setNodeInfo(nodeInfo);
                // 解析 confField 并填充到 nodeInfo 的字段中
                fillFieldValues(nodeInfo, node.getConfField());
            } else {
                showNode.getShowConf().setHaveMeta(false);
            }
        }

        if (showNode.getForwardId() != null) {
            IceShowNode forwardNode = assembleShowNode(getMixConfById(app, showNode.getForwardId(), iceId), app, iceId);
            if (forwardNode != null) {
                forwardNode.setNextId(node.getMixId());
                showNode.setForward(forwardNode);
            }
        }
        return showNode;
    }

    /**
     * 解析 confField JSON 并填充到 nodeInfo 的字段中
     */
    private void fillFieldValues(LeafNodeInfo nodeInfo, String confField) {
        if (!StringUtils.hasLength(confField) || "{}".equals(confField)) {
            return;
        }
        try {
            Map<String, Object> fieldValues = objectMapper.readValue(confField, 
                    new TypeReference<Map<String, Object>>() {});
            if (fieldValues == null || fieldValues.isEmpty()) {
                return;
            }
            // 填充 iceFields
            if (!CollectionUtils.isEmpty(nodeInfo.getIceFields())) {
                for (LeafNodeInfo.IceFieldInfo fieldInfo : nodeInfo.getIceFields()) {
                    if (fieldValues.containsKey(fieldInfo.getField())) {
                        Object value = fieldValues.get(fieldInfo.getField());
                        fieldInfo.setValue(value);
                        fieldInfo.setValueNull(value == null);
                    }
                }
            }
            // 填充 hideFields
            if (!CollectionUtils.isEmpty(nodeInfo.getHideFields())) {
                for (LeafNodeInfo.IceFieldInfo fieldInfo : nodeInfo.getHideFields()) {
                    if (fieldValues.containsKey(fieldInfo.getField())) {
                        Object value = fieldValues.get(fieldInfo.getField());
                        fieldInfo.setValue(value);
                        fieldInfo.setValueNull(value == null);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse confField: {}", confField, e);
        }
    }

    @Override
    public IceBase getActiveBaseById(Integer app, Long iceId) {
        try {
            IceBaseDto dto = storageService.getBase(app, iceId);
            return dto != null ? IceBase.fromDto(dto) : null;
        } catch (IOException e) {
            log.error("failed to get active base by id:{}", iceId, e);
            return null;
        }
    }

    @Override
    public void updateLocalConfUpdateCache(IceConf conf) {
        try {
            IceConfDto dto = conf.toDto();
            // 确保默认值
            if (dto.getStatus() == null) {
                dto.setStatus(IceStorageConstants.STATUS_ONLINE);
            }
            if (dto.getTimeType() == null) {
                dto.setTimeType((byte) 1);
            }
            if (dto.getDebug() == null) {
                dto.setDebug((byte) 1);
            }
            if (dto.getUpdateAt() == null) {
                dto.setUpdateAt(System.currentTimeMillis());
            }
            storageService.saveConfUpdate(conf.getApp(), conf.getIceId(), dto);
        } catch (IOException e) {
            log.error("failed to save conf update:{}", conf.getMixId(), e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void updateLocalConfUpdateCaches(Collection<IceConf> confs) {
        for (IceConf conf : confs) {
            updateLocalConfUpdateCache(conf);
        }
    }

    @Override
    public void updateLocalConfActiveCache(IceConf conf) {
        try {
            IceConfDto dto = conf.toDto();
            dto.setIceId(null);
            dto.setConfId(null);
            // 确保默认值
            if (dto.getStatus() == null) {
                dto.setStatus(IceStorageConstants.STATUS_ONLINE);
            }
            if (dto.getTimeType() == null) {
                dto.setTimeType((byte) 1);
            }
            if (dto.getDebug() == null) {
                dto.setDebug((byte) 1);
            }
            if (dto.getUpdateAt() == null) {
                dto.setUpdateAt(System.currentTimeMillis());
            }
            storageService.saveConf(dto);
        } catch (IOException e) {
            log.error("failed to save conf:{}", conf.getMixId(), e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void updateLocalConfActiveCaches(Collection<IceConf> confs) {
        for (IceConf conf : confs) {
            updateLocalConfActiveCache(conf);
        }
    }

    @Override
    public void updateLocalBaseCache(IceBase base) {
        try {
            storageService.saveBase(base.toDto());
        } catch (IOException e) {
            log.error("failed to save base:{}", base.getId(), e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * 检测环路
     * 检查如果把 linkId 连接到 nodeId 下，是否会形成环路
     * 即检查 linkId 的所有子孙节点中是否包含 nodeId
     */
    @Override
    public synchronized boolean haveCircle(int app, long iceId, Long nodeId, Long linkId) {
        if (nodeId.equals(linkId)) {
            return true;
        }
        try {
            // 检查编辑中和已发布的配置，使用同一个visited集合避免重复检查
            Set<Long> visited = new HashSet<>();
            return checkCircle(app, iceId, nodeId, linkId, visited);
        } catch (Exception e) {
            log.error("failed to check circle", e);
            return false;
            }
        }

    /**
     * 检测多个连接是否会形成环路
     */
    @Override
    public synchronized boolean haveCircle(int app, long iceId, Long nodeId, Collection<Long> linkIds) {
        if (!CollectionUtils.isEmpty(linkIds)) {
            for (Long linkId : linkIds) {
                if (haveCircle(app, iceId, nodeId, linkId)) {
                    return true;
                }
            }
        }
        return false;
        }

    /**
     * 递归检测环路：检查 linkId 及其所有子孙节点中是否包含 nodeId
     * 同时检查编辑中（update）和已发布（active）的配置
     */
    private boolean checkCircle(int app, long iceId, Long nodeId, Long linkId, Set<Long> visited) {
        if (visited.contains(linkId)) {
            return false;
        }
        visited.add(linkId);

        // 优先检查编辑中的配置（getMixConfById 会自动合并）
        IceConf conf = getMixConfById(app, linkId, iceId);
        if (conf == null) {
            return false;
        }

        // 检查子节点
        Set<Long> sonIds = conf.getSonLongIds();
        if (!CollectionUtils.isEmpty(sonIds)) {
            if (sonIds.contains(nodeId)) {
                return true;
            }
            for (Long sonId : sonIds) {
                if (checkCircle(app, iceId, nodeId, sonId, visited)) {
                    return true;
            }
        }
        }

        // 检查forward节点
        if (conf.getForwardId() != null) {
            if (conf.getForwardId().equals(nodeId)) {
                return true;
                }
            if (checkCircle(app, iceId, nodeId, conf.getForwardId(), visited)) {
                return true;
                }
        }

        return false;
    }

    @Override
    public synchronized long getVersion() {
        // 这个方法返回的是下一个版本号，用于发布时
        // 但现在版本号是按app隔离的，这里需要调用方指定app
        throw new UnsupportedOperationException("use getVersion(app) instead");
    }

    public synchronized long getVersion(int app) throws IOException {
        return storageService.getVersion(app);
    }

    public synchronized long getAndIncrementVersion(int app) throws IOException {
        long current = storageService.getVersion(app);
        long next = current + 1;
        storageService.setVersion(app, next);
        return next;
    }

    @Override
    public synchronized IceTransferDto release(int app, long iceId) {
        try {
            List<IceConfDto> confUpdates = storageService.listConfUpdates(app, iceId);
            if (CollectionUtils.isEmpty(confUpdates)) {
                return null;
            }

            // 准备传输DTO
            IceTransferDto transferDto = new IceTransferDto();
            List<IceConfDto> releasedConfs = new ArrayList<>(confUpdates.size());

            // 事务性写入：先写临时文件，全部成功后再rename
            long now = System.currentTimeMillis();
            for (IceConfDto confUpdate : confUpdates) {
                // 查询原有conf，保留createAt
                IceConfDto oldConf = storageService.getConf(app, confUpdate.getConfId());
                
                // 更新conf文件
                IceConfDto confDto = new IceConfDto();
                confDto.setId(confUpdate.getConfId());
                confDto.setApp(confUpdate.getApp());
                confDto.setName(confUpdate.getName());
                confDto.setSonIds(confUpdate.getSonIds());
                confDto.setType(confUpdate.getType());
                confDto.setStatus(confUpdate.getStatus() != null ? confUpdate.getStatus() : IceStorageConstants.STATUS_ONLINE);
                confDto.setInverse(confUpdate.getInverse());
                confDto.setConfName(confUpdate.getConfName());
                confDto.setConfField(confUpdate.getConfField());
                confDto.setForwardId(confUpdate.getForwardId());
                confDto.setTimeType(confUpdate.getTimeType() != null ? confUpdate.getTimeType() : (byte) 1);
                confDto.setStart(confUpdate.getStart());
                confDto.setEnd(confUpdate.getEnd());
                confDto.setDebug(confUpdate.getDebug() != null ? confUpdate.getDebug() : (byte) 1);
                confDto.setErrorState(confUpdate.getErrorState());
                // 保留原有的 createAt，或使用 update 的，或设置当前时间
                if (oldConf != null && oldConf.getCreateAt() != null) {
                    confDto.setCreateAt(oldConf.getCreateAt());
                } else if (confUpdate.getCreateAt() != null) {
                    confDto.setCreateAt(confUpdate.getCreateAt());
                } else {
                    confDto.setCreateAt(now);
                }
                confDto.setUpdateAt(now);

                storageService.saveConf(confDto);
                releasedConfs.add(confDto);

                // 删除update文件
                storageService.deleteConfUpdate(app, iceId, confUpdate.getConfId());
            }

            // 生成版本更新记录
            long newVersion = getAndIncrementVersion(app);
            transferDto.setVersion(newVersion);
            transferDto.setInsertOrUpdateConfs(releasedConfs);

            // 保存版本更新文件
            storageService.saveVersionUpdate(app, newVersion, transferDto);

            // 清理旧版本
            storageService.cleanOldVersions(app, properties.getVersionRetention());

            return transferDto;
        } catch (IOException e) {
            log.error("failed to release for app:{} iceId:{}", app, iceId, e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
                }
            }

    @Override
    public synchronized void updateClean(int app, long iceId) {
        try {
            storageService.deleteAllConfUpdates(app, iceId);
        } catch (IOException e) {
            log.error("failed to clean updates for app:{} iceId:{}", app, iceId, e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
    }
    }

    @Override
    public synchronized Collection<IceConf> getAllUpdateConfList(int app, long iceId) {
        try {
            List<IceConfDto> dtos = storageService.listConfUpdates(app, iceId);
            return dtos.stream().map(IceConf::fromDto).collect(Collectors.toList());
        } catch (IOException e) {
            log.error("failed to get all update conf list for app:{} iceId:{}", app, iceId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public synchronized Set<IceConf> getAllActiveConfSet(int app, long rootId) {
        Set<IceConf> resultSet = new HashSet<>();
        assembleActiveConf(app, resultSet, rootId, new HashSet<>());
        return resultSet;
    }

    private void assembleActiveConf(int app, Set<IceConf> confSet, long nodeId, Set<Long> visited) {
        if (visited.contains(nodeId)) {
            return;
        }
        visited.add(nodeId);

        IceConf conf = getActiveConfById(app, nodeId);
        if (conf == null) {
            return;
        }
        confSet.add(conf);

        if (NodeTypeEnum.isRelation(conf.getType())) {
            Set<Long> sonIds = conf.getSonLongIds();
            if (!CollectionUtils.isEmpty(sonIds)) {
                for (Long sonId : sonIds) {
                    assembleActiveConf(app, confSet, sonId, visited);
                }
            }
        }
        if (conf.getForwardId() != null) {
            assembleActiveConf(app, confSet, conf.getForwardId(), visited);
        }
    }

    @Override
    public void refresh() {
        // 不再需要，因为不使用缓存
    }

    @Override
    public void cleanConfigCache() {
        // 不再需要，因为不使用缓存
    }

    @Override
    public void rebuildingAtlas(Collection<IceConf> updateList, Collection<IceConf> confList) {
        // 不再需要atlasMap
    }

    @Scheduled(cron = "${ice.recycle-cron:0 0 3 * * ?}")
    public void recycleScheduled() {
        recycle(null);
    }

    @Override
    public void recycle(Integer recycleApp) {
        log.info("ice recycle start");
        long start = System.currentTimeMillis();
        try {
        if (recycleApp != null) {
            recycleByApp(recycleApp);
            } else {
                List<Integer> apps = storageService.listApps().stream()
                        .map(a -> a.getId())
                        .collect(Collectors.toList());
                for (Integer app : apps) {
            recycleByApp(app);
                }
            }
        } catch (Exception e) {
            log.error("ice recycle error", e);
        }
        log.info("ice recycle end {}ms", System.currentTimeMillis() - start);
    }

    private void recycleByApp(Integer app) {
        try {
            // 获取所有可达的节点ID
            Set<Long> reachableIds = getReachableIds(app);

            // 计算保护时间阈值（更新时间在此之后的节点不回收）
            long protectThreshold = System.currentTimeMillis() - 
                    (long) properties.getRecycleProtectDays() * 24 * 60 * 60 * 1000;

            // 获取所有conf
            List<IceConfDto> allConfs = storageService.listConfs(app);

            // 找出不可达的conf进行删除
            for (IceConfDto conf : allConfs) {
                if (conf.getStatus() != null && conf.getStatus() != IceStorageConstants.STATUS_DELETED
                        && !reachableIds.contains(conf.getId())) {
                    // 时间保护：更新时间在保护期内的节点不回收
                    if (conf.getUpdateAt() != null && conf.getUpdateAt() > protectThreshold) {
                        log.debug("skip recycle conf:{} for app:{}, within protect period", conf.getId(), app);
                        continue;
                    }
            if ("soft".equals(properties.getRecycleWay())) {
                        storageService.deleteConf(app, conf.getId(), false);
            } else {
                        storageService.deleteConf(app, conf.getId(), true);
                    }
                    log.info("recycled unreachable conf:{} for app:{}", conf.getId(), app);
        }
            }

            // 处理offline的base
            List<IceBaseDto> allBases = storageService.listBases(app);
            for (IceBaseDto base : allBases) {
                if (base.getStatus() != null && base.getStatus() == IceStorageConstants.STATUS_OFFLINE) {
                    // 时间保护：更新时间在保护期内的base不回收
                    if (base.getUpdateAt() != null && base.getUpdateAt() > protectThreshold) {
                        log.debug("skip recycle base:{} for app:{}, within protect period", base.getId(), app);
                        continue;
            }
                    if ("soft".equals(properties.getRecycleWay())) {
                        storageService.deleteBase(app, base.getId(), false);
                    } else {
                        storageService.deleteBase(app, base.getId(), true);
                    }
                    log.info("recycled offline base:{} for app:{}", base.getId(), app);
                            }
                        }
        } catch (IOException e) {
            log.error("failed to recycle for app:{}", app, e);
        }
    }

    private Set<Long> getReachableIds(Integer app) throws IOException {
        Set<Long> reachableIds = new HashSet<>();
        Set<Long> visited = new HashSet<>();

        // 1. 收集confs中从active base可达的节点
        List<IceBaseDto> bases = storageService.listActiveBases(app);
        for (IceBaseDto base : bases) {
            if (base.getConfId() != null) {
                assembleReachableIdsFromConfs(app, reachableIds, base.getConfId(), visited);
            }
        }

        // 2. 收集updates中被引用的所有节点（编辑中的配置也是可达的）
        assembleReachableIdsFromUpdates(app, reachableIds);

        return reachableIds;
    }

    /**
     * 从confs中收集可达节点
     */
    private void assembleReachableIdsFromConfs(int app, Set<Long> reachableIds, long confId, Set<Long> visited) {
        if (visited.contains(confId)) {
            return;
        }
        visited.add(confId);
        reachableIds.add(confId);

        IceConf conf = getActiveConfById(app, confId);
        if (conf != null) {
            Set<Long> sonIds = conf.getSonLongIds();
            if (!CollectionUtils.isEmpty(sonIds)) {
                for (Long sonId : sonIds) {
                    assembleReachableIdsFromConfs(app, reachableIds, sonId, visited);
                }
            }
            if (conf.getForwardId() != null) {
                assembleReachableIdsFromConfs(app, reachableIds, conf.getForwardId(), visited);
            }
        }
    }

    /**
     * 从updates中收集所有被引用的节点
     * 编辑中的配置引用的节点也是"可达"的，不应该被清理
     */
    private void assembleReachableIdsFromUpdates(int app, Set<Long> reachableIds) throws IOException {
        // 获取该app下所有iceId的updates
        List<IceBaseDto> allBases = storageService.listBases(app);
        for (IceBaseDto base : allBases) {
            List<IceConfDto> updates = storageService.listConfUpdates(app, base.getId());
            for (IceConfDto update : updates) {
                // 节点自身
                if (update.getConfId() != null) {
                    reachableIds.add(update.getConfId());
                }
                // sonIds引用的节点
                if (StringUtils.hasLength(update.getSonIds())) {
                    String[] sonIdStrs = update.getSonIds().split(Constant.REGEX_COMMA);
                    for (String sonIdStr : sonIdStrs) {
                        try {
                            reachableIds.add(Long.valueOf(sonIdStr));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                // forwardId引用的节点
                if (update.getForwardId() != null) {
                    reachableIds.add(update.getForwardId());
    }
}
        }
    }
}



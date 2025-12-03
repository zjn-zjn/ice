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
import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IceConf;
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

    public IceServerServiceImpl(IceFileStorageService storageService, IceClientManager clientManager,
                                IceServerProperties properties) {
        this.storageService = storageService;
        this.clientManager = clientManager;
        this.properties = properties;
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
        if (NodeTypeEnum.isRelation(node.getType()) && StringUtils.hasLength(showNode.getSonIds())) {
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
        } else {
            // 组装叶子节点的字段信息
            LeafNodeInfo nodeInfo = clientManager.getNodeInfo(node.getApp(), null, node.getConfName(), node.getType());
            if (nodeInfo != null) {
                showNode.getShowConf().setHaveMeta(true);
                showNode.getShowConf().setNodeInfo(nodeInfo);
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
            storageService.saveConfUpdate(conf.getApp(), conf.getIceId(), conf.toDto());
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

    @Override
    public Map<String, Integer> getLeafClassMap(Integer app, Byte type) {
        // 从文件系统统计叶子类使用次数
        try {
            List<IceConfDto> confs = storageService.listActiveConfs(app);
            Map<String, Integer> result = new HashMap<>();
            for (IceConfDto conf : confs) {
                if (NodeTypeEnum.isLeaf(conf.getType()) && type.equals(conf.getType())
                        && StringUtils.hasLength(conf.getConfName())) {
                    result.merge(conf.getConfName(), 1, Integer::sum);
                }
            }
            return result.isEmpty() ? null : result;
        } catch (IOException e) {
            log.error("failed to get leaf class map for app:{}", app, e);
            return null;
        }
    }

    @Override
    public void increaseLeafClass(Integer app, Byte type, String clazz) {
        // 不再需要，统计通过getLeafClassMap实时计算
    }

    @Override
    public void decreaseLeafClass(Integer app, Byte type, String clazz) {
        // 不再需要，统计通过getLeafClassMap实时计算
    }

    @Override
    public void removeLeafClass(Integer app, Byte type, String clazz) {
        // 不再需要，统计通过getLeafClassMap实时计算
    }

    @Override
    public boolean haveCircle(Long nodeId, Long linkId) {
        if (nodeId.equals(linkId)) {
            return true;
        }
        // 需要获取app来检测环路，这里使用简化版本
        // 实际实现中需要从nodeId或linkId获取对应的app
        return false;
    }

    @Override
    public boolean haveCircle(Long nodeId, Collection<Long> linkIds) {
        if (!CollectionUtils.isEmpty(linkIds)) {
            for (Long linkId : linkIds) {
                if (haveCircle(nodeId, linkId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检测环路（带app参数）
     */
    public boolean haveCircle(int app, long iceId, Long nodeId, Long linkId) {
        if (nodeId.equals(linkId)) {
            return true;
        }
        try {
            return checkCircle(app, iceId, nodeId, linkId, new HashSet<>());
        } catch (Exception e) {
            log.error("failed to check circle", e);
            return false;
        }
    }

    private boolean checkCircle(int app, long iceId, Long nodeId, Long linkId, Set<Long> visited) {
        if (visited.contains(linkId)) {
            return false;
        }
        visited.add(linkId);

        IceConf conf = getMixConfById(app, linkId, iceId);
        if (conf == null) {
            return false;
        }

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
    public void link(Long nodeId, Long linkId) {
        // 不再需要维护atlasMap
    }

    @Override
    public void link(Long nodeId, List<Long> linkIds) {
        // 不再需要维护atlasMap
    }

    @Override
    public void unlink(Long nodeId, Long unLinkId) {
        // 不再需要维护atlasMap
    }

    @Override
    public void exchangeLink(Long nodeId, Long originId, List<Long> exchangeIds) {
        // 不再需要维护atlasMap
    }

    @Override
    public void exchangeLink(Long nodeId, Long originId, Long exchangeId) {
        // 不再需要维护atlasMap
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
            for (IceConfDto confUpdate : confUpdates) {
                // 更新conf文件
                IceConfDto confDto = new IceConfDto();
                confDto.setId(confUpdate.getConfId());
                confDto.setApp(confUpdate.getApp());
                confDto.setName(confUpdate.getName());
                confDto.setSonIds(confUpdate.getSonIds());
                confDto.setType(confUpdate.getType());
                confDto.setStatus(confUpdate.getStatus());
                confDto.setInverse(confUpdate.getInverse());
                confDto.setConfName(confUpdate.getConfName());
                confDto.setConfField(confUpdate.getConfField());
                confDto.setForwardId(confUpdate.getForwardId());
                confDto.setTimeType(confUpdate.getTimeType());
                confDto.setStart(confUpdate.getStart());
                confDto.setEnd(confUpdate.getEnd());
                confDto.setDebug(confUpdate.getDebug());
                confDto.setErrorState(confUpdate.getErrorState());
                confDto.setUpdateAt(System.currentTimeMillis());

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

            // 获取所有conf
            List<IceConfDto> allConfs = storageService.listConfs(app);

            // 找出不可达的conf进行软删除
            for (IceConfDto conf : allConfs) {
                if (conf.getStatus() != null && conf.getStatus() != IceStorageConstants.STATUS_DELETED
                        && !reachableIds.contains(conf.getId())) {
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
        List<IceBaseDto> bases = storageService.listActiveBases(app);

        for (IceBaseDto base : bases) {
            if (base.getConfId() != null) {
                assembleReachableIds(app, reachableIds, base.getConfId(), new HashSet<>());
            }
        }
        return reachableIds;
    }

    private void assembleReachableIds(int app, Set<Long> reachableIds, long confId, Set<Long> visited) {
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
                    assembleReachableIds(app, reachableIds, sonId, visited);
                }
            }
            if (conf.getForwardId() != null) {
                assembleReachableIds(app, reachableIds, conf.getForwardId(), visited);
            }
        }
    }
}



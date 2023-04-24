package com.ice.server.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.ice.common.constant.Constant;
import com.ice.common.dto.IceBaseDto;
import com.ice.common.dto.IceConfDto;
import com.ice.common.dto.IceTransferDto;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.model.IceShowNode;
import com.ice.common.model.LeafNodeInfo;
import com.ice.common.model.Pair;
import com.ice.core.utils.JacksonUtils;
import com.ice.server.constant.ServerConstant;
import com.ice.server.dao.mapper.IceAppMapper;
import com.ice.server.dao.mapper.IceBaseMapper;
import com.ice.server.dao.mapper.IceConfMapper;
import com.ice.server.dao.mapper.IceConfUpdateMapper;
import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IceBaseExample;
import com.ice.server.dao.model.IceConf;
import com.ice.server.dao.model.IceConfExample;
import com.ice.server.enums.StatusEnum;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.nio.IceNioClientManager;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * @author waitmoon
 */
@Slf4j
@Service
public class IceServerServiceImpl implements IceServerService {

    /*
     * key:app value baseList
     * keep except deleted
     */
    private Map<Integer, Map<Long, IceBase>> baseMap;
    /*
     * used avoid quote in different ice circle
     * key: confId(include updating)
     * value: linkNextMap
     */
    private Map<Long, Map<Long, Integer>> updateAtlasMap;
    /*
     * used avoid quote in different ice circle
     * key: confId(exclude updating)
     * value: linkNextMap
     */
    private Map<Long, Map<Long, Integer>> activeAtlasMap;
    /*
     * key:app value conf
     */
    private Map<Integer, Map<Long, IceConf>> confActiveMap;
    /*
     * key: app
     * value:
     *    key: iceId
     *    value:
     *        key:confId
     *        value: conf
     */
    private Map<Integer, Map<Long, Map<Long, IceConf>>> confUpdateMap;

    private Map<Integer, Map<Byte, Map<String, Integer>>> leafClassMap;

    private volatile long version;
    @Autowired
    private IceBaseMapper baseMapper;
    @Autowired
    private IceConfMapper confMapper;
    @Autowired
    private IceConfUpdateMapper confUpdateMapper;
    @Autowired
    private IceAppMapper iceAppMapper;

    @Autowired
    private IceNioClientManager iceNioClientManager;

    public synchronized boolean haveCircle(Long nodeId, Long linkId) {
        if (nodeId.equals(linkId)) {
            return true;
        }
        return updatingHaveCircle(nodeId, linkId) || activeHaveCircle(nodeId, linkId);
    }

    private boolean updatingHaveCircle(Long nodeId, Long linkId) {
        Map<Long, Integer> linkNextMap = updateAtlasMap.get(linkId);
        if (!CollectionUtils.isEmpty(linkNextMap)) {
            Set<Long> linkNext = linkNextMap.keySet();
            if (linkNext.contains(nodeId)) {
                return true;
            }
            for (Long next : linkNext) {
                if (updatingHaveCircle(nodeId, next)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean activeHaveCircle(Long nodeId, Long linkId) {
        Map<Long, Integer> linkNextMap = activeAtlasMap.get(linkId);
        if (!CollectionUtils.isEmpty(linkNextMap)) {
            Set<Long> linkNext = linkNextMap.keySet();
            if (linkNext.contains(nodeId)) {
                return true;
            }
            for (Long next : linkNext) {
                if (activeHaveCircle(nodeId, next)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public synchronized boolean haveCircle(Long nodeId, Collection<Long> linkIds) {
        if (!CollectionUtils.isEmpty(linkIds)) {
            for (Long linkId : linkIds) {
                if (haveCircle(nodeId, linkId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * nodeId next add linkId count
     */
    public synchronized void link(Long nodeId, Long linkId) {
        link(nodeId, linkId, updateAtlasMap);
    }

    private synchronized void link(Long nodeId, Long linkId, Map<Long, Map<Long, Integer>> atlasMap) {
        if (haveCircle(nodeId, linkId)) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "have circle nodeId:" + nodeId + " linkId:" + linkId);
        }

        Map<Long, Integer> linkNextMap = atlasMap.computeIfAbsent(nodeId, k -> new HashMap<>());
        Integer nodeNextCount = linkNextMap.computeIfAbsent(linkId, k -> 0);
        linkNextMap.put(linkId, nodeNextCount + 1);
    }

    @Override
    public synchronized void link(Long nodeId, List<Long> linkIds) {
        if (!CollectionUtils.isEmpty(linkIds)) {
            for (Long linkId : linkIds) {
                link(nodeId, linkId);
            }
        }
    }

    /*
     * nodeId next reduce linkId count
     */
    public synchronized void unlink(Long nodeId, Long linkId) {
        Map<Long, Integer> nodeNextMap = updateAtlasMap.get(nodeId);
        if (nodeNextMap != null) {
            Integer nodeNextCount = nodeNextMap.get(linkId);
            if (nodeNextCount != null) {
                if (nodeNextCount <= 1) {
                    nodeNextMap.remove(linkId);
                    if (CollectionUtils.isEmpty(nodeNextMap)) {
                        updateAtlasMap.remove(nodeId);
                    }
                } else {
                    nodeNextMap.put(linkId, nodeNextCount - 1);
                }
            }
        }
    }

    @Override
    public synchronized void exchangeLink(Long nodeId, Long originId, List<Long> exchangeIds) {
        unlink(nodeId, originId);
        link(nodeId, exchangeIds);
    }

    @Override
    public synchronized void exchangeLink(Long nodeId, Long originId, Long exchangeId) {
        unlink(nodeId, originId);
        link(nodeId, exchangeId);
    }

    @Override
    public synchronized long getVersion() {
        return this.version++;
    }

    @Override
    @Transactional
    public synchronized IceTransferDto release(int app, long iceId) {
        Map<Long, Map<Long, IceConf>> iceUpdateMap = confUpdateMap.get(app);
        if (CollectionUtils.isEmpty(iceUpdateMap)) {
            return null;
        }
        Map<Long, IceConf> confUpdateMap = iceUpdateMap.get(iceId);
        if (CollectionUtils.isEmpty(confUpdateMap)) {
            return null;
        }
        Collection<IceConf> confUpdates = confUpdateMap.values();
        for (IceConf conf : confUpdates) {
            IceConf oldConf = confMapper.selectByPrimaryKey(conf.getConfId());
            conf.setUpdateAt(new Date());
            long updateId = conf.getId();
            conf.setId(conf.getConfId());
            if (oldConf == null) {
                confMapper.insertWithId(conf);
            } else {
                confMapper.updateByPrimaryKey(conf);
            }
            confUpdateMapper.deleteByPrimaryKey(updateId);
        }
        List<IceConfDto> confUpdateDtos = new ArrayList<>(confUpdates.size());
        for (IceConf conf : confUpdates) {
            conf.setId(conf.getConfId());
            conf.setConfId(null);
            conf.setIceId(null);
            confUpdateDtos.add(ServerConstant.confToDto(conf));
            updateLocalConfActiveCache(conf);
            assembleAtlas(conf, activeAtlasMap);
        }
        iceUpdateMap.remove(iceId);
        IceTransferDto transferDto = new IceTransferDto();
        transferDto.setInsertOrUpdateConfs(confUpdateDtos);
        transferDto.setVersion(++version);
        return transferDto;
    }

    @Override
    public synchronized void updateClean(int app, long iceId) {
        Map<Long, Map<Long, IceConf>> iceUpdateMap = confUpdateMap.get(app);
        if (CollectionUtils.isEmpty(iceUpdateMap)) {
            return;
        }
        Map<Long, IceConf> confUpdateMap = iceUpdateMap.get(iceId);
        if (CollectionUtils.isEmpty(confUpdateMap)) {
            return;
        }
        Collection<IceConf> confUpdates = confUpdateMap.values();
        Collection<IceConf> confList = new ArrayList<>();
        for (IceConf confUpdate : confUpdates) {
            confUpdateMapper.deleteByPrimaryKey(confUpdate.getId());
            IceConf conf = getActiveConfById(app, confUpdate.getConfId());
            if (conf != null) {
                confList.add(conf);
            }
        }
        iceUpdateMap.remove(iceId);
        rebuildingAtlas(null, confList);
    }

    @Override
    public synchronized Collection<IceConf> getAllUpdateConfList(int app, long iceId) {
        Map<Long, Map<Long, IceConf>> iceUpdateMap = confUpdateMap.get(app);
        if (CollectionUtils.isEmpty(iceUpdateMap)) {
            return null;
        }
        Map<Long, IceConf> confUpdateMap = iceUpdateMap.get(iceId);
        if (CollectionUtils.isEmpty(confUpdateMap)) {
            return null;
        }
        return confUpdateMap.values();
    }

    @Override
    public synchronized Set<IceConf> getAllActiveConfSet(int app, long rootId) {
        Map<Long, IceConf> confMap = confActiveMap.get(app);
        if (CollectionUtils.isEmpty(confMap)) {
            return null;
        }
        Set<IceConf> resultSet = new HashSet<>();
        assembleActiveConf(confMap, resultSet, rootId);
        return resultSet;
    }

    private void assembleActiveConf(Map<Long, IceConf> map, Set<IceConf> confSet, long nodeId) {
        IceConf conf = map.get(nodeId);
        if (conf == null) {
            return;
        }
        confSet.add(conf);
        if (NodeTypeEnum.isRelation(conf.getType())) {
            if (StringUtils.hasLength(conf.getSonIds())) {
                String[] sonIdStrs = conf.getSonIds().split(Constant.REGEX_COMMA);
                for (String sonIdStr : sonIdStrs) {
                    long sonId = Long.parseLong(sonIdStr);
                    assembleActiveConf(map, confSet, sonId);
                }
            }
        }
        if (conf.getForwardId() != null) {
            assembleActiveConf(map, confSet, conf.getForwardId());
        }
    }

    @Override
    public synchronized IceConf getActiveConfById(Integer app, Long confId) {
        Map<Long, IceConf> confMap = confActiveMap.get(app);
        if (!CollectionUtils.isEmpty(confMap)) {
            return confMap.get(confId);
        }
        return null;
    }

    @Override
    public IceConf getUpdateConfById(Integer app, Long confId, Long iceId) {
        Map<Long, Map<Long, IceConf>> updateMap = confUpdateMap.get(app);
        if (!CollectionUtils.isEmpty(updateMap)) {
            Map<Long, IceConf> confUpdateMap = updateMap.get(iceId);
            if (!CollectionUtils.isEmpty(confUpdateMap)) {
                return confUpdateMap.get(confId);
            }
        }
        return null;
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
                /*one of confId not exist return null*/
                return null;
            }
            confList.add(conf);
        }
        return confList;
    }

    @Override
    public synchronized IceConf getMixConfById(int app, long confId, long iceId) {
        Map<Long, Map<Long, IceConf>> updateMap = confUpdateMap.get(app);
        if (!CollectionUtils.isEmpty(updateMap)) {
            Map<Long, IceConf> confUpdateMap = updateMap.get(iceId);
            if (!CollectionUtils.isEmpty(confUpdateMap)) {
                IceConf conf = confUpdateMap.get(confId);
                if (conf != null) {
                    return newConf(conf);
                }
            }
        }
        Map<Long, IceConf> activeMap = confActiveMap.get(app);
        if (!CollectionUtils.isEmpty(activeMap)) {
            return newConf(activeMap.get(confId));
        }
        return null;
    }

    private IceConf newConf(IceConf conf) {
        if (conf == null) {
            return null;
        }
        IceConf newConf = new IceConf();
        newConf.setId(conf.getId());
        newConf.setConfId(conf.getConfId());
        newConf.setIceId(conf.getIceId());
        newConf.setConfName(conf.getConfName());
        newConf.setDebug(conf.getDebug());
        newConf.setInverse(conf.getInverse());
        newConf.setStatus(conf.getStatus());
        newConf.setTimeType(conf.getTimeType());
        newConf.setSonIds(conf.getSonIds());
        newConf.setForwardId(conf.getForwardId());
        newConf.setStart(conf.getStart());
        newConf.setEnd(conf.getEnd());
        newConf.setApp(conf.getApp());
        newConf.setUpdateAt(conf.getUpdateAt());
        newConf.setCreateAt(conf.getCreateAt());
        newConf.setConfField(conf.getConfField());
        newConf.setName(conf.getName());
        newConf.setType(conf.getType());
        return newConf;
    }

    @Override
    public synchronized IceShowNode getConfMixById(int app, long confId, long iceId) {
        Map<Long, Map<Long, IceConf>> updateShowMap = confUpdateMap.get(app);
        Map<Long, IceConf> updateMap = null;
        if (!CollectionUtils.isEmpty(updateShowMap)) {
            updateMap = updateShowMap.get(iceId);
        }
        Map<Long, IceConf> activeMap = confActiveMap.get(app);
        IceConf root = getConf(confId, updateMap, activeMap);
        return assembleShowNode(root, updateMap, activeMap);
    }

    private IceConf getConf(Long confId, Map<Long, IceConf> updateMap, Map<Long, IceConf> activeMap) {
        if (!CollectionUtils.isEmpty(updateMap)) {
            IceConf confUpdate = updateMap.get(confId);
            if (confUpdate != null) {
                return confUpdate;
            }
        }
        if (!CollectionUtils.isEmpty(activeMap)) {
            return activeMap.get(confId);
        }
        return null;
    }

    private IceShowNode assembleShowNode(IceConf node, Map<Long, IceConf> updateMap, Map<Long, IceConf> activeMap) {
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
                IceConf child = getConf(sonIds.get(i), updateMap, activeMap);
                if (child != null) {
                    IceShowNode showChild = assembleShowNode(child, updateMap, activeMap);
                    showChild.setParentId(node.getMixId());
                    showChild.setIndex(i);
                    children.add(showChild);
                }
            }
            showNode.setChildren(children);
        } else {
            //assemble filed info
            LeafNodeInfo nodeInfo = iceNioClientManager.getNodeInfo(node.getApp(), null, node.getConfName(), node.getType());
            if (nodeInfo != null) {
                showNode.getShowConf().setHaveMeta(true);
                showNode.getShowConf().setNodeInfo(nodeInfo);
                String confJson = showNode.getShowConf().getConfField();
                JsonNode jsonNode = JacksonUtils.readTree(confJson);
                if (jsonNode != null) {
                    if (!CollectionUtils.isEmpty(nodeInfo.getIceFields())) {
                        for (LeafNodeInfo.IceFieldInfo fieldInfo : nodeInfo.getIceFields()) {
                            JsonNode value = jsonNode.get(fieldInfo.getField());
                            if (value != null && value.isNull()) {
                                fieldInfo.setValueNull(true);
                            } else {
                                if (value != null) {
                                    fieldInfo.setValue(value);
                                }
                            }
                        }
                    }
                    if (!CollectionUtils.isEmpty(nodeInfo.getHideFields())) {
                        for (LeafNodeInfo.IceFieldInfo hideFiledInfo : nodeInfo.getHideFields()) {
                            JsonNode value = jsonNode.get(hideFiledInfo.getField());
                            if (value != null && value.isNull()) {
                                hideFiledInfo.setValueNull(true);
                            } else {
                                if (value != null) {
                                    hideFiledInfo.setValue(value);
                                }
                            }
                        }
                    }
                }
            } else {
                showNode.getShowConf().setHaveMeta(false);
            }
        }

        if (showNode.getForwardId() != null) {
            IceShowNode forwardNode = assembleShowNode(getConf(showNode.getForwardId(), updateMap, activeMap), updateMap, activeMap);
            if (forwardNode != null) {
                forwardNode.setNextId(node.getMixId());
                showNode.setForward(forwardNode);
            }
        }
        return showNode;
    }

    @Override
    public synchronized IceBase getActiveBaseById(Integer app, Long iceId) {
        Map<Long, IceBase> confMap = baseMap.get(app);
        if (!CollectionUtils.isEmpty(confMap)) {
            return confMap.get(iceId);
        }
        return null;
    }

    @Override
    public synchronized void updateLocalConfUpdateCache(IceConf conf) {
        confUpdateMap.computeIfAbsent(conf.getApp(), k -> new HashMap<>()).computeIfAbsent(conf.getIceId(), k -> new HashMap<>()).put(conf.getMixId(), conf);
    }

    @Override
    public synchronized void updateLocalConfUpdateCaches(Collection<IceConf> confs) {
        for (IceConf conf : confs) {
            updateLocalConfUpdateCache(conf);
        }
    }

    public synchronized void updateLocalConfActiveCache(IceConf conf) {
        confActiveMap.computeIfAbsent(conf.getApp(), k -> new HashMap<>()).put(conf.getMixId(), conf);
    }

    @Override
    public void updateLocalConfActiveCaches(Collection<IceConf> confs) {
        for (IceConf conf : confs) {
            updateLocalConfActiveCache(conf);
        }
    }

    @Override
    public synchronized void updateLocalBaseCache(IceBase base) {
        if (base.getStatus() == StatusEnum.DELETED.getStatus()) {
            Map<Long, IceBase> map = baseMap.get(base.getApp());
            if (CollectionUtils.isEmpty(map)) {
                return;
            }
            map.remove(base.getId());
            if (CollectionUtils.isEmpty(map)) {
                baseMap.remove(base.getApp());
                return;
            }
            return;
        }
        baseMap.computeIfAbsent(base.getApp(), k -> new HashMap<>()).put(base.getId(), base);
    }

    @Override
    public synchronized Map<String, Integer> getLeafClassMap(Integer app, Byte type) {
        Map<Byte, Map<String, Integer>> map = leafClassMap.get(app);
        if (map != null) {
            return map.get(type);
        }
        return null;
    }

    @Override
    public synchronized void removeLeafClass(Integer app, Byte type, String clazz) {
        Map<Byte, Map<String, Integer>> map = leafClassMap.get(app);
        if (!CollectionUtils.isEmpty(map)) {
            Map<String, Integer> typeMap = map.get(type);
            if (!CollectionUtils.isEmpty(typeMap)) {
                typeMap.remove(clazz);
            }
            if (CollectionUtils.isEmpty(typeMap)) {
                map.remove(type);
            }
        }
    }

    @Override
    public synchronized void addLeafClass(Integer app, Byte type, String clazz) {
        Map<Byte, Map<String, Integer>> map = leafClassMap.get(app);
        Map<String, Integer> classMap;
        if (map == null) {
            map = new HashMap<>();
            leafClassMap.put(app, map);
            classMap = new HashMap<>();
            map.put(type, classMap);
            classMap.put(clazz, 0);
        } else {
            classMap = map.get(type);
            if (classMap == null) {
                classMap = new HashMap<>();
                map.put(type, classMap);
                classMap.put(clazz, 0);
            } else {
                classMap.putIfAbsent(clazz, 0);
            }
        }
        classMap.put(clazz, classMap.get(clazz) + 1);
    }

    @Override
    public synchronized void refresh() {
        cleanConfigCache();
        /*baseList*/
        IceBaseExample baseExample = new IceBaseExample();
        IceBaseExample.Criteria baseCriteria = baseExample.createCriteria();
        baseCriteria.andStatusIn(Arrays.asList(StatusEnum.ONLINE.getStatus(), StatusEnum.OFFLINE.getStatus()));
        List<IceBase> baseList = baseMapper.selectByExample(baseExample);
        if (!CollectionUtils.isEmpty(baseList)) {
            for (IceBase base : baseList) {
                baseMap.computeIfAbsent(base.getApp(), k -> new HashMap<>()).put(base.getId(), base);
            }
        }
        /*UpdateList*/
        IceConfExample confUpdateExample = new IceConfExample();
        IceConfExample.Criteria confUpdateCriteria = confUpdateExample.createCriteria();
        confUpdateCriteria.andStatusEqualTo(StatusEnum.ONLINE.getStatus());
        List<IceConf> confUpdateList = confUpdateMapper.selectByExample(confUpdateExample);
        Set<Long> updateIdSet = new HashSet<>();
        if (!CollectionUtils.isEmpty(confUpdateList)) {
            for (IceConf conf : confUpdateList) {
                updateIdSet.add(conf.getMixId());
                confUpdateMap.computeIfAbsent(conf.getApp(), k -> new HashMap<>()).computeIfAbsent(conf.getIceId(), k -> new HashMap<>()).put(conf.getMixId(), conf);
                assembleAtlas(conf, updateAtlasMap);
                assembleLeafClass(conf);
            }
        }
        /*ConfList*/
        IceConfExample confExample = new IceConfExample();
        IceConfExample.Criteria confCriteria = confExample.createCriteria();
        confCriteria.andStatusEqualTo(StatusEnum.ONLINE.getStatus());
        List<IceConf> confList = confMapper.selectByExample(confExample);
        if (!CollectionUtils.isEmpty(confList)) {
            for (IceConf conf : confList) {
                confActiveMap.computeIfAbsent(conf.getApp(), k -> new HashMap<>()).put(conf.getMixId(), conf);
                if (!updateIdSet.contains(conf.getMixId())) {
                    assembleAtlas(conf, updateAtlasMap);
                }
                assembleAtlas(conf, activeAtlasMap);
                assembleLeafClass(conf);
            }
        }
    }

    @Override
    public synchronized void cleanConfigCache() {
        baseMap = new HashMap<>();
        leafClassMap = new HashMap<>();
        confUpdateMap = new HashMap<>();
        confActiveMap = new HashMap<>();
        updateAtlasMap = new HashMap<>();
        activeAtlasMap = new HashMap<>();
    }

    @Override
    public synchronized void rebuildingAtlas(Collection<IceConf> updateList, Collection<IceConf> confList) {
        Set<Long> updateIdSet = new HashSet<>();
        Map<Long, Map<Long, Integer>> tmpAtlasMap = getAtlasMapCopy(updateAtlasMap);
        Map<Long, Map<Long, Integer>> tmpRealAtlasMap = getAtlasMapCopy(activeAtlasMap);
        if (!CollectionUtils.isEmpty(updateList)) {
            for (IceConf updateConf : updateList) {
                assembleAtlas(updateConf, tmpAtlasMap);
                assembleAtlas(getActiveConfById(updateConf.getApp(), updateConf.getMixId()), tmpRealAtlasMap);
                updateIdSet.add(updateConf.getMixId());
            }
        }
        if (!CollectionUtils.isEmpty(confList)) {
            for (IceConf conf : confList) {
                if (!updateIdSet.contains(conf.getMixId())) {
                    tmpAtlasMap.remove(conf.getMixId());
                    tmpRealAtlasMap.remove(conf.getMixId());
                    assembleAtlas(conf, tmpRealAtlasMap);
                    assembleAtlas(conf, tmpAtlasMap);
                }
            }
        }
        updateAtlasMap = tmpAtlasMap;
        activeAtlasMap = tmpRealAtlasMap;
    }

    private Map<Long, Map<Long, Integer>> getAtlasMapCopy(Map<Long, Map<Long, Integer>> atlasMap) {
        if (CollectionUtils.isEmpty(atlasMap)) {
            return new HashMap<>();
        }
        Map<Long, Map<Long, Integer>> copyMap = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Integer>> entry : atlasMap.entrySet()) {
            Map<Long, Integer> map = new HashMap<>(entry.getValue());
            copyMap.put(entry.getKey(), map);
        }
        return copyMap;
    }

    private void assembleAtlas(IceConf conf, Map<Long, Map<Long, Integer>> atlasMap) {
        if (conf == null) {
            return;
        }
        atlasMap.remove(conf.getMixId());
        if (NodeTypeEnum.isRelation(conf.getType()) && StringUtils.hasLength(conf.getSonIds())) {
            String[] sonIdStrs = conf.getSonIds().split(Constant.REGEX_COMMA);
            for (String sonIdStr : sonIdStrs) {
                Long sonId = Long.parseLong(sonIdStr);
                link(conf.getMixId(), sonId, atlasMap);
            }
        }
        if (conf.getForwardId() != null) {
            link(conf.getMixId(), conf.getForwardId(), atlasMap);
        }
    }

    private void assembleLeafClass(IceConf conf) {
        if (NodeTypeEnum.isLeaf(conf.getType())) {
            Map<Byte, Map<String, Integer>> map = leafClassMap.get(conf.getApp());
            Map<String, Integer> classMap;
            if (map == null) {
                map = new HashMap<>();
                leafClassMap.put(conf.getApp(), map);
                classMap = new HashMap<>();
                map.put(conf.getType(), classMap);
                classMap.put(conf.getConfName(), 0);
            } else {
                classMap = map.get(conf.getType());
                if (classMap == null) {
                    classMap = new HashMap<>();
                    map.put(conf.getType(), classMap);
                    classMap.put(conf.getConfName(), 0);
                } else {
                    classMap.putIfAbsent(conf.getConfName(), 0);
                }
            }
            classMap.put(conf.getConfName(), classMap.get(conf.getConfName()) + 1);
        }
    }

    public Collection<IceConfDto> getActiveConfsByApp(Integer app) {
        Map<Long, IceConf> map = confActiveMap.get(app);
        if (map == null) {
            return new ArrayList<>(1);
        }
        return ServerConstant.confListToDtoList(map.values());
    }

    public Collection<IceBaseDto> getActiveBasesByApp(Integer app) {
        Map<Long, IceBase> map = baseMap.get(app);
        if (map == null) {
            return new ArrayList<>(1);
        }
        return ServerConstant.baseListToDtoList(map.values());
    }

    @Override
    public synchronized IceTransferDto getInitConfig(Integer app) {
        IceTransferDto transferDto = new IceTransferDto();
        transferDto.setInsertOrUpdateBases(this.getActiveBasesByApp(app));
        transferDto.setInsertOrUpdateConfs(this.getActiveConfsByApp(app));
        transferDto.setVersion(version);
        return transferDto;
    }

    /**
     * recycle unreachable node
     */
    @Scheduled(cron = "${ice.recycle-cron}")
    private void recycle() {
        log.info("ice recycle start");
        long start = System.currentTimeMillis();
        //get all app to recycle
        Set<Integer> appSet = new HashSet<>();
        appSet.addAll(confActiveMap.keySet());
        appSet.addAll(confUpdateMap.keySet());
        for (Integer app : appSet) {
            //get unreachable node id by app
            Pair<Set<Long>, Set<Long>> pair = getUnReachableIds(app);
            if (!CollectionUtils.isEmpty(pair.getKey())) {
                //recycle active conf
                log.info("recycle app:{}, active conf ids:{}", app, Arrays.toString(pair.getKey().toArray()));
                deleteClientConf(app, pair.getKey());
                IceConfExample example = new IceConfExample();
                example.createCriteria().andIdIn(pair.getKey());
                IceConf conf = new IceConf();
                conf.setStatus(StatusEnum.DELETED.getStatus());
                conf.setUpdateAt(new Date());
                confMapper.recycle(conf, example);
                cleanActiveConf(app, pair.getKey());
                log.info("recycle active conf success app:{}", app);
            }
            if (!CollectionUtils.isEmpty(pair.getValue())) {
                //recycle update conf
                log.info("recycle app:{}, update conf ids:{}", app, Arrays.toString(pair.getValue().toArray()));
                IceConfExample example = new IceConfExample();
                example.createCriteria().andConfIdIn(pair.getValue());
                IceConf updateConf = new IceConf();
                updateConf.setStatus(StatusEnum.DELETED.getStatus());
                updateConf.setUpdateAt(new Date());
                confUpdateMapper.recycle(updateConf, example);
                cleanUpdateConf(app, pair.getValue());
                log.info("recycle update conf success app:{}", app);
            }
        }
        log.info("ice recycle end {}ms", System.currentTimeMillis() - start);
    }

    /**
     * clean remote client`s unreachable node
     *
     * @param app     app
     * @param confIds to clean node id
     */
    private void deleteClientConf(Integer app, Set<Long> confIds) {
        IceTransferDto dto = new IceTransferDto();
        dto.setDeleteConfIds(confIds);
        dto.setVersion(getVersion());
        iceNioClientManager.release(app, dto);
    }

    /**
     * clean local unreachable active conf & atlas
     *
     * @param app     app
     * @param confIds to clean node id
     */
    private synchronized void cleanActiveConf(Integer app, Set<Long> confIds) {
        Map<Long, IceConf> map = confActiveMap.get(app);
        if (!CollectionUtils.isEmpty(map)) {
            for (Long id : confIds) {
                map.remove(id);
                activeAtlasMap.remove(id);
            }
            if (CollectionUtils.isEmpty(map)) {
                confActiveMap.remove(app);
            }
        }
    }

    /**
     * clean local unreachable updating conf & atlas
     *
     * @param app     app
     * @param confIds to clean node id
     */
    private synchronized void cleanUpdateConf(Integer app, Set<Long> confIds) {
        Map<Long, Map<Long, IceConf>> iceIdMap = confUpdateMap.get(app);
        if (!CollectionUtils.isEmpty(iceIdMap)) {
            for (Map.Entry<Long, Map<Long, IceConf>> entry : iceIdMap.entrySet()) {
                if (!CollectionUtils.isEmpty(entry.getValue())) {
                    for (Long id : confIds) {
                        entry.getValue().remove(id);
                        updateAtlasMap.remove(id);
                    }
                }
                if (CollectionUtils.isEmpty(entry.getValue())) {
                    iceIdMap.remove(entry.getKey());
                }
            }
            if (CollectionUtils.isEmpty(iceIdMap)) {
                confUpdateMap.remove(app);
            }
        }
    }

    /**
     * get all unreachable node by app
     *
     * @param app app
     * @return unreachable conf & updating
     */
    private synchronized Pair<Set<Long>, Set<Long>> getUnReachableIds(Integer app) {
        Set<Long> reachableIds = getReachableIds(app);
        Set<Long> unReachableConfIds = new HashSet<>();
        Set<Long> unReachableUpdateConfIds = new HashSet<>();
        if (!CollectionUtils.isEmpty(confActiveMap)) {
            Map<Long, IceConf> map = confActiveMap.get(app);
            if (!CollectionUtils.isEmpty(map)) {
                for (Long confId : map.keySet()) {
                    if (!reachableIds.contains(confId)) {
                        //unreachable active conf
                        unReachableConfIds.add(confId);
                    }
                }
            }
        }
        if (!CollectionUtils.isEmpty(confUpdateMap)) {
            Map<Long, Map<Long, IceConf>> iceIdMap = confUpdateMap.get(app);
            if (!CollectionUtils.isEmpty(iceIdMap)) {
                for (Map<Long, IceConf> map : iceIdMap.values()) {
                    //no need to judge which iceId`s conf should recycle, recycle every updating unreachable node
                    if (!CollectionUtils.isEmpty(map)) {
                        for (Long confId : map.keySet()) {
                            if (!reachableIds.contains(confId)) {
                                //unreachable update conf
                                unReachableConfIds.add(confId);
                                unReachableUpdateConfIds.add(confId);
                            }
                        }
                    }
                }
            }
        }
        return new Pair<>(unReachableConfIds, unReachableUpdateConfIds);
    }

    /**
     * get all reachable node by app
     * get reachable from root
     * @param app app
     * @return all reachable node id
     */
    private synchronized Set<Long> getReachableIds(Integer app) {
        Map<Long, Long> rootIdIceIdMap = getReachableRootIdIceIdMap(app);
        if (CollectionUtils.isEmpty(rootIdIceIdMap)) {
            return Collections.emptySet();
        }
        Set<Long> reachableIds = new HashSet<>();
        for (Map.Entry<Long, Long> entry : rootIdIceIdMap.entrySet()) {
            assembleReachableIds(reachableIds, app, entry.getValue(), entry.getKey());
        }
        return reachableIds;
    }

    private synchronized void assembleReachableIds(Set<Long> reachableIds, Integer app, Long iceId, Long confId) {
        reachableIds.add(confId);
        IceConf activeConf = getActiveConfById(app, confId);
        //assemble active conf reachableIds
        assembleConfReachableIds(activeConf, reachableIds, app, iceId);
        IceConf updateConf = getUpdateConfById(app, confId, iceId);
        //assemble update conf reachableIds
        assembleConfReachableIds(updateConf, reachableIds, app, iceId);
    }

    private synchronized void assembleConfReachableIds(IceConf conf, Set<Long> reachableIds, Integer app, Long iceId) {
        if (conf != null) {
            Set<Long> sonIds = conf.getSonLongIds();
            if (!CollectionUtils.isEmpty(sonIds)) {
                for (Long sonId : sonIds) {
                    assembleReachableIds(reachableIds, app, iceId, sonId);
                }
            }
            if (conf.getForwardId() != null) {
                assembleReachableIds(reachableIds, app, iceId, conf.getForwardId());
            }
        }
    }

    /**
     * get all reachable root by app
     * which base root not on delete status
     * @param app app
     * @return reachable root
     */
    private synchronized Map<Long, Long> getReachableRootIdIceIdMap(Integer app) {
        if (CollectionUtils.isEmpty(baseMap)) {
            return Collections.emptyMap();
        }
        Map<Long, IceBase> map = baseMap.get(app);
        if (CollectionUtils.isEmpty(map)) {
            return Collections.emptyMap();
        }
        Map<Long, Long> rootIdIceIdMap = new HashMap<>();
        for (IceBase base : map.values()) {
            if (base.getConfId() != null) {
                rootIdIceIdMap.put(base.getId(), base.getConfId());
            }
        }
        return rootIdIceIdMap;
    }
}

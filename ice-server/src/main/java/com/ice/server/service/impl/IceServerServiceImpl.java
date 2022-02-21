package com.ice.server.service.impl;

import com.alibaba.fastjson.JSON;
import com.ice.common.dto.IceBaseDto;
import com.ice.common.dto.IceConfDto;
import com.ice.common.dto.IceTransferDto;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.StatusEnum;
import com.ice.common.model.IceShowNode;
import com.ice.server.constant.Constant;
import com.ice.server.dao.mapper.IceAppMapper;
import com.ice.server.dao.mapper.IceBaseMapper;
import com.ice.server.dao.mapper.IceConfMapper;
import com.ice.server.dao.mapper.IceConfUpdateMapper;
import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IceBaseExample;
import com.ice.server.dao.model.IceConf;
import com.ice.server.dao.model.IceConfExample;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.rmi.IceRmiClientManager;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;

/**
 * @author zjn
 */
@Slf4j
@Service
public class IceServerServiceImpl implements IceServerService, InitializingBean {

    private static final Set<Integer> appSet = new HashSet<>();

    private static final Object LOCK = new Object();
    /*
     * key:app value baseList
     */
    private final Map<Integer, Map<Long, IceBase>> baseActiveMap = new HashMap<>();

    private final Map<Long, Map<Long, Integer>> atlasMap = new HashMap<>();
    /*
     * key:app value conf
     */
    private final Map<Integer, Map<Long, IceConf>> confActiveMap = new HashMap<>();

    private final Map<Integer, Map<Long, IceConf>> confUpdateMap = new HashMap<>();

    private final Map<Integer, Map<Long, IceShowNode>> mixConfShowMap = new HashMap<>();

    private final Map<Integer, Map<Long, List<IceConf>>> iceIdConfUpdatesMap = new HashMap<>();

    private final Map<Integer, Map<Byte, Map<String, Integer>>> leafClassMap = new HashMap<>();
    /*
     * last update base
     */
    private Date lastBaseUpdateTime;
    /*
     * last update conf
     */
    private Date lastConfUpdateTime;
    private volatile long version;
    @Resource
    private IceBaseMapper baseMapper;
    @Resource
    private IceConfMapper confMapper;
    @Resource
    private IceConfUpdateMapper confUpdateMapper;
    @Resource
    private IceAppMapper iceAppMapper;

    public synchronized boolean haveCircle(Long nodeId, Long linkId) {
        if (nodeId.equals(linkId)) {
            return true;
        }
        Map<Long, Integer> linkNextMap = atlasMap.get(linkId);
        if (CollectionUtils.isEmpty(linkNextMap)) {
            return false;
        }
        Set<Long> linkNext = linkNextMap.keySet();
        if (linkNext.contains(nodeId)) {
            return true;
        }
        for (Long next : linkNext) {
            if (haveCircle(nodeId, next)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean haveCircle(Long nodeId, List<Long> linkIds) {
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
        if (haveCircle(nodeId, linkId)) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "have circle nodeId:" + nodeId + " linkId:" + linkId);
        }
        Map<Long, Integer> nodeNextMap = atlasMap.computeIfAbsent(nodeId, k -> new HashMap<>());
        Integer nodeNextCount = nodeNextMap.computeIfAbsent(linkId, k -> 0);
        nodeNextMap.put(linkId, nodeNextCount + 1);
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
        Map<Long, Integer> nodeNextMap = atlasMap.get(nodeId);
        if (nodeNextMap != null) {
            Integer nodeNextCount = nodeNextMap.get(linkId);
            if (nodeNextCount != null) {
                if (nodeNextCount <= 1) {
                    nodeNextMap.remove(linkId);
                    if (CollectionUtils.isEmpty(nodeNextMap)) {
                        atlasMap.remove(nodeId);
                    }
                } else {
                    nodeNextMap.put(linkId, nodeNextCount - 1);
                }
            }
        }
    }

    @Override
    public synchronized void exchangeLink(Long nodeId, Long originId, Long exchangeId) {
        unlink(nodeId, originId);
        link(nodeId, exchangeId);
    }

    @Override
    public IceConf getActiveConfById(Integer app, Long confId) {
        Map<Long, IceConf> confMap = confActiveMap.get(app);
        if (!CollectionUtils.isEmpty(confMap)) {
            return confMap.get(confId);
        }
        return null;
    }

    @Override
    public List<IceConf> getMixConfListByIds(Integer app, Set<Long> confSet) {
        if (CollectionUtils.isEmpty(confSet)) {
            return null;
        }
        List<IceConf> confList = new ArrayList<>(confSet.size());
        for (Long confId : confSet) {
            IceConf conf = this.getMixConfById(app, confId);
            if (conf == null) {
                /*one of confId not exist return null*/
                return null;
            }
            confList.add(conf);
        }
        return confList;
    }

    @Override
    public IceConf getMixConfById(Integer app, Long confId) {
        Map<Long, IceConf> updateMap = confUpdateMap.get(app);
        if (!CollectionUtils.isEmpty(updateMap)) {
            IceConf updateConf = updateMap.get(confId);
            if (updateConf != null) {
                return updateConf;
            }
        }
        Map<Long, IceConf> activeMap = confActiveMap.get(app);
        if (!CollectionUtils.isEmpty(activeMap)) {
            return activeMap.get(confId);
        }
        return null;
    }

    @Override
    public IceShowNode getConfMixById(Integer app, Long confId) {
        Map<Long, IceShowNode> mixMap = mixConfShowMap.get(app);
        if (!CollectionUtils.isEmpty(mixMap)) {
            return mixMap.get(confId);
        }
        return null;
    }

    @Override
    public IceBase getActiveBaseById(Integer app, Long iceId) {
        Map<Long, IceBase> confMap = baseActiveMap.get(app);
        if (!CollectionUtils.isEmpty(confMap)) {
            return confMap.get(iceId);
        }
        return null;
    }

    @Override
    public Map<String, Integer> getLeafClassMap(Integer app, Byte type) {
        Map<Byte, Map<String, Integer>> map = leafClassMap.get(app);
        if (map != null) {
            return map.get(type);
        }
        return null;
    }

    @Override
    public void removeLeafClass(Integer app, Byte type, String clazz) {
        Map<Byte, Map<String, Integer>> map = leafClassMap.get(app);
        if (!CollectionUtils.isEmpty(map)) {
            Map<String, Integer> typeMap = map.get(type);
            if (!CollectionUtils.isEmpty(typeMap)) {
                typeMap.remove(clazz);
            }
        }
    }

    @Override
    public void addLeafClass(Integer app, Byte type, String clazz) {
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
    public void updateByEdit() {
        update();
    }

    /*
     * update by schedule
     */
    @Scheduled(fixedDelay = 20000)
    private void update() {
        Date now = new Date();
        Date lastMaxBaseDate = lastBaseUpdateTime;
        Date lastMaxConfDate = lastConfUpdateTime;
        Map<Integer, Set<Long>> deleteConfMap = new HashMap<>(appSet.size());
        Map<Integer, Set<Long>> deleteBaseMap = new HashMap<>(appSet.size());

        Map<Integer, Map<Long, IceConf>> activeChangeConfMap = new HashMap<>(appSet.size());
        Map<Integer, Map<Long, IceBase>> activeChangeBaseMap = new HashMap<>(appSet.size());

        /*find change in db*/
        IceConfExample confExample = new IceConfExample();
        confExample.createCriteria().andUpdateAtGreaterThan(lastConfUpdateTime).andUpdateAtLessThanOrEqualTo(now);
        List<IceConf> confList = confMapper.selectByExample(confExample);
        if (!CollectionUtils.isEmpty(confList)) {
            for (IceConf conf : confList) {
                if (conf.getUpdateAt().after(lastMaxConfDate)) {
                    lastMaxConfDate = conf.getUpdateAt();
                }
                appSet.add(conf.getApp());
                if (conf.getStatus() == StatusEnum.OFFLINE.getStatus()) {
                    /*update offline in db by hand*/
                    deleteConfMap.computeIfAbsent(conf.getApp(), k -> new HashSet<>()).add(conf.getId());
                    continue;
                }
                activeChangeConfMap.computeIfAbsent(conf.getApp(), k -> new HashMap<>()).put(conf.getId(), conf);
            }
        }
        IceBaseExample baseExample = new IceBaseExample();
        baseExample.createCriteria().andUpdateAtGreaterThan(lastBaseUpdateTime).andUpdateAtLessThanOrEqualTo(now);
        List<IceBase> baseList = baseMapper.selectByExample(baseExample);
        if (!CollectionUtils.isEmpty(baseList)) {
            for (IceBase base : baseList) {
                if (base.getUpdateAt().after(lastMaxBaseDate)) {
                    lastMaxBaseDate = base.getUpdateAt();
                }
                appSet.add(base.getApp());
                if (base.getStatus() == StatusEnum.OFFLINE.getStatus()) {
                    /*update offline in db by hand*/
                    deleteBaseMap.computeIfAbsent(base.getApp(), k -> new HashSet<>()).add(base.getId());
                    continue;
                }
                activeChangeBaseMap.computeIfAbsent(base.getApp(), k -> new HashMap<>()).put(base.getId(), base);
            }
        }
        /*update local cache*/
        long updateVersion = updateLocal(deleteBaseMap, deleteConfMap, activeChangeBaseMap,
                activeChangeConfMap);
        /*send update msg to remote client*/
        sendChange(deleteConfMap, deleteBaseMap, activeChangeConfMap, activeChangeBaseMap, updateVersion);
        /*update time (why not update to now? to avoid timeline conflict)*/
        lastBaseUpdateTime = lastMaxBaseDate;
        lastConfUpdateTime = lastMaxConfDate;
    }

    private void sendChange(Map<Integer, Set<Long>> deleteConfMap,
                            Map<Integer, Set<Long>> deleteBaseMap,
                            Map<Integer, Map<Long, IceConf>> activeChangeConfMap,
                            Map<Integer, Map<Long, IceBase>> activeChangeBaseMap,
                            long updateVersion) {
        for (Integer app : appSet) {
            IceTransferDto transferDto = null;
            Map<Long, IceConf> insertOrUpdateConfMap = activeChangeConfMap.get(app);
            if (!CollectionUtils.isEmpty(insertOrUpdateConfMap)) {
                transferDto = new IceTransferDto();
                Collection<IceConfDto> confDtoList = new ArrayList<>(insertOrUpdateConfMap.values().size());
                for (IceConf conf : insertOrUpdateConfMap.values()) {
                    confDtoList.add(Constant.confToDto(conf));
                }
                transferDto.setInsertOrUpdateConfs(confDtoList);
            }
            Map<Long, IceBase> insertOrUpdateBaseMap = activeChangeBaseMap.get(app);
            if (!CollectionUtils.isEmpty(insertOrUpdateBaseMap)) {
                if (transferDto == null) {
                    transferDto = new IceTransferDto();
                }
                Collection<IceBaseDto> baseDtoList = new ArrayList<>(insertOrUpdateBaseMap.values().size());
                for (IceBase base : insertOrUpdateBaseMap.values()) {
                    baseDtoList.add(Constant.baseToDto(base));
                }
                transferDto.setInsertOrUpdateBases(baseDtoList);
            }
            Set<Long> deleteConfIds = deleteConfMap.get(app);
            if (!CollectionUtils.isEmpty(deleteConfMap)) {
                if (transferDto == null) {
                    transferDto = new IceTransferDto();
                }
                transferDto.setDeleteConfIds(deleteConfIds);
            }
            Set<Long> deleteBases = deleteBaseMap.get(app);
            if (!CollectionUtils.isEmpty(deleteBases)) {
                if (transferDto == null) {
                    transferDto = new IceTransferDto();
                }
                transferDto.setDeleteBaseIds(deleteBases);
            }
            /*send update msg to remote client while has change*/
            if (transferDto != null) {
                transferDto.setVersion(updateVersion);
                IceRmiClientManager.update(app, transferDto);
                log.info("ice update app:{}, content:{}", app, JSON.toJSONString(transferDto));
            }
        }
    }

    /*
     * update local cache
     * first handle delete,then insert&update
     */
    private long updateLocal(Map<Integer, Set<Long>> deleteBaseMap,
                             Map<Integer, Set<Long>> deleteConfMap,
                             Map<Integer, Map<Long, IceBase>> activeChangeBaseMap,
                             Map<Integer, Map<Long, IceConf>> activeChangeConfMap) {
        synchronized (LOCK) {
            for (Map.Entry<Integer, Set<Long>> entry : deleteConfMap.entrySet()) {
                for (Long id : entry.getValue()) {
                    Map<Long, IceConf> tmpActiveMap = confActiveMap.get(entry.getKey());
                    if (tmpActiveMap != null) {
                        confActiveMap.get(entry.getKey()).remove(id);
                    }
                }
            }
            for (Map.Entry<Integer, Set<Long>> entry : deleteBaseMap.entrySet()) {
                for (Long id : entry.getValue()) {
                    Map<Long, IceBase> tmpActiveMap = baseActiveMap.get(entry.getKey());
                    if (tmpActiveMap != null) {
                        baseActiveMap.get(entry.getKey()).remove(id);
                    }
                }
            }
            for (Map.Entry<Integer, Map<Long, IceBase>> appEntry : activeChangeBaseMap.entrySet()) {
                for (Map.Entry<Long, IceBase> entry : appEntry.getValue().entrySet()) {
                    baseActiveMap.computeIfAbsent(appEntry.getKey(), k -> new HashMap<>()).put(entry.getKey(), entry.getValue());
                }
            }
            for (Map.Entry<Integer, Map<Long, IceConf>> appEntry : activeChangeConfMap.entrySet()) {
                for (Map.Entry<Long, IceConf> entry : appEntry.getValue().entrySet()) {
                    confActiveMap.computeIfAbsent(appEntry.getKey(), k -> new HashMap<>()).put(entry.getKey(), entry.getValue());
                }
            }
            version++;
            return version;
        }
    }

    @Override
    public void afterPropertiesSet() {
//        Date now = new Date();
        /*baseList*/
        IceBaseExample baseExample = new IceBaseExample();
        baseExample.createCriteria().andStatusEqualTo(StatusEnum.ONLINE.getStatus());
        List<IceBase> baseList = baseMapper.selectByExample(baseExample);

        if (!CollectionUtils.isEmpty(baseList)) {
            for (IceBase base : baseList) {
                appSet.add(base.getApp());
                baseActiveMap.computeIfAbsent(base.getApp(), k -> new HashMap<>()).put(base.getId(), base);
            }
        }
        /*UpdateList*/
        IceConfExample confUpdateExample = new IceConfExample();
        confUpdateExample.createCriteria().andStatusEqualTo(StatusEnum.ONLINE.getStatus());
        List<IceConf> confUpdateList = confUpdateMapper.selectByExample(confUpdateExample);
        Map<Long, IceShowNode> showNodeMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(confUpdateList)) {
            for (IceConf conf : confUpdateList) {
                appSet.add(conf.getApp());
                IceShowNode showNode = Constant.confToShow(conf, true);
                showNodeMap.put(conf.getId(), showNode);
                confUpdateMap.computeIfAbsent(conf.getApp(), k -> new HashMap<>()).put(conf.getId(), conf);
                mixConfShowMap.computeIfAbsent(conf.getApp(), k -> new HashMap<>()).put(conf.getId(), showNode);
                iceIdConfUpdatesMap.computeIfAbsent(conf.getApp(), k -> new HashMap<>()).computeIfAbsent(conf.getIceId(), k -> new ArrayList<>()).add(conf);
                assembleAtlas(conf);
                assembleLeafClass(conf);
            }
        }
        /*ConfList*/
        IceConfExample confExample = new IceConfExample();
        confExample.createCriteria().andStatusEqualTo(StatusEnum.ONLINE.getStatus());
        List<IceConf> confList = confMapper.selectByExample(confExample);
        if (!CollectionUtils.isEmpty(confList)) {
            for (IceConf conf : confList) {
                appSet.add(conf.getApp());
                confActiveMap.computeIfAbsent(conf.getApp(), k -> new HashMap<>()).put(conf.getId(), conf);
                if (!showNodeMap.containsKey(conf.getId())) {
                    assembleAtlas(conf);
                    showNodeMap.put(conf.getId(), Constant.confToShow(conf, false));
                }
                assembleLeafClass(conf);
            }
        }
        for (IceShowNode showNode : showNodeMap.values()) {
            if (NodeTypeEnum.isRelation(showNode.getNodeType())) {
                if (StringUtils.hasLength(showNode.getSonIds())) {
                    String[] sonIdStrs = showNode.getSonIds().split(",");
                    List<Long> sonIds = new ArrayList<>(sonIdStrs.length);
                    for (String sonStr : sonIdStrs) {
                        sonIds.add(Long.valueOf(sonStr));
                    }
                    List<IceShowNode> children = new ArrayList<>(sonIdStrs.length);
                    for (Long sonId : sonIds) {
                        IceShowNode node = showNodeMap.get(sonId);
                        if (node != null) {
                            children.add(showNodeMap.get(sonId));
                        }
                    }
                    showNode.setChildren(children);
                }
            }
            if (showNode.getForwardId() != null) {
                showNode.setForward(showNodeMap.get(showNode.getForwardId()));
            }
        }
    }

    private void assembleAtlas(IceConf conf) {
        if (NodeTypeEnum.isRelation(conf.getType()) && StringUtils.hasLength(conf.getSonIds())) {
            String[] sonIdStrs = conf.getSonIds().split(",");
            for (String sonIdStr : sonIdStrs) {
                Long sonId = Long.parseLong(sonIdStr);
                Map<Long, Integer> nextMap = atlasMap.computeIfAbsent(conf.getId(), k -> new HashMap<>());
                int nextCount = nextMap.computeIfAbsent(sonId, k -> 0);
                nextCount += 1;
                nextMap.put(sonId, nextCount);
            }
        }
        if (conf.getForwardId() != null) {
            Map<Long, Integer> nextMap = atlasMap.computeIfAbsent(conf.getId(), k -> new HashMap<>());
            int nextCount = nextMap.computeIfAbsent(conf.getForwardId(), k -> 0);
            nextCount += 1;
            nextMap.put(conf.getForwardId(), nextCount);
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
            return Collections.emptyList();
        }
        return Constant.confListToDtoList(map.values());
    }

    public Collection<IceBaseDto> getActiveBasesByApp(Integer app) {
        Map<Long, IceBase> map = baseActiveMap.get(app);
        if (map == null) {
            return Collections.emptyList();
        }
        return Constant.baseListToDtoList(map.values());
    }

    @Override
    public IceTransferDto getInitConfig(Integer app) {
        synchronized (LOCK) {
            IceTransferDto transferDto = new IceTransferDto();
            transferDto.setInsertOrUpdateBases(this.getActiveBasesByApp(app));
            transferDto.setInsertOrUpdateConfs(this.getActiveConfsByApp(app));
            transferDto.setVersion(version);
            return transferDto;
        }
    }
}

package com.ice.server.service.impl;

import com.alibaba.fastjson.JSON;
import com.ice.common.constant.Constant;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.StatusEnum;
import com.ice.common.model.IceBaseDto;
import com.ice.common.model.IceConfDto;
import com.ice.common.model.IceTransferDto;
import com.ice.server.dao.mapper.IceAppMapper;
import com.ice.server.dao.mapper.IceBaseMapper;
import com.ice.server.dao.mapper.IceConfMapper;
import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IceBaseExample;
import com.ice.server.dao.model.IceConf;
import com.ice.server.dao.model.IceConfExample;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

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
    /*
     * key:app value conf
     */
    private final Map<Integer, Map<Long, IceConf>> confActiveMap = new HashMap<>();
    private final Map<Integer, Map<Byte, Map<String, Integer>>> leafClassMap = new HashMap<>();
    /*
     * 上次更新时间
     */
    private Date lastUpdateTime;
    private volatile long version;
    @Resource
    private IceBaseMapper baseMapper;
    @Resource
    private IceConfMapper confMapper;
    @Resource
    private IceAppMapper iceAppMapper;
    @Resource
    private AmqpTemplate amqpTemplate;

    /*
     * 根据confId获取配置信息
     */
    @Override
    public IceConf getActiveConfById(Integer app, Long confId) {
        Map<Long, IceConf> confMap = confActiveMap.get(app);
        if (!CollectionUtils.isEmpty(confMap)) {
            IceConf conf = confMap.get(confId);
            addLeafClass(app, conf.getType(), conf.getConfName());
            return conf;
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

    public void addLeafClass(Integer app, Byte type, String className) {
        if (isLeaf(type)) {
            Map<Byte, Map<String, Integer>> map = leafClassMap.get(app);
            Map<String, Integer> classMap;
            if (map == null) {
                map = new HashMap<>();
                leafClassMap.put(app, map);
                classMap = new HashMap<>();
                map.put(type, classMap);
                classMap.put(className, 0);
            } else {
                classMap = map.get(type);
                if (classMap == null) {
                    classMap = new HashMap<>();
                    map.put(type, classMap);
                    classMap.put(className, 0);
                } else {
                    classMap.putIfAbsent(className, 0);
                }
            }
            classMap.put(className, classMap.get(className) + 1);
        }
    }

    @Override
    public void updateByEdit() {
        update();
    }

    /*
     * 定时任务,距上次执行完成20s后执行
     */
    @Scheduled(fixedDelay = 20000)
    private void update() {
        Date now = new Date();
        Map<Integer, Set<Long>> deleteConfMap = new HashMap<>(appSet.size());
        Map<Integer, Set<Long>> deleteBaseMap = new HashMap<>(appSet.size());

        Map<Integer, Map<Long, IceConf>> activeChangeConfMap = new HashMap<>(appSet.size());
        Map<Integer, Map<Long, IceBase>> activeChangeBaseMap = new HashMap<>(appSet.size());

        /*先找数据库里的变化*/
        IceConfExample confExample = new IceConfExample();
        confExample.createCriteria().andUpdateAtGreaterThan(lastUpdateTime).andUpdateAtLessThanOrEqualTo(now);
        List<IceConf> confList = confMapper.selectByExample(confExample);
        if (!CollectionUtils.isEmpty(confList)) {
            log.info("===============change conf list:{}", JSON.toJSONString(confList));
        }
        if (!CollectionUtils.isEmpty(confList)) {
            for (IceConf conf : confList) {
                appSet.add(conf.getApp());
                if (conf.getStatus() == StatusEnum.OFFLINE.getStatus()) {
                    /*手动更新下线*/
                    deleteConfMap.computeIfAbsent(conf.getApp(), k -> new HashSet<>()).add(conf.getId());
                    continue;
                }
                activeChangeConfMap.computeIfAbsent(conf.getApp(), k -> new HashMap<>()).put(conf.getId(), conf);
            }
        }
        IceBaseExample baseExample = new IceBaseExample();
        baseExample.createCriteria().andUpdateAtGreaterThan(lastUpdateTime).andUpdateAtLessThanOrEqualTo(now);
        List<IceBase> baseList = baseMapper.selectByExample(baseExample);
        if (!CollectionUtils.isEmpty(baseList)) {
            log.info("===============change base list:{}", JSON.toJSONString(baseList));
        }
        if (!CollectionUtils.isEmpty(baseList)) {
            for (IceBase base : baseList) {
                appSet.add(base.getApp());
                if (base.getStatus() == StatusEnum.OFFLINE.getStatus()) {
                    /*更新下线*/
                    deleteBaseMap.computeIfAbsent(base.getApp(), k -> new HashSet<>()).add(base.getId());
                    continue;
                }
                activeChangeBaseMap.computeIfAbsent(base.getApp(), k -> new HashMap<>()).put(base.getId(), base);
            }
        }
        /*更新本地缓存*/
        long updateVersion = updateLocal(deleteBaseMap, deleteConfMap, activeChangeBaseMap,
                activeChangeConfMap);
        /*更新完毕 发送变更消息*/
        sendChange(deleteConfMap, deleteBaseMap, activeChangeConfMap, activeChangeBaseMap, updateVersion);
        /*更新时间*/
        lastUpdateTime = now;
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
                    confDtoList.add(convert(conf));
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
                    baseDtoList.add(convert(base));
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
            /*有更新就推送消息*/
            if (transferDto != null) {
                transferDto.setVersion(updateVersion);
                String message = JSON.toJSONString(transferDto);
                amqpTemplate.convertAndSend(Constant.getUpdateExchange(), Constant.getUpdateRouteKey(app), message);
                log.info("ice update app:{}, content:{}", app, message);
            }
        }
    }

    /*
     * 更新本地cache
     * 先处理删除,再处理插入与更新
     * @return 当前更新版本
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
        Date now = new Date();
        /*baseList*/
        IceBaseExample baseExample = new IceBaseExample();
        baseExample.createCriteria().andStatusEqualTo(StatusEnum.ONLINE.getStatus()).andUpdateAtLessThanOrEqualTo(now);
        List<IceBase> baseList = baseMapper.selectByExample(baseExample);

        if (!CollectionUtils.isEmpty(baseList)) {
            for (IceBase base : baseList) {
                appSet.add(base.getApp());
                baseActiveMap.computeIfAbsent(base.getApp(), k -> new HashMap<>()).put(base.getId(), base);
            }
        }
        /*ConfList*/
        IceConfExample confExample = new IceConfExample();
        confExample.createCriteria().andStatusEqualTo(StatusEnum.ONLINE.getStatus()).andUpdateAtLessThanOrEqualTo(now);
        List<IceConf> confList = confMapper.selectByExample(confExample);
        if (!CollectionUtils.isEmpty(confList)) {
            for (IceConf conf : confList) {
                appSet.add(conf.getApp());
                confActiveMap.computeIfAbsent(conf.getApp(), k -> new HashMap<>()).put(conf.getId(), conf);
                if (isLeaf(conf.getType())) {
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
        }
        lastUpdateTime = now;
    }

    private boolean isLeaf(byte type) {
        return type == NodeTypeEnum.LEAF_FLOW.getType() || type == NodeTypeEnum.LEAF_NONE.getType() || type == NodeTypeEnum.LEAF_RESULT.getType();
    }

    /*
     * 根据app获取生效中的ConfList
     */
    public Collection<IceConfDto> getActiveConfsByApp(Integer app) {
        Map<Long, IceConf> map = confActiveMap.get(app);
        if (map == null) {
            return Collections.emptyList();
        }
        return map.values().stream().map(this::convert).collect(Collectors.toList());
    }

    /*
     * 根据app获取生效中的baseList
     */
    public Collection<IceBaseDto> getActiveBasesByApp(Integer app) {
        Map<Long, IceBase> map = baseActiveMap.get(app);
        if (map == null) {
            return Collections.emptyList();
        }
        return map.values().stream().map(this::convert).collect(Collectors.toList());
    }

    /*
     * 根据app获取初始化json
     */
    @Override
    public String getInitJson(Integer app) {
        synchronized (LOCK) {
            IceTransferDto transferDto = new IceTransferDto();
            transferDto.setInsertOrUpdateBases(this.getActiveBasesByApp(app));
            transferDto.setInsertOrUpdateConfs(this.getActiveConfsByApp(app));
            transferDto.setVersion(version);
            return JSON.toJSONString(transferDto);
        }
    }

    private IceBaseDto convert(IceBase base) {
        IceBaseDto baseDto = new IceBaseDto();
        baseDto.setConfId(base.getConfId());
        baseDto.setDebug(base.getDebug());
        baseDto.setId(base.getId());
        baseDto.setStart(base.getStart() == null ? 0 : base.getStart().getTime());
        baseDto.setEnd(base.getEnd() == null ? 0 : base.getEnd().getTime());
        baseDto.setTimeType(base.getTimeType());
        baseDto.setPriority(base.getPriority());
        baseDto.setScenes(base.getScenes());
        baseDto.setStatus(base.getStatus());
        return baseDto;
    }

    private IceConfDto convert(IceConf conf) {
        IceConfDto confDto = new IceConfDto();
        confDto.setForwardId(conf.getForwardId());
        confDto.setDebug(conf.getDebug());
        confDto.setId(conf.getId());
        confDto.setStart(conf.getStart() == null ? 0 : conf.getStart().getTime());
        confDto.setEnd(conf.getEnd() == null ? 0 : conf.getEnd().getTime());
        confDto.setTimeType(conf.getTimeType());
        confDto.setComplex(conf.getComplex());
        confDto.setSonIds(conf.getSonIds());
        confDto.setStatus(conf.getStatus());
        confDto.setConfName(conf.getConfName());
        confDto.setConfField(conf.getConfField());
        confDto.setInverse(conf.getInverse());
        confDto.setType(conf.getType());
        return confDto;
    }
}

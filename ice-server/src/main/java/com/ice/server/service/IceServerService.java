package com.ice.server.service;

import com.ice.common.dto.IceTransferDto;
import com.ice.common.model.IceShowNode;
import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IceConf;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author waitmoon
 */
public interface IceServerService {

    IceTransferDto getInitConfig(Integer app);

    IceConf getActiveConfById(Integer app, Long confId);

    IceConf getUpdateConfById(Integer app, Long confId, Long iceId);

    List<IceConf> getMixConfListByIds(Integer app, Set<Long> confSet, long iceId);

    IceConf getMixConfById(int app, long confId, long iceId);

    IceShowNode getConfMixById(int app, long confId, long iceId);

    IceBase getActiveBaseById(Integer app, Long iceId);

    void updateLocalConfUpdateCache(IceConf conf);

    void updateLocalConfUpdateCaches(Collection<IceConf> confs);

    void updateLocalConfActiveCache(IceConf conf);

    void updateLocalConfActiveCaches(Collection<IceConf> confs);

    void updateLocalBaseCache(IceBase base);

    Map<String, Integer> getLeafClassMap(Integer app, Byte type);

    void increaseLeafClass(Integer app, Byte type, String clazz);

    void decreaseLeafClass(Integer app, Byte type, String clazz);

    void removeLeafClass(Integer app, Byte type, String clazz);

    /**
     * 检测环路：如果把 linkId 连接到 nodeId 下，是否会形成环路
     * 即检查 linkId 的所有子孙节点中是否包含 nodeId
     */
    boolean haveCircle(int app, long iceId, Long nodeId, Long linkId);

    boolean haveCircle(int app, long iceId, Long nodeId, Collection<Long> linkIds);

    long getVersion();

    IceTransferDto release(int app, long iceId);

    void updateClean(int app, long iceId);

    Collection<IceConf> getAllUpdateConfList(int app, long iceId);

    Set<IceConf> getAllActiveConfSet(int app, long rootId);

    void refresh();

    void cleanConfigCache();

    void rebuildingAtlas(Collection<IceConf> updateList, Collection<IceConf> confList);

    void recycle(Integer app);

}

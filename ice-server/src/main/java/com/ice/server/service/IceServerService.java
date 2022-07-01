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

    List<IceConf> getMixConfListByIds(Integer app, Set<Long> confSet, long iceId);

    IceConf getMixConfById(int app, long confId, long iceId);

    IceShowNode getConfMixById(int app, long confId, long iceId);

    IceBase getActiveBaseById(Integer app, Long iceId);

    void updateLocalConfUpdateCache(IceConf conf);

    void updateLocalConfUpdateCaches(Collection<IceConf> confs);

    void updateLocalConfActiveCache(IceConf conf);

    void updateLocalConfActiveCaches(Collection<IceConf> confs);

    void updateLocalBaseActiveCache(IceBase base);

    Map<String, Integer> getLeafClassMap(Integer app, Byte type);

    void addLeafClass(Integer app, Byte type, String clazz);

    void removeLeafClass(Integer app, Byte type, String clazz);

    boolean haveCircle(Long nodeId, Long linkId);

    boolean haveCircle(Long nodeId, Collection<Long> linkIds);

    void link(Long nodeId, Long linkId);

    void link(Long nodeId, List<Long> linkIds);

    void unlink(Long nodeId, Long linkId);

    void exchangeLink(Long nodeId, Long originId, List<Long> exchangeIds);

    void exchangeLink(Long nodeId, Long originId, Long exchangeId);

    long getVersion();

    IceTransferDto release(int app, long iceId);

    void updateClean(int app, long iceId);

    Collection<IceConf> getAllUpdateConfList(int app, long iceId);

    Set<IceConf> getAllActiveConfSet(int app, long rootId);
}

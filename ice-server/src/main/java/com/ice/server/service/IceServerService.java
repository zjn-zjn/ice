package com.ice.server.service;

import com.ice.server.dao.model.IceConf;

import java.util.List;
import java.util.Map;

/**
 * @author zjn
 */
public interface IceServerService {

    String getInitJson(Integer app);

    IceConf getActiveConfById(Integer app, Long confId);

    Map<String, Integer> getLeafClassMap(Integer app, Byte type);

    void addLeafClass(Integer app, Byte type, String clazz);

    void removeLeafClass(Integer app, Byte type, String clazz);

    void updateByEdit();

    boolean haveCircle(Long nodeId, Long linkId);

    boolean haveCircle(Long nodeId, List<Long> linkIds);

    void link(Long nodeId, Long linkId);

    void link(Long nodeId, List<Long> linkIds);

    void unlink(Long nodeId, Long linkId);

    void exchangeLink(Long nodeId, Long originId, Long exchangeId);
}

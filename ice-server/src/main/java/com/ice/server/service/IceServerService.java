package com.ice.server.service;

import com.ice.server.dao.model.IceConf;

import java.util.Map;

/**
 * @author zjn
 */
public interface IceServerService {

    String getInitJson(Integer app);

    IceConf getActiveConfById(Integer app, Long confId);

    Map<String, Integer> getLeafClassMap(Integer app, Byte type);

    void updateByEdit();
}

package com.ice.server.service;

import com.ice.server.dao.model.IceConf;

import java.util.Map;

/**
 * @author zjn
 */
public interface ServerService {

    /*
     * 根据app获取初始化json
     */
    String getInitJson(Integer app);

    /*
     * 根据confId获取配置信息
     */
    IceConf getActiveConfById(Integer app, Long confId);

    Map<String, Integer> getLeafClassMap(Integer app, Byte type);

    void updateByEdit();
}

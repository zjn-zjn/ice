package com.ice.server.service;

import com.ice.common.model.IceClientConf;
import com.ice.server.dao.model.IceConf;
import com.ice.server.model.IceLeafClass;

import java.util.List;

/**
 * @author zjn
 */
public interface IceConfService {
    Long confEdit(Integer app,IceConf conf);

    Long confAddSon(Integer app,IceConf conf, Long parentId);

    Long confAddSonIds(Integer app,String sonIds, Long parentId);

    Long confAddForward(Integer app,IceConf conf, Long nextId);

    Long confAddForwardId(Integer app,Long forwardId, Long nextId);

    Long confEditId(Integer app,Long nodeId, Long exchangeId, Long parentId, Long nextId, Integer index);

    Long confForwardDelete(Integer app,Long forwardId, Long nextId);

    Long confSonDelete(Integer app,Long sonId, Long parentId, Integer index);

    List<IceLeafClass> getConfLeafClass(Integer app, Byte type);

    String leafClassCheck(Integer app, String clazz, Byte type);

    IceClientConf confDetail(Integer app, Long confId);
}

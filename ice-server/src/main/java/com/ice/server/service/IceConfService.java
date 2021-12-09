package com.ice.server.service;

import com.ice.server.dao.model.IceConf;
import com.ice.server.model.IceLeafClass;

import java.util.List;

/**
 * @author zjn
 */
public interface IceConfService {
    Long confEdit(IceConf conf, Long parentId, Long nextId);

    List<IceLeafClass> getConfLeafClass(Integer app, Byte type);
}

package com.ice.server.service;

import com.ice.common.model.IceShowConf;
import com.ice.server.model.IceEditNode;
import com.ice.server.model.IceLeafClass;

import java.util.List;

/**
 * @author zjn
 */
public interface IceConfService {
    Long confEdit(IceEditNode editNode);

    List<IceLeafClass> getConfLeafClass(Integer app, Byte type);

    String leafClassCheck(Integer app, String clazz, Byte type);

    IceShowConf confDetail(int app, long confId, String address, long iceId);
}

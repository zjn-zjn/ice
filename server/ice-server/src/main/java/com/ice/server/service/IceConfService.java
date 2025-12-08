package com.ice.server.service;

import com.ice.common.model.IceShowConf;
import com.ice.common.model.LeafNodeInfo;
import com.ice.server.model.IceEditNode;
import com.ice.server.model.IceLeafClass;

import java.util.List;

/**
 * @author waitmoon
 */
public interface IceConfService {
    Long confEdit(IceEditNode editNode);

    List<IceLeafClass> getConfLeafClass(int app, byte type);

    LeafNodeInfo leafClassCheck(int app, String clazz, byte type);

    IceShowConf confDetail(int app, long confId, String address, long iceId);
}

package com.ice.server.service;

import com.ice.server.model.IceApp;
import com.ice.server.model.PageResult;

import java.util.Set;

/**
 * @author waitmoon
 */
public interface IceAppService {
    PageResult<IceApp> appList(Integer pageNum, Integer pageSize, String name, Integer appId);

    Integer appEdit(IceApp app);

    Set<String> getRegisterClients(int app);
}

package com.ice.server.service;

import com.ice.server.dao.model.IceApp;

import java.util.List;
import java.util.Set;

/**
 * @author waitmoon
 */
public interface IceAppService {
    List<IceApp> appList();

    Integer appEdit(IceApp app);

    Set<String> getRegisterClients(int app);
}

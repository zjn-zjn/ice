package com.ice.server.service;

import com.ice.server.dao.model.IceApp;
import com.ice.server.model.PageResult;

/**
 * @author waitmoon
 */
public interface IceAppService {
    PageResult<IceApp> appList(Integer pageNum, Integer pageSize, String name, Integer app);

    Long appEdit(IceApp app);
}

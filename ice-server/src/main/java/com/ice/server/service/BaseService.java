package com.ice.server.service;

import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IcePushHistory;
import com.ice.server.model.IceBaseSearch;
import com.ice.server.model.PageResult;
import com.ice.server.model.PushData;

/**
 * @author zjn
 */
public interface BaseService {

    PageResult<IceBase> baseList(IceBaseSearch search);

    Long baseEdit(IceBase base);

    Long push(Integer app, Long iceId, String reason);

    PageResult<IcePushHistory> history(Integer app, Long iceId, Integer pageNum, Integer pageSize);

    String exportData(Long iceId, Long pushId);

    void rollback(Long pushId);

    void importData(PushData data);
}

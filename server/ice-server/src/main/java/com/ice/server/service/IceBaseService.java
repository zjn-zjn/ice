package com.ice.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ice.server.model.IceBase;
import com.ice.server.model.IcePushHistory;
import com.ice.server.model.IceBaseSearch;
import com.ice.server.model.PageResult;
import com.ice.server.model.PushData;

import java.util.List;

/**
 * @author waitmoon
 */
public interface IceBaseService {

    PageResult<IceBase> baseList(IceBaseSearch search);

    Long baseCreate(IceBase base);

    Long baseEdit(IceBase base);

    Long push(Integer app, Long iceId, String reason);

    PageResult<IcePushHistory> history(Integer app, Long iceId, Integer pageNum, Integer pageSize);

    String exportData(Integer app, Long iceId, Long pushId);

    String exportBatchData(Integer app, List<Long> iceIds);

    void rollback(Integer app, Long pushId) throws JsonProcessingException;

    void importData(PushData data);

    void delete(Integer app, Long pushId);
}

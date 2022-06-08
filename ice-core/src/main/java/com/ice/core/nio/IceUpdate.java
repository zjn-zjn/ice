package com.ice.core.nio;

import com.ice.common.dto.IceTransferDto;
import com.ice.core.cache.IceConfCache;
import com.ice.core.cache.IceHandlerCache;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zjn
 * update client local ice cache from ice-server`s init/update
 */
@Slf4j
public final class IceUpdate {

    public static List<String> update(IceTransferDto info) {
        List<String> errors = new ArrayList<>();
        /*conf first*/
        if (info.getDeleteConfIds() != null && !info.getDeleteConfIds().isEmpty()) {
            IceConfCache.delete(info.getDeleteConfIds());
        }
        if (info.getInsertOrUpdateConfs() != null && !info.getInsertOrUpdateConfs().isEmpty()) {
            errors.addAll(IceConfCache.insertOrUpdate(info.getInsertOrUpdateConfs()));
        }
        /*handler next*/
        if (info.getDeleteBaseIds() != null && !info.getDeleteBaseIds().isEmpty()) {
            IceHandlerCache.delete(info.getDeleteBaseIds());
        }
        if (info.getInsertOrUpdateBases() != null && !info.getInsertOrUpdateBases().isEmpty()) {
            errors.addAll(IceHandlerCache.insertOrUpdate(info.getInsertOrUpdateBases()));
        }
        return errors;
    }
}

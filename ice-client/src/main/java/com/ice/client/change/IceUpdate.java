package com.ice.client.change;

import com.ice.common.dto.IceTransferDto;
import com.ice.core.cache.IceConfCache;
import com.ice.core.cache.IceHandlerCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

/**
 * @author zjn
 * update client local ice cache from ice-server`s init/update msg
 */
@Slf4j
public final class IceUpdate {

    public static void update(IceTransferDto info) {
        /*conf first*/
        if (!CollectionUtils.isEmpty(info.getDeleteConfIds())) {
            IceConfCache.delete(info.getDeleteConfIds());
        }
        if (!CollectionUtils.isEmpty(info.getInsertOrUpdateConfs())) {
            IceConfCache.insertOrUpdate(info.getInsertOrUpdateConfs());
        }
        /*handler next*/
        if (!CollectionUtils.isEmpty(info.getDeleteBaseIds())) {
            IceHandlerCache.delete(info.getDeleteBaseIds());
        }
        if (!CollectionUtils.isEmpty(info.getInsertOrUpdateBases())) {
            IceHandlerCache.insertOrUpdate(info.getInsertOrUpdateBases());
        }
    }
}

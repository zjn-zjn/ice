package com.ice.client.listener;

import com.ice.common.dto.IceTransferDto;
import com.ice.core.cache.IceConfCache;
import com.ice.core.cache.IceHandlerCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

/**
 * @author zjn
 * 更新Client端本地缓存
 */
@Slf4j
public final class IceUpdate {

  public static void update(IceTransferDto info) {
    /*优先conf*/
    if (!CollectionUtils.isEmpty(info.getDeleteConfIds())) {
      IceConfCache.delete(info.getDeleteConfIds());
    }
    if (!CollectionUtils.isEmpty(info.getInsertOrUpdateConfs())) {
      IceConfCache.insertOrUpdate(info.getInsertOrUpdateConfs());
    }
    /*其次handler*/
    if (!CollectionUtils.isEmpty(info.getDeleteBaseIds())) {
      IceHandlerCache.delete(info.getDeleteBaseIds());
    }
    if (!CollectionUtils.isEmpty(info.getInsertOrUpdateBases())) {
      IceHandlerCache.insertOrUpdate(info.getInsertOrUpdateBases());
    }
  }
}

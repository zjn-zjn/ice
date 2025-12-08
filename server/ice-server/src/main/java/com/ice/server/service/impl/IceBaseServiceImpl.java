package com.ice.server.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.IceBaseDto;
import com.ice.common.dto.IceConfDto;
import com.ice.common.dto.IcePushHistoryDto;
import com.ice.common.dto.IceTransferDto;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.core.utils.JacksonUtils;
import com.ice.server.constant.ServerConstant;
import com.ice.server.model.IceBase;
import com.ice.server.model.IceConf;
import com.ice.server.model.IcePushHistory;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.IceBaseSearch;
import com.ice.server.model.PageResult;
import com.ice.server.model.PushData;
import com.ice.server.service.IceBaseService;
import com.ice.server.service.IceServerService;
import com.ice.server.storage.IceFileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author waitmoon
 */
@Slf4j
@Service
public class IceBaseServiceImpl implements IceBaseService {

    private final IceFileStorageService storageService;
    private final IceServerService iceServerService;

    public IceBaseServiceImpl(IceFileStorageService storageService, IceServerService iceServerService) {
        this.storageService = storageService;
        this.iceServerService = iceServerService;
    }

    @Override
    public PageResult<IceBase> baseList(IceBaseSearch search) {
        try {
            List<IceBaseDto> allBases = storageService.listBases(search.getApp());

            // 过滤
            List<IceBase> filteredBases = allBases.stream()
                    .filter(b -> b.getStatus() != null && b.getStatus() == IceStorageConstants.STATUS_ONLINE)
                    .filter(b -> {
                        if (search.getBaseId() != null) {
                            return search.getBaseId().equals(b.getId());
                        }
                        boolean match = true;
                        if (StringUtils.hasLength(search.getName())) {
                            match = b.getName() != null && b.getName().startsWith(search.getName());
                        }
                        if (match && StringUtils.hasLength(search.getScene())) {
                            match = b.getScenes() != null && Arrays.asList(b.getScenes().split(",")).contains(search.getScene());
                        }
                        return match;
                    })
                    .map(IceBase::fromDto)
                    .sorted((a, b) -> {
                        if (a.getUpdateAt() == null && b.getUpdateAt() == null) return 0;
                        if (a.getUpdateAt() == null) return 1;
                        if (b.getUpdateAt() == null) return -1;
                        return b.getUpdateAt().compareTo(a.getUpdateAt());
                    })
                    .collect(Collectors.toList());

            // 分页
            int total = filteredBases.size();
            int start = (search.getPageNum() - 1) * search.getPageSize();
            int end = Math.min(start + search.getPageSize(), total);

            PageResult<IceBase> pageResult = new PageResult<>();
            pageResult.setList(start < total ? filteredBases.subList(start, end) : Collections.emptyList());
            pageResult.setTotal((long) total);
            pageResult.setPages((total + search.getPageSize() - 1) / search.getPageSize());
            pageResult.setPageNum(search.getPageNum());
            pageResult.setPageSize(search.getPageSize());
            return pageResult;
        } catch (IOException e) {
            log.error("failed to list bases", e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public Long baseEdit(IceBase base) {
        try {
            IceTransferDto transferDto = new IceTransferDto();

            if (base.getId() == null) {
                // 新建base - 设置默认值
                long newId = storageService.nextBaseId(base.getApp());
                base.setId(newId);
                base.setCreateAt(new Date());
                base.setDebug(base.getDebug() == null ? (byte) 0 : base.getDebug());
                base.setScenes(base.getScenes() == null ? "" : base.getScenes());
                base.setStatus(base.getStatus() == null ? IceStorageConstants.STATUS_ONLINE : base.getStatus());
                base.setTimeType(base.getTimeType() == null ? (byte) 1 : base.getTimeType());
                base.setPriority(base.getPriority() == null ? 1L : base.getPriority());

                if (base.getConfId() == null) {
                    // 创建默认root节点
                    long confId = storageService.nextConfId(base.getApp());
                    IceConfDto createConf = new IceConfDto();
                    createConf.setId(confId);
                    createConf.setApp(base.getApp());
                    createConf.setStatus(IceStorageConstants.STATUS_ONLINE);
                    createConf.setType(NodeTypeEnum.NONE.getType());
                    createConf.setInverse(false);
                    createConf.setDebug((byte) 1);
                    createConf.setTimeType(TimeTypeEnum.NONE.getType());
                    createConf.setCreateAt(System.currentTimeMillis());
                    createConf.setUpdateAt(System.currentTimeMillis());
                    storageService.saveConf(createConf);

                    transferDto.setInsertOrUpdateConfs(Collections.singletonList(createConf));
                    base.setConfId(confId);
                }
            } else {
                // 编辑base - 保留原有值
                IceBaseDto origin = storageService.getBase(base.getApp(), base.getId());
                if (origin == null || origin.getStatus() == IceStorageConstants.STATUS_OFFLINE) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "iceId", base.getId());
                }
                base.setConfId(origin.getConfId());
                base.setCreateAt(origin.getCreateAt() != null ? new Date(origin.getCreateAt()) : null);
                // 如果前端没传，保留原有值
                if (base.getDebug() == null) {
                    base.setDebug(origin.getDebug());
                }
                if (base.getScenes() == null) {
                    base.setScenes(origin.getScenes());
                }
                if (base.getStatus() == null) {
                    base.setStatus(origin.getStatus());
                }
                if (base.getTimeType() == null) {
                    base.setTimeType(origin.getTimeType());
                }
                if (base.getPriority() == null) {
                    base.setPriority(origin.getPriority());
                }
            }
            
            timeCheck(base);

            base.setUpdateAt(new Date());
            storageService.saveBase(base.toDto());

            if (base.getStatus() == IceStorageConstants.STATUS_ONLINE) {
                transferDto.setInsertOrUpdateBases(Collections.singletonList(base.toDto()));
            } else {
                transferDto.setDeleteBaseIds(Collections.singletonList(base.getId()));
            }

            // 更新版本
            long newVersion = ((IceServerServiceImpl) iceServerService).getAndIncrementVersion(base.getApp());
            transferDto.setVersion(newVersion);
            storageService.saveVersionUpdate(base.getApp(), newVersion, transferDto);

            return base.getId();
        } catch (IOException e) {
            log.error("failed to edit base", e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    private static void timeCheck(IceBase base) {
        base.setUpdateAt(new Date());
        TimeTypeEnum typeEnum = TimeTypeEnum.getEnum(base.getTimeType());
        if (typeEnum == null) {
            base.setTimeType(TimeTypeEnum.NONE.getType());
            typeEnum = TimeTypeEnum.NONE;
        }
        switch (typeEnum) {
            case NONE:
                base.setStart(null);
                base.setEnd(null);
                break;
            case AFTER_START:
                if (base.getStart() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "start null");
                }
                base.setEnd(null);
                break;
            case BEFORE_END:
                if (base.getEnd() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "end null");
                }
                base.setStart(null);
                break;
            case BETWEEN:
                if (base.getStart() == null || base.getEnd() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "start|end null");
                }
                break;
        }
    }

    @Override
    public Long push(Integer app, Long iceId, String reason) {
        try {
            IceBaseDto base = storageService.getBase(app, iceId);
            if (base == null || !app.equals(base.getApp())) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "iceId", iceId);
            }

            IcePushHistoryDto history = new IcePushHistoryDto();
            history.setId(storageService.nextPushId(app));
            history.setApp(app);
            history.setIceId(iceId);
            history.setReason(reason);
            history.setOperator("waitmoon");
            history.setCreateAt(System.currentTimeMillis());
            history.setPushData(getPushDataJson(IceBase.fromDto(base)));

            storageService.savePushHistory(history);
            return history.getId();
        } catch (IOException e) {
            log.error("failed to push", e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    private String getPushDataJson(IceBase base) {
        return JacksonUtils.toJsonString(getPushData(base));
    }

    private PushData getPushData(IceBase base) {
        PushData pushData = new PushData();
        pushData.setApp(base.getApp());
        pushData.setBase(ServerConstant.baseToDtoWithName(base));

        Collection<IceConf> confUpdates = iceServerService.getAllUpdateConfList(base.getApp(), base.getId());
        if (!CollectionUtils.isEmpty(confUpdates)) {
            pushData.setConfUpdates(ServerConstant.confListToDtoListWithName(confUpdates));
        }

        Set<IceConf> activeConfs = iceServerService.getAllActiveConfSet(base.getApp(), base.getConfId());
        if (!CollectionUtils.isEmpty(activeConfs)) {
            pushData.setConfs(ServerConstant.confListToDtoListWithName(activeConfs));
        }
        return pushData;
    }

    @Override
    public PageResult<IcePushHistory> history(Integer app, Long iceId, Integer pageNum, Integer pageSize) {
        try {
            List<IcePushHistoryDto> allHistory = storageService.listPushHistories(app, iceId);

            int total = allHistory.size();
            int start = (pageNum - 1) * pageSize;
            int end = Math.min(start + pageSize, total);

            PageResult<IcePushHistory> pageResult = new PageResult<>();
            pageResult.setList(start < total
                    ? allHistory.subList(start, end).stream().map(IcePushHistory::fromDto).collect(Collectors.toList())
                    : Collections.emptyList());
            pageResult.setTotal((long) total);
            pageResult.setPages((total + pageSize - 1) / pageSize);
            pageResult.setPageNum(pageNum);
            pageResult.setPageSize(pageSize);
            return pageResult;
        } catch (IOException e) {
            log.error("failed to get history", e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public String exportData(Integer app, Long iceId, Long pushId) {
        try {
            if (pushId != null && pushId > 0) {
                IcePushHistoryDto history = storageService.getPushHistory(app, pushId);
                if (history != null) {
                    return history.getPushData();
                }
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "pushId", pushId);
            }

            IceBaseDto base = storageService.getBase(app, iceId);
            if (base != null) {
                return getPushDataJson(IceBase.fromDto(base));
            }
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "iceId", iceId);
        } catch (IOException e) {
            log.error("failed to export data for app:{}", app, e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void rollback(Integer app, Long pushId) throws JsonProcessingException {
        try {
            IcePushHistoryDto history = storageService.getPushHistory(app, pushId);
            if (history != null) {
                importData(JacksonUtils.readJson(history.getPushData(), PushData.class));
                return;
            }
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "pushId", pushId);
        } catch (IOException e) {
            log.error("failed to rollback for app:{}", app, e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void importData(PushData data) {
        try {
            Collection<IceConfDto> confUpdates = data.getConfUpdates();
            Collection<IceConfDto> confs = data.getConfs();
            long now = System.currentTimeMillis();

            // 导入 confUpdates（编辑中的配置）
            if (!CollectionUtils.isEmpty(confUpdates)) {
                for (IceConfDto confUpdate : confUpdates) {
                    confUpdate.setApp(data.getApp());
                    confUpdate.setUpdateAt(now);
                    if (confUpdate.getStatus() == null) {
                        confUpdate.setStatus(IceStorageConstants.STATUS_ONLINE);
                    }
                    storageService.saveConfUpdate(data.getApp(), confUpdate.getIceId(), confUpdate);
                }
            }

            // 导入 confs（已发布的配置）- 强力覆盖
            if (!CollectionUtils.isEmpty(confs)) {
                for (IceConfDto conf : confs) {
                    // 查询旧数据，保留 createAt
                    IceConfDto oldConf = storageService.getConf(data.getApp(), conf.getId());
                    
                    conf.setApp(data.getApp());
                    conf.setUpdateAt(now);
                    if (conf.getStatus() == null) {
                        conf.setStatus(IceStorageConstants.STATUS_ONLINE);
                    }
                    // 保留原有的 createAt，或设置新的
                    if (oldConf != null && oldConf.getCreateAt() != null) {
                        conf.setCreateAt(oldConf.getCreateAt());
                    } else if (conf.getCreateAt() == null) {
                        conf.setCreateAt(now);
                    }
                    
                    storageService.saveConf(conf);
                }
            }

            // 导入 base - 强力覆盖
            IceBaseDto base = data.getBase();
            if (base != null) {
                // 查询旧数据，保留 createAt
                IceBaseDto oldBase = storageService.getBase(data.getApp(), base.getId());
                
                base.setApp(data.getApp());
                base.setUpdateAt(now);
                if (base.getStatus() == null) {
                    base.setStatus(IceStorageConstants.STATUS_ONLINE);
                }
                // 保留原有的 createAt，或设置新的
                if (oldBase != null && oldBase.getCreateAt() != null) {
                    base.setCreateAt(oldBase.getCreateAt());
                } else if (base.getCreateAt() == null) {
                    base.setCreateAt(now);
                }
                // 设置默认值
                if (base.getTimeType() == null) {
                    base.setTimeType((byte) 1);
                }
                if (base.getPriority() == null) {
                    base.setPriority(1L);
                }
                
                storageService.saveBase(base);
            }

            // 更新版本并发布变更
            IceTransferDto transferDto = new IceTransferDto();
            if (!CollectionUtils.isEmpty(confs)) {
                transferDto.setInsertOrUpdateConfs(new ArrayList<>(confs));
            }
            if (base != null) {
                if (base.getStatus() == IceStorageConstants.STATUS_ONLINE) {
                    transferDto.setInsertOrUpdateBases(Collections.singletonList(base));
                } else {
                    transferDto.setDeleteBaseIds(Collections.singletonList(base.getId()));
                }
            }

            long newVersion = ((IceServerServiceImpl) iceServerService).getAndIncrementVersion(data.getApp());
            transferDto.setVersion(newVersion);
            storageService.saveVersionUpdate(data.getApp(), newVersion, transferDto);

        } catch (IOException e) {
            log.error("failed to import data", e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void delete(Integer app, Long pushId) {
        try {
            storageService.deletePushHistory(app, pushId);
        } catch (IOException e) {
            log.error("failed to delete push history for app:{}", app, e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }
}

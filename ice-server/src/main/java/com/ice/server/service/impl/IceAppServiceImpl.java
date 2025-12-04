package com.ice.server.service.impl;

import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.IceAppDto;
import com.ice.server.dao.model.IceApp;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.PageResult;
import com.ice.server.service.IceAppService;
import com.ice.server.storage.IceClientManager;
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
public class IceAppServiceImpl implements IceAppService {

    private final IceFileStorageService storageService;
    private final IceClientManager clientManager;

    public IceAppServiceImpl(IceFileStorageService storageService, IceClientManager clientManager) {
        this.storageService = storageService;
        this.clientManager = clientManager;
    }

    @Override
    public Integer appEdit(IceApp app) {
        try {
            if (app.getId() == null) {
                int newId = storageService.nextAppId();
                app.setId(newId);
                app.setCreateAt(new Date());
                app.setStatus(IceStorageConstants.STATUS_ONLINE);
            } else {
                IceAppDto origin = storageService.getApp(app.getId());
                if (origin == null) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "app", app.getId());
                }
                app.setCreateAt(origin.getCreateAt() != null ? new Date(origin.getCreateAt()) : null);
            }

            app.setUpdateAt(new Date());
            storageService.saveApp(app.toDto());
            storageService.ensureAppDirectories(app.getId());

            return app.getId();
        } catch (IOException e) {
            log.error("failed to edit app", e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public PageResult<IceApp> appList(Integer pageNum, Integer pageSize, String name, Integer appId) {
        try {
            List<IceAppDto> apps = storageService.listApps();

            // 过滤
            List<IceApp> filteredApps = apps.stream()
                    .filter(a -> {
                        if (appId != null) {
                            return appId.equals(a.getId());
                        }
                        if (StringUtils.hasLength(name)) {
                            return a.getName() != null && a.getName().contains(name);
                        }
                        return true;
                    })
                    .map(IceApp::fromDto)
                    .sorted((a, b) -> {
                        if (a.getUpdateAt() == null && b.getUpdateAt() == null) return 0;
                        if (a.getUpdateAt() == null) return 1;
                        if (b.getUpdateAt() == null) return -1;
                        return b.getUpdateAt().compareTo(a.getUpdateAt());
                    })
                    .collect(Collectors.toList());

            // 分页
            int total = filteredApps.size();
            int start = (pageNum - 1) * pageSize;
            int end = Math.min(start + pageSize, total);

            PageResult<IceApp> pageResult = new PageResult<>();
            pageResult.setList(start < total ? filteredApps.subList(start, end) : Collections.emptyList());
            pageResult.setTotal((long) total);
            pageResult.setPages((total + pageSize - 1) / pageSize);
            pageResult.setPageNum(pageNum);
            pageResult.setPageSize(pageSize);
            return pageResult;
        } catch (IOException e) {
            log.error("failed to list apps", e);
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public Set<String> getRegisterClients(int app) {
        return clientManager.getRegisterClients(app);
    }
}

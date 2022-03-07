package com.ice.server.rmi;

import com.ice.common.dto.IceTransferDto;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.Pair;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.rmi.common.client.IceRmiClientService;
import com.ice.rmi.common.model.RegisterInfo;
import com.ice.server.config.IceServerProperties;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
public final class IceRmiClientManager {

    private static final Map<Integer, Map<String, RmiClientInfo>> clientRmiMap = new ConcurrentHashMap<>();

    @Resource
    private IceServerProperties properties;

    private static ExecutorService executor;

    public Set<String> getRegisterClients(int app) {
        Map<String, RmiClientInfo> clientInfoMap = clientRmiMap.get(app);
        if (CollectionUtils.isEmpty(clientInfoMap)) {
            return null;
        }
        return clientInfoMap.keySet();
    }

    public void register(RegisterInfo register, IceRmiClientService clientService) {
//        register.setClientService(clientService);
        clientRmiMap.computeIfAbsent(register.getApp(), k -> new HashMap<>()).put(register.getAddress(), new RmiClientInfo(register.getApp(), register.getAddress(), clientService));
        log.info("client register success app:{} address:{}", register.getApp(), register.getAddress());
    }

    public void unRegister(RegisterInfo unRegister) {
        Map<String, RmiClientInfo> clientMap = clientRmiMap.get(unRegister.getApp());
        if (CollectionUtils.isEmpty(clientMap)) {
            return;
        }
        clientMap.remove(unRegister.getAddress());
    }

    public Pair<Integer, String> confClazzCheck(int app, String clazz, byte type) {
        Map<String, RmiClientInfo> clientMap = clientRmiMap.get(app);
        if (CollectionUtils.isEmpty(clientMap)) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        Collection<RmiClientInfo> clientInfoList = clientMap.values();
        if (CollectionUtils.isEmpty(clientInfoList)) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        RmiClientInfo clientInfo = clientInfoList.iterator().next();
        Pair<Integer, String> result;
        try {
            result = clientInfo.getClientService().confClazzCheck(clazz, type);
        } catch (Exception e) {
            clientMap.remove(clientInfo.getAddress());
            throw new ErrorCodeException(ErrorCode.REMOTE_RUN_ERROR, app, clientInfo.getAddress());
        }
        return result;
    }

    public List<String> update(int app, IceTransferDto dto) {
        if (dto == null) {
            return null;
        }
        Map<String, RmiClientInfo> clientMap = clientRmiMap.get(app);
        if (CollectionUtils.isEmpty(clientMap)) {
            log.warn("no available client app:" + app);
            return null;
        }
        for (RmiClientInfo clientInfo : clientMap.values()) {
            submitRelease(clientMap, clientInfo, dto);
        }
        return null;
    }

    private void submitRelease(Map<String, RmiClientInfo> clientMap, RmiClientInfo clientInfo, IceTransferDto dto) {
        executor.submit(() -> {
            try {
                clientInfo.getClientService().update(dto);
            } catch (RemoteException e) {
                clientMap.remove(clientInfo.getAddress());
                log.warn("remote client may down app:{} address:{}", clientInfo.getApp(), clientInfo.getAddress());
            }
        });
    }

    public IceShowConf getClientShowConf(int app, Long confId, String address) {
        Map<String, RmiClientInfo> clientMap = clientRmiMap.get(app);
        if (CollectionUtils.isEmpty(clientMap)) {
            throw new ErrorCodeException(ErrorCode.CLIENT_NOT_AVAILABLE, app, address);
        }
        RmiClientInfo clientInfo = clientMap.get(address);
        if (clientInfo == null) {
            throw new ErrorCodeException(ErrorCode.CLIENT_NOT_AVAILABLE, app, address);
        }
        IceShowConf result;
        try {
            result = clientInfo.getClientService().getShowConf(confId);
            result.setApp(app);
        } catch (Exception e) {
            clientMap.remove(clientInfo.getAddress());
            throw new ErrorCodeException(ErrorCode.REMOTE_RUN_ERROR, app, clientInfo.getAddress());
        }
        return result;
    }

    public List<IceContext> mock(int app, IcePack pack) {
        Map<String, RmiClientInfo> clientMap = clientRmiMap.get(app);
        if (CollectionUtils.isEmpty(clientMap)) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        Collection<RmiClientInfo> clientInfoList = clientMap.values();
        if (CollectionUtils.isEmpty(clientInfoList)) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        RmiClientInfo clientInfo = clientInfoList.iterator().next();
        List<IceContext> result;
        try {
            result = clientInfo.getClientService().mock(pack);
        } catch (Exception e) {
            clientMap.remove(clientInfo.getAddress());
            throw new ErrorCodeException(ErrorCode.REMOTE_RUN_ERROR, app, clientInfo.getAddress());
        }
        return result;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class RmiClientInfo {
        private int app;
        private String address;
        private IceRmiClientService clientService;
    }
}

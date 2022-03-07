package com.ice.server.rmi;

import com.ice.common.dto.IceTransferDto;
import com.ice.common.exception.IceException;
import com.ice.common.model.IceShowConf;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.rmi.common.client.IceRmiClientService;
import com.ice.server.config.IceServerProperties;
import com.ice.server.dao.mapper.IceRmiMapper;
import com.ice.server.dao.model.IceRmi;
import com.ice.server.dao.model.IceRmiExample;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.common.model.Pair;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public final class IceRmiClientManager implements InitializingBean {

    private static final Map<Integer, Map<String, RmiClientInfo>> clientRmiMap = new ConcurrentHashMap<>();

    @Resource
    private IceServerProperties properties;

    private static ExecutorService executor;

    @Resource
    private IceRmiMapper iceRmiMapper;

    public Set<String> getRegisterClients(int app) {
        Map<String, RmiClientInfo> clientInfoMap = clientRmiMap.get(app);
        if (CollectionUtils.isEmpty(clientInfoMap)) {
            return null;
        }
        return clientInfoMap.keySet();
    }

    public void registerClient(int app, String host, int port, IceRmiClientService clientService) {
        Map<String, RmiClientInfo> clientMap = clientRmiMap.computeIfAbsent(app, k -> new HashMap<>());
        String address = host + ":" + port;
        try {
            RmiClientInfo oldClientInfo = clientMap.get(address);
            if (oldClientInfo != null) {
                if (oldClientInfo.getId() != null) {
                    iceRmiMapper.deleteByPrimaryKey(oldClientInfo.getId());
                }
                clientMap.remove(address);
            }
//            IceRmiClientService clientService = (IceRmiClientService) LocateRegistry.getRegistry(host, port).lookup("IceRemoteClientService");
//            clientService.ping();
            IceRmi iceRmi = new IceRmi(app, host, port);
            iceRmiMapper.insertSelective(iceRmi);
            RmiClientInfo clientInfo = new RmiClientInfo();
            clientInfo.setAddress(address);
            clientInfo.setHost(host);
            clientInfo.setPort(port);
            clientInfo.setApp(app);
            clientInfo.setId(iceRmi.getId());
            clientInfo.setClientService(clientService);
            clientMap.put(address, clientInfo);
        } catch (Exception e) {
            throw new IceException("server connect client error app:" + app + "address:" + address, e);
        }
    }

    public void registerClientInit(IceRmi rmi) {
        String address = rmi.getHost() + ":" + rmi.getPort();
        try {
            IceRmiClientService clientService = (IceRmiClientService) LocateRegistry.getRegistry(rmi.getHost(), rmi.getPort()).lookup("IceRemoteClientService");
            clientService.ping();
            clientRmiMap.computeIfAbsent(rmi.getApp(), k -> new HashMap<>()).put(address, new RmiClientInfo(rmi.getApp(), rmi.getId(), clientService, address, rmi.getHost(), rmi.getPort()));
        } catch (Exception e) {
            log.warn("server connect client failed app:{} address:{}", rmi.getApp(), address);
            iceRmiMapper.deleteByPrimaryKey(rmi.getId());
        }
    }

    public void unRegisterClient(int app, String host, int port) {
        String address = host + ":" + port;
        Map<String, RmiClientInfo> clientMap = clientRmiMap.get(app);
        if (CollectionUtils.isEmpty(clientMap)) {
            return;
        }
        errorHandle(clientMap, clientMap.get(address));
    }

    private void errorHandle(Map<String, RmiClientInfo> clientMap, RmiClientInfo clientInfo) {
        if (clientInfo != null) {
            if (clientInfo.getId() != null) {
                iceRmiMapper.deleteByPrimaryKey(clientInfo.getId());
            }
            clientMap.remove(clientInfo.address);
        }
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
            result = clientInfo.clientService.confClazzCheck(clazz, type);
        } catch (Exception e) {
            errorHandle(clientMap, clientInfo);
            throw new ErrorCodeException(ErrorCode.REMOTE_RUN_ERROR, app, clientInfo.address);
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
                clientInfo.clientService.update(dto);
            } catch (RemoteException e) {
                errorHandle(clientMap, clientInfo);
                log.warn("remote client may down app:{} address:{}", clientInfo.app, clientInfo.address);
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
            result = clientInfo.clientService.getShowConf(confId);
        } catch (Exception e) {
            errorHandle(clientMap, clientInfo);
            throw new ErrorCodeException(ErrorCode.REMOTE_RUN_ERROR, app, clientInfo.address);
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
            result = clientInfo.clientService.mock(pack);
        } catch (Exception e) {
            errorHandle(clientMap, clientInfo);
            throw new ErrorCodeException(ErrorCode.REMOTE_RUN_ERROR, app, clientInfo.address);
        }
        return result;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        executor = new ThreadPoolExecutor(properties.getPool().getCoreSize(), properties.getPool().getMaxSize(),
                properties.getPool().getKeepAliveSeconds(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getPool().getQueueCapacity()), new ThreadPoolExecutor.CallerRunsPolicy());
        List<IceRmi> iceRmiList = iceRmiMapper.selectByExample(new IceRmiExample());
        if (!CollectionUtils.isEmpty(iceRmiList)) {
            for (IceRmi rmi : iceRmiList) {
                this.registerClientInit(rmi);
            }
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class RmiClientInfo {
        private int app;
        private Long id;
        private IceRmiClientService clientService;
        private String address;
        private String host;
        private int port;
    }
}

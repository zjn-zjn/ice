package com.ice.server.rmi;

import com.ice.common.dto.IceTransferDto;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.Pair;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.rmi.common.enums.RmiNetModeEnum;
import com.ice.rmi.common.model.ClientInfo;
import com.ice.rmi.common.model.ClientOneWayRequest;
import com.ice.rmi.common.model.ClientOneWayResponse;
import com.ice.server.config.IceServerProperties;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public final class IceRmiClientManager implements InitializingBean {

    private static final Map<Integer, Map<String, ClientInfo>> clientRmiTwoWayMap = new ConcurrentHashMap<>();

    private static final Map<Integer, Map<String, ClientInfo>> clientRmiOneWayMap = new ConcurrentHashMap<>();

    private static final Map<String, Collection<Pair<Object, ClientOneWayRequest>>> workMap = new ConcurrentHashMap<>();

    private static final Map<String, Set<Object>> lockMap = new ConcurrentHashMap<>();

    private static final Map<Object, ClientOneWayResponse> lockResponse = new ConcurrentHashMap<>();

    @Resource
    private IceServerProperties properties;

    private static ExecutorService executor;

    public Set<String> getRegisterClients(int app) {
        Map<String, ClientInfo> twoWayClientInfoMap = clientRmiTwoWayMap.get(app);
        Set<String> resultSets = null;
        if (!CollectionUtils.isEmpty(twoWayClientInfoMap)) {
            resultSets = new HashSet<>(twoWayClientInfoMap.keySet());
        }
        Map<String, ClientInfo> oneWayClientInfoMap = clientRmiOneWayMap.get(app);
        if (!CollectionUtils.isEmpty(oneWayClientInfoMap)) {
            if (resultSets == null) {
                return oneWayClientInfoMap.keySet();
            } else {
                resultSets.addAll(oneWayClientInfoMap.keySet());
            }
        }

        return resultSets;
    }

    public void register(ClientInfo client) {
        if (client.getMode() == RmiNetModeEnum.TWO_WAY) {
            clientRmiTwoWayMap.computeIfAbsent(client.getApp(), k -> new ConcurrentHashMap<>()).put(client.getAddress(), client);
        } else {
            clientRmiOneWayMap.computeIfAbsent(client.getApp(), k -> new ConcurrentHashMap<>()).put(client.getAddress(), client);
        }
    }

    public void unRegister(ClientInfo client) {

        Map<String, ClientInfo> clientMap;
        if (client.getMode() == RmiNetModeEnum.TWO_WAY) {
            clientMap = clientRmiTwoWayMap.get(client.getApp());
        } else {
            clientMap = clientRmiOneWayMap.get(client.getApp());
        }
        if (CollectionUtils.isEmpty(clientMap)) {
            return;
        }
        clientMap.remove(client.getAddress());
        log.info("client unregister success app:{} address:{} mode:{}", client.getApp(), client.getAddress(), client.getMode());
    }

    public Pair<Integer, String> confClazzCheck(int app, String clazz, byte type) {
        Map<String, ClientInfo> clientMap = clientRmiTwoWayMap.get(app);
        if (CollectionUtils.isEmpty(clientMap)) {
            clientMap = clientRmiOneWayMap.get(app);
            if (CollectionUtils.isEmpty(clientMap)) {
                throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
            }
        }
        Collection<ClientInfo> clientInfoList = clientMap.values();
        if (CollectionUtils.isEmpty(clientInfoList)) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        ClientInfo clientInfo = clientInfoList.iterator().next();
        Pair<Integer, String> result;
        if (clientInfo.getMode() == RmiNetModeEnum.TWO_WAY) {
            try {
                result = clientInfo.getClientService().confClazzCheck(clazz, type);
            } catch (Exception e) {
                clientMap.remove(clientInfo.getAddress());
                throw new ErrorCodeException(ErrorCode.REMOTE_RUN_ERROR, app, clientInfo.getAddress());
            }
            return result;
        }
        //one way
        ClientOneWayRequest request = new ClientOneWayRequest();
        request.setClazz(clazz);
        request.setType(type);
        request.setApp(app);
        request.setName("confClazzCheck");
        ClientOneWayResponse response = getFromClient(clientInfo.getAddress(), request);
        return response.getClazzCheck();
    }

    public synchronized List<ClientOneWayRequest> getWorks(ClientInfo client) {
        register(client);
        Collection<Pair<Object, ClientOneWayRequest>> list = workMap.get(client.getAddress());
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        List<ClientOneWayRequest> works = new ArrayList<>(list.size());
        for (Pair<Object, ClientOneWayRequest> pair : list) {
            lockMap.computeIfAbsent(client.getAddress() + "-" + pair.getValue().getName(), k -> Collections.synchronizedSet(new HashSet<>())).add(pair.getKey());
            works.add(pair.getValue());
        }
        workMap.remove(client.getAddress());
        return works;
    }

    public void doneWork(ClientOneWayRequest request, ClientOneWayResponse response) {
        String key = response.getAddress() + "-" + request.getName();
        Set<Object> lockSet = lockMap.get(key);
        if (!CollectionUtils.isEmpty(lockSet)) {
            for (Object lock : lockSet) {
                synchronized (lock) {
                    lockResponse.put(lock, response);
                    lockSet.remove(lock);
                    lock.notify();
                }
            }
        }
    }

    public ClientOneWayResponse getFromClient(String address, ClientOneWayRequest request) {
        Object lock = new Object();
        String key = address + "-" + request.getName();
        synchronized (lock) {
            Pair<Object, ClientOneWayRequest> work = new Pair<>(lock, request);
            Collection<Pair<Object, ClientOneWayRequest>> list = workMap.computeIfAbsent(address, k -> Collections.synchronizedCollection(new LinkedList<>()));
            list.add(work);
            try {
                lock.wait(5800);
            } catch (InterruptedException e) {
                //ignore
            }
            ClientOneWayResponse res = lockResponse.get(lock);
            if (res == null) {
                list.remove(work);
                lockResponse.remove(lock);
                Set<Object> lockSet = lockMap.get(key);
                if (!CollectionUtils.isEmpty(lockSet)) {
                    lockSet.remove(lock);
                }
                Map<String, ClientInfo> clientMap = clientRmiOneWayMap.get(request.getApp());
                if (!CollectionUtils.isEmpty(clientMap)) {
                    clientMap.remove(address);
                }
                throw new ErrorCodeException(ErrorCode.REMOTE_RUN_ERROR, request.getApp(), address);
            } else {
                lockResponse.remove(lock);
            }
            return res;
        }
    }

    public List<String> update(int app, IceTransferDto dto) {
        if (dto == null) {
            return null;
        }
        Map<String, ClientInfo> clientMap = clientRmiTwoWayMap.get(app);
        if (!CollectionUtils.isEmpty(clientMap)) {
            for (ClientInfo clientInfo : clientMap.values()) {
                submitRelease(clientMap, clientInfo, dto);
            }
        }

        Map<String, ClientInfo> oneWayClientMap = clientRmiOneWayMap.get(app);
        if (!CollectionUtils.isEmpty(oneWayClientMap)) {
            for (ClientInfo clientInfo : oneWayClientMap.values()) {
                submitReleaseOneWay(clientInfo, dto);
            }
        }
        return null;
    }

    private void submitRelease(Map<String, ClientInfo> clientMap, ClientInfo clientInfo, IceTransferDto dto) {
        executor.submit(() -> {
            try {
                clientInfo.getClientService().update(dto);
            } catch (RemoteException e) {
                clientMap.remove(clientInfo.getAddress());
                log.warn("remote client may down app:{} address:{}", clientInfo.getApp(), clientInfo.getAddress());
            }
        });
    }

    private void submitReleaseOneWay(ClientInfo clientInfo, IceTransferDto dto) {
        executor.submit(() -> {
            try {
                ClientOneWayRequest request = new ClientOneWayRequest();
                request.setDto(dto);
                request.setApp(clientInfo.getApp());
                request.setName("update");
                getFromClient(clientInfo.getAddress(), request);
            } catch (Exception e) {
                log.warn("remote client update failed app:{} address:{} mode:{}", clientInfo.getApp(), clientInfo.getAddress(), clientInfo.getMode());
            }
        });
    }

    public IceShowConf getClientShowConf(int app, Long confId, String address) {
        Map<String, ClientInfo> clientMap = clientRmiTwoWayMap.get(app);
        if (CollectionUtils.isEmpty(clientMap)) {
            clientMap = clientRmiOneWayMap.get(app);
            if (CollectionUtils.isEmpty(clientMap)) {
                throw new ErrorCodeException(ErrorCode.CLIENT_NOT_AVAILABLE, app, address);
            }
        }
        Pair<Map<String, ClientInfo>, ClientInfo> pair = getRegisterInfo(app, address);
        if (pair == null) {
            clientMap.remove(address);
            throw new ErrorCodeException(ErrorCode.CLIENT_NOT_AVAILABLE, app, address);
        }
        ClientInfo clientInfo = pair.getValue();
        if (clientInfo == null) {
            clientMap.remove(address);
            throw new ErrorCodeException(ErrorCode.CLIENT_NOT_AVAILABLE, app, address);
        }
        IceShowConf result;
        if (clientInfo.getMode() == RmiNetModeEnum.TWO_WAY) {
            try {
                result = clientInfo.getClientService().getShowConf(confId);
                result.setApp(app);
            } catch (Exception e) {
                pair.getKey().remove(clientInfo.getAddress());
                throw new ErrorCodeException(ErrorCode.REMOTE_RUN_ERROR, app, clientInfo.getAddress());
            }
            return result;
        }
        ClientOneWayRequest request = new ClientOneWayRequest();
        request.setConfId(confId);
        request.setApp(app);
        request.setName("getShowConf");
        ClientOneWayResponse response = getFromClient(clientInfo.getAddress(), request);
        return response.getShowConf();
    }

    private Pair<Map<String, ClientInfo>, ClientInfo> getRegisterInfo(int app, String address) {
        Map<String, ClientInfo> clientMap = clientRmiTwoWayMap.get(app);
        if (CollectionUtils.isEmpty(clientMap)) {
            clientMap = clientRmiOneWayMap.get(app);
            if (CollectionUtils.isEmpty(clientMap)) {
                return null;
            } else {
                return new Pair<>(clientMap, clientMap.get(address));
            }
        } else {
            ClientInfo clientInfo = clientMap.get(address);
            if (clientInfo == null) {
                clientMap = clientRmiOneWayMap.get(app);
                if (CollectionUtils.isEmpty(clientMap)) {
                    return null;
                } else {
                    return new Pair<>(clientMap, clientMap.get(address));
                }
            } else {
                return new Pair<>(clientMap, clientInfo);
            }
        }
    }

    public List<IceContext> mock(int app, IcePack pack) {
        Map<String, ClientInfo> clientMap = clientRmiTwoWayMap.get(app);
        if (CollectionUtils.isEmpty(clientMap)) {
            clientMap = clientRmiOneWayMap.get(app);
            if (CollectionUtils.isEmpty(clientMap)) {
                throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
            }
        }
        Collection<ClientInfo> clientInfoList = clientMap.values();
        if (CollectionUtils.isEmpty(clientInfoList)) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        ClientInfo clientInfo = clientInfoList.iterator().next();
        List<IceContext> result;
        if (clientInfo.getMode() == RmiNetModeEnum.TWO_WAY) {
            try {
                result = clientInfo.getClientService().mock(pack);
            } catch (Exception e) {
                clientMap.remove(clientInfo.getAddress());
                throw new ErrorCodeException(ErrorCode.REMOTE_RUN_ERROR, app, clientInfo.getAddress());
            }
            return result;
        }
        ClientOneWayRequest request = new ClientOneWayRequest();
        request.setPack(pack);
        request.setApp(app);
        request.setName("mock");
        ClientOneWayResponse response = getFromClient(clientInfo.getAddress(), request);
        return response.getMockResults();
    }

    @Override
    public void afterPropertiesSet() {
        executor = new ThreadPoolExecutor(properties.getPool().getCoreSize(), properties.getPool().getMaxSize(),
                properties.getPool().getKeepAliveSeconds(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getPool().getQueueCapacity()), new ThreadPoolExecutor.CallerRunsPolicy());
    }
}

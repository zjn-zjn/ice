package com.ice.server.trans;

import com.ice.common.dto.IceTransferDto;
import com.ice.common.exception.IceException;
import com.ice.common.model.IceClientConf;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.rmi.common.client.IceRmiClientService;
import com.ice.server.dao.mapper.IceRmiMapper;
import com.ice.server.dao.model.IceRmi;
import com.ice.server.dao.model.IceRmiExample;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import javafx.util.Pair;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.rmi.registry.LocateRegistry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public final class IceRmiClientManager implements InitializingBean {

    private final static Random random = ThreadLocalRandom.current();

    private static Map<String, Pair<Long, IceRmiClientService>> map = new ConcurrentHashMap<>();

    private static Map<Integer, List<IceRmiClientService>> mapList = new ConcurrentHashMap<>();

    @Resource
    private IceRmiMapper iceRmiMapper;

    public synchronized void registerClient(int app, String host, int port) {
        List<IceRmiClientService> clientList = mapList.computeIfAbsent(app, k -> new ArrayList<>());
        String address = host + ":" + port;
        try {
            Pair<Long, IceRmiClientService> oldPair = map.get(address);
            if (oldPair != null) {
                iceRmiMapper.deleteByPrimaryKey(oldPair.getKey());
                clientList.remove(oldPair.getValue());
            }
            IceRmiClientService clientService = (IceRmiClientService) LocateRegistry.getRegistry(host, port).lookup("IceRemoteClientService");
            clientService.ping();
            IceRmi iceRmi = new IceRmi(app, host, port);
            iceRmiMapper.insertSelective(iceRmi);
            clientList.add(clientService);
            map.put(address, new Pair<>(iceRmi.getId(), clientService));
        } catch (Exception e) {
            throw new IceException("server connect client error " + host + ":" + port, e);
        }
    }

    public synchronized void registerClientInit(IceRmi rmi) {
        List<IceRmiClientService> clientList = mapList.computeIfAbsent(rmi.getApp(), k -> new ArrayList<>());
        String address = rmi.getHost() + ":" + rmi.getPort();
        try {
            IceRmiClientService clientService = (IceRmiClientService) LocateRegistry.getRegistry(rmi.getHost(), rmi.getPort()).lookup("IceRemoteClientService");
            clientService.ping();
            clientList.add(clientService);
            map.put(address, new Pair<>(rmi.getId(), clientService));
        } catch (Exception e) {
            log.warn("client connect failed app:{} address:{}", rmi.getApp(), (rmi.getHost() + ":" + rmi.getPort()));
            iceRmiMapper.deleteByPrimaryKey(rmi.getId());
        }
    }

    public void unRegisterClient(int app, String host, int port) {
        String address = host + ":" + port;
        Pair<Long, IceRmiClientService> pair = map.get(address);
        if (pair != null) {
            iceRmiMapper.deleteByPrimaryKey(pair.getKey());
            map.remove(address);
            List<IceRmiClientService> clientList = mapList.get(app);
            if (!CollectionUtils.isEmpty(clientList)) {
                clientList.remove(pair.getValue());
            }
        }
    }

    @SneakyThrows
    public static Set<Long> getAllConfId(int app, long iceId) {
        List<IceRmiClientService> clientList = mapList.get(app);
        if (CollectionUtils.isEmpty(clientList)) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        IceRmiClientService service = clientList.get(randomIndex(clientList.size()));
        return service.getAllConfId(iceId);
    }

    @SneakyThrows
    public static Pair<Integer, String> confClazzCheck(int app, String clazz, byte type) {
        List<IceRmiClientService> clientList = mapList.get(app);
        if (CollectionUtils.isEmpty(clientList)) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        IceRmiClientService service = clientList.get(randomIndex(clientList.size()));
        return service.confClazzCheck(clazz, type);
    }

    @SneakyThrows
    public static List<String> update(int app, IceTransferDto dto) {
        List<IceRmiClientService> clientList = mapList.get(app);
        if (CollectionUtils.isEmpty(clientList)) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        List<String> errList = new ArrayList<>();
        for (IceRmiClientService service : clientList) {
            //TODO
            errList.addAll(service.update(dto));
        }
        return errList;
    }

    @SneakyThrows
    public static Map<String, Object> getShowConf(int app, Long iceId) {
        List<IceRmiClientService> clientList = mapList.get(app);
        if (CollectionUtils.isEmpty(clientList)) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        IceRmiClientService service = clientList.get(randomIndex(clientList.size()));
        return service.getShowConf(iceId);
    }

    @SneakyThrows
    public static IceClientConf getConf(int app, Long confId) {
        List<IceRmiClientService> clientList = mapList.get(app);
        if (CollectionUtils.isEmpty(clientList)) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        IceRmiClientService service = clientList.get(randomIndex(clientList.size()));
        return service.getConf(confId);
    }

    @SneakyThrows
    public static List<IceContext> mock(int app, IcePack pack) {
        List<IceRmiClientService> clientList = mapList.get(app);
        if (CollectionUtils.isEmpty(clientList)) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        IceRmiClientService service = clientList.get(randomIndex(clientList.size()));
        return service.mock(pack);
    }

    private static int randomIndex(int size) {
        return random.nextInt(size);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        List<IceRmi> iceRmiList = iceRmiMapper.selectByExample(new IceRmiExample());
        if (!CollectionUtils.isEmpty(iceRmiList)) {
            for (IceRmi rmi : iceRmiList) {
                this.registerClientInit(rmi);
            }
        }
    }
}

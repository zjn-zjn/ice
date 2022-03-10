package com.ice.client.change;

import com.alibaba.fastjson.JSON;
import com.ice.client.config.IceClientProperties;
import com.ice.client.rmi.IceRmiClientServiceImpl;
import com.ice.client.utils.AddressUtils;
import com.ice.common.dto.IceTransferDto;
import com.ice.common.enums.RmiNetModeEnum;
import com.ice.common.exception.IceException;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.Pair;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceExecutor;
import com.ice.rmi.common.client.IceRmiClientService;
import com.ice.rmi.common.model.ClientInfo;
import com.ice.rmi.common.model.ClientOneWayRequest;
import com.ice.rmi.common.model.ClientOneWayResponse;
import com.ice.rmi.common.server.IceRmiServerService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * @author zjn
 */
@Slf4j
@Service
@DependsOn({"iceBeanFactory", "iceAddressUtils"})
public final class IceClientInit implements InitializingBean, DisposableBean {

    @Resource
    private IceClientProperties properties;

    @Resource
    private Registry iceServerRegistry;

    private Thread touchServerThread;

    /*
     * to avoid loss update msg in init,make init first
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        if (properties.getPool().getParallelism() <= 0) {
            IceExecutor.setExecutor(new ForkJoinPool());
        } else {
            IceExecutor.setExecutor(new ForkJoinPool(properties.getPool().getParallelism()));
        }
        log.info("ice client init start");
        IceRmiServerService remoteServerService;
        try {
            remoteServerService = (IceRmiServerService) iceServerRegistry.lookup("IceRmiServerService");
        } catch (Exception e) {
            throw new IceException("ice client connect server error, maybe server is down app:" + properties.getApp(), e);
        }
        IceRmiClientService clientService = new IceRmiClientServiceImpl();
        ClientInfo client = new ClientInfo(properties.getApp(), properties.getRmi().getMode(), AddressUtils.getAddress(), properties.getRmi().getMode() == RmiNetModeEnum.TWO_WAY ? (IceRmiClientService) UnicastRemoteObject.exportObject(clientService, properties.getRmi().getPort()) : null);
        try {
            remoteServerService.register(client);
        } catch (Exception e) {
            throw new IceException("ice client register error app:" + properties.getApp(), e);
        }
        IceTransferDto dto;
        try {
            dto = remoteServerService.getInitConfig(properties.getApp());
        } catch (Exception e) {
            throw new IceException("ice init error, maybe server is down app:" + properties.getApp(), e);
        }
        if (dto != null) {
            log.info("ice client init content:{}", JSON.toJSONString(dto));
            IceUpdate.update(dto);
            log.info("ice client init iceEnd success");
            IceRmiClientServiceImpl.initEnd(dto.getVersion());
        }
        touchServerThread = new Thread(new TouchServer(properties.getRmi().getMode(), client, remoteServerService, clientService, iceServerRegistry));
        touchServerThread.setDaemon(true);
        touchServerThread.start();
    }

    @Override
    public void destroy() {
        try {
            if (touchServerThread != null) {
                touchServerThread.interrupt();
            }
            IceRmiServerService serverService = (IceRmiServerService) iceServerRegistry.lookup("IceRmiServerService");
            serverService.unRegister(new ClientInfo(properties.getApp(), AddressUtils.getAddress(), properties.getRmi().getMode()));
        } catch (RemoteException | NotBoundException e) {
            log.warn("unregister ice client failed", e);
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public final static class TouchServer implements Runnable {

        private RmiNetModeEnum mode;

        private ClientInfo client;

        private IceRmiServerService serverService;

        private IceRmiClientService clientService;

        private Registry iceServerRegistry;

        @Override
        public void run() {
            while (true) {
                try {
                    if (mode == RmiNetModeEnum.TWO_WAY) {
                        //keep register on server
                        Thread.sleep(2000);
                        serverService.register(client);
                    } else {
                        Thread.sleep(1000);
                        //single way should get work from server
                        List<ClientOneWayRequest> works = serverService.getWorks(client);
                        if (!CollectionUtils.isEmpty(works)) {
                            for (ClientOneWayRequest work : works) {
                                ClientOneWayResponse workResult = new ClientOneWayResponse();
                                workResult.setAddress(client.getAddress());
                                switch (work.getName()) {
                                    case "confClazzCheck":
                                        Pair<Integer, String> checkResult = clientService.confClazzCheck(work.getClazz(), work.getType());
                                        workResult.setClazzCheck(checkResult);
                                        serverService.doneWork(work, workResult);
                                        break;
                                    case "update":
                                        List<String> updateResults = clientService.update(work.getDto());
                                        workResult.setUpdateResults(updateResults);
                                        serverService.doneWork(work, workResult);
                                        break;
                                    case "getShowConf":
                                        IceShowConf conf = clientService.getShowConf(work.getConfId());
                                        workResult.setShowConf(conf);
                                        serverService.doneWork(work, workResult);
                                        break;
                                    case "mock":
                                        List<IceContext> mockResults = clientService.mock(work.getPack());
                                        workResult.setMockResults(mockResults);
                                        serverService.doneWork(work, workResult);
                                        break;
                                    case "ping":
                                        serverService.doneWork(work, null);
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                    }
                } catch (InterruptedException ie) {
                    //destroy
                    return;
                } catch (Exception e) {
                    try {
                        //server down reconnect
                        serverService = (IceRmiServerService) iceServerRegistry.lookup("IceRmiServerService");
                    } catch (Exception le) {
                        //ignore
                    }
                }
            }
        }
    }
}

package com.ice.client.rmi;

import com.ice.client.config.IceClientProperties;
import com.ice.client.utils.AddressUtils;
import com.ice.rmi.common.client.IceRmiClientService;
import com.ice.rmi.common.server.IceRmiServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.rmi.PortableRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

@Slf4j
@Service
public class IceRmiClientInit implements InitializingBean, DisposableBean {

    @Resource
    private IceRmiClientService remoteClientService;

    @Resource
    private IceClientProperties properties;

    private static Registry registry;

    @Resource
    private Registry iceServerRegistry;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("create ice rmi client service...");
        IceRmiClientService clientService = (IceRmiClientService) UnicastRemoteObject.exportObject(remoteClientService, properties.getRmi().getCommunicatePort());
        registry = LocateRegistry.createRegistry(properties.getRmi().getPort());
        registry.rebind("IceRemoteClientService", clientService);
        log.info("create ice rmi client service...success");
    }

    @Override
    public void destroy() throws Exception {
        if (registry != null) {
            registry.unbind("IceRemoteClientService");
            UnicastRemoteObject.unexportObject(remoteClientService, true);
            PortableRemoteObject.unexportObject(registry);
            IceRmiServerService serverService = (IceRmiServerService) iceServerRegistry.lookup("IceRemoteServerService");
            serverService.unRegister(properties.getApp(), AddressUtils.getHost(), properties.getRmi().getPort());
        }
    }
}

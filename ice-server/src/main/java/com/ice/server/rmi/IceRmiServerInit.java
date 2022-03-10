package com.ice.server.rmi;

import com.ice.rmi.common.server.IceRmiServerService;
import com.ice.server.config.IceServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.rmi.PortableRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

@Slf4j
@Service
@DependsOn({"iceServerServiceImpl", "iceRmiClientManager"})
public class IceRmiServerInit implements InitializingBean, DisposableBean {

    @Resource
    private IceRmiServerService remoteServerService;

    @Resource
    private IceServerProperties properties;

    private static Registry registry;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("create ice rmi server service...");
        IceRmiServerService serverService = (IceRmiServerService) UnicastRemoteObject.exportObject(remoteServerService, properties.getRmi().getPort());
        registry = LocateRegistry.createRegistry(properties.getRmi().getPort());
        registry.rebind("IceRmiServerService", serverService);
        //waiting for client register
        Thread.sleep(2500);
        log.info("create ice rmi server service...success");
    }

    @Override
    public void destroy() throws Exception {
        if (registry != null) {
            registry.unbind("IceRmiServerService");
            UnicastRemoteObject.unexportObject(remoteServerService, true);
            PortableRemoteObject.unexportObject(registry);
        }
    }
}

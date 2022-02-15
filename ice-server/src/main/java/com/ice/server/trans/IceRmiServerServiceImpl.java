package com.ice.server.trans;

import com.ice.common.dto.IceTransferDto;
import com.ice.rmi.common.server.IceRmiServerService;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.rmi.PortableRemoteObject;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

@Service
@Slf4j
public class IceRmiServerServiceImpl implements IceRmiServerService, InitializingBean, DisposableBean {

    @Resource
    private IceServerService iceServerService;

    @Resource
    private IceRmiServerService remoteServerService;

    @Value("${ice.rmi.port:8088}")
    private int rmiPort;

    private static Registry registry;

    @Resource
    private IceRmiClientManager clientManage;

    @Override
    public IceTransferDto getInitConfig(int app) throws RemoteException {
        return iceServerService.getInitConfig(app);
    }

    @Override
    public void register(int app, String host, int port) throws RemoteException {
        clientManage.registerClient(app, host, port);
    }

    @Override
    public void unRegister(int app, String host, int port) throws RemoteException {
        clientManage.unRegisterClient(app, host, port);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("create ice rmi server service...");
        IceRmiServerService serverService = (IceRmiServerService) UnicastRemoteObject.exportObject(remoteServerService, 0);
        registry = LocateRegistry.createRegistry(rmiPort);
        registry.rebind("IceRemoteServerService", serverService);
        log.info("create ice rmi server service...success");
    }

    @Override
    public void destroy() throws Exception {
        if (registry != null) {
            registry.unbind("IceRemoteServerService");
            UnicastRemoteObject.unexportObject(remoteServerService, true);
            PortableRemoteObject.unexportObject(registry);
        }
    }
}

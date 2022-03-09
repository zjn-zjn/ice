package com.ice.server.rmi;

import com.ice.common.dto.IceTransferDto;
import com.ice.rmi.common.model.ClientInfo;
import com.ice.rmi.common.model.ClientOneWayRequest;
import com.ice.rmi.common.model.ClientOneWayResponse;
import com.ice.rmi.common.server.IceRmiServerService;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.rmi.RemoteException;
import java.util.List;

@Service
@Slf4j
public class IceRmiServerServiceImpl implements IceRmiServerService {

    @Resource
    private IceServerService iceServerService;

    @Resource
    private IceRmiClientManager rmiClientManager;

    @Override
    public IceTransferDto getInitConfig(int app) throws RemoteException {
        return iceServerService.getInitConfig(app);
    }

    @Override
    public void register(ClientInfo client) throws RemoteException {
        rmiClientManager.register(client);
    }

    @Override
    public void unRegister(ClientInfo client) throws RemoteException {
        rmiClientManager.unRegister(client);
    }

    @Override
    public List<ClientOneWayRequest> getWorks(ClientInfo client) throws RemoteException {
        return rmiClientManager.getWorks(client);
    }

    @Override
    public void doneWork(ClientOneWayRequest request, ClientOneWayResponse response) throws RemoteException {
        rmiClientManager.doneWork(request, response);
    }

}

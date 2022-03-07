package com.ice.server.rmi;

import com.alibaba.fastjson.JSON;
import com.ice.common.dto.IceTransferDto;
import com.ice.rmi.common.client.IceRmiClientService;
import com.ice.rmi.common.model.RegisterInfo;
import com.ice.rmi.common.server.IceRmiServerService;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.rmi.RemoteException;

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
    public void register(RegisterInfo register) throws RemoteException {
        rmiClientManager.register(register);
    }

    @Override
    public void unRegister(RegisterInfo unRegister) throws RemoteException {
        rmiClientManager.unRegister(unRegister);
    }
}

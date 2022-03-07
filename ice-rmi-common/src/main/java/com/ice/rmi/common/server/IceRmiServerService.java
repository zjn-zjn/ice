package com.ice.rmi.common.server;

import com.ice.common.dto.IceTransferDto;
import com.ice.rmi.common.client.IceRmiClientService;
import com.ice.rmi.common.model.RegisterInfo;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IceRmiServerService extends Remote {

    IceTransferDto getInitConfig(int app) throws RemoteException;

    void register(RegisterInfo register, IceRmiClientService clientService) throws RemoteException;

    void unRegister(RegisterInfo unRegister) throws RemoteException;
}

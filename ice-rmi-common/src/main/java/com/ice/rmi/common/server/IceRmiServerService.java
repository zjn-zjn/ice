package com.ice.rmi.common.server;

import com.ice.common.dto.IceTransferDto;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IceRmiServerService extends Remote {

    IceTransferDto getInitConfig(int app) throws RemoteException;

    void register(int app, String host, int port) throws RemoteException;

    void unRegister(int app, String host, int port) throws RemoteException;
}

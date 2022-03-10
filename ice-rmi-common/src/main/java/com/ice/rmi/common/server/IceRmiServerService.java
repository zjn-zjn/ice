package com.ice.rmi.common.server;

import com.ice.common.dto.IceTransferDto;
import com.ice.rmi.common.model.ClientInfo;
import com.ice.rmi.common.model.ClientOneWayRequest;
import com.ice.rmi.common.model.ClientOneWayResponse;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IceRmiServerService extends Remote {

    IceTransferDto getInitConfig(int app) throws RemoteException;

    void register(ClientInfo client) throws RemoteException;

    void unRegister(ClientInfo client) throws RemoteException;

    List<ClientOneWayRequest> getWorks(ClientInfo register) throws RemoteException;

    void doneWork(ClientOneWayRequest input, ClientOneWayResponse result) throws RemoteException;
}

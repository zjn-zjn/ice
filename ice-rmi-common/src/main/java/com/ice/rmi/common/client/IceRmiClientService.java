package com.ice.rmi.common.client;

import com.ice.common.dto.IceTransferDto;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.Pair;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IceRmiClientService extends Remote {

    Pair<Integer, String> confClazzCheck(String clazz, byte type) throws RemoteException;

    List<String> update(IceTransferDto dto) throws RemoteException;

    IceShowConf getShowConf(Long confId) throws RemoteException;

    List<IceContext> mock(IcePack pack) throws RemoteException;

    void ping() throws RemoteException;
}

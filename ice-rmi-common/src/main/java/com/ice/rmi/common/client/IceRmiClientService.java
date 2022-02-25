package com.ice.rmi.common.client;

import com.ice.common.dto.IceTransferDto;
import com.ice.common.model.IceShowConf;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import javafx.util.Pair;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;

public interface IceRmiClientService extends Remote {

//    Set<Long> getAllConfId(Long iceId) throws RemoteException;

    Pair<Integer, String> confClazzCheck(String clazz, byte type) throws RemoteException;

    List<String> update(IceTransferDto dto) throws RemoteException;

//    Map<String, Object> getShowConf(Long iceId) throws RemoteException;

    IceShowConf getShowConf(Long confId) throws RemoteException;

    List<IceContext> mock(IcePack pack) throws RemoteException;

    void ping() throws RemoteException;
}

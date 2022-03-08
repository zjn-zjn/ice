package com.ice.client.change;

import com.alibaba.fastjson.JSON;
import com.ice.client.config.IceClientProperties;
import com.ice.client.rmi.IceRmiClientServiceImpl;
import com.ice.client.utils.AddressUtils;
import com.ice.common.dto.IceTransferDto;
import com.ice.common.exception.IceException;
import com.ice.core.utils.IceExecutor;
import com.ice.rmi.common.model.RegisterInfo;
import com.ice.rmi.common.server.IceRmiServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author zjn
 */
@Slf4j
@Service
@DependsOn({"iceBeanFactory", "iceAddressUtils"})
public final class IceClientInit implements InitializingBean, DisposableBean {

    @Resource
    private IceClientProperties properties;

    @Resource
    private Registry iceServerRegistry;

    /*
     * to avoid loss update msg in init,make init first
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        IceExecutor.setExecutor(new ThreadPoolExecutor(properties.getPool().getCoreSize(), properties.getPool().getMaxSize(),
                properties.getPool().getKeepAliveSeconds(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getPool().getQueueCapacity()), new ThreadPoolExecutor.CallerRunsPolicy()));
        log.info("ice client init start");
        IceRmiServerService remoteServerService;
        try {
            remoteServerService = (IceRmiServerService) iceServerRegistry.lookup("IceRmiServerService");
        } catch (Exception e) {
            throw new IceException("ice client connect server error, maybe server is down app:" + properties.getApp(), e);
        }
        RegisterInfo registerInfo = new RegisterInfo(properties.getApp(), AddressUtils.getAddress(), new IceRmiClientServiceImpl(properties.getRmi().getPort()));
        try {
            remoteServerService.register(registerInfo);
        } catch (Exception e) {
            throw new IceException("ice client register error app:" + properties.getApp() + " maybe firewall", e);
        }
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    //when server restart, can register immediately
                    IceRmiServerService remoteServer = (IceRmiServerService) iceServerRegistry.lookup("IceRmiServerService");
                    remoteServer.register(registerInfo);
                } catch (RemoteException | NotBoundException e) {
                    //ignore
                }
            }
        }, 2000, 2000);
        IceTransferDto dto;
        try {
            dto = remoteServerService.getInitConfig(properties.getApp());
        } catch (Exception e) {
            throw new IceException("ice init error, maybe server is down app:" + properties.getApp(), e);
        }
        if (dto != null) {
            log.info("ice client init content:{}", JSON.toJSONString(dto));
            IceUpdate.update(dto);
            log.info("ice client init iceEnd success");
            IceRmiClientServiceImpl.initEnd(dto.getVersion());
        }
    }

    @Override
    public void destroy() {
        try {
            IceRmiServerService serverService = (IceRmiServerService) iceServerRegistry.lookup("IceRmiServerService");
            serverService.unRegister(new RegisterInfo(properties.getApp(), AddressUtils.getAddress()));
        } catch (RemoteException | NotBoundException e) {
            log.warn("unregister ice client failed", e);
        }
    }
}

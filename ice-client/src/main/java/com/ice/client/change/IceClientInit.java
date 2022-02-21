package com.ice.client.change;

import com.alibaba.fastjson.JSON;
import com.ice.client.config.IceClientProperties;
import com.ice.client.rmi.IceRmiClientServiceImpl;
import com.ice.client.utils.AddressUtils;
import com.ice.common.dto.IceTransferDto;
import com.ice.common.exception.IceException;
import com.ice.rmi.common.server.IceRmiServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.rmi.registry.Registry;

/**
 * @author zjn
 */
@Slf4j
@Service
@DependsOn({"iceRmiClientServiceImpl", "iceBeanFactory", "iceAddressUtils"})
public final class IceClientInit implements InitializingBean {

    @Resource
    private IceClientProperties properties;

    @Resource
    private Registry iceRegistry;

    /*
     * to avoid loss update msg in init,make init first
     */
    @Override
    public void afterPropertiesSet() {
        log.info("ice client init iceStart");
        IceRmiServerService remoteServerService;
        try {
            remoteServerService = (IceRmiServerService) iceRegistry.lookup("IceRemoteServerService");
        } catch (Exception e) {
            throw new IceException("ice client connect server error, maybe server is down app:" + properties.getApp(), e);
        }
        try {
            remoteServerService.register(properties.getApp(), AddressUtils.getHost(), properties.getRmi().getPort());
        } catch (Exception e) {
            throw new IceException("ice client register error app:" + properties.getApp(), e);
        }
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
}

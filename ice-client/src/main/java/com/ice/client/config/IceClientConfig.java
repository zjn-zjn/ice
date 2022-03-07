package com.ice.client.config;

import com.ice.client.rmi.IceRmiClientServiceImpl;
import com.ice.rmi.common.client.IceRmiClientService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * @author zjn
 */
@Configuration
@EnableConfigurationProperties(IceClientProperties.class)
public class IceClientConfig {

    @Resource
    private IceClientProperties properties;

    @Bean
    public Registry iceServerRegistry() throws Exception {
        return LocateRegistry.getRegistry(properties.getRmi().getServerHost(), properties.getRmi().getServerPort());
    }

    @Bean
    public IceRmiClientService iceRmiClientService() throws Exception {
        return (IceRmiClientService) UnicastRemoteObject.exportObject(new IceRmiClientServiceImpl(), properties.getRmi().getCommunicatePort());
    }
}

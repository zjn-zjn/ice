package com.ice.client.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * @author zjn
 */
@Configuration
@EnableConfigurationProperties(IceClientProperties.class)
public class IceClientConfig {

    @Resource
    private IceClientProperties properties;

    @Bean
    public Registry iceServerRegistry() throws RemoteException {
        return LocateRegistry.getRegistry(properties.getRmi().getServerHost(), properties.getRmi().getServerPort());
    }
}

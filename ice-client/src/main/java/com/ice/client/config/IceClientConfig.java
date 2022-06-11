package com.ice.client.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * @author zjn
 */
@Configuration
@EnableConfigurationProperties(IceClientProperties.class)
public class IceClientConfig {

    @Resource
    private IceClientProperties properties;
}

package com.ice.client.config;

import com.ice.core.client.IceNioClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * waiting ice nio client init
 *
 * @author waitmoon
 */
@Component
@DependsOn("iceSpringBeanFactory")
public class IceNioClientInit implements CommandLineRunner {

    @Resource
    private IceClientProperties properties;

    @Override
    public void run(String... args) throws Exception {
        IceNioClient iceNioClient = new IceNioClient(properties.getApp(), properties.getServer(), properties.getPool().getParallelism(), properties.getMaxFrameLength(), properties.getScan());
        iceNioClient.connect();
        Runtime.getRuntime().addShutdownHook(new Thread(iceNioClient::destroy));
    }
}
 
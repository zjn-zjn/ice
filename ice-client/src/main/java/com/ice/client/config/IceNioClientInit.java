package com.ice.client.config;

import com.ice.core.client.IceNioClient;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.IOException;

/**
 * waiting ice nio client init
 *
 * @author waitmoon
 */
@Component
@DependsOn("iceSpringBeanFactory")
public class IceNioClientInit {

    @Resource
    private IceClientProperties properties;

    private IceNioClient iceNioClient;

    @PostConstruct
    public void init() throws IOException, InterruptedException {
        iceNioClient = new IceNioClient(properties.getApp(), properties.getServer(), properties.getPool().getParallelism(), properties.getMaxFrameLength(), properties.getScan());
        iceNioClient.connect();
    }

    @PreDestroy
    public void destroy() {
        if (iceNioClient != null) {
            iceNioClient.destroy();
        }
    }
}
 
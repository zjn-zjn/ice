package com.ice.client.config;

import com.ice.core.client.IceNioClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * waiting ice nio client init
 * @author waitmoon
 */
@Component
@DependsOn("iceSpringBeanFactory")
public class IceNioClientInit implements InitializingBean, DisposableBean {

    @Resource
    private IceClientProperties properties;

    private IceNioClient iceNioClient;

    @Override
    public void afterPropertiesSet() throws IOException {
        iceNioClient = new IceNioClient(properties.getApp(), properties.getServer(), properties.getPool().getParallelism(), properties.getMaxFrameLength(), properties.getScan());
        iceNioClient.connect();
    }

    @Override
    public void destroy() {
        if (iceNioClient != null) {
            iceNioClient.destroy();
        }
    }
}
 
package com.ice.client.config;

import com.ice.core.nio.IceNioClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * @author zjn
 * init the ice nio client
 */
@Component
@DependsOn("iceBeanFactory")
public final class IceSpringClient implements InitializingBean, DisposableBean {

    private IceNioClient iceNioClient;

    @Resource
    private IceClientProperties properties;

    @Override
    public void afterPropertiesSet() throws IOException {
        iceNioClient = IceNioClient.open(properties.getApp(), properties.getServer(), properties.getPool().getParallelism());
    }

    @Override
    public void destroy() {
        if (iceNioClient != null) {
            iceNioClient.destroy();
        }
    }
}

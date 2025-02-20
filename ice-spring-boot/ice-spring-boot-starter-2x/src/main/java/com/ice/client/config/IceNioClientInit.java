package com.ice.client.config;

import com.ice.core.client.IceNioClient;
import com.ice.core.client.ha.IceServerHaDiscovery;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * waiting ice nio client init
 *
 * @author waitmoon
 */
@Component
@DependsOn("iceSpringBeanFactory")
public class IceNioClientInit implements InitializingBean, DisposableBean {

    @Autowired
    private IceClientProperties properties;

    private IceNioClient iceNioClient;

    @Autowired(required = false)
    private IceServerHaDiscovery iceServerHaDiscovery;

    @Override
    public void afterPropertiesSet() throws Exception {
        iceNioClient = new IceNioClient(properties.getApp(), properties.getServer(), properties.getPool().getParallelism(), properties.getMaxFrameLength(), properties.getScan(), properties.getInitRetryTimes(), properties.getInitRetrySleepMs(), iceServerHaDiscovery);
        iceNioClient.start();
    }

    public void destroy() {
        if (iceNioClient != null) {
            iceNioClient.destroy();
        }
    }
}
 
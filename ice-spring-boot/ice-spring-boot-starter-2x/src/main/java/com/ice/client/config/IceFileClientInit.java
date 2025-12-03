package com.ice.client.config;

import com.ice.core.client.IceFileClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * 初始化基于文件系统的Ice客户端
 *
 * @author waitmoon
 */
@Component
@DependsOn("iceSpringBeanFactory")
public class IceFileClientInit implements InitializingBean, DisposableBean {

    @Autowired
    private IceClientProperties properties;

    private IceFileClient iceFileClient;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (properties.getStorage() == null || properties.getStorage().getPath() == null) {
            throw new IllegalArgumentException("ice.storage.path must be configured");
        }

        iceFileClient = new IceFileClient(
                properties.getApp(),
                properties.getStorage().getPath(),
                properties.getPool().getParallelism(),
                properties.getScan(),
                properties.getPollInterval(),
                properties.getHeartbeatInterval()
        );
        iceFileClient.start();
    }

    @Override
    public void destroy() {
        if (iceFileClient != null) {
            iceFileClient.destroy();
        }
    }

    public IceFileClient getIceFileClient() {
        return iceFileClient;
    }
}


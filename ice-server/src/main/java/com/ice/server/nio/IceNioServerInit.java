package com.ice.server.nio;

import com.ice.server.config.IceServerProperties;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author waitmoon
 */
@Component
@Slf4j
public class IceNioServerInit implements InitializingBean, DisposableBean {

    @Resource
    private IceServerProperties properties;

    @Resource
    private IceServerService serverService;

    @Value("${server.port}")
    private int serverPort;

    private IceNioServer iceNioServer;

    @Override
    public void afterPropertiesSet() throws Exception {
        iceNioServer = new IceNioServer(properties, serverService, serverPort);
        try {
            iceNioServer.start();
        } catch (Throwable t) {
            iceNioServer.destroy();
            throw new RuntimeException("ice nio server start error", t);
        }
    }

    @Override
    public void destroy() throws Exception {
        if (iceNioServer != null) {
            iceNioServer.destroy();
        }
    }
}
 